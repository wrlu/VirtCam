package com.wrlus.virtcam.utils;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by wrlu on 2024/3/13.
 */
public class VideoUtils {
    private static final String TAG = "VideoUtils";

    public static MediaPlayer playVideo(File videoFile, Surface surface) {
        MediaPlayer mediaPlayer = new MediaPlayer();
        mediaPlayer.setSurface(surface);
        mediaPlayer.setVolume(0, 0);
        mediaPlayer.setLooping(true);
        mediaPlayer.setOnPreparedListener(
                new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        mp.start();
                    }
                });
        try {
            mediaPlayer.setDataSource(videoFile.getAbsolutePath());
            mediaPlayer.prepare();
            return mediaPlayer;
        } catch (IOException e) {
            Log.e(TAG, "playVideo - IOException", e);
        }
        return null;
    }

    public static byte[] rotateNV21(byte[] yuv, int width, int height, int rotation) {
        if (rotation == 0) return yuv;
        if (rotation % 90 != 0 || rotation < 0 || rotation > 270) {
            throw new IllegalArgumentException("0 <= rotation < 360, rotation % 90 == 0");
        }

        final byte[]  output    = new byte[yuv.length];
        final int     frameSize = width * height;
        final boolean swap      = rotation % 180 != 0;
        final boolean xflip     = rotation % 270 != 0;
        final boolean yflip     = rotation >= 180;

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                final int yIn = j * width + i;
                final int uIn = frameSize + (j >> 1) * width + (i & ~1);
                final int vIn = uIn       + 1;

                final int wOut     = swap  ? height              : width;
                final int hOut     = swap  ? width               : height;
                final int iSwapped = swap  ? j                   : i;
                final int jSwapped = swap  ? i                   : j;
                final int iOut     = xflip ? wOut - iSwapped - 1 : iSwapped;
                final int jOut     = yflip ? hOut - jSwapped - 1 : jSwapped;

                final int yOut = jOut * wOut + iOut;
                final int uOut = frameSize + (jOut >> 1) * wOut + (iOut & ~1);
                final int vOut = uOut + 1;

                output[yOut] = (byte)(0xff & yuv[yIn]);
                output[uOut] = (byte)(0xff & yuv[uIn]);
                output[vOut] = (byte)(0xff & yuv[vIn]);
            }
        }
        return output;
    }

    public static void savePreviewFrameImage(byte[] data, int width, int height,
                                             File dumpFrameOutput, int frameCount) {
        YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21,
                width, height, null);
        try {
            FileOutputStream fos = new FileOutputStream(
                    new File(dumpFrameOutput, frameCount + ".jpg"));
            yuvImage.compressToJpeg(new Rect(0, 0, width, height)
                    , 100, fos);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            Log.e(TAG, "savePreviewFrameImage - IOException", e);
        }
    }
}
