package com.wrlus.virtcam.hook;

import androidx.annotation.NonNull;

import java.io.File;

public class ImageHookResource {
    /**
     * Images path for inject or dump frames.
     */
    public File imagesPath;
    /**
     * Current count of inject or dump frames.
     */
    public int count;
    /**
     * Max count of inject (image inject use only).
     */
    public int maxCount;

    public ImageHookResource(File path) {
        imagesPath = path;
        count = 0;
        maxCount = 1;
    }

    public ImageHookResource(String path) {
        imagesPath = new File(path);
        count = 0;
        maxCount = 1;
    }

    @NonNull
    @Override
    public String toString() {
        return "ImageHookResource{" +
                "imagesPath=" + imagesPath +
                ", count=" + count +
                ", maxCount=" + maxCount +
                '}';
    }
}
