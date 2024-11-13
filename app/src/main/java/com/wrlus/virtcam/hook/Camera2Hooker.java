package com.wrlus.virtcam.hook;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import com.wrlus.virtcam.utils.Config;
import com.wrlus.virtcam.utils.VideoUtils;
import com.wrlus.xposed.framework.HookInterface;

import java.io.File;
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
            XposedHelpers.findAndHookMethod(ImageReader.class, "close", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            ImageReader imageReader = (ImageReader) param.thisObject;
                            imageReaderSet.remove(imageReader);
                        }
                    });
        }
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
                                    .getOrDefault(output, findAndReplaceSurface(output, i));
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
                                Log.e(TAG, "Generate camera hook resource failed.");
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
                                        .getOrDefault(output, findAndReplaceSurface(output, i));
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
                                    Log.e(TAG, "Generate camera hook resource failed.");
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
                                .getOrDefault(target, findAndReplaceSurface(target, addTargetSurfaceCount));
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
                            Log.e(TAG, "Generate camera hook resource failed.");
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
                                if (isTexture &&
                                        resource.mediaPlayer == null && resource.isConfigured) {
                                    File videoFile = new File(baseFile, Config.videoPath);
                                    if (videoFile.exists()) {
                                        resource.mediaPlayer =
                                                VideoUtils.playVideo(videoFile, output);
                                        Log.d(TAG, "Start playing video" +
                                                " on output surface: " + output
                                                + ", resource: " + resource);
                                    }
                                }
                                // Use MediaCodec to inject ImageReader.
                                if (!isTexture &&
                                        resource.mediaCodec == null && resource.isConfigured) {
                                    // Only support YUV_420_888 format now.
                                    if (resource.fakeImageReader.getImageFormat()
                                            != ImageFormat.YUV_420_888) {
                                        return;
                                    }
                                    // Fake image reader has same parameters with the origin one.
                                    File rotatedSpecResolutionVideoFile = new File(baseFile,
                                            selectSpecResolutionVideo(resource.fakeImageReader));
                                    File rotatedVideoFile = new File(baseFile,
                                            Config.rotatedVideoPath);
                                    // Use rotated video in ImageReader hook.
                                    if (rotatedSpecResolutionVideoFile.exists()) {
                                        resource.mediaCodec = VideoUtils.decodeVideoToSurface(
                                                rotatedSpecResolutionVideoFile, output);
                                        Log.d(TAG, "Start decode special resolution video" +
                                                " on output surface: " + output
                                                + ", resource: " + resource);
                                    } else {
                                        if (rotatedVideoFile.exists()) {
                                            resource.mediaCodec = VideoUtils.decodeVideoToSurface(
                                                    rotatedVideoFile, output);
                                            Log.d(TAG, "Start decode video" +
                                                    " on output surface: " + output
                                                    + ", resource: " + resource);
                                        }
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

    private CameraHookResource findAndReplaceSurface(Surface output, int index) {
        if (isCreateBySurfaceTexture(output)) {
            SurfaceTexture fakeSurfaceTexture = new SurfaceTexture(10 + index);
            return new CameraHookResource(new Surface(fakeSurfaceTexture), fakeSurfaceTexture);
        } else {
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

    public static String selectSpecResolutionVideo(ImageReader imageReader) {
        int width = imageReader.getWidth();
        int height = imageReader.getHeight();
        return Config.rotatedVideoPath.replace(".mp4", "_") +
                width + "x" + height + ".mp4";
    }

    public static boolean isCreateBySurfaceTexture(Surface surface) {
        return surface.toString().contains(SurfaceTexture.class.getName());
    }

    public static boolean matchStackTrace(StackTraceElement[] stackTraces,
                                    String className, String methodName) {
        for (StackTraceElement trace : stackTraces) {
            if (className.equals(trace.getClassName()) && methodName.equals(trace.getMethodName())) {
                return true;
            }
        }
        return false;
    }
}
