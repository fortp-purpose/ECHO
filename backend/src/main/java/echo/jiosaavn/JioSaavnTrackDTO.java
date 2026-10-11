package echo.jiosaavn;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class JioSaavnTrackDTO {
    @JsonProperty("id")
    public String id;

    @JsonProperty("name")
    public String name;

    @JsonProperty("type")
    public String type;

    @JsonProperty("year")
    public String year;

    @JsonProperty("duration")
    public Integer duration;

    @JsonProperty("label")
    public String label;

    @JsonProperty("explicitContent")
    public Boolean explicitContent;

    @JsonProperty("playCount")
    public Integer playCount;

    @JsonProperty("language")
    public String language;

    @JsonProperty("url")
    public String url;

    @JsonProperty("copyright")
    public String copyright;

    @JsonProperty("album")
    public JioSaavnAlbumDTO album;

    @JsonProperty("artists")
    public JioSaavnArtistsDTO artists;

    @JsonProperty("image")
    public List<JioSaavnImageDTO> image;

    @JsonProperty("downloadUrl")
    public List<JioSaavnDownloadUrlDTO> downloadUrl;

    public static class JioSaavnAlbumDTO {
        @JsonProperty("id")
        public String id;

        @JsonProperty("name")
        public String name;

        @JsonProperty("url")
        public String url;
    }

    public static class JioSaavnArtistsDTO {
        @JsonProperty("primary")
        public List<JioSaavnArtistDTO> primary;

        @JsonProperty("featured")
        public List<JioSaavnArtistDTO> featured;

        @JsonProperty("all")
        public List<JioSaavnArtistDTO> all;
    }

    public static class JioSaavnArtistDTO {
        @JsonProperty("id")
        public String id;

        @JsonProperty("name")
        public String name;

        @JsonProperty("role")
        public String role;

        @JsonProperty("image")
        public List<JioSaavnImageDTO> image;

        @JsonProperty("type")
        public String type;

        @JsonProperty("url")
        public String url;
    }

    public static class JioSaavnImageDTO {
        @JsonProperty("quality")
        public String quality;

        @JsonProperty("url")
        public String url;
    }

    public static class JioSaavnDownloadUrlDTO {
        @JsonProperty("quality")
        public String quality;

        @JsonProperty("url")
        public String url;
    }
}

class JioSaavnSearchResponse {
    @JsonProperty("data")
    public JioSaavnDataDTO data;

    public static class JioSaavnDataDTO {
        @JsonProperty("results")
        public List<JioSaavnTrackDTO> results;
    }
}