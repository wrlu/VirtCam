package com.wrlus.virtcam.utils;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.polarxiong.videotoimages.OutputImageFormat;
import com.polarxiong.videotoimages.VideoToFrames;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by wrlu on 2024/3/13.
 */
public class VideoUtils {
    private static final String TAG = "VideoUtils";
    private static final ConcurrentLinkedQueue<String> decodedFrames =
            new ConcurrentLinkedQueue<>();
    public enum DecodeStatus {
        NOT_START,
        DECODING,
        FINISHED,
    }
    private static DecodeStatus decodeToFileStatus = DecodeStatus.NOT_START;

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

    public static MediaCodec decodeVideoToSurface(File videoFile, Surface surface) {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(videoFile.toString());
            int trackIndex = VideoToFrames.selectTrack(extractor);
            extractor.selectTrack(trackIndex);
            MediaFormat mediaFormat = extractor.getTrackFormat(trackIndex);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            mediaFormat.setInteger(MediaFormat.KEY_WIDTH, 640);
            mediaFormat.setInteger(MediaFormat.KEY_WIDTH, 480);
            MediaCodec mediaCodec = MediaCodec
                    .createDecoderByType(mediaFormat.getString(MediaFormat.KEY_MIME));
            mediaCodec.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            long startMs = System.currentTimeMillis();
            mediaCodec.setCallback(new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
                    try {
                        ByteBuffer inputBuffer = codec.getInputBuffer(index);
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize > 0) {
                            long presentationTimeUs = extractor.getSampleTime();
                            codec.queueInputBuffer(index, 0, sampleSize, presentationTimeUs, 0);
                            extractor.advance();
                        } else {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                            extractor.advance();
                        }
                    } catch (IllegalStateException e) {
                        Log.e(TAG, "IllegalStateException in onInputBufferAvailable", e);
                    }
                }

                @Override
                public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        return;
                    }
                    // Time sync
                    long timeDifference = info.presentationTimeUs / 1000 - (System.currentTimeMillis() - startMs);
                    if (timeDifference > 0) {
                        try {
                            Thread.sleep(timeDifference);
                        } catch (InterruptedException e) {
                            Log.e(TAG, "InterruptedException when sync in onOutputBufferAvailable", e);
                        }
                    }
                    try {
                        codec.releaseOutputBuffer(index, true);
                    } catch (IllegalStateException e) {
                        Log.e(TAG, "IllegalStateException in onOutputBufferAvailable", e);
                    }
                }

                @Override
                public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {

                }

                @Override
                public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {

                }
            });
            mediaCodec.configure(mediaFormat, surface, null, 0);
            mediaCodec.start();
            return mediaCodec;
        } catch (IOException e) {
            Log.e(TAG, "decodeVideoToSurface - IOException", e);
        }
        return null;
    }

    public static void decodeVideoAndSaveNV21(File videoFile, File outputDir) {
        if (decodeToFileStatus == DecodeStatus.NOT_START) {
            decodeToFileStatus = DecodeStatus.DECODING;
            // Create decoded video frame saved path.
            Log.w(TAG, "Create dir " + outputDir.getAbsolutePath() +
                    " result: " + outputDir.mkdir());
            // Use VideoToFrames to decode video, will run in a handler thread.
            VideoToFrames videoToFrames = new VideoToFrames();
            videoToFrames.setSaveFrames(outputDir.getAbsolutePath(),
                    OutputImageFormat.NV21);
            videoToFrames.setCallback(new VideoToFrames.Callback() {
                @Override
                public void onDecodeFrameToFile(int index, String fileName) {
                    decodedFrames.add(fileName);
                }

                @Override
                public void onFinishDecode() {
                    decodeToFileStatus = DecodeStatus.FINISHED;
                    Log.i(TAG, "onFinishDecode: finish decode video: " +
                            videoFile.getAbsolutePath() + ", to path: " +
                            outputDir.getAbsolutePath());
                }
            });
            videoToFrames.decode(videoFile.getAbsolutePath());
        }
    }

    public static byte[] getReplacedPreviewFrame() {
        if (VideoUtils.getDecodeToFileStatus() == VideoUtils.DecodeStatus.FINISHED) {
            String savedFrameFileName = VideoUtils.getDecodedFrames().poll();
            // Looping play.
            VideoUtils.getDecodedFrames().add(savedFrameFileName);
            Log.w(TAG, "Serve replaced preview frame: " + savedFrameFileName);
            return readFile(savedFrameFileName);
        } else {
            return null;
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

    public static DecodeStatus getDecodeToFileStatus() {
        return decodeToFileStatus;
    }

    public static ConcurrentLinkedQueue<String> getDecodedFrames() {
        return decodedFrames;
    }
}
