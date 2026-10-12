package echo.jamendo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class JamendoClient {

    private static final Logger log = LoggerFactory.getLogger(JamendoClient.class);

    private static final String BASE_URL = "https://discoveryprovider.audius.co/v1";
    private static final String APP_NAME = "ECHO_MVP";

    private final RestClient http;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public JamendoClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(8));

        this.http = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .build();
    }

    public List<JamendoTrack> search(String query, int limit) {
        // Audius nodes are occasionally slow or return a transient error. Retry
        // once before giving up so a single flaky response does not empty the UI.
        List<JamendoTrack> tracks = searchOnce(query, limit);
        if (tracks.isEmpty()) {
            tracks = searchOnce(query, limit);
        }
        return tracks;
    }

    private List<JamendoTrack> searchOnce(String query, int limit) {
        try {
            String uri = UriComponentsBuilder.fromPath("/tracks/search")
                    .queryParam("query", query)
                    .queryParam("app_name", APP_NAME)
                    .queryParam("limit", Math.min(Math.max(limit, 1), 20))
                    .build()
                    .toUriString();

            String body = http.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                return List.of();
            }

            return parseTracks(body);
        } catch (Exception e) {
            log.warn("Audius search failed for query '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    private List<JamendoTrack> parseTracks(String json) {
        List<JamendoTrack> tracks = new ArrayList<>();

        try {
            AudiusSearchResponse response = objectMapper.readValue(json, AudiusSearchResponse.class);
            if (response.data == null) {
                return List.of();
            }

            for (AudiusTrackDTO track : response.data) {
                String streamUrl = extractStreamUrl(track);
                if (streamUrl == null || streamUrl.isBlank()) {
                    continue;
                }

                String artwork = track.artwork != null ? track.artwork.image480x480 : null;
                String artistName = track.user != null ? track.user.name : null;

                tracks.add(new JamendoTrack(
                        track.id,
                        track.title,
                        artistName,
                        null,
                        artwork,
                        artwork,
                        streamUrl
                ));
            }
        } catch (Exception e) {
            log.warn("Failed to parse Audius response: {}", e.getMessage());
        }

        return tracks;
    }

    /**
     * The search payload carries a resolved {@code stream.url} for streamable
     * tracks, but it can be omitted. Fall back to the canonical stream endpoint
     * so a streamable track is not dropped just because the field is absent.
     */
    private String extractStreamUrl(AudiusTrackDTO track) {
        if (track.stream != null && track.stream.url != null && !track.stream.url.isBlank()) {
            return track.stream.url;
        }
        if (Boolean.TRUE.equals(track.isStreamable) && track.id != null && !track.id.isBlank()) {
            return BASE_URL + "/tracks/" + track.id + "/stream?app_name=" + APP_NAME;
        }
        return null;
    }
}
