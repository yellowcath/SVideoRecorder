package us.pinguo.svideo.utils;

import android.os.Environment;
import us.pinguo.svideo.interfaces.IVideoPathGenerator;

import java.io.File;
import java.text.SimpleDateFormat;

/**
 * Created by huangwei on 2016/4/23.
 */
public class DateVideoNameGenerator implements IVideoPathGenerator {
    @Override
    public String generate() {
        File DCIM = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        File dir = new File(DCIM, "Camera");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String name = "VID_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(System.currentTimeMillis())+".mp4";
        return new File(dir, name).getAbsolutePath();
    }
}
