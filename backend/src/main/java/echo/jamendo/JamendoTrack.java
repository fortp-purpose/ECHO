package echo.jamendo;

public record JamendoTrack(
        String id,
        String title,
        String artistName,
        String albumName,
        String image,
        String albumImage,
        String audioUrl
) {
}
