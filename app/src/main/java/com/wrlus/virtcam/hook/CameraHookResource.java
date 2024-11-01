package com.wrlus.virtcam.hook;

import android.graphics.SurfaceTexture;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.media.MediaCodec;
import android.media.MediaPlayer;
import android.view.Surface;

public class CameraHookResource {
    public CameraHookResource(Surface surface, SurfaceTexture surfaceTexture) {
        fakeSurface = surface;
        fakeSurfaceTexture = surfaceTexture;
        isConfigured = false;
    }

    public CameraHookResource(Surface surface, ImageReader imageReader) {
        fakeSurface = surface;
        fakeImageReader = imageReader;
        isConfigured = false;
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
     * Fake image reader
     */
    public ImageReader fakeImageReader;
    /**
     * MediaPlayer to play inject video.
     */
    public MediaPlayer mediaPlayer;
    /**
     * MediaCodec to decode inject video.
     */
    public MediaCodec mediaCodec;
    /**
     * Surface is configured by addTarget and createCaptureSession.
     */
    public boolean isConfigured;
}