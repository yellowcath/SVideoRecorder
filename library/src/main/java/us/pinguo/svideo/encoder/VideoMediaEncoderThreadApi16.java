package us.pinguo.svideo.encoder;

import android.media.MediaMuxer;

import java.util.concurrent.CountDownLatch;

/**
 * Created by huangwei on 2016/1/18.
 */
public class VideoMediaEncoderThreadApi16 extends VideoMediaEncoderThread {
    public VideoMediaEncoderThreadApi16(int width, int height, int bitRate, int frameRate, int iFrameInterval, String path, MediaMuxer mediaMuxer, CountDownLatch countDownLatch) {
        super(width, height, bitRate, frameRate, iFrameInterval, path,mediaMuxer, countDownLatch);
    }

    @Override
    protected void initRecorder(int width, int height, int bitRate, int frameRate, int iFrameInterval,String path,MediaMuxer mediaMuxer) {
        mRecorder = new VideoEncoderApi16(
                width,
                height,
                bitRate,
                frameRate,
                iFrameInterval,
                path
        );
    }
}
