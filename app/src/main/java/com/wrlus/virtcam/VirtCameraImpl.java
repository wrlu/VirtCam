package com.wrlus.virtcam;

import android.os.Environment;

import com.wrlus.virtcam.hook.LegacyCameraHooker;
import com.wrlus.virtcam.hook.Camera2Hooker;
import com.wrlus.virtcam.utils.Settings;
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
        File baseFile = new File(new File(Environment.getExternalStorageDirectory(),
                "Android/data"), loadPackageParam.packageName);
        Settings.initSettings(baseFile);

        if (Settings.get("enableCamera2Hook").equals("1")) {
            HookInterface hooker = new Camera2Hooker(baseFile);
            hooker.onHookPackage(loadPackageParam);
        }
        if (Settings.get("enableLegacyCameraHook").equals("1")) {
            HookInterface hooker = new LegacyCameraHooker(baseFile);
            hooker.onHookPackage(loadPackageParam);
        }
    }
}
