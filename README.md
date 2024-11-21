# VirtCam
Android Virtual Camera
> Warning: This project is for study usage only, DO NOT use it for illegal purposes.


# Hooked APIs:
## Legacy Camera (android.hardware.Camera)
```
android.hardware.Camera#setPreviewTexture
android.hardware.Camera#setPreviewDisplay
android.hardware.Camera#startPreview
android.hardware.Camera#setPreviewCallback
android.hardware.Camera$PreviewCallback#onPreviewFrame
```
## Camera 2 (android.hardware.camera2)
```
android.hardware.camera2.impl.CameraDeviceImpl#createCaptureSession
android.hardware.camera2.CaptureRequest$Builder#addTarget
android.hardware.camera2.impl.CameraCaptureSessionImpl#setRepeatingRequest
android.hardware.camera2.impl.CameraDeviceImpl#close
android.media.ImageReader#newInstance
android.media.ImageReader#close
android.media.ImageReader#acquireNextImage
android.media.ImageReader$SurfaceImage#getPlanes
```

# Default settings:
```
enableCamera2Hook=1
enableCamera2DumpFrame=0
enableCamera2ImageInject=0
enableLegacyCameraHook=1
enableLegacyCameraDumpFrame=0
videoPath=files/fkv/virtual.mp4
rotatedVideoPath=files/fkv/rotated_virtual.mp4
imageFramesPath=files/fkv/image_frames
```

# Credits
* Android-VideoToImages: https://github.com/zhantong/Android-VideoToImages
* VCam: https://github.com/Xposed-Modules-Repo/com.example.vcam