package us.pinguo.svideo.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.opengl.EGL14;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.os.Build;
import android.text.TextUtils;
import android.util.Pair;
import us.pinguo.svideo.encoder.VideoEncoderFromBuffer;
import us.pinguo.svideo.interfaces.ICameraProxyForRecord;
import us.pinguo.svideo.recorder.SAbsVideoRecorder;
import us.pinguo.svideo.recorder.SSurfaceRecorder;

import javax.microedition.khronos.egl.EGL10;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.List;
import java.util.Locale;

/**
 * Created by huangwei on 2015/12/10.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class SVideoUtil {
    /**
     * Version >= 14
     */
    public static final boolean AFTER_ICE_CREAM_SANDWICH = Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    /**
     * Version >= 18
     */
    public static final boolean AFTER_JELLY_BEAN_MR2 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2;
    /**
     * Version >= 21
     */
    public static final boolean AFTER_LOLLIPOP = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    /**
     * Version < 16
     */
    public static final boolean BEFORE_JELLY_BEAN = Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN;
    /**
     * Version < 18
     */
    public static final boolean BEFORE_JELLY_BEAN_MR2 = Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2;

    private static File getCacheDir(Context context) {
        File extCache = context.getExternalCacheDir();
        return extCache != null && extCache.exists() ? extCache : context.getCacheDir();
    }

    /**
     * 注意，这个函数在video文件没写完或者什么异常的情况下，好像会卡死
     *
     * @param videoPath
     * @return
     */
    public static long getVideoDuration(String videoPath) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        long duration = 0;
        try {
            mmr.setDataSource(videoPath);

            String durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            duration = Long.parseLong(durationStr);
        } catch (Exception ex) {
            RL.e("MediaMetadataRetriever exception " + ex);
        } finally {
            mmr.release();
        }
        return duration;
    }

    public static Pair<Integer, Integer> getVideoFrameCount(String input) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(input);
        int trackIndex = selectTrack(extractor, false);
        extractor.selectTrack(trackIndex);
        int keyFrameCount = 0;
        int frameCount = 0;
        while (true) {
            int flags = extractor.getSampleFlags();
            if (flags > 0 && (flags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                keyFrameCount++;
            }
            long sampleTime = extractor.getSampleTime();
            if (sampleTime < 0) {
                break;
            }
            frameCount++;
            extractor.advance();
        }
        extractor.release();
        return new Pair<>(keyFrameCount, frameCount);
    }

    public static void deleteListRecord(List<String> list) {
        if (list == null || list.size() == 0) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            File file = new File(list.get(i));
            if (file.exists()) {
                file.delete();
            }
        }
        list.clear();
    }

