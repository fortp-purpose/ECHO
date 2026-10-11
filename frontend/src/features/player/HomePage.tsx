import React, { useState, useEffect, useCallback, useRef } from "react";
import TrackCard, { type TrackResult } from "./TrackCard";

interface TrackRowProps {
  title: string;
  tracks: TrackResult[];
  onSelect: (track: TrackResult) => void;
  onPlayNext?: (track: TrackResult) => void;
  onAddToQueue?: (track: TrackResult) => void;
  currentTrackId?: string;
  loading?: boolean;
}

const TrackRow: React.FC<TrackRowProps> = ({
  title,
  tracks,
  onSelect,
  onPlayNext,
  onAddToQueue,
  currentTrackId,
  loading,
}) => {
  const scrollRef = useRef<HTMLDivElement>(null);

  const scrollLeft = () => {
    scrollRef.current?.scrollBy({ left: -320, behavior: "smooth" });
  };

  const scrollRight = () => {
    scrollRef.current?.scrollBy({ left: 320, behavior: "smooth" });
  };

  if (loading) {
    return (
      <section className="track-row loading" aria-labelledby={`${title}-label`}>
        <div className="track-row-header">
          <h3 id={`${title}-label`} className="track-row-title">{title}</h3>
        </div>
        <div className="track-row-skeleton">
          {[...Array(5)].map((_, i) => (
            <div key={i} className="track-card-skeleton" />
          ))}
        </div>
      </section>
    );
  }

  if (tracks.length === 0) return null;

  return (
    <section className="track-row" aria-labelledby={`${title}-label`}>
      <div className="track-row-header">
        <h3 id={`${title}-label`} className="track-row-title">{title}</h3>
        <div className="track-row-nav">
          <button
            className="nav-btn"
            onClick={scrollLeft}
            aria-label="Scroll left"
            disabled={scrollRef.current?.scrollLeft === 0}
          >
            ‹
          </button>
          <button
            className="nav-btn"
            onClick={scrollRight}
            aria-label="Scroll right"
            disabled={
              scrollRef.current &&
              scrollRef.current.scrollLeft + scrollRef.current.clientWidth >=
                scrollRef.current.scrollWidth - 10
            }
          >
            ›
          </button>
        </div>
      </div>
      <div className="track-row-list" ref={scrollRef}>
        {tracks.map((track) => (
          <TrackCard
            key={track.id}
            track={track}
            isActive={currentTrackId === track.id}
            onPlay={onSelect}
            onPlayNext={onPlayNext}
            onAddToQueue={onAddToQueue}
          />
        ))}
      </div>
    </section>
  );
};

const GENRES = [
  { query: "lofi", title: "Lofi Beats" },
  { query: "chill", title: "Chill Vibes" },
  { query: "electronic", title: "Electronic" },
  { query: "hip hop", title: "Hip Hop" },
  { query: "ambient", title: "Ambient" },
  { query: "jazz", title: "Jazz" },
  { query: "indie", title: "Indie" },
];

const HomePage: React.FC<{
  onSelect: (track: TrackResult) => void;
  onPlayNext?: (track: TrackResult) => void;
  onAddToQueue?: (track: TrackResult) => void;
  currentTrackId?: string;
}> = ({ onSelect, onPlayNext, onAddToQueue, currentTrackId }) => {
  const [genreTracks, setGenreTracks] = useState<Record<string, TrackResult[]>>({});
  const [loadingGenres, setLoadingGenres] = useState<Record<string, boolean>>({});
  const [error, setError] = useState<string | null>(null);

  const fetchGenre = useCallback(async (query: string, title: string) => {
    setLoadingGenres((prev) => ({ ...prev, [title]: true }));
    try {
      const [audiusResp, jiosaavnResp] = await Promise.allSettled([
        fetch(`/api/v1/catalog/jamendo/search?q=${encodeURIComponent(query)}&limit=10`),
        fetch(`/api/v1/catalog/jiosaavn/search?q=${encodeURIComponent(query)}&limit=10`),
      ]);

      let allTracks: TrackResult[] = [];

      if (audiusResp.status === "fulfilled") {
        const resp = audiusResp.value;
        if (resp.ok) {
          const data = await resp.json();
          const tracks: TrackResult[] = (data.tracks || []).map(t => ({ ...t, provider: "audius" }));
          allTracks = allTracks.concat(tracks);
        }
      }

      if (jiosaavnResp.status === "fulfilled") {
        const resp = jiosaavnResp.value;
        if (resp.ok) {
          const data = await resp.json();
          const tracks: TrackResult[] = (data.tracks || []).map(t => ({ ...t, provider: "jiosaavn" }));
          allTracks = allTracks.concat(tracks);
        }
      }

      // Deduplicate by title + artist
      const seen = new Set<string>();
      const deduped = allTracks.filter(track => {
        const key = `${track.title.toLowerCase()}|${track.artist.toLowerCase()}`;
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
      });

      setGenreTracks((prev) => ({ ...prev, [title]: deduped }));
    } catch (e) {
      console.error(`Failed to load ${title}:`, e);
    } finally {
      setLoadingGenres((prev) => ({ ...prev, [title]: false }));
    }
  }, []);

  useEffect(() => {
    GENRES.forEach(({ query, title }) => {
      fetchGenre(query, title);
    });
  }, [fetchGenre]);

  return (
    <div className="home-page">
      <section className="hero-section" aria-labelledby="hero-title">
        <div className="hero-content">
          <p className="eyebrow">WELCOME TO ECHO</p>
          <h1 id="hero-title">Music for every <span>moment.</span></h1>
          <p className="hero-copy">
            Discover your next favorite track. Explore by genre, mood, or vibe.
          </p>
        </div>
        <div className="hero-visual" aria-hidden="true">
          <div className="hero-orb orb-1" />
          <div className="hero-orb orb-2" />
          <div className="hero-orb orb-3" />
        </div>
      </section>

      {error && (
        <div className="home-error" role="alert">
          {error}
          <button onClick={() => window.location.reload()}>Retry</button>
        </div>
      )}

      <main className="home-main">
        {GENRES.map(({ title }) => (
          <TrackRow
            key={title}
            title={title}
            tracks={genreTracks[title] || []}
            onSelect={onSelect}
            onPlayNext={onPlayNext}
            onAddToQueue={onAddToQueue}
            currentTrackId={currentTrackId}
            loading={loadingGenres[title]}
          />
        ))}
      </main>
    </div>
  );
};

export default HomePage;