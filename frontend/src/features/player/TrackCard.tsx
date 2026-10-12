import React, { useState, useRef, useEffect } from "react";

export interface TrackResult {
  id: string;
  title: string;
  artist: string;
  album: string;
  image: string;
  audioUrl: string;
  provider?: string;
}

interface TrackCardProps {
  track: TrackResult;
  isActive?: boolean;
  onPlay: (track: TrackResult) => void;
  onPlayNext?: (track: TrackResult) => void;
  onAddToQueue?: (track: TrackResult) => void;
}

const TrackCard: React.FC<TrackCardProps> = ({
  track,
  isActive,
  onPlay,
  onPlayNext,
  onAddToQueue,
}) => {
  const [showMenu, setShowMenu] = useState(false);
  const [hovered, setHovered] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setShowMenu(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const handleMenuClick = (e: React.MouseEvent, action: () => void) => {
    e.stopPropagation();
    action();
    setShowMenu(false);
  };

  const handlePlay = (e: React.MouseEvent) => {
    e.stopPropagation();
    onPlay(track);
  };

  const providerLabel = track.provider === "jiosaavn" ? "JioSaavn" : "Audius";

  return (
    <div className={`track-card-wrapper ${isActive ? "active" : ""}`} onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)}>
      <button
        className={`track-card ${isActive ? "active" : ""}`}
        onClick={() => onPlay(track)}
      >
        {track.image && (
          <img
            src={track.image}
            alt=""
            className="track-image"
            loading="lazy"
            onError={(e) => {
              e.currentTarget.style.visibility = "hidden";
            }}
          />
        )}
        <div className="track-info">
          <span className="track-title">{track.title}</span>
          <span className="track-artist">{track.artist}</span>
          {track.album && <span className="track-album">{track.album}</span>}
        </div>
        <div className="track-actions">
          <span className="track-provider-badge">{providerLabel}</span>
          {hovered && !showMenu && (
            <button
              type="button"
              className="track-play-btn"
              onClick={handlePlay}
              aria-label="Play"
            >
              ▶
            </button>
          )}
          {hovered && (
            <button
              type="button"
              className="track-menu-btn"
              onClick={(e) => {
                e.stopPropagation();
                setShowMenu(!showMenu);
              }}
              aria-label="More options"
              aria-expanded={showMenu}
              aria-haspopup="menu"
            >
              ⋯
            </button>
          )}
        </div>
      </button>

      {showMenu && (
        <div
          ref={menuRef}
          className="track-menu"
          role="menu"
          onClick={(e) => e.stopPropagation()}
        >
          {onPlayNext && (
            <button
              type="button"
              className="track-menu-item"
              role="menuitem"
              onClick={(e) => handleMenuClick(e, () => onPlayNext(track))}
            >
              <span className="menu-icon">⏭</span>
              Play Next
            </button>
          )}
          {onAddToQueue && (
            <button
              type="button"
              className="track-menu-item"
              role="menuitem"
              onClick={(e) => handleMenuClick(e, () => onAddToQueue(track))}
            >
              <span className="menu-icon">➕</span>
              Add to Queue
            </button>
          )}
          <button
            type="button"
            className="track-menu-item"
            role="menuitem"
          >
            <span className="menu-icon">🔗</span>
            Share
          </button>
        </div>
      )}
    </div>
  );
};

export default TrackCard;