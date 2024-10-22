package com.wrlus.virtcam.hook;

import android.graphics.SurfaceTexture;
import android.media.ImageReader;
import android.media.MediaPlayer;
import android.view.Surface;

public class CameraHookResource {
    public CameraHookResource(Surface surface, SurfaceTexture surfaceTexture) {
        fakeSurface = surface;
        fakeSurfaceTexture = surfaceTexture;
    }

    public CameraHookResource(Surface surface, ImageReader imageReader) {
        fakeSurface = surface;
        fakeImageReader = imageReader;
    }

    /**
     * Fake surface to receive camera image.
     */
    public Surface fakeSurface;
    /**
     * Fake surface texture
     */
    public SurfaceTexture fakeSurfaceTexture;
    /**
     * Fake surface texture
     */
    public ImageReader fakeImageReader;
    /**
     * MediaPlayer to play inject video.
     */
    public MediaPlayer mediaPlayer;
}