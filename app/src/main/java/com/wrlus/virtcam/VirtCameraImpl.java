package com.wrlus.virtcam;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by wrlu on 2024/3/13.
 */
public class VirtCameraImpl implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        Camera1Hooker camera1Hooker = new Camera1Hooker();
        camera1Hooker.hookCamera1(loadPackageParam.packageName, loadPackageParam.classLoader);

        Camera2Hooker camera2Hooker = new Camera2Hooker();
        camera2Hooker.hookCamera2(loadPackageParam.packageName, loadPackageParam.classLoader);
    }
}
