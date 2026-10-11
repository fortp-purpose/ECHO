package echo.jiosaavn;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/catalog/jiosaavn")
public class JioSaavnController {

    private final JioSaavnClient client;

    public JioSaavnController(JioSaavnClient client) {
        this.client = client;
    }

    @GetMapping("/search")
    public Map<String, Object> search(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        if (query == null || query.trim().isEmpty()) {
            return Map.of("tracks", List.of());
        }

        List<JioSaavnTrack> tracks = client.search(query.trim(), limit);

        List<Map<String, Object>> results = tracks.stream()
                .map(t -> Map.<String, Object>of(
                        "id", t.id(),
                        "title", t.title(),
                        "artist", t.artistName() != null ? t.artistName() : "Unknown",
                        "album", t.albumName() != null ? t.albumName() : "",
                        "image", t.image() != null ? t.image() : t.albumImage(),
                        "audioUrl", t.audioUrl(),
                        "provider", "jiosaavn"
                ))
                .toList();

        return Map.of("tracks", results);
    }
}