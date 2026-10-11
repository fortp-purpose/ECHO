import React from "react";
import AudioPlayer from "./AudioPlayer";
import type { TrackResult } from "./TrackSearch";

interface BottomPlayerProps {
  track: TrackResult | null;
  onNext: () => void;
  onPrev: () => void;
  hasNext: boolean;
  hasPrev: boolean;
}

const BottomPlayer: React.FC<BottomPlayerProps> = ({
  track,
  onNext,
  onPrev,
  hasNext,
  hasPrev,
}) => {
  if (!track) return null;

  return (
    <aside className="bottom-player" role="region" aria-label="Now playing">
      <div className="bottom-player-inner">
        <AudioPlayer
          track={track}
          onNext={onNext}
          onPrev={onPrev}
          hasNext={hasNext}
          hasPrev={hasPrev}
        />
      </div>
    </aside>
  );
};

export default BottomPlayer;