# SVideoRecorder

[SVideoRecorder](https://github.com/yellowcath/SVideoRecorder)使用Android原生的MediaCodec进行视频录制，对比大量使用FFmpeg进行录制的库优点如下：

- **体积小** ：编译后的aar只有187K，ffmpeg一个so就7、8M，精简之后也差不多还有一半大小
- **速度快** ：在huaweiP9上，720P的一帧：
 FFmpeg编码时间:50~60ms
 MediaCodec（YUV）编码时间：20~25ms
 MediaCodec（Surface）编码时间：10~15ms
- **CPU占用低** ：ffmpeg录制时占用CPU低端机明显卡顿，MediaCodec录制时几乎无影响

缺点是只支持Android4.3+（Android4.1和4.2已有MediaCodec，但是官方不保证可用）

-------------------
[TOC]

## 功能简介
1、录制相机原始视频(YUV)
2、录制Surface，用户可自行在相机原始预览数据上添加滤镜、贴纸等特效，再直接录制下来
3、支持分段录制
4、支持分段录制时进行回退

##使用
主要类图如下
 ![enter image description here](https://github.com/yellowcath/SVideoRecorder/raw/develop-git/readme/ISVideoRecorder.png)
SMediaCodecRecorder:接收YUV数据进行录制
SSurfaceRecorder:提供一个Surface，录制绘制到该Surface上的图像数据
SSegmentRecorder:对上述两个类进行包装，扩展出分段录制的能力
###初始化
``` java
        //实现ICameraProxyForRecord接口，提供预览参数
        ICameraProxyForRecord cameraProxyForRecord = new ICameraProxyForRecord() {
            @Override
            public void addSurfaceDataListener(PreviewSurfaceListener listener, SurfaceCreatedCallback callback) {
            //SSurfaceRecorder调用
                 RecordHelper.setPreviewSurfaceListener(previewSurfaceListener, surfaceCreatedCallback);
            }
            @Override
            public void removeSurfaceDataListener(PreviewSurfaceListener listener) {
             //SSurfaceRecorder调用
             RecordHelper.setPreviewSurfaceListener(null, null);
            }
            @Override
            public void addPreviewDataCallback(PreviewDataCallback callback) {
                //SMediaCodecRecorder调用
                mCallback = callback;
            }
            @Override
            public void removePreviewDataCallback(PreviewDataCallback callback) {
                //SMediaCodecRecorder调用
                mCallback = null;
            }
            @Override
            public int getPreviewWidth() {
                return mPreviewSize.width;
            }
            @Override
            public int getPreviewHeight() {
                return mPreviewSize.height;
            }
            @Override
            public int getVideoRotation() {
                return mCameraFacing == Camera.CameraInfo.CAMERA_FACING_BACK ? 90 : 270;
            }
        };
        mRecorder = new SMediaCodecRecorder(this, cameraProxyForRecord);
        mRecorder.addRecordListener(this);
```
### 数据帧来源
SMediaCodecRecorder
``` java
    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (mCallback != null) {
            long timeUs = System.nanoTime() / 1000;
            mCallback.onPreviewData(data, timeUs);
        }
    }
```
SSurfaceRecorder
> Demo里提供两种方式（详见RecordHelper.java）：
> 1、 drawBlitFrameBuffer,将预览界面的图像数据直接拷贝到MediaCodec的Surface里，要求GLES3.0，部分老机型可能支持不太好
> 2、drawBlit2X，直接将预览界面的图像数据重复绘制一次到MediaCodec的Surface，考虑到性能问题，这里需要使用FBO
###调用
``` java
    //开始录制
    mRecorder.startRecord();
    //结束录制,成功后回调OnRecordSuccess
    mRecorder.stopRecord();
    //暂停录制，只用于SSegmentRecorder
    mRecorder.pauseRecord();
    //恢复录制，只用于SSegmentRecorder
    mRecorder.resumeRecord();
    //取消，回调OnRecordFail()
    mRecorder.cancelRecord();
```
##Demo
1、正常录YUV格式视频
2、分段录YUV格式视频
3、分段录带特效视频（Surface）
![enter image description here](https://github.com/yellowcath/SVideoRecorder/raw/develop-git/readme/demo1.png)
![enter image description here](https://github.com/yellowcath/SVideoRecorder/raw/develop-git/readme/demo2.png)





