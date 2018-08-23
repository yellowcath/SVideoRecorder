package us.pinguo.svideo.bean;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import us.pinguo.svideo.utils.RL;
import us.pinguo.svideo.utils.SVideoUtil;

public class VideoInfo implements Parcelable {
    //视频原始路径
    private String videoPath;
    //视频的宽
    private int videoWidth;
    //视频的高
    private int videoHeight;
    /**
     * 视频帧率
     */
    private int frameRate;
    /**
     * 视频时长，单位ms
     */
    private long duration;

    private int videoRotation;

    public String getVideoPath() {
        return videoPath;
    }

    public void setVideoPath(String videoPath) {
        this.videoPath = videoPath;
    }

    public int getVideoWidth() {
        return videoWidth;
    }

    public void setVideoWidth(int videoWidth) {
        this.videoWidth = videoWidth;
    }

    public int getVideoHeight() {
        return videoHeight;
    }

    public void setVideoHeight(int videoHeight) {
        this.videoHeight = videoHeight;
    }

    public VideoInfo() {
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.videoPath);
        dest.writeInt(this.videoWidth);
        dest.writeInt(this.videoHeight);
        dest.writeLong(duration);
        dest.writeFloat(frameRate);
        dest.writeInt(videoRotation);
    }

    protected VideoInfo(Parcel in) {
        this.videoPath = in.readString();
        this.videoWidth = in.readInt();
        this.videoHeight = in.readInt();
        duration = in.readLong();
        frameRate = in.readInt();
        videoRotation = in.readInt();
    }

    public static final Creator<VideoInfo> CREATOR = new Creator<VideoInfo>() {
        public VideoInfo createFromParcel(Parcel source) {
            return new VideoInfo(source);
        }

        public VideoInfo[] newArray(int size) {
            return new VideoInfo[size];
        }
    };


    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public int getFrameRate() {
        return frameRate;
    }

    public void setFrameRate(int frameRate) {
        this.frameRate = frameRate;
    }

    public int getVideoRotation() {
        return videoRotation;
    }

    public void setVideoRotation(int videoRotation) {
        this.videoRotation = videoRotation;
    }

    @Override
    public VideoInfo clone() {
        VideoInfo videoInfo = new VideoInfo();
        videoInfo.videoRotation = videoRotation;
        videoInfo.frameRate = frameRate;
        videoInfo.duration = duration;
        videoInfo.videoHeight = videoHeight;
        videoInfo.videoWidth = videoWidth;
        videoInfo.videoPath = videoPath;
        return videoInfo;
    }

    public static void fillVideoInfo(String videoPath, VideoInfo videoInfo) {
        if (TextUtils.isEmpty(videoPath) || videoInfo == null) {
            return;
        }
        videoInfo.setVideoPath(videoPath);
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(videoPath);
            int selectTrack = SVideoUtil.selectTrack(extractor, false);
            MediaFormat trackFormat = extractor.getTrackFormat(selectTrack);
            videoInfo.setFrameRate(trackFormat.containsKey(MediaFormat.KEY_FRAME_RATE) ?
                    trackFormat.getInteger(MediaFormat.KEY_FRAME_RATE) : 0);
            videoInfo.setDuration(trackFormat.containsKey(MediaFormat.KEY_DURATION) ?
                    trackFormat.getLong(MediaFormat.KEY_DURATION) / 1000 : 0);
        } catch (Exception e) {
            RL.e(e);
        } finally {
            extractor.release();
        }
    }

    @Override
    public String toString() {
        return "VideoInfo{" +
                "videoPath='" + videoPath + '\'' +
                ", videoWidth=" + videoWidth +
                ", videoHeight=" + videoHeight +
                ", frameRate=" + frameRate +
                ", duration=" + duration +
                ", videoRotation=" + videoRotation +
                '}';
    }
}
