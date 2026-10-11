package echo.jiosaavn;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Component
public class JioSaavnClient {

    private static final Logger log = LoggerFactory.getLogger(JioSaavnClient.class);

    private final RestClient http = RestClient.create("https://saavn.sumit.co/api");
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public List<JioSaavnTrack> search(String query, int limit) {
        try {
            String uri = UriComponentsBuilder.fromPath("/search/songs")
                    .queryParam("query", query)
                    .queryParam("limit", Math.min(Math.max(limit, 1), 50))
                    .build()
                    .toUriString();

            log.debug("JioSaavn search request: {}", uri);
            String body = http.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            log.debug("JioSaavn search response length: {}", body != null ? body.length() : 0);
            log.debug("JioSaavn search response preview: {}", body != null ? body.substring(0, Math.min(500, body.length())) : "null");

            if (body == null || body.isBlank()) {
                return List.of();
            }

            return parseTracks(body);
        } catch (Exception e) {
            log.warn("JioSaavn search failed for query '{}': {}", query, e.getMessage(), e);
            return List.of();
        }
    }

    private List<JioSaavnTrack> parseTracks(String json) {
        List<JioSaavnTrack> tracks = new ArrayList<>();

        try {
            JioSaavnSearchResponse response = objectMapper.readValue(json, JioSaavnSearchResponse.class);
            if (response.data == null || response.data.results == null) {
                log.debug("Parsed JioSaavn response, results: null or empty");
                return List.of();
            }

            log.debug("Parsed JioSaavn response, results size: {}", response.data.results.size());

            for (JioSaavnTrackDTO track : response.data.results) {
                if (!"song".equals(track.type)) {
                    continue;
                }

                String streamUrl = extractBestAudioUrl(track);
                if (streamUrl == null || streamUrl.isBlank()) {
                    continue;
                }

                String artwork = extractArtwork(track);
                String artistName = extractArtistName(track);
                String albumName = track.album != null ? track.album.name : null;

                tracks.add(new JioSaavnTrack(
                        track.id,
                        track.name,
                        artistName,
                        albumName,
                        artwork,
                        artwork,
                        streamUrl
                ));
            }
        } catch (Exception e) {
            log.warn("Failed to parse JioSaavn response: {}", e.getMessage(), e);
        }

        return tracks;
    }

    private String extractBestAudioUrl(JioSaavnTrackDTO track) {
        if (track.downloadUrl == null || track.downloadUrl.isEmpty()) {
            return null;
        }

        // Prefer 320kbps, then 160kbps, then 96kbps, then 48kbps, then 12kbps
        String[] qualityOrder = {"320kbps", "160kbps", "96kbps", "48kbps", "12kbps"};
        
        for (String quality : qualityOrder) {
            for (JioSaavnTrackDTO.JioSaavnDownloadUrlDTO url : track.downloadUrl) {
                if (quality.equals(url.quality) && url.url != null && !url.url.isBlank()) {
                    return url.url;
                }
            }
        }

        // Fallback to first available
        return track.downloadUrl.get(0).url;
    }

    private String extractArtwork(JioSaavnTrackDTO track) {
        if (track.image == null || track.image.isEmpty()) {
            return null;
        }

        // Prefer 500x500, then 150x150, then 50x50
        String[] qualityOrder = {"500x500", "150x150", "50x50"};
        
        for (String quality : qualityOrder) {
            for (JioSaavnTrackDTO.JioSaavnImageDTO img : track.image) {
                if (quality.equals(img.quality) && img.url != null && !img.url.isBlank()) {
                    return img.url;
                }
            }
        }

        return track.image.get(0).url;
    }

    private String extractArtistName(JioSaavnTrackDTO track) {
        if (track.artists != null && track.artists.primary != null && !track.artists.primary.isEmpty()) {
            return track.artists.primary.get(0).name;
        }
        if (track.artists != null && track.artists.all != null && !track.artists.all.isEmpty()) {
            return track.artists.all.get(0).name;
        }
        return null;
    }
}