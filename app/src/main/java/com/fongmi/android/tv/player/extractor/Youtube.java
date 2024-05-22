package com.fongmi.android.tv.player.extractor;

import android.util.Base64;

import com.fongmi.android.tv.player.Source;
import com.github.kiulian.downloader.YoutubeDownloader;
import com.github.kiulian.downloader.downloader.request.RequestVideoInfo;
import com.github.kiulian.downloader.model.videos.VideoInfo;
import com.github.kiulian.downloader.model.videos.formats.AudioFormat;
import com.github.kiulian.downloader.model.videos.formats.Format;
import com.github.kiulian.downloader.model.videos.formats.VideoFormat;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Youtube implements Source.Extractor {

    private static final Pattern PATTERN = Pattern.compile("(?<=watch\\?v=|/videos/|embed\\/|youtu.be\\/|\\/v\\/|\\/e\\/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed\\?video_id=|&v=|\\?v=)([\\w-]{11})");
    private static final String ADAPTATION_SET = "<AdaptationSet lang='chi'>\n" + "<ContentComponent contentType='%s'/>\n" + "<Representation id='%d' bandwidth='%d' codecs='%s' mimeType='%s' %s>\n" + "<BaseURL>%s</BaseURL>\n" + "<SegmentBase indexRange='%s'>\n" + "<Initialization range='%s'/>\n" + "</SegmentBase>\n" + "</Representation>\n" + "</AdaptationSet>";
    private static final String MPD = "<MPD xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xmlns='urn:mpeg:dash:schema:mpd:2011' xsi:schemaLocation='urn:mpeg:dash:schema:mpd:2011 DASH-MPD.xsd' type='static' mediaPresentationDuration='PT%sS' minBufferTime='PT1.500S' profiles='urn:mpeg:dash:profile:isoff-on-demand:2011'>\n" + "<Period duration='PT%sS' start='PT0S'>\n" + "%s\n" + "%s\n" + "</Period>\n" + "</MPD>";
    private final YoutubeDownloader downloader;

    @Override
    public boolean match(String scheme, String host) {
        return host.contains("youtube.com") || host.contains("youtu.be");
    }

    public Youtube() {
        downloader = new YoutubeDownloader();
    }

    @Override
    public String fetch(String url) throws Exception {
        Matcher matcher = PATTERN.matcher(url);
        if (!matcher.find()) return "";
        String videoId = matcher.group();
        RequestVideoInfo request = new RequestVideoInfo(videoId);
        VideoInfo info = downloader.getVideoInfo(request).data();
        return info.details().isLive() ? info.details().liveUrl() : getMpdWithBase64(info);
    }

    private String getMpdWithBase64(VideoInfo info) {
        StringBuilder video = new StringBuilder();
        StringBuilder audio = new StringBuilder();
        List<VideoFormat> videoFormats = info.videoFormats();
        List<AudioFormat> audioFormats = info.audioFormats();
        for (VideoFormat format : videoFormats) video.append(getAdaptationSet(format, getVideoParam(format)));
        for (AudioFormat format : audioFormats) audio.append(getAdaptationSet(format, getAudioParam(format)));
        String mpd = String.format(Locale.getDefault(), MPD, info.details().lengthSeconds(), info.details().lengthSeconds(), video, audio);
        return "data:application/dash+xml;base64," + Base64.encodeToString(mpd.getBytes(), 0);
    }

    private String getVideoParam(VideoFormat format) {
        return String.format(Locale.getDefault(), "height='%d' width='%d' frameRate='%d' maxPlayoutRate='1' startWithSAP='1'", format.height(), format.width(), format.fps());
    }

    private String getAudioParam(AudioFormat format) {
        return String.format(Locale.getDefault(), "subsegmentAlignment='true' audioSamplingRate='%d'", format.audioSampleRate());
    }

    private String getAdaptationSet(Format format, String param) {
        if (format.initRange() == null || format.indexRange() == null) return "";
        String mimeType = format.mimeType().split(";")[0];
        String contentType = format.mimeType().split("/")[0];
        int iTag = format.itag().id();
        int bitrate = format.bitrate();
        String url = format.url().replace("&", "&amp;");
        String codecs = format.mimeType().split("=")[1].replace("\"", "");
        String initRange = format.initRange().getStart() + "-" + format.initRange().getEnd();
        String indexRange = format.indexRange().getStart() + "-" + format.indexRange().getEnd();
        return String.format(Locale.getDefault(), ADAPTATION_SET, contentType, iTag, bitrate, codecs, mimeType, param, url, indexRange, initRange);
    }

    @Override
    public void stop() {
    }

    @Override
    public void exit() {
    }
}
