package com.wrlus.virtcam.utils;

import android.os.Environment;

import java.io.File;

public class Config {
    public static final File baseStorage = Default.EXTERNAL_STORAGE;
    public static final String videoPath = Default.VIDEO_PATH;
    public static final boolean enableCamera2Hook = true;
    public static final boolean enableLegacyCameraHook = true;
    public static final boolean enableLegacyCameraDumpFrame = false;

    static final class Default {
        public static final File EXTERNAL_STORAGE =
                new File(Environment.getExternalStorageDirectory(), "Android/data");
        public static final File INTERNAL_STORAGE = new File("/data/data");
        public static final String VIDEO_PATH = "files/ccc/virtual.mp4";
    }
}
