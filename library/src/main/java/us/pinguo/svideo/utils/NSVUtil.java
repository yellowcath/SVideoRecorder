package us.pinguo.svideo.utils;

/**
 * Created by huangwei on 2018/8/15 0015.
 */
public class NSVUtil {
    static {
        System.loadLibrary("NSVUtil");
    }
    public static native void NV12ToNV21(byte[] data,int width,int height,int len);
    public static native void NV12To420P(byte[] data,int width,int height,int len);
}
