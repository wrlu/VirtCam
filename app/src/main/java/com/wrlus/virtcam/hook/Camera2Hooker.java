package com.wrlus.virtcam.hook;

import android.annotation.SuppressLint;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import com.wrlus.virtcam.utils.VideoUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Created by wrlu on 2024/10/17.
 */
public class Camera2Hooker {
    private static final String TAG = "VirtCamera-2";
    private final Map<Surface, CameraHookTexture> hookTextureQueue =
            new ConcurrentHashMap<>();
    private final File videoFile;

    public Camera2Hooker(File videoFile) {
        this.videoFile = videoFile;
    }

    public void hookCamera2(ClassLoader classLoader) {
        if (!videoFile.exists()) {
            Log.e(TAG, "Cannot find virtual video, please put in " +
                    videoFile.getAbsolutePath());
            return;
        }
        XposedHelpers.findAndHookMethod("android.hardware.camera2.impl.CameraDeviceImpl",
                classLoader, "createCaptureSession", List.class,
                CameraCaptureSession.StateCallback.class, Handler.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Log.w(TAG, "Before createCaptureSession");
                        List<Surface> outputs = (List<Surface>) param.args[0];
                        List<Surface> fakeOutputs = new ArrayList<>();
                        int i = 1;
                        for (Surface output : outputs) {
                            Log.e(TAG, "Output surface: " + output);
                            @SuppressLint("Recycle")
                            SurfaceTexture fakeSurfaceTexture = new SurfaceTexture(10 + i);
                            Surface fakeSurface = new Surface(fakeSurfaceTexture);
                            fakeOutputs.add(fakeSurface);
                            hookTextureQueue.put(output, new CameraHookTexture(fakeSurface, null));
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
                            Log.w(TAG, "Before createCaptureSession");
                            SessionConfiguration config = (SessionConfiguration) param.args[0];
                            List<OutputConfiguration> outputConfigs = config.getOutputConfigurations();
                            List<OutputConfiguration> fakeOutputConfigs = new ArrayList<>();
                            int i = 1;
                            for (OutputConfiguration outputConfig : outputConfigs) {
                                Log.e(TAG, "Output config: " + outputConfig);
                                @SuppressLint("Recycle")
                                SurfaceTexture fakeSurfaceTexture = new SurfaceTexture(10 + i);
                                Surface fakeSurface = new Surface(fakeSurfaceTexture);
                                OutputConfiguration fakeConfig = new OutputConfiguration(fakeSurface);
                                fakeOutputConfigs.add(fakeConfig);
                                hookTextureQueue.put(outputConfig.getSurface(),
                                        new CameraHookTexture(fakeSurface, null));
                                ++i;
                            }
                            param.args[0] = fakeOutputConfigs;
                            Log.w(TAG, "createCaptureSession (SessionConfiguration): " +
                                    "replaced with " + (i - 1) + " fake surfaces !!!");
                        }
                    });
        }
        XposedHelpers.findAndHookMethod(CaptureRequest.Builder.class,
                "addTarget", Surface.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Log.w(TAG, "Before addTarget");
                        Surface addTargetSurface = (Surface) param.args[0];
                        if (hookTextureQueue.containsKey(addTargetSurface)) {
                            param.args[0] = hookTextureQueue.get(addTargetSurface).fakeSurface;
                        }
                    }
                });
        XposedHelpers.findAndHookMethod("android.hardware.camera2.impl.CameraCaptureSessionImpl",
                classLoader, "setRepeatingRequest", CaptureRequest.class,
                CameraCaptureSession.CaptureCallback.class, Handler.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Log.w(TAG, "Before setRepeatingRequest");
                        for (Surface output : hookTextureQueue.keySet()) {
                            if (output != null && output.isValid()) {
                                hookTextureQueue.get(output).mediaPlayer =
                                        VideoUtils.playVideo(videoFile, output);
                            }
                        }
                    }
                });
        XposedHelpers.findAndHookMethod("android.hardware.camera2.impl.CameraDeviceImpl",
                classLoader, "close", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Log.w(TAG, "After close");
                        for (CameraHookTexture texture : hookTextureQueue.values()) {
                            if (texture.fakeSurface != null) texture.fakeSurface.release();
                            if (texture.mediaPlayer != null) texture.mediaPlayer.release();
                        }
                        hookTextureQueue.clear();
                    }
                });
    }
}
