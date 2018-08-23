package us.pinguo.svideoDemo;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.opengl.GLES20;
import android.os.Build;
import android.view.Surface;
import us.pinguo.svideo.utils.gles.EglRecordEnv;

import java.io.IOException;

/**
 * Created by huangwei on 2016/5/16.
 */
public class SurfaceTest extends ApplicationTest {
    public static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video
    private MediaCodec mMediaCodec = null;
    private int mWidth = 480;
    private int mHeight = 640;
    private Surface mInputSurface;

    public void test() {
        initInThread();
        {
            EglRecordEnv eglRecordEnv = new EglRecordEnv(mInputSurface, null, false);
            eglRecordEnv.makeCurrent();
            GLES20.glClearColor(1f, 0f, 0f, 1f);
            eglRecordEnv.swapBuffers();
            eglRecordEnv.release(true);
        }
        {
            mMediaCodec.stop();
            Surface surface = mMediaCodec.createInputSurface();
            mMediaCodec.start();
            EglRecordEnv eglRecordEnv = new EglRecordEnv(surface, null, false);
            eglRecordEnv.makeCurrent();
            GLES20.glClearColor(1f, 0f, 0f, 1f);
            eglRecordEnv.swapBuffers();
            eglRecordEnv.release(true);
        }
        System.out.println("asdasd");
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void initInThread() {
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE,
                this.mWidth, this.mHeight);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1000000);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 24);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,
                5);

        try {
            mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
            mMediaCodec.configure(mediaFormat, null, null,
                    MediaCodec.CONFIGURE_FLAG_ENCODE);
            mInputSurface = mMediaCodec.createInputSurface();
            mMediaCodec.start();
        } catch (IOException e) {
        } finally {
        }
    }
}
