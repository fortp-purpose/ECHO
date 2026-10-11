package echo.jamendo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Component
public class JamendoClient {

    private static final Logger log = LoggerFactory.getLogger(JamendoClient.class);

    private final RestClient http = RestClient.create("https://discoveryprovider.audius.co/v1");
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public List<JamendoTrack> search(String query, int limit) {
        try {
            String uri = UriComponentsBuilder.fromPath("/tracks/search")
                    .queryParam("query", query)
                    .queryParam("app_name", "ECHO_MVP")
                    .queryParam("limit", Math.min(Math.max(limit, 1), 20))
                    .build()
                    .toUriString();

            log.debug("Audius search request: {}", uri);
            String body = http.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            log.debug("Audius search response length: {}", body != null ? body.length() : 0);
            log.debug("Audius search response preview: {}", body != null ? body.substring(0, Math.min(500, body.length())) : "null");

            if (body == null || body.isBlank()) {
                return List.of();
            }

            return parseTracks(body);
        } catch (Exception e) {
            log.warn("Audius search failed for query '{}': {}", query, e.getMessage(), e);
            return List.of();
        }
    }

    private List<JamendoTrack> parseTracks(String json) {
        List<JamendoTrack> tracks = new ArrayList<>();

        try {
            AudiusSearchResponse response = objectMapper.readValue(json, AudiusSearchResponse.class);
            log.debug("Parsed Audius response, data size: {}", response.data != null ? response.data.size() : 0);
            if (response.data == null) {
                return List.of();
            }

            for (AudiusTrackDTO track : response.data) {
                if (!Boolean.TRUE.equals(track.isStreamable)) {
                    continue;
                }

                String streamUrl = track.stream != null ? track.stream.url : null;
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
            log.warn("Failed to parse Audius response: {}", e.getMessage(), e);
        }

        return tracks;
    }
}