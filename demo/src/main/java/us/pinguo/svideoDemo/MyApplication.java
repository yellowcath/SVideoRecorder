package us.pinguo.svideoDemo;

import android.app.Application;
import android.content.Context;
import us.pinguo.svideo.utils.RL;

/**
 * Created by huangwei on 2016/5/16.
 */
public class MyApplication extends Application {
    private static Context sAppContext;
    public static Context getAppContext(){
        return sAppContext;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        RL.setLogEnable(true);
        sAppContext = getApplicationContext();
    }
}
