import React, { useState, useCallback, useRef } from "react";
import HealthCheck from "./components/HealthCheck";
import PlayerSection from "./features/player/PlayerSection";
import HomePage from "./features/player/HomePage";
import BottomPlayer from "./features/player/BottomPlayer";
import type { TrackResult } from "./features/player/TrackCard";

const App: React.FC = () => {
  const [currentTrack, setCurrentTrack] = useState<TrackResult | null>(null);
  const [queue, setQueue] = useState<TrackResult[]>([]);
  const [queueIndex, setQueueIndex] = useState(-1);

  const handleSelect = useCallback((track: TrackResult) => {
    setQueue((prev) => {
      const idx = prev.findIndex((t) => t.id === track.id);
      if (idx >= 0) {
        setQueueIndex(idx);
        return prev;
      }
      setQueueIndex(prev.length);
      return [...prev, track];
    });
    setCurrentTrack(track);
  }, []);

  const handlePlayNext = useCallback((track: TrackResult) => {
    setQueue((prev) => {
      const idx = prev.findIndex((t) => t.id === track.id);
      if (idx >= 0) {
        if (idx === queueIndex + 1) return prev;
        const nextQueue = [...prev];
        nextQueue.splice(idx, 1);
        nextQueue.splice(queueIndex + 1, 0, track);
        return nextQueue;
      }
      const nextQueue = [...prev];
      nextQueue.splice(queueIndex + 1, 0, track);
      return nextQueue;
    });
  }, [queueIndex]);

  const handleAddToQueue = useCallback((track: TrackResult) => {
    setQueue((prev) => {
      if (prev.some((t) => t.id === track.id)) return prev;
      return [...prev, track];
    });
  }, []);

  const handleNext = useCallback(() => {
    if (queueIndex < queue.length - 1) {
      const next = queueIndex + 1;
      setQueueIndex(next);
      setCurrentTrack(queue[next]);
    }
  }, [queueIndex, queue]);

  const handlePrev = useCallback(() => {
    if (queueIndex > 0) {
      const prev = queueIndex - 1;
      setQueueIndex(prev);
      setCurrentTrack(queue[prev]);
    }
  }, [queueIndex, queue]);

  const hasNext = queueIndex < queue.length - 1;
  const hasPrev = queueIndex > 0;

  return (
    <div className="app-root">
      <header className="topbar">
        <a className="brand" href="/" aria-label="ECHO home">
          <span className="brand-mark">E</span>
          <span>ECHO</span>
        </a>

        <div className="topbar-search">
          <GlobalSearch onSelect={handleSelect} currentTrackId={currentTrack?.id} />
        </div>

        <span className="topbar-label">YOUR MUSIC SPACE</span>
      </header>

      <main className="app-main">
        <HomePage
          onSelect={handleSelect}
          onPlayNext={handlePlayNext}
          onAddToQueue={handleAddToQueue}
          currentTrackId={currentTrack?.id}
        />

        <PlayerSection
          currentTrack={currentTrack}
          onSelect={handleSelect}
          onPlayNext={handlePlayNext}
          onAddToQueue={handleAddToQueue}
        />
      </main>

      <section className="status-section" aria-labelledby="status-title">
        <div className="section-heading">
          <div>
            <p className="eyebrow">PLATFORM STATUS</p>
            <h2 id="status-title">Connected to ECHO.</h2>
          </div>
          <span className="featured-badge">LIVE CHECK</span>
        </div>

        <HealthCheck />
      </section>

      <footer className="app-footer">
        <span>ECHO</span>
        <span>Engaging Collaborative Hub for Online Music</span>
      </footer>

      <BottomPlayer
        track={currentTrack}
        onNext={hasNext ? handleNext : undefined}
        onPrev={hasPrev ? handlePrev : undefined}
        hasNext={hasNext}
        hasPrev={hasPrev}
      />
    </div>
  );
};

const GlobalSearch: React.FC<{ onSelect: (track: TrackResult) => void; currentTrackId?: string }> = ({
  onSelect,
  currentTrackId,
}) => {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<TrackResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [searched, setSearched] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const doSearch = async (q: string) => {
    if (!q.trim()) {
      setResults([]);
      setSearched(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [audiusResp, jiosaavnResp] = await Promise.allSettled([
        fetch(`/api/v1/catalog/jamendo/search?q=${encodeURIComponent(q)}&limit=12`),
        fetch(`/api/v1/catalog/jiosaavn/search?q=${encodeURIComponent(q)}&limit=12`),
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

      // Deduplicate by title + artist (case-insensitive)
      const seen = new Set<string>();
      const deduped = allTracks.filter(track => {
        const key = `${track.title.toLowerCase()}|${track.artist.toLowerCase()}`;
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
      });

      setResults(deduped);
      setSearched(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Search failed");
      setResults([]);
      setSearched(true);
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (value: string) => {
    setQuery(value);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => doSearch(value), 400);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (debounceRef.current) clearTimeout(debounceRef.current);
    doSearch(query);
  };

  return (
    <div className="global-search">
      <form onSubmit={handleSubmit} className="search-form">
        <label htmlFor="global-search" className="sr-only">Search music</label>
        <input
          id="global-search"
          type="text"
          value={query}
          onChange={(e) => handleChange(e.target.value)}
          placeholder="Search tracks, artists, albums..."
          className="search-input"
          aria-label="Search music"
        />
        <button type="submit" className="search-button" disabled={loading}>
          {loading ? "..." : "Search"}
        </button>
      </form>

      {error && <div className="search-error">{error}</div>}

      {searched && !loading && !error && results.length === 0 && (
        <div className="search-empty">No tracks found</div>
      )}

      {results.length > 0 && (
        <div className="search-results-dropdown" role="listbox">
          {results.map((track) => (
            <button
              key={`${track.provider}-${track.id}`}
              className={`track-card ${currentTrackId === track.id ? "active" : ""}`}
              onClick={() => onSelect(track)}
              role="option"
              aria-selected={currentTrackId === track.id}
            >
              {track.image && (
                <img src={track.image} alt="" className="track-image" loading="lazy" />
              )}
              <div className="track-info">
                <span className="track-title">{track.title}</span>
                <span className="track-artist">{track.artist}</span>
              </div>
              <span className="track-play-icon">▶</span>
              <span className="track-provider-badge">{track.provider === "jiosaavn" ? "JioSaavn" : "Audius"}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
};

export default App;