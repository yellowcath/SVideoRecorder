package us.pinguo.svideo.utils;

import android.content.Context;
import us.pinguo.svideo.interfaces.IVideoPathGenerator;

import java.io.File;
import java.text.SimpleDateFormat;

/**
 * Created by huangwei on 2016/4/23.
 */
public class SegVideoNameGenerator implements IVideoPathGenerator {
    private Context mContext;
    public SegVideoNameGenerator(Context context) {
        mContext = context.getApplicationContext();
    }

    @Override
    public String generate() {
        File dir = new File(mContext.getCacheDir(), "SegVideos");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String name = "VID_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(System.currentTimeMillis()) + ".mp4";
        return new File(dir, name).getAbsolutePath();
    }
}
