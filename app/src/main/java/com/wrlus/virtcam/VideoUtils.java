package com.wrlus.virtcam;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.MediaPlayer;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import com.polarxiong.videotoimages.OutputImageFormat;
import com.polarxiong.videotoimages.VideoToFrames;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by wrlu on 2024/3/13.
 */
public class VideoUtils {
    private static final String TAG = "VideoUtils";
    private static final Map<String, ConcurrentLinkedQueue<String>> savedFrameInfoMap =
            new ConcurrentHashMap<>();

    public static void playVideo(File videoFile, Surface surface) {
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
        } catch (IOException e) {
            Log.e(TAG, "playVideo - IOException", e);
        }
    }

    public static void decodeVideo(File videoFile, String pkgName) {
        String mapKey = pkgName + "_" + videoFile.getAbsolutePath();
        if (!savedFrameInfoMap.containsKey(mapKey)) {
            ConcurrentLinkedQueue<String> infoQueue = new ConcurrentLinkedQueue<>();
            synchronized (savedFrameInfoMap) {
                savedFrameInfoMap.put(mapKey, infoQueue);
            }
            File outputFolder = new File(Environment.getExternalStorageDirectory(),
                    "Android/data/" + pkgName + "/files/decode_video_" + UUID.randomUUID() + "/");
            VideoToFrames videoToFrames = new VideoToFrames();
            videoToFrames.setSaveFrames(outputFolder.getAbsolutePath(), OutputImageFormat.NV21);
            videoToFrames.setCallback(new VideoToFrames.Callback() {
                @Override
                public void onDecodeFrameToFile(int index, String fileName) {
                    ConcurrentLinkedQueue<String> infoQueue = savedFrameInfoMap.get(mapKey);
                    if (infoQueue != null) {
                        infoQueue.add(fileName);
                    }
                }
                @Override
                public void onFinishDecode() {
                    Log.i(TAG, "onFinishDecode: finish decode video: " +
                            videoFile.getAbsolutePath() + ", to output path: " +
                            outputFolder.getAbsolutePath());
                }
            });
            videoToFrames.decode(videoFile.getAbsolutePath());
        }

    }

    public static byte[] getReplacedPreviewFrame(File videoFile, String pkgName) {
        String mapKey = pkgName + "_" + videoFile.getAbsolutePath();
        ConcurrentLinkedQueue<String> infoQueue = null;
        if (savedFrameInfoMap.containsKey(mapKey)) {
            infoQueue = savedFrameInfoMap.get(mapKey);
        } else {
            Log.e(TAG, "replacePreviewFrame: " +
                    "cannot find saved frame queue.");
        }
        if (infoQueue != null) {
            String savedFrameFileName = infoQueue.poll();
            if (savedFrameFileName != null) {
                infoQueue.add(savedFrameFileName);
                return readFile(savedFrameFileName);
            }
        } else {
            Log.e(TAG, "replacePreviewFrame: " +
                    "found null saved frame queue.");
        }
        return null;
    }

    public static void savePreviewFrameImage(byte[] data, int width, int height,
                                             File dumpFrameOutput, int frameCount) {
        YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21,
                width, height, null);
        try {
            FileOutputStream fos = new FileOutputStream(
                    new File(dumpFrameOutput, frameCount + ".jpeg"));
            yuvImage.compressToJpeg(new Rect(0, 0, width, height)
                    , 100, fos);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            Log.e(TAG, "savePreviewFrameImage - IOException", e);
        }
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

    private static byte[] readFile(String filePath) {
        Path path = Paths.get(filePath);
        try {
            int size = (int) Files.size(path);
            byte[] data = new byte[size];
            FileInputStream fis = new FileInputStream(filePath);
            int readSize = fis.read(data);
            if (readSize != size) {
                Log.w(TAG, "readFile: readSize != size");
            }
            fis.close();
            return data;
        } catch (IOException e) {
            Log.e(TAG, "readFile - IOException", e);
        }
        return null;
    }
}
