package com.wrlus.virtcam.hook;

import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import com.wrlus.xposed.framework.HookInterface;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Camera2PreviewHooker implements HookInterface {
    private static final String TAG = "VirtCamera-2P";

    @Override
    public void onHookPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        startHook(loadPackageParam.classLoader);
    }

    private void startHook(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(ImageReader.class, "setOnImageAvailableListener",
                ImageReader.OnImageAvailableListener.class, Handler.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Log.w(TAG, "Before setOnImageAvailableListener");
                        ImageReader.OnImageAvailableListener listener =
                                (ImageReader.OnImageAvailableListener) param.args[0];
                        if (listener != null) {
                            hookImageAvailableListener(listener);
                        }
                    }
                });
    }

    private void hookImageAvailableListener(ImageReader.OnImageAvailableListener listener) {
        Class<? extends ImageReader.OnImageAvailableListener> listenerClass = listener.getClass();
        XposedHelpers.findAndHookMethod(listenerClass,
                "onImageAvailable", ImageReader.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Log.w(TAG, "Before onImageAvailable");
                        ImageReader imageReader = (ImageReader) param.args[0];
                        Log.e(TAG, "ImageReader " + imageReader + " format: " + imageReader.getImageFormat());
                    }
                });
    }
}
