package net.wrlu.virtcam;

import android.os.Environment;

import net.wrlu.virtcam.hook.LegacyCameraHooker;
import net.wrlu.virtcam.hook.Camera2Hooker;
import net.wrlu.virtcam.utils.Settings;
import net.wrlu.xposed.framework.HookInterface;

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
