package us.pinguo.svideoDemo;

import android.annotation.TargetApi;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;
import us.pinguo.svideo.bean.VideoInfo;
import us.pinguo.svideo.interfaces.OnRecordListener;

/**
 * Created by Bhuvnesh on 08-03-2017.
 */
@TargetApi(16)
public class PreviewActivity2 extends AppCompatActivity {
    private VideoView videoView;
    private SeekBar seekBar;
    private int stopPosition;
    private static final String POSITION = "position";
    static final String FILEPATH = "filepath";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        videoView = (VideoView) findViewById(R.id.videoView);
        seekBar = (SeekBar) findViewById(R.id.seekBar);

        final TextView tvInstruction = (TextView) findViewById(R.id.tvInstruction);

        TextureRecordActivity.mRecorder.waitRecordSuccess(new OnRecordListener() {
            @Override
            public void onRecordSuccess(VideoInfo videoInfo) {
                videoView.setVideoURI(Uri.parse(videoInfo.getVideoPath()));
                videoView.start();
                String filePath = videoInfo.getVideoPath();

                String videoInfoStr = "";
                try {
                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    retriever.setDataSource(filePath);
                    int bitrate = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));
                    retriever.release();
                    int frameRate = videoInfo.getFrameRate();
                    int width = videoInfo.getVideoWidth();
                    int height = videoInfo.getVideoHeight();
                    int rotation = videoInfo.getVideoRotation();
                    long duration = videoInfo.getDuration();
                    videoInfoStr = String.format("size:%dX%d,framerate:%d,rotation:%d,bitrate:%d,duration:%.1fs", width, height, frameRate, rotation, bitrate,
                            duration / 1000f / 1000f);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                tvInstruction.setText("Video stored at path " + filePath + "\n" + videoInfoStr);
            }

            @Override
            public void onRecordStart() {

            }

            @Override
            public void onRecordFail(Throwable t) {

            }

            @Override
            public void onRecordStop() {

            }

            @Override
            public void onRecordPause() {

            }

            @Override
            public void onRecordResume() {

            }
        });

        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {

            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.setLooping(true);
                seekBar.setMax(videoView.getDuration());
                seekBar.postDelayed(onEverySecond, 1000);
            }
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {

                if (fromUser) {
                    // this is when actually seekbar has been seeked to a new position
                    videoView.seekTo(progress);
                }
            }
        });


    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPosition = videoView.getCurrentPosition(); //stopPosition is an int
        videoView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        videoView.seekTo(stopPosition);
        videoView.start();
    }

    private Runnable onEverySecond = new Runnable() {

        @Override
        public void run() {

            if (seekBar != null) {
                seekBar.setProgress(videoView.getCurrentPosition());
            }

            if (videoView.isPlaying()) {
                seekBar.postDelayed(onEverySecond, 1000);
            }

        }
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // handle arrow click here
        if (item.getItemId() == android.R.id.home) {
            finish(); // close this activity and return to preview activity (if there is any)
        }

        return super.onOptionsItemSelected(item);
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
}
