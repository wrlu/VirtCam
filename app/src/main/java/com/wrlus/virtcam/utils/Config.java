package com.wrlus.virtcam.utils;

import android.os.Environment;

import java.io.File;

public class Config {
    public static final File baseStorage = Default.EXTERNAL_STORAGE;
    public static final String videoPath = Default.VIDEO_PATH;
    public static final String rotatedVideoPath = Default.ROTATED_VIDEO_PATH;
    public static final String imageFramesPath = Default.IMAGE_FRAMES_PATH;
    public static final boolean enableCamera2Hook = true;
    public static final boolean enableCamera2DumpFrame = true;
    public static final boolean enableCamera2ImageInject = false;
    public static final boolean enableLegacyCameraHook = true;
    public static final boolean enableLegacyCameraDumpFrame = false;

    static final class Default {
        public static final File EXTERNAL_STORAGE =
                new File(Environment.getExternalStorageDirectory(), "Android/data");
        public static final File INTERNAL_STORAGE = new File("/data/data");
        public static final String VIDEO_PATH = "files/fkv/virtual.mp4";
        public static final String ROTATED_VIDEO_PATH = "files/fkv/rotated_virtual.mp4";
        public static final String IMAGE_FRAMES_PATH = "files/fkv/image_frames";
    }
}
