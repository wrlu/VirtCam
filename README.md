# VirtCam
Android Virtual Camera
> Warning: This project is for study usage only, DO NOT use it for illegal purposes.

# Usage
Inject video path: 
```
/sdcard/Android/data/{hooked_package_name}/files/ccc/virtual.mp4
```
(Camera1Hooker only) This video will be decoded and save frames to this path (can be deleted after hooked manually):
```
/sdcard/Android/data/{hooked_package_name}/files/decode_video_{random_uuid}/
```
(Camera1Hooker only, Optional) Saved preview callback frames to this path (can be deleted after hooked manually):
```
/sdcard/Android/data/{hooked_package_name}/files/dump_frame_{random_uuid}/
```

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
```

# Credits
* Android-VideoToImages: https://github.com/zhantong/Android-VideoToImages
* VCam: https://github.com/Xposed-Modules-Repo/com.example.vcam