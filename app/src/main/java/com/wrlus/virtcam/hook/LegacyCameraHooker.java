package com.wrlus.virtcam.hook;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.polarxiong.videotoimages.OutputImageFormat;
import com.polarxiong.videotoimages.VideoToFrames;
import com.wrlus.virtcam.utils.Config;
import com.wrlus.virtcam.utils.VideoUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;

/**
 * Created by wrlu on 2024/3/13.
 */
public class LegacyCameraHooker {
    private static final String TAG = "VirtCamera-1";
    private int frameCount = 0;
    private final Map<Surface, CameraHookTexture> hookTextureQueue =
            new ConcurrentHashMap<>();
    private SurfaceTexture fakeSurfaceTexture;
    private final Map<String, ConcurrentLinkedQueue<String>> decodedFrameInfoMap =
            new ConcurrentHashMap<>();
    private final File baseFile;
    private final File videoFile;

    public LegacyCameraHooker(File baseFile) {
        this.baseFile = baseFile;
        this.videoFile = new File(baseFile, Config.videoPath);
    }

    @SuppressWarnings({"deprecation"})
    public void hookCamera1(ClassLoader classLoader) {
        if (!videoFile.exists()) {
            Log.e(TAG, "Cannot find virtual video, please put in " +
                    videoFile.getAbsolutePath());
            return;
        }
        XposedHelpers.findAndHookMethod(Camera.class,
                "setPreviewTexture", SurfaceTexture.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Log.w(TAG, "Before setPreviewTexture");
                        SurfaceTexture surfaceTexture = (SurfaceTexture) param.args[0];
                        if (surfaceTexture != null && !fakeSurfaceTexture.equals(surfaceTexture)) {
                            Surface textureSurface = new Surface(surfaceTexture);
                            fakeSurfaceTexture = new SurfaceTexture(10);
                            Surface fakeSurface = new Surface(fakeSurfaceTexture);
                            hookTextureQueue.put(textureSurface,
                                    new CameraHookTexture(fakeSurface, null));
                            param.args[0] = fakeSurfaceTexture;
                        }
                    }
                });
        XposedHelpers.findAndHookMethod(Camera.class,
                "setPreviewDisplay", SurfaceHolder.class, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        Log.w(TAG, "Replace setPreviewDisplay");
                        Camera thisCamera = (Camera) param.thisObject;
                        SurfaceHolder surfaceHolder = (SurfaceHolder) param.args[0];
                        if (surfaceHolder != null) {
                            Surface displaySurface = surfaceHolder.getSurface();
                            fakeSurfaceTexture = new SurfaceTexture(10);
                            Surface fakeSurface = new Surface(fakeSurfaceTexture);
                            hookTextureQueue.put(displaySurface,
                                    new CameraHookTexture(fakeSurface, null));
                            // Give up setPreviewDisplay call and use setPreviewTexture instead.
                            thisCamera.setPreviewTexture(fakeSurfaceTexture);
                        }
                        return null;
                    }
                });
        XposedHelpers.findAndHookMethod(Camera.class,
                "startPreview", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Log.w(TAG, "Before startPreview");
                        for (Surface output : hookTextureQueue.keySet()) {
                            if (output != null && output.isValid()) {
                                hookTextureQueue.get(output).mediaPlayer =
                                        VideoUtils.playVideo(videoFile, output);
                            }
                        }
                    }
                });
        XposedHelpers.findAndHookMethod(Camera.class,
                "stopPreview", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Log.w(TAG, "After stopPreview");
                        for (CameraHookTexture texture : hookTextureQueue.values()) {
                            if (texture.fakeSurface != null) texture.fakeSurface.release();
                            if (texture.mediaPlayer != null) texture.mediaPlayer.release();
                        }
                        hookTextureQueue.clear();
                        fakeSurfaceTexture.release();
                        fakeSurfaceTexture = null;
                        frameCount = 0;
                    }
                });
        XposedHelpers.findAndHookMethod(Camera.class,
                "setPreviewCallback", Camera.PreviewCallback.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Log.w(TAG, "Before setPreviewCallback");
                        Camera.PreviewCallback callback = (Camera.PreviewCallback) param.args[0];
                        if (videoFile.exists()) {
                            String infoKey = videoFile.getAbsolutePath();
                            // We will only decode a video once.
                            if (!decodedFrameInfoMap.containsKey(infoKey)) {
                                // Create decoded video frame saved path.
                                File outputDir = new File(baseFile,
                                        "files/decode_video_" + UUID.randomUUID());
                                Log.w(TAG, "Create dir " + outputDir.getAbsolutePath() +
                                        " result: " + outputDir.mkdir());

                                final ConcurrentLinkedQueue<String> decodeFrameInfoQueue =
                                        new ConcurrentLinkedQueue<>();
                                decodedFrameInfoMap.put(infoKey, decodeFrameInfoQueue);

                                // Use VideoToFrames to decode video, will run in a handler thread.
                                VideoToFrames videoToFrames = new VideoToFrames();
                                videoToFrames.setSaveFrames(outputDir.getAbsolutePath(),
                                        OutputImageFormat.NV21);
                                videoToFrames.setCallback(new VideoToFrames.Callback() {
                                    @Override
                                    public void onDecodeFrameToFile(int index, String fileName) {
                                        decodeFrameInfoQueue.add(fileName);
                                    }
                                    @Override
                                    public void onFinishDecode() {
                                        Log.i(TAG, "onFinishDecode: finish decode video: " +
                                                videoFile.getAbsolutePath() + ", to path: " +
                                                outputDir.getAbsolutePath());
                                    }
                                });
                                videoToFrames.decode(videoFile.getAbsolutePath());
                            }
                            // Hook the real preview callback method.
                            hookPreviewCallback(callback, videoFile);
                        } else {
                            Log.e(TAG, "Video not exists!");
                        }
                    }
                });
    }

    @SuppressWarnings({"deprecation"})
    private void hookPreviewCallback(Camera.PreviewCallback callback, File videoFile) {
        File dumpFrameOutput = new File(
                baseFile, "files/dump_frame_" + UUID.randomUUID() + "/");
        if (Config.enableLegacyCameraDumpFrame) {
            if (!dumpFrameOutput.exists()) {
                Log.e(TAG, "dump frame output mkdir: " +
                        dumpFrameOutput.mkdir());
            }
        }
        Class<?> callbackClass = callback.getClass();
        Log.e(TAG, "Callback class name: " + callbackClass.getName());
        XposedHelpers.findAndHookMethod(callbackClass, "onPreviewFrame",
                byte[].class, Camera.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Log.w(TAG, "Before onPreviewFrame");
                        Camera camera = (Camera) param.args[1];
                        Camera.Size previewSize = camera
                                .getParameters().getPreviewSize();
                        byte[] newData = getReplacedPreviewFrame(videoFile);
                        if (newData != null) {
                            // We need exchange width and height for rotation.
                            int videoWidth = previewSize.height;
                            int videoHeight = previewSize.width;
                            byte[] rotateData = VideoUtils.rotateNV21(newData,
                                    videoWidth, videoHeight, 90);
                            param.args[0] = rotateData;
                        } else {
                            // We do not want to leak real camera data here.
                            param.args[0] = null;
                            Log.e(TAG, "Replace " +
                                    "onPreviewFrame data failed !!!");
                        }
                    }
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Log.w(TAG, "After onPreviewFrame");
                        if (Config.enableLegacyCameraDumpFrame) {
                            byte[] data = (byte[]) param.args[0];
                            Camera camera = (Camera) param.args[1];
                            Camera.Size previewSize = camera
                                    .getParameters().getPreviewSize();
                            VideoUtils.savePreviewFrameImage(data,
                                    previewSize.width, previewSize.height,
                                    dumpFrameOutput, frameCount);
                            ++frameCount;
                        }
                    }
                });
    }

    private byte[] getReplacedPreviewFrame(File videoFile) {
        String mapKey = videoFile.getAbsolutePath();
        if (decodedFrameInfoMap.containsKey(mapKey)) {
            ConcurrentLinkedQueue<String> decodeFrameInfoQueue = decodedFrameInfoMap.get(mapKey);
            if (decodeFrameInfoQueue != null) {
                String savedFrameFileName = decodeFrameInfoQueue.poll();
                if (savedFrameFileName != null) {
                    decodeFrameInfoQueue.add(savedFrameFileName);
                    return readFile(savedFrameFileName);
                }
            } else {
                Log.e(TAG, "replacePreviewFrame: " +
                        "found null saved frame queue.");
            }
        } else {
            Log.e(TAG, "replacePreviewFrame: " +
                    "cannot find saved frame queue.");
        }
        return null;
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

