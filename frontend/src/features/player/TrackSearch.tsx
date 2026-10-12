import React, { useState, useRef } from "react";
import TrackCard, { type TrackResult } from "./TrackCard";
import { apiUrl } from "../../api/client";

export { type TrackResult } from "./TrackCard";

interface TrackSearchProps {
  onSelect: (track: TrackResult) => void;
  onPlayNext?: (track: TrackResult) => void;
  onAddToQueue?: (track: TrackResult) => void;
  currentTrackId?: string;
}

const TrackSearch: React.FC<TrackSearchProps> = ({
  onSelect,
  onPlayNext,
  onAddToQueue,
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
      // apiUrl() carries VITE_API_BASE in production (Vercel); the query string
      // stays outside the literal so the contract gate still resolves the path.
      const params = `?q=${encodeURIComponent(q)}&limit=12`;
      const [audiusResp, jiosaavnResp] = await Promise.allSettled([
        fetch(apiUrl("/v1/catalog/jamendo/search") + params),
        fetch(apiUrl("/v1/catalog/jiosaavn/search") + params),
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
        <div className="search-results" role="list">
          {results.map((track) => (
            <TrackCard
              key={`${track.provider}-${track.id}`}
              track={track}
              isActive={currentTrackId === track.id}
              onPlay={onSelect}
              onPlayNext={onPlayNext}
              onAddToQueue={onAddToQueue}
            />
          ))}
        </div>
      )}
    </div>
  );
};

export default TrackSearch;