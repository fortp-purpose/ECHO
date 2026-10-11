import React, { useRef, useState, useEffect, useCallback } from "react";
import type { TrackResult } from "./TrackSearch";

interface AudioPlayerProps {
  track: TrackResult | null;
  onNext?: () => void;
  onPrev?: () => void;
  hasNext?: boolean;
  hasPrev?: boolean;
}

const formatTime = (seconds: number): string => {
  if (!isFinite(seconds) || seconds < 0) return "0:00";
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
};

const AudioPlayer: React.FC<AudioPlayerProps> = ({
  track,
  onNext,
  onPrev,
  hasNext,
  hasPrev,
}) => {
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const [playing, setPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [volume, setVolume] = useState(0.8);
  const [muted, setMuted] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [buffered, setBuffered] = useState(0);

  useEffect(() => {
    if (!audioRef.current) {
      audioRef.current = new Audio();
      audioRef.current.volume = volume;
    }
    const audio = audioRef.current;

    const onTime = () => setCurrentTime(audio.currentTime);
    const onDur = () => {
      if (isFinite(audio.duration)) {
        setDuration(audio.duration);
      }
    };
    const onEnd = () => {
      setPlaying(false);
      setCurrentTime(0);
      if (onNext && hasNext) {
        onNext();
      }
    };
    const onErr = (e: Event) => {
      const audioEl = e.target as HTMLAudioElement;
      const code = audioEl.error?.code;
      let msg = "Playback failed";
      if (code === HTMLMediaElement.MEDIA_ERR_NETWORK) msg = "Network error";
      else if (code === HTMLMediaElement.MEDIA_ERR_DECODE) msg = "Decode error";
      else if (code === HTMLMediaElement.MEDIA_ERR_SRC_NOT_SUPPORTED) msg = "Unsupported format";
      setError(msg);
      setPlaying(false);
      setLoading(false);
    };
    const onCanPlay = () => {
      setLoading(false);
      setError(null);
    };
    const onWaiting = () => setLoading(true);
    const onPlay = () => setPlaying(true);
    const onPause = () => setPlaying(false);
    const onProgress = () => {
      if (audio.buffered.length > 0) {
        setBuffered(audio.buffered.end(audio.buffered.length - 1));
      }
    };

    audio.addEventListener("timeupdate", onTime);
    audio.addEventListener("durationchange", onDur);
    audio.addEventListener("ended", onEnd);
    audio.addEventListener("error", onErr);
    audio.addEventListener("canplay", onCanPlay);
    audio.addEventListener("waiting", onWaiting);
    audio.addEventListener("play", onPlay);
    audio.addEventListener("pause", onPause);
    audio.addEventListener("progress", onProgress);

    return () => {
      audio.removeEventListener("timeupdate", onTime);
      audio.removeEventListener("durationchange", onDur);
      audio.removeEventListener("ended", onEnd);
      audio.removeEventListener("error", onErr);
      audio.removeEventListener("canplay", onCanPlay);
      audio.removeEventListener("waiting", onWaiting);
      audio.removeEventListener("play", onPlay);
      audio.removeEventListener("pause", onPause);
      audio.removeEventListener("progress", onProgress);
    };
  }, [onNext, hasNext]);

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio || !track) return;

    setLoading(true);
    setError(null);
    setCurrentTime(0);
    setDuration(0);
    setBuffered(0);
    audio.src = track.audioUrl;
    audio.load();
    audio.volume = muted ? 0 : volume;
    audio.play().catch(() => {
      setError("Click play to start");
      setLoading(false);
    });
  }, [track, muted, volume]);

  useEffect(() => {
    if (audioRef.current) {
      audioRef.current.volume = muted ? 0 : volume;
    }
  }, [volume, muted]);

  const toggle = useCallback(() => {
    const audio = audioRef.current;
    if (!audio) return;
    if (audio.paused) {
      audio.play().catch(() => setError("Playback blocked"));
    } else {
      audio.pause();
    }
  }, []);

  const seek = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const audio = audioRef.current;
    if (!audio || !isFinite(audio.duration) || audio.duration <= 0) return;
    const t = (parseFloat(e.target.value) / 100) * audio.duration;
    audio.currentTime = t;
    setCurrentTime(t);
  }, []);

  const changeVolume = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const v = parseFloat(e.target.value);
    setVolume(v);
    setMuted(v === 0);
  }, []);

  const toggleMute = useCallback(() => {
    setMuted((m) => !m);
  }, []);

  const retry = useCallback(() => {
    const audio = audioRef.current;
    if (!audio || !track) return;
    setError(null);
    setLoading(true);
    audio.load();
    audio.play().catch(() => setError("Playback blocked"));
  }, [track]);

  if (!track) {
    return (
      <div className="audio-player empty">
        <span className="player-placeholder">Select a track to play</span>
      </div>
    );
  }

  const progress = duration > 0 && isFinite(duration) ? (currentTime / duration) * 100 : 0;
  const displayVolume = muted ? 0 : volume;

  return (
    <div className="audio-player">
      <div className="player-info">
        {track.image && <img src={track.image} alt="" className="player-image" />}
        <div className="player-meta">
          <span className="player-title">{track.title}</span>
          <span className="player-artist">{track.artist}</span>
        </div>
      </div>

      <div className="player-controls">
        <button
          className="control-btn"
          onClick={onPrev}
          disabled={!hasPrev || !onPrev}
          aria-label="Previous"
        >
          &#9664;&#9664;
        </button>
        <button
          className="control-btn play-btn"
          onClick={toggle}
          aria-label={playing ? "Pause" : "Play"}
        >
          {loading ? "..." : playing ? "❚❚" : "▶"}
        </button>
        <button
          className="control-btn"
          onClick={onNext}
          disabled={!hasNext || !onNext}
          aria-label="Next"
        >
          &#9654;&#9654;
        </button>
      </div>

      <div className="player-progress">
        <span className="time">{formatTime(currentTime)}</span>
        <div className="seek-wrapper">
          <input
            type="range"
            min="0"
            max="100"
            value={progress}
            onChange={seek}
            className="seek-bar"
            aria-label="Seek"
            disabled={!isFinite(duration) || duration <= 0}
          />
          {buffered > 0 && duration > 0 && isFinite(duration) && (
            <div
              className="buffered-bar"
              style={{ width: `${(buffered / duration) * 100}%` }}
            />
          )}
        </div>
        <span className="time">{formatTime(duration)}</span>
      </div>

      <div className="player-volume">
        <button
          type="button"
          className="volume-icon"
          onClick={toggleMute}
          aria-label={muted ? "Unmute" : "Mute"}
          aria-pressed={muted}
        >
          {muted ? "&#128263;" : displayVolume > 0.5 ? "&#128266;" : "&#128265;"}
        </button>
        <input
          type="range"
          min="0"
          max="1"
          step="0.05"
          value={displayVolume}
          onChange={changeVolume}
          className="volume-bar"
          aria-label="Volume"
          disabled={muted}
        />
      </div>

      {error && (
        <div className="player-error">
          <span>{error}</span>
          <button type="button" onClick={retry} className="retry-btn">
            Retry
          </button>
        </div>
      )}
    </div>
  );
};

export default AudioPlayer;