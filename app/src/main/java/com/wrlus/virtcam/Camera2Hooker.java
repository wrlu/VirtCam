package com.wrlus.virtcam;

import android.annotation.SuppressLint;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Created by wrlu on 2024/10/17.
 */
public class Camera2Hooker {
    private static final String TAG = "VirtCamera-2";
    private final List<Surface> targetSurfaces = new ArrayList<>();

    public void hookCamera2(String packageName, ClassLoader classLoader) {
        File videoFile = new File(Environment.getExternalStorageDirectory(),
                "Android/data/" + packageName +
                        "/files/ccc/virtual.mp4");
        XposedHelpers.findAndHookMethod(CaptureRequest.Builder.class,
                "addTarget", Surface.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Log.i(TAG, "Before setPreviewTexture");
                        Surface appSurface = (Surface) param.args[0];
                        if (appSurface != null && !targetSurfaces.contains(appSurface)) {
                            targetSurfaces.add(appSurface);
                            @SuppressLint("Recycle")
                            SurfaceTexture fakeSurfaceTexture = new SurfaceTexture(11);
                            param.args[0] = new Surface(fakeSurfaceTexture);
                        }
                    }
                });
        XposedHelpers.findAndHookMethod(CameraCaptureSession.class,
                "setRepeatingRequest", CaptureRequest.class,
                CameraCaptureSession.CaptureCallback.class, Handler.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Log.i(TAG, "Before setRepeatingRequest");
                        if (videoFile.exists()) {
                            for (Surface targetSurface : targetSurfaces) {
                                if (targetSurface != null && targetSurface.isValid()) {
                                    VideoUtils.playVideo(videoFile, targetSurface);
                                }
                            }
                        } else {
                            Log.e(TAG, "Video not exists!");
                        }
                    }
                });
    }
}
