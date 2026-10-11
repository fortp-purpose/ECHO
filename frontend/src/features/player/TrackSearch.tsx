import React, { useState, useRef } from "react";

export interface TrackResult {
  id: string;
  title: string;
  artist: string;
  album: string;
  image: string;
  audioUrl: string;
}

interface TrackSearchProps {
  onSelect: (track: TrackResult) => void;
  currentTrackId?: string;
}

const TrackSearch: React.FC<TrackSearchProps> = ({ onSelect, currentTrackId }) => {
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
      const resp = await fetch(`/api/v1/catalog/jamendo/search?q=${encodeURIComponent(q)}&limit=12`);
      if (!resp.ok) throw new Error(`Search failed (${resp.status})`);
      const data = await resp.json();
      const tracks: TrackResult[] = data.tracks || [];
      setResults(tracks);
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
    <div className="track-search">
      <form onSubmit={handleSubmit} className="search-form">
        <input
          type="text"
          value={query}
          onChange={(e) => handleChange(e.target.value)}
          placeholder="Search tracks, artists..."
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
        <div className="search-results">
          {results.map((track) => (
            <button
              key={track.id}
              className={`track-card ${currentTrackId === track.id ? "active" : ""}`}
              onClick={() => onSelect(track)}
            >
              {track.image && (
                <img src={track.image} alt="" className="track-image" loading="lazy" />
              )}
              <div className="track-info">
                <span className="track-title">{track.title}</span>
                <span className="track-artist">{track.artist}</span>
                {track.album && <span className="track-album">{track.album}</span>}
              </div>
              <span className="track-play-icon">&#9654;</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
};

export default TrackSearch;
