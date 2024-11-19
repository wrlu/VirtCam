package com.wrlus.virtcam.hook;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import com.wrlus.virtcam.utils.Config;
import com.wrlus.virtcam.utils.VideoUtils;
import com.wrlus.xposed.framework.HookInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by wrlu on 2024/10/17.
 */
public class Camera2Hooker implements HookInterface {
    private static final String TAG = "VirtCamera-2";
    private int addTargetSurfaceCount = 0;
    private final Map<Surface, CameraHookResource> surfaceHookResourceMap =
            new ConcurrentHashMap<>();
    private final Set<ImageReader> imageReaderSet =
            Collections.synchronizedSet(new HashSet<>());
    private final Map<ImageReader, ImageHookResource> imageInjectResourceMap =
            new ConcurrentHashMap<>();
    private final Map<ImageReader, ImageHookResource> dumpFrameResourceMap =
            new ConcurrentHashMap<>();
    private final File baseFile;

    public Camera2Hooker(File baseFile) {
        this.baseFile = baseFile;
    }

    @Override
    public void onHookPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        startHook(loadPackageParam.classLoader);
    }

    private void startHook(ClassLoader classLoader) {
        hookImageReader();
        hookCamera2(classLoader);
        if (Config.enableCamera2ImageInject || Config.enableCamera2DumpFrame) {
            hookAcquireNextImage();
        }
        if (Config.enableCamera2ImageInject) {
            hookGetPlanes(classLoader);
        }
    }

    public void hookImageReader() {
        XposedHelpers.findAndHookMethod(ImageReader.class, "newInstance",
                int.class, int.class, int.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        int width = (int) param.args[0];
                        int height = (int) param.args[1];
                        int format = (int) param.args[2];
                        int maxImages = (int) param.args[3];
                        ImageReader imageReader = (ImageReader) param.getResult();
                        hookImageReaderNewInstance(width, height, format, maxImages, imageReader);
                    }
                });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            XposedHelpers.findAndHookMethod(ImageReader.class, "newInstance",
                    int.class, int.class, int.class, int.class, long.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            int width = (int) param.args[0];
                            int height = (int) param.args[1];
                            int format = (int) param.args[2];
                            int maxImages = (int) param.args[3];
                            ImageReader imageReader = (ImageReader) param.getResult();
                            hookImageReaderNewInstance(width, height, format, maxImages, imageReader);
                        }
                    });
        }
        XposedHelpers.findAndHookMethod(ImageReader.class, "close", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                ImageReader imageReader = (ImageReader) param.thisObject;
                imageReaderSet.remove(imageReader);
            }
        });
    }

    private void hookImageReaderNewInstance(int width, int height, int format, int maxImages,
                                            ImageReader imageReader) {
        try {
            throw new Exception("check");
        } catch (Exception e) {
            if (matchStackTrace(e.getStackTrace(), Camera2Hooker.class.getName(),
                    "findAndReplaceSurface")) {
                return;
            }
        }
        Log.d(TAG, "ImageReader#newInstance: width=" + width + " , height=" + height +
                " ,format=" + format + " ,maxImages=" + maxImages);
        try {
            imageReaderSet.add(imageReader);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "This imageReader: " + imageReader +
                    " has already added to the imageReaderSet. ", e);
        }
    }

    public void hookCamera2(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod("android.hardware.camera2.impl.CameraDeviceImpl",
                classLoader, "createCaptureSession", List.class,
                CameraCaptureSession.StateCallback.class, Handler.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Log.w(TAG, "Before CameraDeviceImpl createCaptureSession");
                        List<Surface> outputs = (List<Surface>) param.args[0];
                        List<Surface> fakeOutputs = new ArrayList<>();
                        int i = 1;
                        for (Surface output : outputs) {
                            Log.d(TAG, "Output surface: " + output);
                            CameraHookResource resource = surfaceHookResourceMap
                                    .getOrDefault(output, replaceSurface(output, i));
                            boolean isReuse = surfaceHookResourceMap.containsKey(output);
                            if (resource != null) {
                                fakeOutputs.add(resource.fakeSurface);
                                if (isReuse) {
                                    resource.isConfigured = true;
                                } else {
                                    surfaceHookResourceMap.put(output, resource);
                                }
                                Log.d(TAG, (isReuse ? "Reuse" : "Create") +
                                        " fakeSurface in createCaptureSession: " +
                                        output + " -> " + resource.fakeSurface);
                            } else {
                                Log.w(TAG, "Generate camera hook resource failed" +
                                        ", use original one.");
                                fakeOutputs.add(output);
                            }
                            ++i;
                        }
                        param.args[0] = fakeOutputs;
                        Log.w(TAG, "createCaptureSession: " +
                                "replaced with " + (i - 1) + " fake surfaces !!!");
                    }
                });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            XposedHelpers.findAndHookMethod("android.hardware.camera2.impl.CameraDeviceImpl",
                    classLoader, "createCaptureSession", SessionConfiguration.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Log.w(TAG, "Before CameraDeviceImpl createCaptureSession " +
                                    "(SessionConfiguration)");
                            SessionConfiguration config = (SessionConfiguration) param.args[0];
                            List<OutputConfiguration> outputConfigs = config.getOutputConfigurations();
                            List<OutputConfiguration> fakeOutputConfigs = new ArrayList<>();
                            int i = 1;
                            for (OutputConfiguration outputConfig : outputConfigs) {
                                Log.d(TAG, "Output config: " + outputConfig);
                                Surface output = outputConfig.getSurface();
                                CameraHookResource resource = surfaceHookResourceMap
                                        .getOrDefault(output, replaceSurface(output, i));
                                boolean isReuse = surfaceHookResourceMap.containsKey(output);
                                if (resource != null) {
                                    fakeOutputConfigs.add(new OutputConfiguration(resource.fakeSurface));
                                    if (isReuse) {
                                        resource.isConfigured = true;
                                    } else {
                                        surfaceHookResourceMap.put(output, resource);
                                    }
                                    Log.d(TAG, (isReuse ? "Reuse" : "Create") +
                                            " fakeSurface in createCaptureSession: " +
                                            output + " -> " + resource.fakeSurface);
                                } else {
                                    Log.w(TAG, "Generate camera hook resource failed" +
                                            ", use original one.");
                                    fakeOutputConfigs.add(outputConfig);
                                }
                                ++i;
                            }
                            SessionConfiguration fakeConfig = new SessionConfiguration(
                                    config.getSessionType(), fakeOutputConfigs, config.getExecutor(),
                                    config.getStateCallback());
                            param.args[0] = fakeConfig;
                            Log.w(TAG, "createCaptureSession (SessionConfiguration): " +
                                    "replaced with " + (i - 1) + " fake surfaces !!!");
                        }
                    });
        }
        XposedHelpers.findAndHookMethod(CaptureRequest.Builder.class,
                "addTarget", Surface.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Log.w(TAG, "Before CaptureRequest.Builder addTarget");
                        Surface target = (Surface) param.args[0];
                        Log.d(TAG, "Add target surface: " + target);
                        CameraHookResource resource = surfaceHookResourceMap
                                .getOrDefault(target, replaceSurface(target, addTargetSurfaceCount));
                        boolean isReuse = surfaceHookResourceMap.containsKey(target);
                        if (resource != null) {
                            param.args[0] = resource.fakeSurface;
                            if (isReuse) {
                                resource.isConfigured = true;
                            } else {
                                surfaceHookResourceMap.put(target, resource);
                            }
                            Log.d(TAG, (isReuse ? "Reuse" : "Create") +
                                    " fakeSurface in addTarget: " +
                                    target + " -> " + resource.fakeSurface);
                        } else {
                            Log.w(TAG, "Generate camera hook resource failed" +
                                    ", use original one.");
                        }
                    }
                });
        XposedHelpers.findAndHookMethod("android.hardware.camera2.impl.CameraCaptureSessionImpl",
                classLoader, "setRepeatingRequest", CaptureRequest.class,
                CameraCaptureSession.CaptureCallback.class, Handler.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Log.w(TAG, "Before CameraCaptureSessionImpl setRepeatingRequest");
                        for (Surface output : surfaceHookResourceMap.keySet()) {
                            if (output != null && output.isValid()) {
                                CameraHookResource resource = surfaceHookResourceMap.get(output);
                                if (resource == null) {
                                    continue;
                                }
                                boolean isTexture = isCreateBySurfaceTexture(output);
                                // Use MediaPlayer to inject SurfaceTexture.
                                if (isTexture && resource.mediaPlayer == null
                                        && resource.isConfigured) {
                                    File videoFile = new File(baseFile, Config.videoPath);
                                    if (videoFile.exists()) {
                                        resource.mediaPlayer =
                                                VideoUtils.playVideo(videoFile, output);
                                        Log.d(TAG, "Start playing video" +
                                                " on output surface: " + output
                                                + ", resource: " + resource);
                                        continue;
                                    }
                                }
                                // Use MediaCodec to inject ImageReader.
                                if (!isTexture && resource.mediaCodec == null
                                        && resource.isConfigured) {
                                    // Only support YUV_420_888 format now.
                                    if (resource.fakeImageReader.getImageFormat()
                                            != ImageFormat.YUV_420_888) {
                                        return;
                                    }
                                    // Fake image reader has same parameters with the origin one.
                                    File rotatedVideoFile = new File(baseFile,
                                            selectSpecResolutionVideo(resource.fakeImageReader));
                                    if (!rotatedVideoFile.exists()) {
                                        rotatedVideoFile = new File(baseFile,
                                                Config.rotatedVideoPath);
                                    }
                                    // Use rotated video in ImageReader hook.
                                    if (rotatedVideoFile.exists()) {
                                        resource.mediaCodec = VideoUtils.decodeVideoToSurface(
                                                rotatedVideoFile, output);
                                        Log.d(TAG, "Start decode video on output surface: "
                                                + output + ", resource: " + resource);
                                    }
                                }
                            }
                        }
                    }
                });
        XposedHelpers.findAndHookMethod("android.hardware.camera2.impl.CameraDeviceImpl",
                classLoader, "close", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Log.w(TAG, "After CameraDeviceImpl close");
                        for (CameraHookResource resource : surfaceHookResourceMap.values()) {
                            if (resource.fakeSurfaceTexture != null) resource.fakeSurfaceTexture.release();
                            if (resource.fakeImageReader != null) resource.fakeImageReader.close();
                            if (resource.mediaPlayer != null) resource.mediaPlayer.release();
                            if (resource.mediaCodec != null) resource.mediaCodec.release();
                        }
                        addTargetSurfaceCount = 0;
                        surfaceHookResourceMap.clear();
                    }
                });
    }

    private CameraHookResource replaceSurface(Surface output, int index) {
        // We don't want to replace any surface when dumping surface.
        if (Config.enableCamera2DumpFrame)
            return null;
        if (isCreateBySurfaceTexture(output)) {
            SurfaceTexture fakeSurfaceTexture = new SurfaceTexture(10 + index);
            return new CameraHookResource(new Surface(fakeSurfaceTexture), fakeSurfaceTexture);
        } else {
            // We don't want to modify the original image reader when doing image inject tasks.
            if (Config.enableCamera2ImageInject)
                return null;
            for (ImageReader imageReader : imageReaderSet) {
                if (output.equals(imageReader.getSurface())) {
                    // Create a fake image reader with same parameters.
                    ImageReader fakeImageReader = ImageReader
                            .newInstance(imageReader.getWidth(), imageReader.getHeight(),
                                    imageReader.getImageFormat(), imageReader.getMaxImages());
                    return new CameraHookResource(fakeImageReader.getSurface(), fakeImageReader);
                }
            }
        }
        return null;
    }

    public void hookAcquireNextImage() {
        XposedHelpers.findAndHookMethod(ImageReader.class, "acquireNextImage",
                new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Log.w(TAG, "After ImageReader acquireNextImage");
                ImageReader imageReader = (ImageReader) param.thisObject;
                Image image = (Image) param.getResult();
                if (!(checkImageFormat(image) && imageReaderSet.contains(imageReader))) {
                    return;
                }
                // Control image inject count here for getPlanes can be called more than one time.
                if (Config.enableCamera2ImageInject) {
                    if (imageInjectResourceMap.containsKey(imageReader)) {
                        ImageHookResource resource = imageInjectResourceMap.get(imageReader);
                        if (resource.count < resource.maxCount - 1) {
                            ++resource.count;
                        } else {
                            // Loop playing.
                            resource.count = 0;
                        }
                    } else {
                        File imageFramesPath = new File(baseFile,
                                selectSpecResolutionImageFrames(image));
                        ImageHookResource resource = new ImageHookResource(imageFramesPath);
                        imageInjectResourceMap.put(imageReader, resource);
                        if (imageFramesPath.exists() && imageFramesPath.isDirectory()) {
                            resource.maxCount = imageFramesPath.listFiles().length / 3;
                        } else {
                            Log.e(TAG, "Cannot list files: " + imageFramesPath);
                            resource.maxCount = -1;
                        }
                    }
                }
                if (Config.enableCamera2DumpFrame) {
                    ImageHookResource resource;
                    if (dumpFrameResourceMap.containsKey(imageReader)) {
                        resource = dumpFrameResourceMap.get(imageReader);
                    } else {
                        resource = new ImageHookResource(createDumpFrameDir(image));
                        dumpFrameResourceMap.put(imageReader, resource);
                    }
                    try {
                        Image.Plane[] planes = image.getPlanes();
                        writeByteBufferToFile(planes[0].getBuffer(),
                                new File(resource.imagesPath, resource.count + ".y"));
                        writeByteBufferToFile(planes[1].getBuffer(),
                                new File(resource.imagesPath, resource.count + ".u"));
                        writeByteBufferToFile(planes[2].getBuffer(),
                                new File(resource.imagesPath, resource.count + ".v"));
                    } catch (IOException e) {
                        Log.e(TAG, "Write YUV data to file failed, " + resource, e);
                    }
                    ++resource.count;
                }
            }
        });
    }

    public void hookGetPlanes(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod("android.media.ImageReader$SurfaceImage",
                classLoader, "getPlanes", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Log.w(TAG, "After ImageReader$SurfaceImage getPlanes");
                Image image = (Image) param.thisObject;
                ImageReader imageReader = getReaderFromSurfaceImage(image);
                if (Config.enableCamera2ImageInject) {
                    ImageHookResource resource = imageInjectResourceMap.get(imageReader);
                    if (resource != null && resource.maxCount != -1) {
                        // Replace ByteBuffer in surface planes.
                        try {
                            Image.Plane[] planes = (Image.Plane[]) param.getResult();
                            replacePlaneByteBuffer(planes[0],
                                    new File(resource.imagesPath, resource.count + ".y"));
                            replacePlaneByteBuffer(planes[1],
                                    new File(resource.imagesPath, resource.count + ".u"));
                            replacePlaneByteBuffer(planes[2],
                                    new File(resource.imagesPath, resource.count + ".v"));
                        } catch (IOException e) {
                            Log.e(TAG, "Read YUV data from file failed, " + resource, e);
                        }
                    } else {
                        Log.e(TAG, "Cannot get ImageHookResource for ImageReader: "
                                + imageReader);
                    }
                }
            }
        });
    }

    private static boolean isCreateBySurfaceTexture(Surface surface) {
        return surface.toString().contains(SurfaceTexture.class.getName());
    }

    private static boolean checkImageFormat(Image image) {
        return image.getFormat() == ImageFormat.YUV_420_888;
    }

    private static String selectSpecResolutionVideo(ImageReader imageReader) {
        int width = imageReader.getWidth();
        int height = imageReader.getHeight();
        return Config.rotatedVideoPath.replace(".mp4", "_") +
                width + "x" + height + ".mp4";
    }

    private static String selectSpecResolutionImageFrames(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        return Config.imageFramesPath + "_" + width + "x" + height;
    }

    private File createDumpFrameDir(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        File file = new File(baseFile, "files/fkv/dump_frame_" +
                width + "x" + height + "_" + System.currentTimeMillis());
        Log.d(TAG, "Create dump frame path " + file.getAbsolutePath()
                + ", result: " + file.mkdir());
        return file;
    }

    private static void writeByteBufferToFile(ByteBuffer byteBuffer, File output) throws IOException {
        FileOutputStream fos = new FileOutputStream(output);
        FileChannel writeChannel = fos.getChannel();
        int bytes = writeChannel.write(byteBuffer);
        writeChannel.close();
        fos.close();
        Log.d(TAG, "Write " + bytes + " bytes to file " + output.getAbsolutePath());
    }

    @SuppressLint({"PrivateApi", "SoonBlockedPrivateApi"})
    private static void replacePlaneByteBuffer(Image.Plane plane, File input) throws IOException {
        ByteBuffer byteBuffer = plane.getBuffer();
        FileInputStream fis = new FileInputStream(input);
        FileChannel readChannel = fis.getChannel();
        int bytes = readChannel.read(byteBuffer);
        byteBuffer.flip();
        readChannel.close();
        fis.close();
        Log.d(TAG, "Read " + bytes + " bytes from file " + input.getAbsolutePath());
    }

    @SuppressLint({"PrivateApi", "SoonBlockedPrivateApi"})
    private static ImageReader getReaderFromSurfaceImage(Image image) {
        try {
            Method getReaderMethod = Class.forName("android.media.ImageReader$SurfaceImage")
                    .getDeclaredMethod("getReader");
            getReaderMethod.setAccessible(true);
            return (ImageReader) getReaderMethod.invoke(image);
        } catch (ReflectiveOperationException e) {
            Log.e(TAG, "getReaderFromSurfaceImage", e);
        }
        return null;
    }

    private static boolean matchStackTrace(StackTraceElement[] stackTraces,
                                    String className, String methodName) {
        for (StackTraceElement trace : stackTraces) {
            if (className.equals(trace.getClassName()) && methodName.equals(trace.getMethodName())) {
                return true;
            }
        }
        return false;
    }

}
