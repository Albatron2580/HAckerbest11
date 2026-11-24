package com.whiteclient.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whiteclient.WhiteClient;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Keeps WHITECLIENT up to date: looks for a newer release on the project's GitHub page, downloads it in the
 * background, checks it (SHA-256 published next to it, and that it really is WHITECLIENT), and installs it
 * when you close the game. Only github.com downloads of this one project are accepted.
 */
public final class AutoUpdater {
	public static final String REPO = "Albatron2580/HAckerbest11";
	private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
	private static final String DOWNLOADS = "https://github.com/" + REPO + "/releases/download/";

	private static volatile String status = "Not checked yet";
	private static volatile boolean busy;
	private static volatile String readyVersion;
	private static volatile Path staged;
	private static boolean armed;

	private AutoUpdater() {
	}

	public static String status() {
		return status;
	}

	public static String readyVersion() {
		return readyVersion;
	}

	/** For the screenshots in the tests: pretend a version is ready (nothing is installed without a download). */
	public static void previewReady(String version) {
		readyVersion = version;
	}

	/** A short line for the menus, or null when there is nothing to say. */
	public static String badge() {
		if (readyVersion != null) {
			return "WHITECLIENT " + readyVersion + " is ready - it installs when you close the game";
		}
		String s = status;
		return s.startsWith("Downloading") ? s : null;
	}

	private static Path updateDir() {
		return FabricLoader.getInstance().getGameDir().resolve("whiteclient").resolve("update");
	}

	/** The WHITECLIENT jar the game loaded, or null when running from a development folder. */
	public static Path ownJar() {
		ModContainer c = FabricLoader.getInstance().getModContainer(WhiteClient.MOD_ID).orElse(null);
		if (c == null) {
			return null;
		}
		for (Path p : c.getOrigin().getPaths()) {
			if (Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
				return p;
			}
		}
		return null;
	}

	/** At start: tidy up after a finished update and show what the last install did. */
	public static void startup() {
		Path dir = updateDir();
		try {
			Path log = dir.resolve("swap.log");
			if (Files.isRegularFile(log)) {
				for (String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
					WhiteClient.LOGGER.info("[WHITECLIENT] last update: {}", line);
				}
				Files.deleteIfExists(log);
			}
			if (Files.isDirectory(dir)) {
				try (Stream<Path> files = Files.list(dir)) {
					for (Path p : files.toList()) {
						String v = versionOf(p.getFileName().toString());
						if (v != null && compare(v, WhiteClient.VERSION) <= 0) {
							Files.deleteIfExists(p); // already installed
						}
					}
				}
			}
		} catch (Throwable t) {
			WhiteClient.LOGGER.debug("[WHITECLIENT] update cleanup: {}", t.toString());
		}
	}

	public static void checkAsync(boolean install, boolean manual) {
		if (busy) {
			return;
		}
		busy = true;
		Thread t = new Thread(() -> {
			try {
				check(install, manual);
			} catch (Throwable e) {
				status = "Couldn't check for updates (" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + ")";
				WhiteClient.LOGGER.info("[WHITECLIENT] update check failed: {}", e.toString());
			} finally {
				busy = false;
			}
		}, "WHITECLIENT Updater");
		t.setDaemon(true);
		t.start();
	}

	/** Checks right now on this thread (used by the tests); returns the status line. */
	public static String checkNow(boolean install) {
		try {
			check(install, true);
		} catch (Throwable e) {
			status = "Couldn't check for updates (" + e + ")";
		}
		return status;
	}

	private static HttpClient http() {
		return HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
	}

