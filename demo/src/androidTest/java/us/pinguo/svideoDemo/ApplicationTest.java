package us.pinguo.svideoDemo;

import android.annotation.TargetApi;
import android.app.Application;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.test.ApplicationTestCase;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * <a href="http://d.android.com/tools/testing/testing_android.html">Testing Fundamentals</a>
 */
public class ApplicationTest extends ApplicationTestCase<Application> {
    private static final String TAG = "hwLog";

    public ApplicationTest() {
        super(Application.class);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void test() {
        /** 用来解码 */
        MediaCodec mMediaCodec = null;
        /** 用来读取音频文件 */
        MediaExtractor extractor;
        MediaFormat format = null;
        String mime = null;
        int sampleRate = 0, channels = 0, bitrate = 0;
        long presentationTimeUs = 0, duration = 0;
        String uri = "/mnt/sdcard2/C360VID_20160524_141411.mp4";
        extractor = new MediaExtractor();
        // 根据路径获取源文件
        try {
            extractor.setDataSource(new FileInputStream(new File(uri)).getFD());
        } catch (Exception e) {
            Log.e(TAG, " 设置文件路径错误" + e.getMessage());
        }
        try {
            // 音频文件信息
            format = extractor.getTrackFormat(1);
            mime = format.getString(MediaFormat.KEY_MIME);
            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            // 声道个数：单声道或双声道
            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            // if duration is 0, we are probably playing a live stream
            duration = format.getLong(MediaFormat.KEY_DURATION);
            // System.out.println("歌曲总时间秒:"+duration/1000000);
            bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE);
        } catch (Exception e) {
            Log.e(TAG, "音频文件信息读取出错：" + e.getMessage());
            // 不要退出，下面进行判断
        }
        Log.d(TAG, "Track info: mime:" + mime + " 采样率sampleRate:" + sampleRate + " channels:" + channels + " bitrate:"
                + bitrate + " duration:" + duration);
        if (format == null || !mime.startsWith("video/")) {
            Log.e(TAG, "不是视频文件 end !");
            return;
        }
        // 实例化一个指定类型的解码器,提供数据输出
        // Instantiate an encoder supporting output data of the given mime type
        try {
            mMediaCodec = MediaCodec.createDecoderByType(mime);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mMediaCodec == null) {
            Log.e(TAG, "创建解码器失败！");
            return;
        }
        mMediaCodec.configure(format, null, null, 0);

        mMediaCodec.start();
        // 用来存放目标文件的数据
        ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
        // 解码后的数据
        ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
        extractor.selectTrack(1);
        // ==========开始解码=============
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        final long kTimeOutUs = 10;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (!sawOutputEOS) {
            try {
                if (!sawInputEOS) {
                    int inputBufIndex = mMediaCodec.dequeueInputBuffer(kTimeOutUs);
                    if (inputBufIndex >= 0) {
                        ByteBuffer dstBuf = inputBuffers[inputBufIndex];

                        int sampleSize = extractor.readSampleData(dstBuf, 0);
                        if (sampleSize < 0) {
                            Log.d(TAG, "saw input EOS. Stopping playback");
                            sawInputEOS = true;
                            sampleSize = 0;
                        } else {
                            presentationTimeUs = extractor.getSampleTime();
                            Log.i(TAG, "presentationTimeUs:" + presentationTimeUs);
                        }

                        mMediaCodec.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs,
                                sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

                        if (!sawInputEOS) {
                            extractor.advance();
                        }

                    } else {
                        Log.e(TAG, "inputBufIndex " + inputBufIndex);
                    }
                } // !sawInputEOS

                // decode to PCM and push it to the AudioTrack player
                int res = mMediaCodec.dequeueOutputBuffer(info, kTimeOutUs);

                if (res >= 0) {
                    int outputBufIndex = res;
                    ByteBuffer buf = outputBuffers[outputBufIndex];
                    final byte[] chunk = new byte[info.size];
                    buf.get(chunk);
                    buf.clear();
                    if (chunk.length > 0) {

                        // chunk解码后的音频流
                        // TODO:处理...
                    }
                    mMediaCodec.releaseOutputBuffer(outputBufIndex, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.d(TAG, "saw output EOS.");
                        sawOutputEOS = true;
                    }

                } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outputBuffers = mMediaCodec.getOutputBuffers();
                    Log.w(TAG, "[AudioDecoder]output buffers have changed.");
                } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat oformat = mMediaCodec.getOutputFormat();
                    Log.w(TAG, "[AudioDecoder]output format has changed to " + oformat);
                } else {
                    Log.w(TAG, "[AudioDecoder] dequeueOutputBuffer returned " + res);
                }

            } catch (RuntimeException e) {
                Log.e(TAG, "[decodeMP3] error:" + e.getMessage());
            }
        }
        // =================================================================================
        if (mMediaCodec != null) {
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;
        }
        if (extractor != null) {
            extractor.release();
            extractor = null;
        }
        // clear source and the other globals
        duration = 0;
        mime = null;
        sampleRate = 0;
        channels = 0;
        bitrate = 0;
        presentationTimeUs = 0;
        duration = 0;
    }
}