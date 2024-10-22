package com.wrlus.virtcam;

import com.wrlus.virtcam.hook.Camera2PreviewHooker;
import com.wrlus.virtcam.hook.LegacyCameraHooker;
import com.wrlus.virtcam.hook.Camera2Hooker;
import com.wrlus.virtcam.utils.Config;
import com.wrlus.xposed.framework.HookInterface;

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
            HookInterface hooker = new Camera2Hooker(baseFile);
            hooker.onHookPackage(loadPackageParam);
        }
        if (Config.enableCamera2PreviewHook) {
            HookInterface hooker = new Camera2PreviewHooker();
            hooker.onHookPackage(loadPackageParam);
        }
        if (Config.enableLegacyCameraHook) {
            HookInterface hooker = new LegacyCameraHooker(baseFile);
            hooker.onHookPackage(loadPackageParam);
        }
    }
}
