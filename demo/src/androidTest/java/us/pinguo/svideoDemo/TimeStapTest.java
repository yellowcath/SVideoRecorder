package us.pinguo.svideoDemo;

import android.annotation.TargetApi;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import us.pinguo.svideo.utils.RL;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by huangwei on 2016/6/16.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class TimeStapTest extends ApplicationTest {
    public void test() {
        MediaExtractor mediaExtractor = new MediaExtractor();
        try {
            mediaExtractor.setDataSource("/mnt/sdcard/DCIM/test.mp4");
        } catch (IOException e) {
            e.printStackTrace();
        }
        int videoTrackIndex = -1;
        for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
            //获取码流的详细格式/配置信息
            MediaFormat format = mediaExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                videoTrackIndex = i;
            }
        }
        mediaExtractor.selectTrack(videoTrackIndex);

        while (true) {
            long sampleTime = mediaExtractor.getSampleTime();  //读取一帧数据
            Log.i("hwLog", "time:" + sampleTime);
            boolean b = mediaExtractor.advance(); //移动到下一帧
            if (!b) {
                break;
            }
        }
        mediaExtractor.release(); //读取结束后，要记得释放资源
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void test2() {
        MediaExtractor mediaExtractor = new MediaExtractor();

        try {
            AssetFileDescriptor assetFileDescriptor = getContext().getAssets().openFd("test2.mp4");
            mediaExtractor.setDataSource(assetFileDescriptor.getFileDescriptor(), assetFileDescriptor.getStartOffset(), assetFileDescriptor.getLength());
        } catch (IOException e) {
            e.printStackTrace();
        }
        int videoTrackIndex = -1;
        int width = 0, height = 0;
        for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
            //获取码流的详细格式/配置信息
            MediaFormat format = mediaExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                videoTrackIndex = i;
            }
        }
        mediaExtractor.selectTrack(videoTrackIndex);
        MediaFormat format = mediaExtractor.getTrackFormat(videoTrackIndex);
        int videoMaxInputSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(videoMaxInputSize);
        width = format.getInteger(MediaFormat.KEY_WIDTH);
        height = format.getInteger(MediaFormat.KEY_HEIGHT);
        int frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE);

        MediaFormat newFormat = new MediaFormat();
        newFormat.setInteger(MediaFormat.KEY_HEIGHT, height);
        newFormat.setInteger(MediaFormat.KEY_WIDTH, width);
        newFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        newFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        //初始化MediaCodec
        MediaCodec mMediaCodec = null;
        String mime = format.getString(MediaFormat.KEY_MIME);
        try {
            mMediaCodec = MediaCodec.createDecoderByType(mime);
            mMediaCodec.configure(format, null, null,
                    0);
            mMediaCodec.start();
        } catch (IOException e) {
        }

        byte[] bytes = new byte[width * height * 3 / 2];
        while (true) {
            long sampleTime = mediaExtractor.getSampleTime();  //读取一帧数据
            int sampleSize = mediaExtractor.readSampleData(byteBuffer, 0);
            boolean b = mediaExtractor.advance(); //移动到下一帧
            if (!b) {
                break;
            }

            //输入原始数据
            int inputBufferId = mMediaCodec.dequeueInputBuffer(10000);
            Log.i("hwLog", "inputBufferId:" + inputBufferId);
            if (inputBufferId >= 0) {
                ByteBuffer inputBuffer = mMediaCodec.getInputBuffer(inputBufferId);
                inputBuffer.put(byteBuffer);
                mMediaCodec.queueInputBuffer(inputBufferId, 0,
                        sampleSize, sampleTime, 0);
            } else {
                // either all in use, or we timed out during initial setup
                RL.d("input buffer not available");
            }
            //拿取编码好的数据
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            do  {
                int outputBufferId = mMediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
                Log.i("hwLog","time:"+sampleTime/1000+" outputBufferId:"+outputBufferId);
                if(outputBufferId<0){
                    continue;
                }
                ByteBuffer outputBuffer = mMediaCodec.getOutputBuffer(outputBufferId);
                outputBuffer.get(bytes);
                Log.i("hwLog","byteToBitmap");
                Bitmap bitmap = byteToBitmap(bytes, width, height);
                String path = "/mnt/sdcard/frames/"+sampleTime/1000+".jpg";
                Log.i("hwLog","saveBitmap");
                saveBitmap(path,bitmap);
                Log.i("hwLog","保存图片:"+path);
//                Log.i("hwLog","time:"+sampleTime/1000+" outputBufferId:"+outputBufferId);
                mMediaCodec.releaseOutputBuffer(outputBufferId, false);
                break;
            } while(true);
        }
        mMediaCodec.release();
        mediaExtractor.release(); //读取结束后，要记得释放资源
    }

    private Bitmap byteToBitmap(byte[] bytes, int w, int h) {
        int[] colors = new int[w * h];
//        for(int i=0;i<colors.length;i++){
//            colors[i] = Color.argb(255,bytes[i]+128,bytes[i]+128,bytes[i]+128);
//        }
        decodeYUV420SP(colors, bytes, w, h);
        Bitmap bitmap = Bitmap.createBitmap(colors, w, h, Bitmap.Config.ARGB_8888);
        return bitmap;
    }
    /**
     * 对某个位图保存成文件
     *
     * @param bitmap 需要保存的位图
     * @return 保存文件的路径
     */
    public static String saveBitmap(String path,Bitmap bitmap) {
        boolean sdCardExist = Environment.getExternalStorageState()
                .equals(Environment.MEDIA_MOUNTED);
        if (null == bitmap || !sdCardExist) {
            return null;
        }
        File newFile = new File(path);
        FileOutputStream mFileOutputStream = null;
        try {
            newFile.createNewFile();
            mFileOutputStream = new FileOutputStream(newFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100,
                    mFileOutputStream);
            return newFile.getAbsolutePath();
        } catch (IOException e) {
        } finally {
            if (null != mFileOutputStream) {
                try {
                    mFileOutputStream.flush();
                    mFileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }
    static public void decodeYUV420SP(int[] rgb, byte[] yuv420sp, int width, int height) {
        final int frameSize = width * height;
        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & ((int) yuv420sp[yp])) - 16;
                if (y < 0) {
                    y = 0;
                }
                if ((i & 1) == 0) {
                    u = (0xff & yuv420sp[uvp++]) - 128;
                    v = (0xff & yuv420sp[uvp++]) - 128;
                }

                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                if (r < 0) {
                    r = 0;
                } else if (r > 262143) {
                    r = 262143;
                }
                if (g < 0) {
                    g = 0;
                } else if (g > 262143) {
                    g = 262143;
                }
                if (b < 0) {
                    b = 0;
                } else if (b > 262143) {
                    b = 262143;
                }

                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
    }
}
