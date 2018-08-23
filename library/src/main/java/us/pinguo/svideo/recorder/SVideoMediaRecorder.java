package us.pinguo.svideo.recorder;

/**
 * Created by huangwei on 2015/11/19.
 */
public class SVideoMediaRecorder {
//        implements
//        SVideoTouchController_MR.OnRecordListener,
//        SurfaceHolder.Callback, MediaRecorder.OnInfoListener, VideoCameraPresenter.OnPreviewStartListener {
//
//    private static final String TAG = "RecordVideo";
//    /**
//     * 视频输出帧率
//     */
//    public static int FRAME_RATE = 30;
//
//    public static boolean isVideoRecording;
//
//    private Activity mContext;
//    private CameraPresenter mCameraProxyForRecord;
//    private String mVideoFileName;
//
//    private SVideoTouchController_MR.OnRecordListener mOnRecordListener;
//    private MediaRecorder mMediaRecorder;
//    CameraLayout mCameraLayout;
//
//    private List<String> mFragentPathList;
//
//    private SVideoTouchController_MR mVideoTouchListener;
//
//    private long mRecordedDuration;
//
//    private long mRecordingStartTime;
//
//    private boolean mFirstStart = true;
//
//    public SVideoMediaRecorder(Activity activity, CameraLayout cameraLayout, CameraPresenter cameraPresenter) {
//        mContext = activity;
//        mCameraProxyForRecord = cameraPresenter;
//        mCameraLayout = cameraLayout;
//        mFragentPathList = new ArrayList<>();
//        mMediaRecorder = new MediaRecorder();
//        mCameraProxyForRecord.setMediaRecorder(mMediaRecorder);
//
//        ((VideoCameraPresenter) mCameraProxyForRecord).setOnPreviewStartListener(this);
//
//    }
//
//    private void init() {
//        final String videoFileName = SVideoUtil.getOriginVideoSavePath();
//        File file = new File(videoFileName);
//        if (!file.exists()) {
//            try {
//                file.createNewFile();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//        mFragentPathList.add(videoFileName);
//
////        if (mCameraProxyForRecord.mCameraOps.mOps.mCameraDevice instanceof CameraDeviceImpl1) {
////            CameraDeviceImpl1 impl1 = (CameraDeviceImpl1) mCameraProxyForRecord.mCameraOps.mOps.mCameraDevice;
////            impl1.getRaw().unlock();
////            mMediaRecorder.setCamera(impl1.getRaw());
////        }
//
//        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
//        // 设置录制视频源为Camera(相机)
//        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
//        adjustPreviewSize();
//
//        mMediaRecorder.setOnInfoListener(this);
//        mMediaRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
//            @Override
//            public void onError(MediaRecorder mr, int what, int extra) {
////                Toast.makeText(mContext, "what:" + what + " extra:" + extra, Toast.LENGTH_SHORT).show();
//                StatisticsUtils.reportError(mContext, new Fault(what, "MediaRecorderError(" + extra + ") on" + android.os.Build.MODEL));
//                if (MediaRecorder.MEDIA_ERROR_SERVER_DIED == what) {
//                    //release并重新new一个MediaRecorder
//                    mMediaRecorder.release();
//                    init();
//                }
//            }
//        });
//        // 设置视频文件输出的路径
//        mMediaRecorder.setOutputFile(videoFileName);
//        try {
//            mMediaRecorder.setMaxDuration((int) (SVideoUtil.MAX_RECORD_DURATION - mRecordedDuration));
//            mMediaRecorder.prepare();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//
//    @Override
//    public void onRecordStart(SVideoTouchController_MR sVideoTouchListener) {
////        init();
//        if (!mFirstStart) {
//            mCameraProxyForRecord.resetCameraToMediaRecorder();
//            init();
//        }
//        mFirstStart = false;
//
//        mVideoTouchListener = sVideoTouchListener;
//        if (mOnRecordListener != null) {
//            mOnRecordListener.onRecordStart();
//        }
//        // 准备录制
//        try {
//            mMediaRecorder.start();
//            mRecordingStartTime = System.currentTimeMillis();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        mVideoTouchListener.startRecordProgress();
//        // 开始录制
//        isVideoRecording = true;
//    }
//
//    private void deleteListRecord(List<String> list) {
//        for (int i = 0; i < list.size(); i++) {
//            File file = new File(list.get(i));
//            if (file.exists()) {
//                file.delete();
//            }
//        }
//    }
//
//    @Override
//    public void onRecordPause() {
////        PGSize previewSize = Camera2SettingModel.instance().getPreviewSize();
////        mCameraLayout.setPreviewScale(previewSize.getWidth(), previewSize.getHeight());
//        isVideoRecording = false;
//        try {
//            mMediaRecorder.setOnErrorListener(null);
//            mMediaRecorder.setPreviewDisplay(null);
//            mMediaRecorder.stop();
//        } catch (RuntimeException e) {
//
//        }
////        finally {
////            mMediaRecorder.release();
////        }
//
//        if (mFragentPathList.size() > 0) {
//            String lastVideoFragment = mFragentPathList.get(mFragentPathList.size() - 1);
//            long duration = getVideoDuration(lastVideoFragment);
//            mRecordedDuration += duration;
//            mVideoTouchListener.setRecordedDuration(mRecordedDuration);
//            L.i("已录制视频长度:" + mRecordedDuration + "ms");
//        }
////        File file = new File(mVideoFileName);
////        if (file.exists()) {
////            file.delete();
////        }
////        glSurfaceView.setAspectRatio(0, 0);
//    }
//
//    @Override
//    public void onRecordEnd() {
//        if (!isVideoRecording) {
//            return;
//        }
//        isVideoRecording = false;
//        L.it(TAG, "onRecordEnd");
////        Toast.makeText(mContext, "录制完成", Toast.LENGTH_SHORT).show();
////        glSurfaceView.setAspectRatio(0, 0);
//
//        final PGSize previewSize = Camera2SettingModel.instance().getPreviewSize();
//        mCameraLayout.setPreviewScale(previewSize.getWidth(), previewSize.getHeight());
//
//        try {
//            mMediaRecorder.stop();
//        } catch (RuntimeException e) {
//            //debug断点才会出错
//        }
//        mMediaRecorder.release();
//        mRecordedDuration = 0;
//
//        //需分段合成
//        saveVideo();
//    }
//
//    public void release() {
//        if (mMediaRecorder != null) {
//            mMediaRecorder.release();
//        }
//    }
//
//    public void saveVideo() {
//        release();
//        //需分段合成
//        if (mFragentPathList.size() > 1) {
//            final BSProgressDialog dialog = BSProgressDialog.show(mContext, "", "请稍候……");
//            new Thread(new Runnable() {
//                @Override
//                public void run() {
//                    final String compositedVideoPath = SVideoUtil.compositeSegmentVideo(mFragentPathList);
//                    deleteListRecord(mFragentPathList);
//                    mContext.runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            dialog.dismiss();
//                            if (mOnRecordListener != null && compositedVideoPath != null) {
//                                mOnRecordListener.onRecordSuccess(compositedVideoPath, "");
//                            }
//                        }
//                    });
//                }
//            }).start();
//        } else {
//            if (mOnRecordListener != null) {
//                mContext.runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        mOnRecordListener.onRecordSuccess(mFragentPathList.get(0), "");
//                        mFragentPathList.clear();
//                    }
//                });
//            }
//        }
//    }
//
//
//    public SVideoTouchController_MR.OnRecordListener getOnRecordListener() {
//        return mOnRecordListener;
//    }
//
//    public void addOnRecordListener(SVideoTouchController_MR.OnRecordListener onRecordListener) {
//        this.mOnRecordListener = onRecordListener;
//    }
//
//    @Override
//    public void surfaceCreated(SurfaceHolder holder) {
//
//    }
//
//    @Override
//    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
//
//    }
//
//    @Override
//    public void surfaceDestroyed(SurfaceHolder holder) {
//
//    }
//
//    @Override
//    public void onInfo(MediaRecorder mr, int what, int extra) {
//        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
//            onRecordEnd();
//            if (mVideoTouchListener != null) {
//                mVideoTouchListener.endRecordProgress();
//            }
//        }
//    }
//
//    public long getVideoDuration(String videoPath) {
//        android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
//        long duration = 0;
//        try {
//            mmr.setDataSource(videoPath);
//
//            String durationStr = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
//            duration = Long.parseLong(durationStr);
//        } catch (Exception ex) {
//            L.e("MediaMetadataRetriever exception " + ex);
//        } finally {
//            mmr.release();
//        }
//        return duration;
//    }
//
//    @Override
//    public void OnPreviewStart() {
//        init();
//    }
//
//    /**
//     * 调整预览尺寸
//     */
//    public void adjustPreviewSize() {
//        final CamcorderProfile profile = SVideoUtil.getProperProfile(mContext, mCameraProxyForRecord);
////                CamcorderProfile.get(Integer.valueOf(mCameraProxyForRecord.mCameraSetting.getCameraId()), CamcorderProfile.QUALITY_480P);
//        L.it(TAG, "profile:" + profile.videoFrameHeight + "," + profile.videoFrameWidth);
////        profile.videoFrameRate = VideoEditRenderThread.FRAME_RATE;
//        FRAME_RATE = profile.videoFrameRate;
//        PGSize previewSize = Camera2SettingModel.instance().getPreviewSize();
//        L.it(TAG, "previewSize:" + previewSize.getHeight() + "," + previewSize.getWidth());
//        mMediaRecorder.setProfile(profile);
//
//        mCameraProxyForRecord.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight);
//
//        int mScreenWidth = DisplayUtil.getScreenWidth(mContext);
//        int mScreenHeight = DisplayUtil.getScreenHeight(mContext);
//        int ratioWidth = profile.videoFrameWidth;
//        int ratioHeight = profile.videoFrameHeight;
//        if (mScreenWidth < mScreenHeight * ratioWidth / ratioHeight) {
//            mCameraLayout.setPreviewScale(mScreenWidth, mScreenWidth * ratioHeight / ratioWidth);
//        } else {
//            mCameraLayout.setPreviewScale(mScreenHeight * ratioWidth / ratioHeight, mScreenHeight);
//        }
//    }
}
