package echo.jamendo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class AudiusTrackDTO {
    @JsonProperty("track_id")
    public Integer trackId;

    @JsonProperty("id")
    public String id;

    @JsonProperty("title")
    public String title;

    @JsonProperty("genre")
    public String genre;

    @JsonProperty("user")
    public AudiusUserDTO user;

    @JsonProperty("artwork")
    public AudiusArtworkDTO artwork;

    @JsonProperty("stream")
    public AudiusStreamDTO stream;

    @JsonProperty("is_streamable")
    public Boolean isStreamable;

    public static class AudiusUserDTO {
        @JsonProperty("name")
        public String name;

        @JsonProperty("handle")
        public String handle;
    }

    public static class AudiusArtworkDTO {
        @JsonProperty("480x480")
        public String image480x480;

        @JsonProperty("150x150")
        public String image150x150;

        @JsonProperty("1000x1000")
        public String image1000x1000;
    }

    public static class AudiusStreamDTO {
        @JsonProperty("url")
        public String url;

        @JsonProperty("mirrors")
        public List<String> mirrors;
    }
}

class AudiusSearchResponse {
    @JsonProperty("data")
    public List<AudiusTrackDTO> data;
}