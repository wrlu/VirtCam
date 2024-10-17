package com.wrlus.virtcam;

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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Created by wrlu on 2024/10/17.
 */
public class Camera2Hooker {
    private static final String TAG = "VirtCamera-2";
    // key: original target surface
    // value: fake empty surface
    private final Map<Surface, Surface> target2FakeSurfacesQueue =
            new ConcurrentHashMap<>();

    public void hookCamera2(String packageName, ClassLoader classLoader) {
        File videoFile = new File(Environment.getExternalStorageDirectory(),
                "Android/data/" + packageName +
                        "/files/ccc/virtual.mp4");
        XposedHelpers.findAndHookMethod("android.hardware.camera2.impl.CameraDeviceImpl",
                classLoader, "createCaptureSession", List.class,
                CameraCaptureSession.StateCallback.class, Handler.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Log.e(TAG, "Before createCaptureSession");
                        List<Surface> outputs = (List<Surface>) param.args[0];
                        List<Surface> fakeOutputs = new ArrayList<>();
                        int i = 1;
                        for (Surface output : outputs) {
                            SurfaceTexture fakeSurfaceTexture = new SurfaceTexture(10 + i);
                            Surface fakeSurface = new Surface(fakeSurfaceTexture);
                            fakeOutputs.add(fakeSurface);
                            target2FakeSurfacesQueue.put(output, fakeSurface);
                            ++i;
                        }
                        param.args[0] = fakeOutputs;
                    }
                });
        XposedHelpers.findAndHookMethod(CaptureRequest.Builder.class,
                "addTarget", Surface.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Log.e(TAG, "Before addTarget");
                        Surface addTargetSurface = (Surface) param.args[0];
                        if (target2FakeSurfacesQueue.containsKey(addTargetSurface)) {
                            param.args[0] = target2FakeSurfacesQueue.get(addTargetSurface);
                        }
                    }
                });
        XposedHelpers.findAndHookMethod("android.hardware.camera2.impl.CameraCaptureSessionImpl",
                classLoader, "setRepeatingRequest", CaptureRequest.class,
                CameraCaptureSession.CaptureCallback.class, Handler.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Log.e(TAG, "Before setRepeatingRequest");
                        if (videoFile.exists()) {
                            for (Surface output : target2FakeSurfacesQueue.keySet()) {
                                if (output != null && output.isValid()) {
                                    VideoUtils.playVideo(videoFile, output);
                                }
                            }
                        } else {
                            Log.e(TAG, "Video not exists!");
                        }
                    }
                });
    }
}
