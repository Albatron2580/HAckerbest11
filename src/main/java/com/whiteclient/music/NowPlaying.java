package com.whiteclient.music;

/**
 * Snapshot of what is playing.
 *
 * @param positionKnown false when the song was already playing before we noticed it (no-login mode),
 *                      so lyrics cannot be synced for this song
 */
public record NowPlaying(String id, String title, String artist, String album, long durationMs, long progressMs,
						 boolean playing, long sampledAtMs, String coverUrl, boolean positionKnown, String source) {

	/** Interpolated playback position right now. */
	public long positionMs() {
		long p = playing ? progressMs + (System.currentTimeMillis() - sampledAtMs) : progressMs;
		if (durationMs > 0) {
			p = Math.min(p, durationMs);
		}
		return Math.max(0, p);
	}

	public String key() {
		return (artist + " - " + title).toLowerCase(java.util.Locale.ROOT);
	}
}
