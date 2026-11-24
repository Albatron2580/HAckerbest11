package com.whiteclient.music;

public record NowPlaying(String id, String title, String artist, String album, long durationMs, long progressMs,
						 boolean playing, long sampledAtMs, String coverUrl, boolean positionKnown, String source) {
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
