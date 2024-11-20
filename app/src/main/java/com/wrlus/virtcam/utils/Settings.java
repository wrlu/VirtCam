package com.wrlus.virtcam.utils;

import android.util.Log;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public final class Settings {
    private static final String TAG = "VirtCam-Settings";
    public static final String settingsPath = "files/fkv/settings.prop";
    private static final Map<String, String> settings = new ConcurrentHashMap<>();

    static final class Default {
        /**
         * Default VirtCam settings.
         */
        public static final String videoPath = "files/fkv/virtual.mp4";
        public static final String rotatedVideoPath = "files/fkv/rotated_virtual.mp4";
        public static final String imageFramesPath = "files/fkv/image_frames";
        public static final String enableCamera2Hook = "1";
        public static final String enableCamera2DumpFrame = "0";
        public static final String enableCamera2ImageInject = "0";
        public static final String enableLegacyCameraHook = "1";
        public static final String enableLegacyCameraDumpFrame = "0";
    }

    public static String get(String key) {
        return settings.getOrDefault(key, null);
    }

    public static void initSettings(File baseFile) {
        File settingsFile = new File(baseFile, settingsPath);
        try {
            Properties properties = new Properties();
            properties.load(new FileReader(settingsFile));
            Log.d(TAG, "!!! Load settings from file !!!");
            for (String key : properties.stringPropertyNames()) {
                settings.put(key, properties.getProperty(key));
                Log.d(TAG, key + "=" + properties.getProperty(key));
            }
        } catch (IOException e) {
            Log.w(TAG, "Cannot load settings.prop file, use default settings.", e);
            initDefaultSettings();
        }
    }

    private static void initDefaultSettings() {
        for (Field field : Default.class.getDeclaredFields()) {
            try {
                settings.put(field.getName(), (String) field.get(null));
            } catch (IllegalAccessException e) {
                Log.e(TAG, "Cannot load default settings.", e);
            }
        }
    }
}
