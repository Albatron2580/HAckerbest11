package com.whiteclient.modules;

import com.whiteclient.WhiteClient;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.TextSetting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Custom skins from PNG files in {@code config/whiteclient/skins}. Free, client-side only:
 * you see the skin on yourself (other players keep seeing your normal skin).
 */
public class Wardrobe extends Module {
	public final TextSetting skin = add(new TextSetting("Skin", "", 64));
	public final BoolSetting slim = add(new BoolSetting("Slim Arms", false));

	private static final Map<String, Identifier> LOADED = new HashMap<>();
	private static final Map<String, String> FAILED = new HashMap<>();

	public Wardrobe() {
		super("Wardrobe", "Custom skins (only you see them)", Category.CLIENT, true);
	}

	public static Wardrobe get() {
		WhiteClient wc = WhiteClient.get();
		return wc == null ? null : wc.modules().get(Wardrobe.class);
	}

	public static Path folder() {
		Path p = FabricLoader.getInstance().getConfigDir().resolve("whiteclient").resolve("skins");
		try {
			Files.createDirectories(p);
		} catch (Exception ignored) {
		}
		return p;
	}

	/** Skin file names without ".png", sorted. */
	public static List<String> available() {
		List<String> out = new ArrayList<>();
		try (Stream<Path> s = Files.list(folder())) {
			s.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
					.forEach(f -> {
						String n = f.getFileName().toString();
						out.add(n.substring(0, n.length() - 4));
					});
		} catch (Exception ignored) {
		}
		out.sort(String.CASE_INSENSITIVE_ORDER);
		return out;
	}

	public static String error(String name) {
		return FAILED.get(name);
	}

	/** Forget cached textures so edited PNGs are picked up again. */
	public static void reload() {
		MinecraftClient mc = MinecraftClient.getInstance();
		for (Identifier id : LOADED.values()) {
			try {
				mc.getTextureManager().destroyTexture(id);
				mc.getTextureManager().destroyTexture(texturePath(id));
			} catch (Throwable ignored) {
			}
		}
		LOADED.clear();
		FAILED.clear();
	}

	/** The real texture to draw for a skin file (for face icons), or null. */
	public static Identifier texturePathFor(String name) {
		Identifier id = texture(name);
		return id == null ? null : texturePath(id);
	}

	private static Identifier texturePath(Identifier id) {
		return Identifier.of(id.getNamespace(), "textures/" + id.getPath() + ".png");
	}

	private static String sanitize(String name) {
		StringBuilder b = new StringBuilder();
		for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
			b.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' ? c : '_');
		}
		return b.toString();
	}

	/** Loads (once) and returns the texture id for a skin file, or null if it can't be used. */
	public static Identifier texture(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		Identifier cached = LOADED.get(name);
		if (cached != null || FAILED.containsKey(name)) {
			return cached;
		}
		Path file = folder().resolve(name + ".png");
		try (InputStream in = Files.newInputStream(file)) {
			NativeImage img = NativeImage.read(in);
			if (img.getWidth() != 64 || (img.getHeight() != 64 && img.getHeight() != 32)) {
				img.close();
				FAILED.put(name, "needs a 64x64 skin PNG");
				return null;
			}
			if (img.getHeight() == 32) {
				img = upgradeLegacy(img);
			}
			Identifier id = Identifier.of(WhiteClient.MOD_ID, "skins/" + sanitize(name));
			NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "whiteclient_skin_" + name, img);
			MinecraftClient mc = MinecraftClient.getInstance();
			mc.getTextureManager().registerTexture(texturePath(id), tex);
			LOADED.put(name, id);
			return id;
		} catch (Throwable t) {
			FAILED.put(name, "couldn't read file");
			WhiteClient.LOGGER.warn("[WHITECLIENT] skin {} failed: {}", name, t.toString());
			return null;
		}
	}

	/** Old 64x32 skins: copy the right arm / leg to the left side like vanilla does. */
	private static NativeImage upgradeLegacy(NativeImage old) {
		NativeImage img = new NativeImage(64, 64, true);
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 64; x++) {
				img.setColorArgb(x, y, old.getColorArgb(x, y));
			}
		}
		for (int y = 16; y < 32; y++) {
			for (int x = 0; x < 16; x++) {
				img.setColorArgb(x + 16, y + 32, old.getColorArgb(x, y)); // leg
			}
			for (int x = 40; x < 56; x++) {
				img.setColorArgb(x - 8, y + 32, old.getColorArgb(x, y)); // arm
			}
		}
		old.close();
		return img;
	}

	/** Swaps the body texture for the chosen custom skin and adds the chosen WHITECLIENT cape. */
	public static SkinTextures apply(SkinTextures original) {
		if (original == null) {
			return null;
		}
		Identifier body = null;
		Wardrobe w = get();
		if (w != null && w.isEnabled()) {
			body = texture(w.skin.get());
		}
		Identifier cape = null;
		Capes capes = Capes.get();
		if (capes != null && capes.isEnabled()) {
			try {
				cape = capes.texture();
			} catch (Throwable ignored) {
			}
		}
		if (body == null && cape == null) {
			return original;
		}
		try {
			boolean slim = w != null && w.slim.isOn();
			return original.withOverride(SkinTextures.SkinOverride.create(
					body != null ? Optional.of(new AssetInfo.TextureAssetInfo(body)) : Optional.empty(),
					cape != null ? Optional.of(new AssetInfo.TextureAssetInfo(cape)) : Optional.empty(),
					Optional.empty(),
					body != null ? Optional.of(PlayerSkinType.byModelMetadata(slim ? "slim" : "default")) : Optional.empty()));
		} catch (Throwable t) {
			return original;
		}
	}
}
