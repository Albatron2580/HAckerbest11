package com.whiteclient.music;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.ptr.IntByReference;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * No-login mode (Windows only): reads "Artist - Song" from the Spotify desktop window title.
 * Playback position is tracked from the moment the title changes.
 */
final class WindowsTitleSource {
	private final Map<Integer, Boolean> spotifyPids = new HashMap<>();
	private long pidCacheReset;

	private String lastTitle;
	private long trackStart;
	private long pausedAt = -1;
	private long pausedTotal;
	private boolean positionKnown;
	private boolean sawSpotify;

	static boolean supported() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	private boolean isSpotify(int pid) {
		long now = System.currentTimeMillis();
		if (now - pidCacheReset > 30_000) {
			spotifyPids.clear();
			pidCacheReset = now;
		}
		return spotifyPids.computeIfAbsent(pid, p -> ProcessHandle.of(p)
				.flatMap(h -> h.info().command())
				.map(c -> c.toLowerCase(Locale.ROOT).endsWith("spotify.exe"))
				.orElse(false));
	}

	/** @return [running, title-with-dash-or-null] */
	private Object[] scan() {
		final boolean[] running = {false};
		final String[] title = {null};
		User32.INSTANCE.EnumWindows((hwnd, data) -> {
			IntByReference pid = new IntByReference();
			User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
			if (!isSpotify(pid.getValue())) {
				return true;
			}
			running[0] = true;
			char[] buf = new char[512];
			int len = User32.INSTANCE.GetWindowText(hwnd, buf, buf.length);
			if (len > 0) {
				String t = new String(buf, 0, len);
				if (t.contains(" - ")) {
					title[0] = t;
					return false;
				}
			}
			return true;
		}, null);
		return new Object[]{running[0], title[0]};
	}

	/** Current state, or null if Spotify is not running. */
	NowPlaying poll() {
		Object[] r = scan();
		boolean running = (Boolean) r[0];
		String title = (String) r[1];
		long now = System.currentTimeMillis();
		if (!running) {
			lastTitle = null;
			sawSpotify = false;
			return null;
		}
		if (title != null) {
			if (!title.equals(lastTitle)) {
				// a new song started (if we were already watching, we know it started now)
				positionKnown = sawSpotify;
				lastTitle = title;
				trackStart = now;
				pausedTotal = 0;
				pausedAt = -1;
			} else if (pausedAt >= 0) {
				pausedTotal += now - pausedAt;
				pausedAt = -1;
			}
			sawSpotify = true;
			return build(title, now - trackStart - pausedTotal, true, now);
		}
		// Spotify open but paused (title is just "Spotify Premium" / "Spotify Free")
		sawSpotify = true;
		if (lastTitle == null) {
			return null;
		}
		if (pausedAt < 0) {
			pausedAt = now;
		}
		return build(lastTitle, pausedAt - trackStart - pausedTotal, false, now);
	}

	private NowPlaying build(String windowTitle, long progress, boolean playing, long now) {
		int dash = windowTitle.indexOf(" - ");
		String artist = windowTitle.substring(0, dash).trim();
		String song = windowTitle.substring(dash + 3).trim();
		return new NowPlaying("win:" + windowTitle, song, artist, "", 0, Math.max(0, progress), playing, now,
				null, positionKnown, "Spotify app (no login)");
	}
}
