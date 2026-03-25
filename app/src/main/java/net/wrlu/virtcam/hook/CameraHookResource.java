package net.wrlu.virtcam.hook;

import android.graphics.SurfaceTexture;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaPlayer;
import android.view.Surface;

import androidx.annotation.NonNull;

public class CameraHookResource {
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

    @NonNull
    @Override
    public String toString() {
        return "CameraHookResource{" +
                "fakeSurface=" + fakeSurface +
                ", fakeSurfaceTexture=" + fakeSurfaceTexture +
                ", fakeImageReader=" + fakeImageReader +
                ", mediaPlayer=" + mediaPlayer +
                ", mediaCodec=" + mediaCodec +
                ", isConfigured=" + isConfigured +
                '}';
    }
}