package echo.catalog.api;

import echo.catalog.application.AlbumView;
import echo.catalog.application.ArtistView;
import echo.catalog.application.CatalogProviderUnavailableException;
import echo.catalog.application.CatalogResourceNotFoundException;
import echo.catalog.application.CatalogService;
import echo.catalog.application.TrackView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CatalogController.class)
class CatalogControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CatalogService catalogService;

    @Test
    void returnsTrackView() throws Exception {
        UUID trackId = UUID.randomUUID();
        UUID albumId = UUID.randomUUID();
        when(catalogService.getTrack(trackId))
                .thenReturn(new TrackView(trackId, "Song", albumId, 180_000, true));

        mockMvc.perform(get("/api/v1/catalog/tracks/{id}", trackId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(trackId.toString()))
                .andExpect(jsonPath("$.title").value("Song"))
                .andExpect(jsonPath("$.albumId").value(albumId.toString()))
                .andExpect(jsonPath("$.durationMs").value(180_000))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void returnsArtistView() throws Exception {
        UUID artistId = UUID.randomUUID();
        when(catalogService.getArtist(artistId))
                .thenReturn(new ArtistView(artistId, "Artist"));

        mockMvc.perform(get("/api/v1/catalog/artists/{id}", artistId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(artistId.toString()))
                .andExpect(jsonPath("$.name").value("Artist"));
    }

    @Test
    void returnsAlbumView() throws Exception {
        UUID albumId = UUID.randomUUID();
        when(catalogService.getAlbum(albumId))
                .thenReturn(new AlbumView(albumId, "Album", null, LocalDate.of(2020, 1, 1)));

        mockMvc.perform(get("/api/v1/catalog/albums/{id}", albumId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(albumId.toString()))
                .andExpect(jsonPath("$.title").value("Album"))
                .andExpect(jsonPath("$.releaseDate").value("2020-01-01"));
    }

    @Test
    void returnsAlbumTracksAsArray() throws Exception {
        UUID albumId = UUID.randomUUID();
        when(catalogService.getAlbumTracks(albumId)).thenReturn(List.of(
                new TrackView(UUID.randomUUID(), "Apple", albumId, null, true),
                new TrackView(UUID.randomUUID(), "Zebra", albumId, null, true)));

        mockMvc.perform(get("/api/v1/catalog/albums/{id}/tracks", albumId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Apple"))
                .andExpect(jsonPath("$[1].title").value("Zebra"));
    }

    @Test
    void unknownTrackReturnsNotFoundEnvelope() throws Exception {
        UUID trackId = UUID.randomUUID();
        when(catalogService.getTrack(any()))
                .thenThrow(new CatalogResourceNotFoundException("Track", trackId));

        mockMvc.perform(get("/api/v1/catalog/tracks/{id}", trackId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void noActiveProviderReturnsCatalogUnavailableEnvelope() throws Exception {
        UUID trackId = UUID.randomUUID();
        when(catalogService.getTrack(any()))
                .thenThrow(new CatalogProviderUnavailableException());

        mockMvc.perform(get("/api/v1/catalog/tracks/{id}", trackId))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATALOG_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message")
                        .value("Music catalogue is currently unavailable."))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void malformedTrackIdReturnsValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/tracks/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void malformedAlbumIdReturnsValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/albums/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void artistReturnsUnavailableEnvelopeWhenNoProvider() throws Exception {
        UUID artistId = UUID.randomUUID();
        when(catalogService.getArtist(any()))
                .thenThrow(new CatalogProviderUnavailableException());

        mockMvc.perform(get("/api/v1/catalog/artists/{id}", artistId))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATALOG_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void albumTracksReturnUnavailableEnvelopeWhenNoProvider() throws Exception {
        UUID albumId = UUID.randomUUID();
        when(catalogService.getAlbumTracks(any()))
                .thenThrow(new CatalogProviderUnavailableException());

        mockMvc.perform(get("/api/v1/catalog/albums/{id}/tracks", albumId))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATALOG_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.status").value(503));
    }
}