	private static HttpRequest.Builder request(String url) {
		return HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(60))
				.header("User-Agent", "WHITECLIENT/" + WhiteClient.VERSION);
	}

	private static void check(boolean install, boolean manual) throws Exception {
		if (readyVersion != null && !manual) {
			return; // already downloaded, waiting for the game to close
		}
		status = "Checking for updates...";
		HttpClient http = http();
		String latest = latestVersion();
		if (latest == null || compare(latest, WhiteClient.VERSION) <= 0) {
			status = "Up to date (" + WhiteClient.VERSION + ")";
			return;
		}
		if (!install) {
			status = "WHITECLIENT " + latest + " is out (turn on Install Automatically to get it)";
			toast("WHITECLIENT " + latest + " is out", "Right Shift > Menu > Auto Update");
			return;
		}
		Path own = ownJar();
		if (own == null) {
			status = "WHITECLIENT " + latest + " is out (can't update a development build)";
			return;
		}

		status = "Downloading WHITECLIENT " + latest + "...";
		Path target = download(http, latest, updateDir());
		staged = target;
		readyVersion = latest;
		status = "WHITECLIENT " + latest + " is ready: it installs when you close the game";
		WhiteClient.LOGGER.info("[WHITECLIENT] update {} downloaded, installs when the game closes", latest);
		toast("WHITECLIENT " + latest + " downloaded", "It installs when you close the game");
	}

	/** Downloads one released version into a folder and checks it; returns the checked jar. */
	public static Path download(HttpClient http, String latest, Path dir) throws Exception {
		String jarName = "whiteclient-" + latest + ".jar";
		String jarUrl = DOWNLOADS + "v" + latest + "/" + jarName;
		String shaUrl = jarUrl + ".sha256";
		String shaText = http.send(request(shaUrl).header("Accept", "*/*").build(), HttpResponse.BodyHandlers.ofString()).body();
		String expected = shaText.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
		if (!expected.matches("[0-9a-f]{64}")) {
			throw new IOException("bad checksum file");
		}
		Files.createDirectories(dir);
		Path target = dir.resolve(jarName);
		if (!(Files.isRegularFile(target) && sha256(target).equals(expected))) {
			Path tmp = dir.resolve(jarName + ".download");
			HttpResponse<Path> dl = http.send(request(jarUrl).header("Accept", "application/octet-stream").build(),
					HttpResponse.BodyHandlers.ofFile(tmp));
			if (dl.statusCode() != 200) {
				Files.deleteIfExists(tmp);
				throw new IOException("download answered " + dl.statusCode());
			}
			if (!sha256(tmp).equals(expected)) {
				Files.deleteIfExists(tmp);
				throw new IOException("download was damaged (checksum mismatch)");
			}
			verifyJar(tmp, latest);
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		}
		return target;
	}

	/** Downloads and checks a released version into a temporary folder (used by the tests). */
	public static String testDownload(String version) {
		try {
			Path dir = Files.createTempDirectory("whiteclient-update-test");
			Path jar = download(http(), version, dir);
			return "ok, " + jar.getFileName() + " (" + Files.size(jar) + " bytes, checksum and contents verified)";
		} catch (Throwable t) {
			return "failed: " + t;
		}
	}

	/**
	 * The newest released version. Uses the normal github.com "latest release" link (it just redirects to
	 * the newest tag, with no API limits); GitHub's API is the fallback. Null when nothing is released.
	 */
	private static String latestVersion() throws Exception {
		try {
			HttpClient noRedirect = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
					.followRedirects(HttpClient.Redirect.NEVER).build();
			HttpResponse<Void> r = noRedirect.send(request("https://github.com/" + REPO + "/releases/latest")
					.method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
					HttpResponse.BodyHandlers.discarding());
			String loc = r.headers().firstValue("location").orElse("");
			int i = loc.indexOf("/releases/tag/");
			if (r.statusCode() / 100 == 3 && i >= 0) {
				String tag = loc.substring(i + "/releases/tag/".length());
				return tag.startsWith("v") ? tag.substring(1) : tag;
			}
			if (r.statusCode() / 100 == 3 && loc.endsWith("/releases")) {
				return null; // no release yet
			}
		} catch (IOException ignored) {
			// try the API below
		}
		HttpResponse<String> res = http().send(request(API).header("Accept", "application/vnd.github+json").build(),
				HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() == 404) {
			return null;
		}
		if (res.statusCode() != 200) {
			throw new IOException("GitHub answered " + res.statusCode());
		}
		String tag = JsonParser.parseString(res.body()).getAsJsonObject().get("tag_name").getAsString();
		return tag.startsWith("v") ? tag.substring(1) : tag;
	}

	/** The download must really be WHITECLIENT, with the version it claims, and carry the installer. */
	private static void verifyJar(Path jar, String version) throws IOException {
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			ZipEntry meta = zip.getEntry("fabric.mod.json");
			ZipEntry swap = zip.getEntry("com/whiteclient/update/Swap.class");
			if (meta == null || swap == null) {
				throw new IOException("download is not a WHITECLIENT jar");
			}
			try (InputStream in = zip.getInputStream(meta)) {
				JsonObject json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
				if (!WhiteClient.MOD_ID.equals(json.get("id").getAsString()) || !version.equals(json.get("version").getAsString())) {
					throw new IOException("download is not WHITECLIENT " + version);
				}
			}
		}
	}

	/** Called when the game closes: hand the downloaded jar to the installer, which waits for the game to exit. */
	public static void onGameClosing() {
		if (armed || staged == null || readyVersion == null || !Files.isRegularFile(staged)) {
			return;
		}
		Path own = ownJar();
		if (own == null) {
			return;
		}
		try {
			armed = true;
			Path target = own.getParent().resolve("whiteclient-" + readyVersion + ".jar");
			String java = javaExecutable();
			ProcessBuilder pb = new ProcessBuilder(java, "-cp", staged.toString(), Swap.class.getName(),
					String.valueOf(ProcessHandle.current().pid()), own.toString(), staged.toString(), target.toString());
			pb.redirectErrorStream(true);
			pb.redirectOutput(ProcessBuilder.Redirect.appendTo(updateDir().resolve("swap.log").toFile()));
			pb.start();
			WhiteClient.LOGGER.info("[WHITECLIENT] installing {} after the game closes", readyVersion);
		} catch (Throwable t) {
			WhiteClient.LOGGER.warn("[WHITECLIENT] could not start the update installer: {}", t.toString());
		}
	}

	private static String javaExecutable() {
		String cmd = ProcessHandle.current().info().command().orElse(null);
		if (cmd != null) {
			// java.exe would flash a console window on Windows: use javaw.exe next to it if it is there
			if (cmd.toLowerCase(Locale.ROOT).endsWith("java.exe")) {
				Path w = Path.of(cmd).resolveSibling("javaw.exe");
				if (Files.isRegularFile(w)) {
					return w.toString();
				}
			}
			return cmd;
		}
		boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
		return Path.of(System.getProperty("java.home"), "bin", windows ? "javaw.exe" : "java").toString();
	}

	private static void toast(String title, String text) {
		try {
			MinecraftClient mc = MinecraftClient.getInstance();
			mc.execute(() -> SystemToast.show(mc.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
					Text.literal(title), Text.literal(text)));
		} catch (Throwable ignored) {
		}
	}

	private static String sha256(Path file) throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		try (InputStream in = Files.newInputStream(file)) {
			byte[] buf = new byte[65536];
			int r;
			while ((r = in.read(buf)) > 0) {
				md.update(buf, 0, r);
			}
		}
		return HexFormat.of().formatHex(md.digest());
	}

	private static String versionOf(String fileName) {
		String n = fileName;
		if (!n.startsWith("whiteclient-")) {
			return null;
		}
		n = n.substring("whiteclient-".length());
		int jar = n.indexOf(".jar");
		return jar > 0 ? n.substring(0, jar) : null;
	}

	/** Compares versions like 1.12.0 and 1.12.10 number by number. */
	public static int compare(String a, String b) {
		List<String> pa = List.of(a.split("[.+-]")), pb = List.of(b.split("[.+-]"));
		for (int i = 0; i < Math.max(pa.size(), pb.size()); i++) {
			int x = i < pa.size() ? num(pa.get(i)) : 0, y = i < pb.size() ? num(pb.get(i)) : 0;
			if (x != y) {
				return Integer.compare(x, y);
			}
		}
		return 0;
	}

	private static int num(String s) {
		try {
			return Integer.parseInt(s.replaceAll("[^0-9].*$", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
