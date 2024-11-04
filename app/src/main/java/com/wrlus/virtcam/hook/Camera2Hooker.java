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
import java.util.List;
import java.util.Map;
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
    private final Map<Surface, CameraHookResource> hookTextureMap =
            new ConcurrentHashMap<>();
    private final File baseFile;
    private final File videoFile;
    private final File rotatedVideoFile;

    public Camera2Hooker(File baseFile) {
        this.baseFile = baseFile;
        videoFile = new File(baseFile, Config.videoPath);
        rotatedVideoFile = new File(baseFile, Config.rotatedVideoPath);
    }

    @Override
    public void onHookPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        startHook(loadPackageParam.classLoader);
    }

    private void startHook(ClassLoader classLoader) {
        hookWithoutVideo(classLoader);
        hookWithVideo(classLoader);
    }

    private void hookWithoutVideo(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod("android.media.ImageReader",
                classLoader, "newInstance", int.class, int.class,
                int.class, int.class, new XC_MethodHook() {
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
            XposedHelpers.findAndHookMethod("android.media.ImageReader",
                    classLoader, "newInstance", int.class, int.class,
                    int.class, int.class, long.class, new XC_MethodHook() {
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
    }

    private void hookImageReaderNewInstance(int width, int height, int format, int maxImages,
                                            ImageReader imageReader) {
        Log.w(TAG, "After ImageReader newInstance");
        Log.e(TAG, "ImageReader#newInstance: width=" + width + " , height=" + height +
                " ,format=" + format + " ,maxImages=" + maxImages);
    }

    private void hookWithVideo(ClassLoader classLoader) {
        if (!videoFile.exists() && !rotatedVideoFile.exists()) {
            Log.e(TAG, "Cannot find virtual video, please put in " +
                    videoFile.getAbsolutePath() + " or put rotated video in " +
                    rotatedVideoFile.getAbsolutePath());
            return;
        }
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
                            Log.e(TAG, "Output surface: " + output);
                            if (!hookTextureMap.containsKey(output)) {
                                Surface fakeSurface;
                                if (isCreateBySurfaceTexture(output)) {
                                    SurfaceTexture fakeSurfaceTexture =
                                            createFakeSurfaceTexture(10 + i);
                                    fakeSurface = new Surface(fakeSurfaceTexture);
                                    hookTextureMap.put(output,
                                            new CameraHookResource(fakeSurface, fakeSurfaceTexture));
                                } else {
                                    ImageReader imageReader = createFakeImageReader();
                                    fakeSurface = imageReader.getSurface();
                                    hookTextureMap.put(output,
                                            new CameraHookResource(fakeSurface, imageReader));
                                }
                                fakeOutputs.add(fakeSurface);
                                Log.w(TAG, "Create fakeSurface in createCaptureSession: " +
                                        output + " -> " + fakeSurface);
                            } else {
                                // If surface is exist in hookTextureQueue,
                                // this means it has been already hooked in addTarget method.
                                CameraHookResource resource = hookTextureMap.get(output);
                                if (resource != null) {
                                    fakeOutputs.add(resource.fakeSurface);
                                    resource.isConfigured = true;
                                    Log.w(TAG, "Reuse fakeSurface in createCaptureSession: " +
                                            output + " -> " + resource.fakeSurface);
                                }
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
                            Log.w(TAG, "Before CameraDeviceImpl createCaptureSession");
                            SessionConfiguration config = (SessionConfiguration) param.args[0];
                            List<OutputConfiguration> outputConfigs = config.getOutputConfigurations();
                            List<OutputConfiguration> fakeOutputConfigs = new ArrayList<>();
                            int i = 1;
                            for (OutputConfiguration outputConfig : outputConfigs) {
                                Log.w(TAG, "Output config: " + outputConfig);
                                Surface output = outputConfig.getSurface();
                                if (!hookTextureMap.containsKey(output)) {
                                    Surface fakeSurface;
                                    if (isCreateBySurfaceTexture(output)) {
                                        SurfaceTexture fakeSurfaceTexture =
                                                createFakeSurfaceTexture(10 + i);
                                        fakeSurface = new Surface(fakeSurfaceTexture);
                                        hookTextureMap.put(output,
                                                new CameraHookResource(fakeSurface, fakeSurfaceTexture));
                                    } else {
                                        ImageReader imageReader = createFakeImageReader();
                                        fakeSurface = imageReader.getSurface();
                                        hookTextureMap.put(output,
                                                new CameraHookResource(fakeSurface, imageReader));
                                    }
                                    OutputConfiguration fakeConfig = new OutputConfiguration(fakeSurface);
                                    fakeOutputConfigs.add(fakeConfig);
                                    Log.w(TAG, "Create fakeSurface in createCaptureSession: " +
                                            output + " -> " + fakeSurface);
                                } else {
                                    // If surface is exist in hookTextureQueue,
                                    // this means it has been already hooked in addTarget method.
                                    CameraHookResource resource = hookTextureMap.get(output);
                                    if (resource != null) {
                                        OutputConfiguration fakeConfig =
                                                new OutputConfiguration(resource.fakeSurface);
                                        fakeOutputConfigs.add(fakeConfig);
                                        resource.isConfigured = true;
                                        Log.w(TAG, "Reuse fakeSurface in createCaptureSession: " +
                                                output + " -> " + resource.fakeSurface);
                                    }
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
                        if (!hookTextureMap.containsKey(target)) {
                            // In some cases, addTarget will be called BEFORE createCaptureSession.
                            // So we need to generate fake surface in this hook callback.
                            Surface fakeSurface;
                            if (isCreateBySurfaceTexture(target)) {
                                SurfaceTexture fakeSurfaceTexture =
                                        createFakeSurfaceTexture(20 + addTargetSurfaceCount);
                                fakeSurface = new Surface(fakeSurfaceTexture);
                                hookTextureMap.put(target,
                                        new CameraHookResource(fakeSurface, fakeSurfaceTexture));
                            } else {
                                ImageReader imageReader = createFakeImageReader();
                                fakeSurface = imageReader.getSurface();
                                hookTextureMap.put(target,
                                        new CameraHookResource(fakeSurface, imageReader));
                            }
                            param.args[0] = fakeSurface;
                            ++addTargetSurfaceCount;
                            Log.w(TAG, "Create fakeSurface in addTarget: " +
                                    target + " -> " + fakeSurface);
                        } else {
                            // Already hooked in createCaptureSession.
                            CameraHookResource resource = hookTextureMap.get(target);
                            if (resource != null) {
                                param.args[0] = resource.fakeSurface;
                                resource.isConfigured = true;
                                Log.w(TAG, "Reuse fakeSurface in addTarget: " +
                                        target + " -> " + resource.fakeSurface);
                            }
                        }
                    }
                });
        XposedHelpers.findAndHookMethod("android.hardware.camera2.impl.CameraCaptureSessionImpl",
                classLoader, "setRepeatingRequest", CaptureRequest.class,
                CameraCaptureSession.CaptureCallback.class, Handler.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Log.w(TAG, "Before CameraCaptureSessionImpl setRepeatingRequest");
                        for (Surface output : hookTextureMap.keySet()) {
                            if (output != null && output.isValid()) {
                                CameraHookResource resource = hookTextureMap.get(output);
                                // Use MediaPlayer to inject SurfaceTexture.
                                if (isCreateBySurfaceTexture(output) &&
                                        resource.mediaPlayer == null && resource.isConfigured) {
                                    if (videoFile.exists()) {
                                        resource.mediaPlayer =
                                                VideoUtils.playVideo(videoFile, output);
                                        Log.w(TAG, "Start playing video on output surface: " + output);
                                    }
                                }
                                // Use MediaCodec to inject ImageReader.
                                if (!isCreateBySurfaceTexture(output) &&
                                        resource.mediaCodec == null && resource.isConfigured) {
                                    if (rotatedVideoFile.exists()) {
                                        // Must use rotated video in ImageReader hook.
                                        resource.mediaCodec = VideoUtils.decodeVideoToSurface(
                                                rotatedVideoFile, output);
                                        Log.w(TAG, "Start decode video on output surface: " + output);
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
                        for (CameraHookResource resource : hookTextureMap.values()) {
                            if (resource.fakeSurfaceTexture != null) resource.fakeSurfaceTexture.release();
                            if (resource.fakeImageReader != null) resource.fakeImageReader.close();
                            if (resource.mediaPlayer != null) resource.mediaPlayer.release();
                            if (resource.mediaCodec != null) resource.mediaCodec.release();
                        }
                        addTargetSurfaceCount = 0;
                        hookTextureMap.clear();
                    }
                });
    }

    private static SurfaceTexture createFakeSurfaceTexture(int texName) {
        return new SurfaceTexture(texName);
    }

    private static ImageReader createFakeImageReader() {
        return ImageReader
                .newInstance(640, 480, ImageFormat.YUV_420_888,2);
    }

    private static boolean isCreateBySurfaceTexture(Surface surface) {
        return surface.toString().contains("android.graphics.SurfaceTexture");
    }
}
