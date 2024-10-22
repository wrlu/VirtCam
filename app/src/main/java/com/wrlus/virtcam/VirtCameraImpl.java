package com.wrlus.virtcam;

import com.wrlus.virtcam.hook.LegacyCameraHooker;
import com.wrlus.virtcam.hook.Camera2Hooker;
import com.wrlus.virtcam.utils.Config;

import java.io.File;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by wrlu on 2024/3/13.
 */
public class VirtCameraImpl implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        // Base file is internal or external private storage dir.
        File baseFile = new File(Config.baseStorage, loadPackageParam.packageName);

        if (Config.enableCamera2Hook) {
            Camera2Hooker camera2Hooker = new Camera2Hooker(baseFile);
            camera2Hooker.hookCamera2(loadPackageParam.classLoader);
        }
        if (Config.enableLegacyCameraHook) {
            LegacyCameraHooker legacyCameraHooker = new LegacyCameraHooker(baseFile);
            legacyCameraHooker.hookCamera1(loadPackageParam.classLoader);
        }
    }
}
