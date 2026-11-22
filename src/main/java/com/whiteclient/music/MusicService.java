package com.whiteclient.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whiteclient.WhiteClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.BooleanSupplier;

/** Background poller: what is playing on Spotify right now (+ lyrics and cover). */
public final class MusicService {
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();

	private final SpotifyAuth auth = new SpotifyAuth();
	private final LyricsService lyrics = new LyricsService();
	private final CoverArt cover = new CoverArt();
	private WindowsTitleSource windows;

	private final BooleanSupplier active;
	private final BooleanSupplier allowWindows;
	private volatile NowPlaying current;
	private volatile NowPlaying override;
	private volatile Lyrics overrideLyrics;
	private volatile String status = "Idle";
	private volatile long backoffUntil;
	private Thread thread;

	public MusicService(BooleanSupplier active, BooleanSupplier allowWindows) {
		this.active = active;
		this.allowWindows = allowWindows;
	}

	public SpotifyAuth auth() {
		return auth;
	}

	public CoverArt cover() {
		return cover;
	}

	public NowPlaying current() {
		NowPlaying o = override;
		return o != null ? o : current;
	}

	/** Fake "now playing" for previews and automated tests (null clears it). */
	public void setOverride(NowPlaying np, Lyrics lyrics) {
		this.override = np;
		this.overrideLyrics = lyrics;
	}

	public String status() {
		return status;
	}

	/** Lyrics for the current song: null = still loading, {@link Lyrics#NONE} = none found. */
	public Lyrics lyrics() {
		if (override != null) {
			return overrideLyrics == null ? Lyrics.NONE : overrideLyrics;
		}
		NowPlaying np = current;
		return np == null ? Lyrics.NONE : lyrics.get(np);
	}

	public void start() {
		if (thread != null) {
			return;
		}
		thread = new Thread(this::loop, "WHITECLIENT music");
		thread.setDaemon(true);
		thread.start();
	}

	private void loop() {
		while (true) {
			long sleep = 1000;
			try {
				if (!active.getAsBoolean()) {
					current = null;
					status = "Off";
					sleep = 600;
				} else if (auth.isLoggedIn()) {
					if (System.currentTimeMillis() >= backoffUntil) {
						current = pollWebApi();
					}
				} else if (allowWindows.getAsBoolean() && WindowsTitleSource.supported()) {
					if (windows == null) {
						windows = new WindowsTitleSource();
					}
					current = windows.poll();
					status = current == null ? "Spotify app not found - open Spotify or log in"
							: (current.positionKnown() ? "Synced from Spotify app" : "Lyrics sync starts next song (log in for perfect sync)");
					sleep = 400;
				} else {
					current = null;
					status = "Log in with Spotify to connect";
				}
				NowPlaying np = current;
				if (np != null) {
					lyrics.get(np);
					cover.request(np.coverUrl());
				} else {
					cover.request(null);
				}
			} catch (Throwable t) {
				status = "Error: " + t.getMessage();
				WhiteClient.LOGGER.warn("[WHITECLIENT] music poll failed: {}", t.toString());
				sleep = 3000;
			}
			try {
				Thread.sleep(sleep);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? null : e.getAsString();
	}

	private NowPlaying pollWebApi() throws Exception {
		String token = auth.accessToken();
		if (token == null) {
			status = auth.status();
			return null;
		}
		HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1/me/player/currently-playing?additional_types=episode"))
				.timeout(Duration.ofSeconds(8))
				.header("Authorization", "Bearer " + token)
				.GET().build();
		long sent = System.currentTimeMillis();
		HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
		long now = System.currentTimeMillis();
		if (r.statusCode() == 204 || (r.statusCode() == 200 && r.body().isBlank())) {
			status = "Logged in - nothing playing";
			return null;
		}
		if (r.statusCode() == 401) {
			auth.invalidate();
			status = "Refreshing login...";
			return current;
		}
		if (r.statusCode() == 429) {
			long wait = r.headers().firstValueAsLong("Retry-After").orElse(5);
			backoffUntil = now + wait * 1000;
			status = "Spotify rate limit, waiting " + wait + "s";
			return current;
		}
		if (r.statusCode() != 200) {
			status = "Spotify error " + r.statusCode();
			return current;
		}
		JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
		boolean playing = o.has("is_playing") && o.get("is_playing").getAsBoolean();
		long progress = o.has("progress_ms") && !o.get("progress_ms").isJsonNull() ? o.get("progress_ms").getAsLong() : 0;
		JsonElement itemEl = o.get("item");
		if (itemEl == null || !itemEl.isJsonObject()) {
			status = "Logged in - ad or unknown item";
			return null;
		}
		JsonObject item = itemEl.getAsJsonObject();
		String title = str(item, "name");
		long duration = item.has("duration_ms") ? item.get("duration_ms").getAsLong() : 0;
		String id = str(item, "id");
		String artist = "";
		String album = "";
		String coverUrl = null;
		JsonArray images = null;
		if (item.has("artists") && item.get("artists").isJsonArray()) {
			StringBuilder sb = new StringBuilder();
			for (JsonElement a : item.getAsJsonArray("artists")) {
				String n = str(a.getAsJsonObject(), "name");
				if (n != null) {
					if (sb.length() > 0) {
						sb.append(", ");
					}
					sb.append(n);
				}
			}
			artist = sb.toString();
		}
		if (item.has("album") && item.get("album").isJsonObject()) {
			JsonObject alb = item.getAsJsonObject("album");
			album = str(alb, "name");
			if (alb.has("images") && alb.get("images").isJsonArray()) {
				images = alb.getAsJsonArray("images");
			}
		} else if (item.has("show") && item.get("show").isJsonObject()) {
			JsonObject show = item.getAsJsonObject("show");
			artist = str(show, "name");
			if (show.has("images") && show.get("images").isJsonArray()) {
				images = show.getAsJsonArray("images");
			}
		}
		if (images != null && !images.isEmpty()) {
			// images are sorted big -> small; take the smallest that is still >= 64px
			for (JsonElement img : images) {
				JsonObject io = img.getAsJsonObject();
				int w = io.has("width") && !io.get("width").isJsonNull() ? io.get("width").getAsInt() : 640;
				if (coverUrl == null || w >= 64) {
					coverUrl = str(io, "url");
				}
			}
		}
		// progress was measured somewhere during the request; use the midpoint
		long sampled = (sent + now) / 2;
		status = playing ? "Playing" : "Paused";
		return new NowPlaying(id == null ? title : id, title, artist, album == null ? "" : album, duration, progress,
				playing, sampled, coverUrl, true, "Spotify account");
	}
}
