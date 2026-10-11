package echo.jamendo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/catalog/jamendo")
public class JamendoController {

    private final JamendoClient client;

    public JamendoController(JamendoClient client) {
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

        List<JamendoTrack> tracks = client.search(query.trim(), limit);

        List<Map<String, Object>> results = tracks.stream()
                .map(t -> Map.<String, Object>of(
                        "id", t.id(),
                        "title", t.title(),
                        "artist", t.artistName() != null ? t.artistName() : "Unknown",
                        "album", t.albumName() != null ? t.albumName() : "",
                        "image", t.image() != null ? t.image() : t.albumImage(),
                        "audioUrl", t.audioUrl()
                ))
                .toList();

        return Map.of("tracks", results);
    }
}
