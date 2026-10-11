package echo.jiosaavn;

public record JioSaavnTrack(
        String id,
        String title,
        String artistName,
        String albumName,
        String image,
        String albumImage,
        String audioUrl
) {
}