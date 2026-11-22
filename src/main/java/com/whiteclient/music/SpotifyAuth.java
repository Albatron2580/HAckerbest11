package com.whiteclient.music;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whiteclient.WhiteClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Spotify login with PKCE (no client secret needed). The user registers their own
 * Spotify app with redirect URI {@value #REDIRECT_URI} and pastes its Client ID.
 */
public final class SpotifyAuth {
	public static final int PORT = 25588;
	public static final String REDIRECT_URI = "http://127.0.0.1:" + PORT + "/callback";
	private static final String SCOPES = "user-read-currently-playing user-read-playback-state";
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private static final Gson GSON = new Gson();

	private final Path file = FabricLoader.getInstance().getConfigDir().resolve("whiteclient-spotify-token.json");
	private volatile String clientId = "";
	private volatile String refreshToken;
	private volatile String accessToken;
	private volatile long expiresAt;
	private volatile String status = "Not logged in";
	private volatile boolean loginRunning;

	public SpotifyAuth() {
		load();
	}

	public boolean isLoggedIn() {
		return refreshToken != null && !refreshToken.isBlank();
	}

	public String status() {
		return status;
	}

	public boolean isLoginRunning() {
		return loginRunning;
	}

	private void load() {
		try {
			if (Files.exists(file)) {
				JsonObject o = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
				if (o.has("refresh_token")) {
					refreshToken = o.get("refresh_token").getAsString();
				}
				if (o.has("client_id")) {
					clientId = o.get("client_id").getAsString();
				}
				if (isLoggedIn()) {
					status = "Logged in";
				}
			}
		} catch (Exception e) {
			WhiteClient.LOGGER.warn("[WHITECLIENT] could not read Spotify token file: {}", e.toString());
		}
	}

	private void save() {
		try {
			JsonObject o = new JsonObject();
			o.addProperty("client_id", clientId);
			if (refreshToken != null) {
				o.addProperty("refresh_token", refreshToken);
			}
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(o));
		} catch (Exception e) {
			WhiteClient.LOGGER.warn("[WHITECLIENT] could not save Spotify token: {}", e.toString());
		}
	}

	public void logout() {
		refreshToken = null;
		accessToken = null;
		expiresAt = 0;
		status = "Not logged in";
		save();
	}

	private static String b64url(byte[] data) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	/**
	 * Starts the login: returns the URL the browser has to open. A tiny local web server
	 * waits (3 minutes) for Spotify to redirect back with the code.
	 */
	public String startLogin(String id, Consumer<String> chat) {
		if (id == null || id.isBlank()) {
			status = "Paste your Spotify Client ID first";
			return null;
		}
		if (loginRunning) {
			return null;
		}
		clientId = id.trim();
		byte[] raw = new byte[48];
		new SecureRandom().nextBytes(raw);
		String verifier = b64url(raw);
		String challenge;
		try {
			challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
		} catch (Exception e) {
			status = "SHA-256 unavailable";
			return null;
		}
		byte[] st = new byte[12];
		new SecureRandom().nextBytes(st);
		String state = b64url(st);
		String url = "https://accounts.spotify.com/authorize?response_type=code&client_id=" + enc(clientId)
				+ "&scope=" + enc(SCOPES) + "&redirect_uri=" + enc(REDIRECT_URI)
				+ "&code_challenge_method=S256&code_challenge=" + challenge + "&state=" + state;

		loginRunning = true;
		status = "Waiting for browser login...";
		Thread t = new Thread(() -> {
			try (ServerSocket server = new ServerSocket(PORT, 4, InetAddress.getByName("127.0.0.1"))) {
				server.setSoTimeout(180_000);
				while (true) {
					try (Socket socket = server.accept()) {
						socket.setSoTimeout(5000);
						BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
						String requestLine = in.readLine();
						if (requestLine == null || !requestLine.startsWith("GET /callback")) {
							respond(socket.getOutputStream(), 404, "Not found");
							continue;
						}
						String path = requestLine.split(" ")[1];
						Map<String, String> q = query(path);
						if (q.containsKey("error")) {
							respond(socket.getOutputStream(), 200, page("Spotify login was cancelled", q.get("error")));
							status = "Login cancelled: " + q.get("error");
							break;
						}
						if (!state.equals(q.get("state")) || !q.containsKey("code")) {
							respond(socket.getOutputStream(), 400, page("Login failed", "Invalid response, try again."));
							status = "Login failed (bad state)";
							break;
						}
						boolean ok = exchange("grant_type=authorization_code&code=" + enc(q.get("code"))
								+ "&redirect_uri=" + enc(REDIRECT_URI) + "&client_id=" + enc(clientId)
								+ "&code_verifier=" + enc(verifier));
						respond(socket.getOutputStream(), 200, ok
								? page("WHITECLIENT is connected to Spotify", "You can close this tab and go back to Minecraft.")
								: page("Login failed", status));
						if (ok) {
							chat.accept("§f[WHITECLIENT] §aSpotify connected!");
						}
						break;
					}
				}
			} catch (SocketTimeoutException e) {
				status = "Login timed out";
			} catch (Exception e) {
				status = "Login error: " + e.getMessage();
				WhiteClient.LOGGER.warn("[WHITECLIENT] Spotify login failed", e);
			} finally {
				loginRunning = false;
			}
		}, "WHITECLIENT spotify login");
		t.setDaemon(true);
		t.start();
		return url;
	}

	private static String page(String title, String text) {
		return "<!doctype html><html><head><meta charset=utf-8><title>WHITECLIENT</title></head>"
				+ "<body style=\"background:#0b0b0f;color:#fff;font-family:system-ui,sans-serif;display:flex;height:100vh;"
				+ "align-items:center;justify-content:center;margin:0\"><div style=\"text-align:center\">"
				+ "<div style=\"font-size:13px;letter-spacing:4px;color:#888\">WHITECLIENT</div>"
				+ "<h1 style=\"font-weight:600\">" + title + "</h1><p style=\"color:#aaa\">" + text + "</p></div></body></html>";
	}

	private static void respond(OutputStream out, int code, String body) throws Exception {
		byte[] data = body.getBytes(StandardCharsets.UTF_8);
		String head = "HTTP/1.1 " + code + " OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "
				+ data.length + "\r\nConnection: close\r\n\r\n";
		out.write(head.getBytes(StandardCharsets.US_ASCII));
		out.write(data);
		out.flush();
	}

	private static Map<String, String> query(String path) {
		Map<String, String> map = new HashMap<>();
		int i = path.indexOf('?');
		if (i < 0) {
			return map;
		}
		for (String kv : path.substring(i + 1).split("&")) {
			int eq = kv.indexOf('=');
			if (eq > 0) {
				map.put(URLDecoder.decode(kv.substring(0, eq), StandardCharsets.UTF_8),
						URLDecoder.decode(kv.substring(eq + 1), StandardCharsets.UTF_8));
			}
		}
		return map;
	}

	/** POSTs to the token endpoint; stores the tokens on success. */
	private synchronized boolean exchange(String form) {
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
					.timeout(Duration.ofSeconds(10))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(form)).build();
			HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
			if (r.statusCode() != 200) {
				status = "Spotify said " + r.statusCode() + ": " + r.body();
				if (r.statusCode() == 400 && r.body().contains("invalid_grant")) {
					refreshToken = null;
					save();
				}
				return false;
			}
			JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
			accessToken = o.get("access_token").getAsString();
			expiresAt = System.currentTimeMillis() + o.get("expires_in").getAsLong() * 1000L - 30_000L;
			if (o.has("refresh_token")) {
				refreshToken = o.get("refresh_token").getAsString();
			}
			status = "Logged in";
			save();
			return true;
		} catch (Exception e) {
			status = "Token error: " + e.getMessage();
			return false;
		}
	}

	/** A valid access token (refreshing when needed), or null. Call from a worker thread. */
	public String accessToken() {
		if (!isLoggedIn()) {
			return null;
		}
		if (accessToken != null && System.currentTimeMillis() < expiresAt) {
			return accessToken;
		}
		if (clientId == null || clientId.isBlank()) {
			status = "Missing Client ID - log in again";
			return null;
		}
		boolean ok = exchange("grant_type=refresh_token&refresh_token=" + enc(refreshToken) + "&client_id=" + enc(clientId));
		return ok ? accessToken : null;
	}

	/** Forces a refresh on the next call (after a 401). */
	public void invalidate() {
		expiresAt = 0;
	}
}
