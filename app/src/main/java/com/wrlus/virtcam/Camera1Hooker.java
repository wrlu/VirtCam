package com.wrlus.virtcam;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;

/**
 * Created by wrlu on 2024/3/13.
 */
public class Camera1Hooker {
    private static final String TAG = "VirtCamera-1";

    private int frameCount = 0;
    private final Map<Surface, CameraHookTexture> hookTextureQueue =
            new ConcurrentHashMap<>();
    private SurfaceTexture fakeSurfaceTexture;

    public void hookCamera1(String packageName, ClassLoader classLoader) {
        File videoFile = new File(Environment.getExternalStorageDirectory(),
                "Android/data/" + packageName +
                        "/files/ccc/virtual.mp4");
        XposedHelpers.findAndHookMethod(Camera.class,
                "setPreviewTexture", SurfaceTexture.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Log.w(TAG, "Before setPreviewTexture");
                        SurfaceTexture surfaceTexture = (SurfaceTexture) param.args[0];
                        if (surfaceTexture != null && !fakeSurfaceTexture.equals(surfaceTexture)) {
                            Surface textureSurface = new Surface(surfaceTexture);
                            fakeSurfaceTexture = new SurfaceTexture(10);
                            Surface fakeSurface = new Surface(fakeSurfaceTexture);
                            hookTextureQueue.put(textureSurface,
                                    new CameraHookTexture(fakeSurface, null));
                            param.args[0] = fakeSurfaceTexture;
                        }
                    }
                });
        XposedHelpers.findAndHookMethod(Camera.class,
                "setPreviewDisplay", SurfaceHolder.class, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        Log.w(TAG, "Replace setPreviewDisplay");
                        Camera thisCamera = (Camera) param.thisObject;
                        SurfaceHolder surfaceHolder = (SurfaceHolder) param.args[0];
                        if (surfaceHolder != null) {
                            Surface displaySurface = surfaceHolder.getSurface();
                            fakeSurfaceTexture = new SurfaceTexture(10);
                            Surface fakeSurface = new Surface(fakeSurfaceTexture);
                            hookTextureQueue.put(displaySurface,
                                    new CameraHookTexture(fakeSurface, null));
                            // Give up setPreviewDisplay call and use setPreviewTexture instead.
                            thisCamera.setPreviewTexture(fakeSurfaceTexture);
                        }
                        return null;
                    }
                });
        XposedHelpers.findAndHookMethod(Camera.class,
                "startPreview", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Log.w(TAG, "Before startPreview");
                        if (videoFile.exists()) {
                            for (Surface output : hookTextureQueue.keySet()) {
                                if (output != null && output.isValid()) {
                                    hookTextureQueue.get(output).mediaPlayer =
                                            VideoUtils.playVideo(videoFile, output);
                                }
                            }
                        } else {
                            Log.e(TAG, "Video not exists!");
                        }
                    }
                });
        XposedHelpers.findAndHookMethod(Camera.class,
                "stopPreview", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Log.w(TAG, "After stopPreview");
                        for (CameraHookTexture texture : hookTextureQueue.values()) {
                            if (texture.fakeSurface != null) texture.fakeSurface.release();
                            if (texture.mediaPlayer != null) texture.mediaPlayer.release();
                        }
                        hookTextureQueue.clear();
                        fakeSurfaceTexture.release();
                        fakeSurfaceTexture = null;
                    }
                });
        XposedHelpers.findAndHookMethod(Camera.class,
                "setPreviewCallback", Camera.PreviewCallback.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Log.w(TAG, "Before setPreviewCallback");
                        Camera.PreviewCallback callback = (Camera.PreviewCallback) param.args[0];
                        if (callback != null) {
                            if (videoFile.exists()) {
                                VideoUtils.decodeVideo(videoFile, packageName);
                                hookPreviewCallback(callback, videoFile, packageName,
                                        false);
                            } else {
                                Log.e(TAG, "Video not exists!");
                            }
                        } else {
                            frameCount = 0;
                            Log.e(TAG, "Camera.setPreviewCallback: " +
                                    "callback is null, skip.");
                        }
                    }
                });
    }

    private void hookPreviewCallback(Camera.PreviewCallback callback, File videoFile,
                                     String packageName, boolean dumpFrame) {
        File dumpFrameOutput = new File(
                Environment.getExternalStorageDirectory(),
                "Android/data/" + packageName +
                        "/files/dump_frame_" + UUID.randomUUID() + "/");
        if (dumpFrame) {
            if (!dumpFrameOutput.exists()) {
                Log.e(TAG, "dump frame output mkdir: " +
                        dumpFrameOutput.mkdir());
            }
        }
        Class<?> callbackClass = callback.getClass();
        Log.e(TAG, "Callback class name: " + callbackClass.getName());
        XposedHelpers.findAndHookMethod(callbackClass, "onPreviewFrame",
                byte[].class, Camera.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Log.w(TAG, "Before onPreviewFrame");
                        Camera camera = (Camera) param.args[1];
                        Camera.Size previewSize = camera
                                .getParameters().getPreviewSize();
                        byte[] newData = VideoUtils.getReplacedPreviewFrame(videoFile, packageName);
                        if (newData != null) {
                            // We need exchange width and height for rotation.
                            int videoWidth = previewSize.height;
                            int videoHeight = previewSize.width;
                            byte[] rotateData = VideoUtils.rotateNV21(newData,
                                    videoWidth, videoHeight, 90);
                            param.args[0] = rotateData;
                        } else {
                            // We do not want to leak real camera data here.
                            param.args[0] = null;
                            Log.e(TAG, "Replace " +
                                    "onPreviewFrame data failed !!!");
                        }
                    }
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Log.w(TAG, "After onPreviewFrame");
                        if (dumpFrame) {
                            byte[] data = (byte[]) param.args[0];
                            Camera camera = (Camera) param.args[1];
                            Camera.Size previewSize = camera
                                    .getParameters().getPreviewSize();
                            VideoUtils.savePreviewFrameImage(data,
                                    previewSize.width, previewSize.height,
                                    dumpFrameOutput, frameCount);
                            ++frameCount;
                        }
                    }
                });
    }


}

