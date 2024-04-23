# VirtCam
Android Virtual Camera
> Warning: This project is for study usage only, DO NOT use it for illegal purposes.

# Usage
Inject video path: 
```
/sdcard/Android/data/{hooked_package_name}/files/ccc/virtual.mp4
```
This video will be decoded and save frames to this path (can be deleted after hooked manually):
```
/sdcard/Android/data/{hooked_package_name}/files/decode_video_{random_uuid}/
```
(Optional) Saved preview callback frames to this path (can be deleted after hooked manually):
```
/sdcard/Android/data/{hooked_package_name}/files/dump_frame_{random_uuid}/
```

# Hooked APIs:
```
android.hardware.Camera#setPreviewTexture
android.hardware.Camera#setPreviewDisplay
android.hardware.Camera#startPreview
android.hardware.Camera#setPreviewCallback
android.hardware.Camera$PreviewCallback#onPreviewFrame
```

# Credits
* VCam: https://github.com/Xposed-Modules-Repo/com.example.vcam
* Android-VideoToImages: https://github.com/zhantong/Android-VideoToImages