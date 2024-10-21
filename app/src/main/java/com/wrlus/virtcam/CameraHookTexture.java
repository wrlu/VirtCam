package com.wrlus.virtcam;

import android.media.MediaPlayer;
import android.view.Surface;

public class CameraHookTexture {
    public CameraHookTexture() {}
    public CameraHookTexture(Surface fakeSurface, MediaPlayer mediaPlayer) {
        this.fakeSurface = fakeSurface;
        this.mediaPlayer = mediaPlayer;
    }

    /**
     * Fake surface to receive camera image.
     */
    public Surface fakeSurface;
    /**
     * MediaPlayer to play inject video.
     */
    public MediaPlayer mediaPlayer;
}