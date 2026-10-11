import React from "react";
import TrackSearch, { type TrackResult } from "./TrackSearch";

interface PlayerSectionProps {
  currentTrack: TrackResult | null;
  onSelect: (track: TrackResult) => void;
  onPlayNext?: (track: TrackResult) => void;
  onAddToQueue?: (track: TrackResult) => void;
}

const PlayerSection: React.FC<PlayerSectionProps> = ({
  currentTrack,
  onSelect,
  onPlayNext,
  onAddToQueue,
}) => {
  return (
    <section className="player-section" aria-labelledby="player-title">
      <div className="section-heading">
        <div>
          <p className="eyebrow">NOW STREAMING</p>
          <h2 id="player-title">
            {currentTrack ? "Now Playing" : "Play something."}
          </h2>
        </div>
        <span className="featured-badge">AUDIUS</span>
      </div>

      <TrackSearch
        onSelect={onSelect}
        onPlayNext={onPlayNext}
        onAddToQueue={onAddToQueue}
        currentTrackId={currentTrack?.id}
      />
    </section>
  );
};

export default PlayerSection;