package com.whiteclient.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whiteclient.WhiteClient;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Synced lyrics from LRCLIB (free, no account needed). */
public final class LyricsService {
	static final String USER_AGENT = "WHITECLIENT/" + WhiteClient.VERSION + " (Minecraft mod; github.com/Albatron2580)";
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(6))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	private final Map<String, Lyrics> cache = new ConcurrentHashMap<>();
	private final Set<String> loading = ConcurrentHashMap.newKeySet();
	private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "WHITECLIENT lyrics");
		t.setDaemon(true);
		return t;
	});

	/** Cached lyrics, or null while loading (the first call starts the download). */
	public Lyrics get(NowPlaying np) {
		if (np == null || np.title() == null || np.title().isBlank()) {
			return Lyrics.NONE;
		}
		String key = np.key();
		Lyrics cached = cache.get(key);
		if (cached != null) {
			return cached;
		}
		if (loading.add(key)) {
			exec.submit(() -> {
				Lyrics result;
				try {
					result = fetch(np);
				} catch (Exception e) {
					WhiteClient.LOGGER.warn("[WHITECLIENT] lyrics lookup failed: {}", e.toString());
					result = Lyrics.NONE;
				}
				cache.put(key, result);
				loading.remove(key);
			});
		}
		return null;
	}

	private static String enc(String s) {
		return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
	}

	private static HttpResponse<String> call(String url) throws Exception {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(10))
				.header("User-Agent", USER_AGENT)
				.GET().build();
		return HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
	}

	private static Lyrics fromObject(JsonObject o) {
		if (o == null) {
			return null;
		}
		if (o.has("instrumental") && !o.get("instrumental").isJsonNull() && o.get("instrumental").getAsBoolean()) {
			return new Lyrics(java.util.List.of(), true);
		}
		JsonElement synced = o.get("syncedLyrics");
		if (synced != null && !synced.isJsonNull()) {
			Lyrics l = Lyrics.parseLrc(synced.getAsString());
			if (!l.isEmpty()) {
				return l;
			}
		}
		return null;
	}

	/** Removes " - Remastered 2011", "(feat. X)" etc. for a fuzzier second search. */
	static String cleanTitle(String title) {
		String t = title.replaceAll("\\s*[(\\[][^)\\]]*[)\\]]", "");
		int dash = t.indexOf(" - ");
		if (dash > 0) {
			t = t.substring(0, dash);
		}
		return t.trim();
	}

	private Lyrics fetch(NowPlaying np) throws Exception {
		String artist = np.artist() == null ? "" : np.artist();
		// 1) exact match (needs album + duration)
		if (np.album() != null && !np.album().isBlank() && np.durationMs() > 0) {
			HttpResponse<String> r = call("https://lrclib.net/api/get?artist_name=" + enc(artist)
					+ "&track_name=" + enc(np.title()) + "&album_name=" + enc(np.album())
					+ "&duration=" + Math.round(np.durationMs() / 1000.0));
			if (r.statusCode() == 200) {
				Lyrics l = fromObject(JsonParser.parseString(r.body()).getAsJsonObject());
				if (l != null) {
					return l;
				}
			}
		}
		// 2) search, pick the synced result with the closest duration
		for (String title : new String[]{np.title(), cleanTitle(np.title())}) {
			HttpResponse<String> r = call("https://lrclib.net/api/search?track_name=" + enc(title) + "&artist_name=" + enc(artist));
			if (r.statusCode() != 200) {
				continue;
			}
			JsonElement root = JsonParser.parseString(r.body());
			if (!root.isJsonArray()) {
				continue;
			}
			JsonArray arr = root.getAsJsonArray();
			Lyrics best = null;
			double bestDiff = Double.MAX_VALUE;
			for (JsonElement e : arr) {
				if (!e.isJsonObject()) {
					continue;
				}
				JsonObject o = e.getAsJsonObject();
				Lyrics l = fromObject(o);
				if (l == null || l.isEmpty()) {
					continue;
				}
				double diff = 0;
				if (np.durationMs() > 0 && o.has("duration") && !o.get("duration").isJsonNull()) {
					diff = Math.abs(o.get("duration").getAsDouble() - np.durationMs() / 1000.0);
				}
				if (diff < bestDiff) {
					bestDiff = diff;
					best = l;
				}
			}
			if (best != null && bestDiff < 8) {
				return best;
			}
		}
		return Lyrics.NONE;
	}
}
