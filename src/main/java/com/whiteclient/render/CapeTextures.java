package com.whiteclient.render;

import com.whiteclient.WhiteClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

/** WHITECLIENT capes: drawn in code (64x32 cape layout) or loaded from config/whiteclient/capes/*.png. */
public final class CapeTextures {
	public static final String[] DESIGNS = {"White", "Black", "Galaxy", "Silk", "Sunset", "Aurora", "Custom"};
	private static final Map<String, Identifier> CACHE = new HashMap<>();
	private static final Map<String, Boolean> FAILED = new HashMap<>();

	private CapeTextures() {
	}

	public static Path folder() {
		Path p = FabricLoader.getInstance().getConfigDir().resolve("whiteclient").resolve("capes");
		try {
			Files.createDirectories(p);
		} catch (Exception ignored) {
		}
		return p;
	}

	public static List<String> customFiles() {
		List<String> out = new ArrayList<>();
		try (Stream<Path> s = Files.list(folder())) {
			s.map(f -> f.getFileName().toString()).filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".png"))
					.forEach(n -> out.add(n.substring(0, n.length() - 4)));
		} catch (Exception ignored) {
		}
		out.sort(String.CASE_INSENSITIVE_ORDER);
		return out;
	}

	public static void reload() {
		MinecraftClient mc = MinecraftClient.getInstance();
		for (Map.Entry<String, Identifier> e : CACHE.entrySet()) {
			if (e.getKey().startsWith("file:")) {
				try {
					mc.getTextureManager().destroyTexture(texturePath(e.getValue()));
				} catch (Throwable ignored) {
				}
			}
		}
		CACHE.keySet().removeIf(k -> k.startsWith("file:"));
		FAILED.clear();
	}

	public static Identifier texturePath(Identifier id) {
		return Identifier.of(id.getNamespace(), "textures/" + id.getPath() + ".png");
	}

	private static String sanitize(String name) {
		StringBuilder b = new StringBuilder();
		for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
			b.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' ? c : '_');
		}
		return b.toString();
	}

	/** Texture id (for {@code AssetInfo.TextureAssetInfo}) of a built-in design, or null. */
	public static Identifier design(String name) {
		Identifier cached = CACHE.get(name);
		if (cached != null || FAILED.containsKey(name)) {
			return cached;
		}
		try {
			NativeImage img = new NativeImage(64, 32, true);
			paint(img, name);
			return register(name, "capes/" + sanitize(name), img);
		} catch (Throwable t) {
			FAILED.put(name, true);
			WhiteClient.LOGGER.warn("[WHITECLIENT] cape {} failed", name, t);
			return null;
		}
	}

	/** Texture id of a custom cape PNG, or null. */
	public static Identifier file(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		String key = "file:" + name;
		Identifier cached = CACHE.get(key);
		if (cached != null || FAILED.containsKey(key)) {
			return cached;
		}
		try (InputStream in = Files.newInputStream(folder().resolve(name + ".png"))) {
			NativeImage img = NativeImage.read(in);
			if (img.getWidth() != img.getHeight() * 2) {
				img.close();
				FAILED.put(key, true);
				return null;
			}
			return register(key, "capes/custom_" + sanitize(name), img);
		} catch (Throwable t) {
			FAILED.put(key, true);
			return null;
		}
	}

	private static Identifier register(String key, String path, NativeImage img) {
		Identifier id = Identifier.of(WhiteClient.MOD_ID, path);
		NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "whiteclient_" + path, img);
		MinecraftClient.getInstance().getTextureManager().registerTexture(texturePath(id), tex);
		CACHE.put(key, id);
		return id;
	}

	// ------------------------------------------------------------------ painting

	private static int rgb(int r, int g, int b) {
		return 0xFF000000 | (Math.max(0, Math.min(255, r)) << 16) | (Math.max(0, Math.min(255, g)) << 8) | Math.max(0, Math.min(255, b));
	}

	private static int lerp(int a, int b, float t) {
		int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return rgb((int) (ar + (br - ar) * t), (int) (ag + (bg - ag) * t), (int) (ab + (bb - ab) * t));
	}

	/** Base colour for a design at a cape position (u, v in 0..1 from top-left of the cape). */
	private static int base(String name, float u, float v, int x, int y) {
		switch (name) {
			case "Black":
				return lerp(0x121216, 0x24242C, v);
			case "Galaxy":
				return lerp(lerp(0x0B1030, 0x3B1D5A, v), 0x1B2F6B, 0.3f * (float) Math.sin(u * 3 + v * 2));
			case "Silk": {
				float s = 0.5f + 0.5f * (float) Math.sin((u * 2.2f + v * 1.4f) * Math.PI * 2);
				return lerp(0x6E6F76, 0xE6E6EC, 0.35f + 0.5f * s * s);
			}
			case "Sunset":
				return v < 0.5f ? lerp(0xFFD1A8, 0xFF8FB1, v * 2) : lerp(0xFF8FB1, 0x8E7CFF, (v - 0.5f) * 2);
			case "Aurora": {
				float w = 0.5f + 0.5f * (float) Math.sin(u * 6 + v * 3);
				int c = lerp(0x061A1F, 0x0F3B3A, v);
				return lerp(c, w > 0.6f ? 0x5CFFB0 : 0x8A6CFF, Math.max(0, (w - 0.55f)) * 1.6f * (1 - v * 0.5f));
			}
			default:
				return lerp(0xF6F6FA, 0xD5D6DF, v);
		}
	}

	private static void paint(NativeImage img, String name) {
		// cape box: 22x17 at (0,0); elytra: 24x22 at (22,0)
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 64; x++) {
				img.setColorArgb(x, y, 0);
			}
		}
		for (int y = 0; y < 17; y++) {
			for (int x = 0; x < 22; x++) {
				float u = (x % 11) / 10f, v = Math.max(0, y - 1) / 15f;
				img.setColorArgb(x, y, base(name, u, v, x, y));
			}
		}
		for (int y = 0; y < 22; y++) {
			for (int x = 22; x < 46; x++) {
				img.setColorArgb(x, y, base(name, (x - 22) / 23f, y / 21f, x, y));
			}
		}
		Random r = new Random(name.hashCode());
		if (name.equals("Galaxy")) {
			for (int i = 0; i < 26; i++) {
				int x = 1 + r.nextInt(10), y = 1 + r.nextInt(16);
				img.setColorArgb(x, y, r.nextInt(4) == 0 ? 0xFFAEC8FF : 0xFFFFFFFF);
				int ex = 22 + r.nextInt(24), ey = r.nextInt(22);
				img.setColorArgb(ex, ey, 0xFFFFFFFF);
			}
		}
		// front face is x 1..10, y 1..16 (the side you see from behind)
		int fg = switch (name) {
			case "White", "Silk", "Sunset" -> 0xFF15151B;
			default -> 0xFFFFFFFF;
		};
		if (name.equals("Black")) {
			for (int x = 1; x <= 10; x++) {
				img.setColorArgb(x, 1, 0xFFFFFFFF);
				img.setColorArgb(x, 16, 0xFFFFFFFF);
			}
			for (int y = 1; y <= 16; y++) {
				img.setColorArgb(1, y, 0xFFFFFFFF);
				img.setColorArgb(10, y, 0xFFFFFFFF);
			}
		}
		planet(img, 5.5f, 7.5f, fg);
	}

	/** Tiny pixel planet with a tilted ring (the WHITECLIENT logo). */
	private static void planet(NativeImage img, float cx, float cy, int color) {
		for (int y = 4; y <= 11; y++) {
			for (int x = 2; x <= 9; x++) {
				float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
				if (dx * dx + dy * dy <= 4.2f) {
					img.setColorArgb(x, y, color);
				}
			}
		}
		int[][] ring = {{2, 9}, {3, 9}, {4, 9}, {7, 6}, {8, 6}, {9, 5}, {2, 10}, {9, 6}};
		for (int[] p : ring) {
			img.setColorArgb(p[0], p[1], color);
		}
	}
}