//    /**
//     * 合成视频片段
//     *
//     * @param videoList 待合成的视频片段
//     * @return
//     */
//    public static String compositeSegmentVideo(List<String> videoList) {
//        if (videoList == null || videoList.size() == 0) {
//            return null;
//        }
//        final String videoPath = SVideoUtil.getOriginVideoSavePath();
//        try {
//            new File(videoPath).createNewFile();
//        } catch (IOException e) {
//            e.printStackTrace();
//            return null;
//        }
//        String[] videoArr = new String[videoList.size()];
//        videoList.toArray(videoArr);
//
//        PGNativeMethod.initVideoSDK("renderNextFrameCallback", "videoPlayEndCallback", 0);
//        PGNativeMethod.CompositeSegmentVideo(videoArr, videoPath);
//        PGNativeMethod.destroyVideoSDK();
//        return videoPath;
//    }

    public static void deleteFile(String path) {
        if (TextUtils.isEmpty(path)) {
            return;
        }
        File file = new File(path);
        if (file.exists()) {
            file.delete();
        }
    }


    private static class BufferedWritableFileByteChannel implements WritableByteChannel {
        private static final int BUFFER_CAPACITY = 1000000;

        private boolean isOpen = true;
        private final OutputStream outputStream;
        private final ByteBuffer byteBuffer;
        private final byte[] rawBuffer = new byte[BUFFER_CAPACITY];

        private BufferedWritableFileByteChannel(OutputStream outputStream) {
            this.outputStream = outputStream;
            this.byteBuffer = ByteBuffer.wrap(rawBuffer);
        }

        @Override
        public int write(ByteBuffer inputBuffer) throws IOException {
            int inputBytes = inputBuffer.remaining();

            if (inputBytes > byteBuffer.remaining()) {
                dumpToFile();
                byteBuffer.clear();

                if (inputBytes > byteBuffer.remaining()) {
                    throw new BufferOverflowException();
                }
            }

            byteBuffer.put(inputBuffer);

            return inputBytes;
        }

        @Override
        public boolean isOpen() {
            return isOpen;
        }

        @Override
        public void close() throws IOException {
            dumpToFile();
            isOpen = false;
        }

        private void dumpToFile() {
            try {
                outputStream.write(rawBuffer, 0, byteBuffer.position());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static boolean useSurfaceRecord() {
        return true;
    }

    /**
     * @param context
     * @param cameraProxyForRecord
     * @return
     */
    public static SAbsVideoRecorder initVideoRecorder(Context context, ICameraProxyForRecord cameraProxyForRecord) {
        SAbsVideoRecorder sVideoRecorder;
//        if (VideoRecorderAdapter.useFF()) {
//            sVideoRecorder = new SVideoRecorder(context.getApplicationContext(), sdkEffectKey, cameraProxyForRecord);
//        } else if (BEFORE_JELLY_BEAN_MR2 || VideoRecorderAdapter.useYUV()) {
//            //没有指定，根据API判断
//            if (BEFORE_JELLY_BEAN || !SVideoUtil.supportAvcEncode()) {
//                //小于api16或者不支持视频硬编码,走软编
//                sVideoRecorder = new SVideoRecorder(context.getApplicationContext(), sdkEffectKey, cameraProxyForRecord);
//            } else if (BEFORE_JELLY_BEAN_MR2) {
//                //api16,17走MediaCodec+Mp4Parser
//                sVideoRecorder = new SMediaCodecRecorderApi16(context.getApplicationContext(), sdkEffectKey, cameraProxyForRecord);
//            } else {
//                //api18及以上使用MediaCodec+MediaMuxer
//                sVideoRecorder = new SMediaCodecRecorder(context.getApplicationContext(), sdkEffectKey, cameraProxyForRecord);
//            }
//        } else
//            {
        sVideoRecorder = new SSurfaceRecorder(context.getApplicationContext(), cameraProxyForRecord);
//        }
        return sVideoRecorder;
    }

    public static Bitmap getVideoThumbnail(String filePath) {
        Bitmap bitmap = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(filePath);
            bitmap = retriever.getFrameAtTime();
        } catch (IllegalArgumentException e) {
            RL.e(e);
        } catch (RuntimeException e) {
            RL.e(e);
        } finally {
            try {
                retriever.release();
            } catch (RuntimeException e) {
                RL.e(e);
            }
        }
        return bitmap;
    }

    /**
     * 是否支持MP4硬编码
     *
     * @return
     */
    public static boolean supportAvcEncode() {
        MediaCodecInfo codecInfo = VideoEncoderFromBuffer.selectCodec(VideoEncoderFromBuffer.MIME_TYPE);
        if (codecInfo == null) {
            String err = Build.MODEL + " api" + Build.VERSION.SDK_INT + " 不支持 " + VideoEncoderFromBuffer.MIME_TYPE + " 硬编码,改用软编码";
            RL.e(err);
            RL.e(new NotSupportAvcThrowable(err));
            return false;
        } else {
            checkColorFormat(codecInfo);
        }
        return true;
    }

    private static class NotSupportAvcThrowable extends Throwable {
        public NotSupportAvcThrowable(String msg) {
            super(msg);
        }
    }

    private static int mSupportColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
    private static int mPreviewColorFormat = ImageFormat.YV12;

    public static void checkColorFormat(MediaCodecInfo codecInfo) {
        try {
            mSupportColorFormat = VideoEncoderFromBuffer.selectColorFormat(codecInfo, VideoEncoderFromBuffer.MIME_TYPE);
        } catch (Exception e) {
            RL.e(e);
        }
    }

    public static int getSupportColorFormat() {
        return mSupportColorFormat;
    }

    public static int getPreviewColorFormat() {
        return mPreviewColorFormat;
    }

    public static void convertColorFormat(byte[] data, int width, int height, int len, int colorFormat) {
        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                NSVUtil.NV12ToNV21(data, width, height, len);
                break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                //这个函数其实是NV21转420P
                NSVUtil.NV12To420P(data, width, height, len);
                break;
            default:
                /**
                 * 还为支持的格式先用这个函数顶着，多半录出来不正常
                 */
                NSVUtil.NV12ToNV21(data, width, height, len);
                String err = Build.MODEL + " api" + Build.VERSION.SDK_INT + " ColorFormat:" + colorFormat;
                RL.e(new ColorFormatNotSupportThrowable(err));
                /**gt-i9260和galaxy nexus是COLOR_TI_FormatYUV420PackedSemiPlanar，但是galaxy nexus实测走这儿颜色是正常的*/
                break;
        }
    }

    private static class ColorFormatNotSupportThrowable extends Throwable {
        public ColorFormatNotSupportThrowable(String err) {
            super(err);
        }
    }

    /**
     * 根据toWidth和toHieght，返回适用于bitmap的srcRect,只裁剪不压缩
     * 裁剪方式为裁上下或两边
     *
     * @param srcRect
     * @param bitmapWidth
     * @param bitmapHeight
     * @param toWidth
     * @param toHeight
     * @return
     */
    public static Rect getCroppedRect(Rect srcRect, int bitmapWidth, int bitmapHeight, float toWidth, float toHeight) {
        if (srcRect == null) {
            srcRect = new Rect();
        }
        float rate = toWidth / toHeight;
        float bitmapRate = bitmapWidth / (float) bitmapHeight;

        if (Math.abs(rate - bitmapRate) < 0.01) {

            srcRect.left = 0;
            srcRect.top = 0;
            srcRect.right = bitmapWidth;
            srcRect.bottom = bitmapHeight;
        } else if (bitmapRate > rate) {
            //裁两边
            float cutRate = toHeight / (float) bitmapHeight;
            float toCutWidth = cutRate * bitmapWidth - toWidth;
            float toCutWidthReal = toCutWidth / cutRate;

            srcRect.left = (int) (toCutWidthReal / 2);
            srcRect.top = 0;
            srcRect.right = bitmapWidth - (int) (toCutWidthReal / 2);
            srcRect.bottom = bitmapHeight;
        } else {
            //裁上下
            float cutRate = toWidth / (float) bitmapWidth;
            float toCutHeight = cutRate * bitmapHeight - toHeight;
            float toCutHeightReal = toCutHeight / cutRate;

            srcRect.left = 0;
            srcRect.top = (int) (toCutHeightReal / 2);
            srcRect.right = bitmapWidth;
            srcRect.bottom = bitmapHeight - (int) (toCutHeightReal / 2);

        }
        return srcRect;
    }

    public static final boolean isCN() {
        Locale locale = Locale.getDefault();
        return locale.equals(Locale.CHINA) || locale.equals(Locale.CHINESE) || locale.equals(Locale.SIMPLIFIED_CHINESE);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public static void eglPresentationTimeANDROID(long time) {
        EGLExt.eglPresentationTimeANDROID(EGL14.eglGetCurrentDisplay(), EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW), time);
    }

    public static int getVersionCode(Context context) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static void setPresentationTime(long nsecs) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            EGLDisplay eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            EGLSurface eglSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nsecs);
            checkEGLError("eglPresentationTimeANDROID", false);
        }
    }


    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static void checkEGLError(String msg, boolean throwError) {
        int err;
        if ((err = EGL14.eglGetError()) != EGL10.EGL_SUCCESS) {
            RL.e(msg + ": EGL Error: 0x" + Integer.toHexString(err));
            if (throwError) {
                throw new RuntimeException(msg + ": EGL Error: 0x" + Integer.toHexString(err));
            }
        }
    }

    /**
     * 开启一个什么都不做的音频线程来提前申请权限
     */
    public static void moniRecordForPermission() {
        new AudioThread().start();
    }

    private static class AudioThread extends Thread {
        private static final String MIME_TYPE = "audio/mp4a-latm";
        private static final int SAMPLE_RATE = 44100;    // 44.1[KHz] is only setting guaranteed to be available on all devices.
        private static final int BIT_RATE = 64000;
        public static final int SAMPLES_PER_FRAME = 1024;    // AAC, bytes/frame/channel
        public static final int FRAMES_PER_BUFFER = 25;    // AAC, frame/buffer/sec
        private final int[] AUDIO_SOURCES = new int[]{
                MediaRecorder.AudioSource.CAMCORDER,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.DEFAULT,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
        };

        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
            try {
                final int min_buffer_size = AudioRecord.getMinBufferSize(
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);
                int buffer_size = SAMPLES_PER_FRAME * FRAMES_PER_BUFFER;
                if (buffer_size < min_buffer_size) {
                    buffer_size = ((min_buffer_size / SAMPLES_PER_FRAME) + 1) * SAMPLES_PER_FRAME * 2;
                }

                AudioRecord audioRecord = null;
                for (final int source : AUDIO_SOURCES) {
                    try {
                        audioRecord = new AudioRecord(
                                source, SAMPLE_RATE,
                                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buffer_size);
                        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                            audioRecord = null;
                        }
                    } catch (final Exception e) {
                        return;
                    }
                    if (audioRecord != null) {
                        break;
                    }
                }
                if (audioRecord != null) {
                    try {
                        RL.v("AudioThread:start audio recording");
                        audioRecord.startRecording();
                        audioRecord.stop();
                    } finally {
                        audioRecord.release();
                    }
                } else {
                    RL.e("failed to initialize AudioRecord");
                }
            } catch (final Exception e) {
                RL.e("AudioThread#run", e);
            }
            RL.v("AudioThread:finished");
        }
    }

    public static void combineVideoSegments(List<String> videoList, String outputPath) throws IOException {
        if (videoList == null || videoList.size() == 0) {
            return;
        }
        if (videoList.size() == 1) {
            copySingleFile(videoList.get(0), outputPath);
            return;
        }
        MediaMuxer mediaMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        MediaExtractor extractor;
        int videoTrackIndex = -1, audioTrackIndex = -1;
        long preVideoSegmentsTime = 0;
        long preAudioSegmentsTime = 0;

        long lastVideoFrameTime = 0;
        long lastAudioFrameTime = 0;

        ByteBuffer videoBuffer = null;

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean muxerStated = false;
        for (int i = 0; i < videoList.size(); i++) {
            String path = videoList.get(i);
            extractor = new MediaExtractor();
            extractor.setDataSource(path);
            int videoTrack = selectTrack(extractor, false);
            int audioTrack = selectTrack(extractor, true);

            MediaFormat videoFormat = extractor.getTrackFormat(videoTrack);
            if (videoTrackIndex < 0) {
                videoTrackIndex = mediaMuxer.addTrack(videoFormat);
                int rotation = videoFormat.containsKey(MediaFormat.KEY_ROTATION) ?
                        videoFormat.getInteger(MediaFormat.KEY_ROTATION) : 0;
                mediaMuxer.setOrientationHint(rotation);
            }
            if (audioTrackIndex < 0 && audioTrack >= 0) {
                MediaFormat audioFormat = extractor.getTrackFormat(audioTrack);
                audioTrackIndex = mediaMuxer.addTrack(audioFormat);
            }
            if (!muxerStated) {
                muxerStated = true;
                mediaMuxer.start();
            }
            //每个视频的值不一致，所以每次都要检查buffer是否够用
            int maxBufferSize = videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
            if(videoBuffer==null || maxBufferSize>videoBuffer.capacity()){
                videoBuffer = ByteBuffer.allocateDirect(maxBufferSize);
            }
            //写视频帧
            extractor.selectTrack(videoTrack);
            while (true) {
                long sampleTime = preVideoSegmentsTime + extractor.getSampleTime();
                info.presentationTimeUs = sampleTime;
                info.flags = extractor.getSampleFlags();
                    info.size = extractor.readSampleData(videoBuffer, 0);
                if (info.size < 0) {
                    break;
                }
                RL.i("write video:" + info.flags + " time:" + info.presentationTimeUs / 1000 + "ms" + " size:" + info.size);
                mediaMuxer.writeSampleData(videoTrackIndex, videoBuffer, info);
                lastVideoFrameTime = sampleTime;
                extractor.advance();
            }
            //写音频帧
            if (audioTrackIndex >= 0) {
                extractor.unselectTrack(videoTrack);
                extractor.selectTrack(audioTrack);
                while (true) {
                    long sampleTime = preVideoSegmentsTime > preAudioSegmentsTime ?
                            preVideoSegmentsTime : preAudioSegmentsTime + extractor.getSampleTime();
                    info.presentationTimeUs = sampleTime;
                    info.size = extractor.readSampleData(videoBuffer, 0);
                    info.flags = extractor.getSampleFlags();
                    if (info.size < 0) {
                        break;
                    }
                    RL.i("write audio:" + info.flags + " time:" + info.presentationTimeUs / 1000 + "ms" + " size:" + info.size);
                    mediaMuxer.writeSampleData(audioTrackIndex, videoBuffer, info);
                    lastAudioFrameTime = sampleTime;
                    extractor.advance();
                }
            }
            preVideoSegmentsTime = lastVideoFrameTime;
            preAudioSegmentsTime = lastAudioFrameTime;
            extractor.release();
        }
        mediaMuxer.stop();
        mediaMuxer.release();
    }

    public static int selectTrack(MediaExtractor extractor, boolean audio) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (audio) {
                if (mime.startsWith("audio/")) {
                    return i;
                }
            } else {
                if (mime.startsWith("video/")) {
                    return i;
                }
            }
        }
        return -5;
    }

    public static void copySingleFile(String src, String dst) throws IOException {
        FileChannel from = new FileInputStream(src).getChannel();
        FileChannel to = new FileOutputStream(dst).getChannel();
        from.transferTo(0, from.size(), to);
        from.close();
        to.close();
    }

}
