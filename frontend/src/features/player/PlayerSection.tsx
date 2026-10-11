import React, { useState, useCallback } from "react";
import TrackSearch, { type TrackResult } from "./TrackSearch";
import AudioPlayer from "./AudioPlayer";

const PlayerSection: React.FC = () => {
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

  return (
    <section className="player-section" aria-labelledby="player-title">
      <div className="section-heading">
        <div>
          <p className="eyebrow">NOW STREAMING</p>
          <h2 id="player-title">Play something.</h2>
        </div>
        <span className="featured-badge">AUDIUS</span>
      </div>

      <TrackSearch onSelect={handleSelect} currentTrackId={currentTrack?.id} />

      <AudioPlayer
        track={currentTrack}
        onNext={handleNext}
        onPrev={handlePrev}
        hasNext={queueIndex < queue.length - 1}
        hasPrev={queueIndex > 0}
      />
    </section>
  );
};

export default PlayerSection;
