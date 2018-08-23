package us.pinguo.svideoDemo;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import pub.devrel.easypermissions.EasyPermissions;

/**
 * Created by huangwei on 2018/8/16 0016.
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.texture_record).setOnClickListener(this);
        findViewById(R.id.yuv_record).setOnClickListener(this);
        findViewById(R.id.yuv_record_subsection).setOnClickListener(this);

        requestPermission();
    }

    private void requestPermission(){
        String[] perms = {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,Manifest.permission.WRITE_EXTERNAL_STORAGE};
        if (EasyPermissions.hasPermissions(this, perms)) {
            // Already have permission, do the thing
            // ...
        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(this, "This Demo needs Camera & Audio & Write SDCard Permissions",
                    123, perms);
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }
    @Override
    public void onClick(View v) {
        Intent intent = new Intent();
        if (v.getId() == R.id.texture_record) {
            intent.setClass(this, TextureRecordActivity.class);
        } else if (v.getId() == R.id.yuv_record) {
            intent.setClass(this, YuvRecordActivity.class);
        } else if (v.getId() == R.id.yuv_record_subsection) {
            intent.setClass(this, SegYuvRecordActivity.class);
        }
        startActivity(intent);
    }
}
