package com.whiteclient.modules;

import com.mojang.blaze3d.platform.NativeImage;
import com.whiteclient.WhiteClient;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.TextSetting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
//#if MC >= 1.21.9
import net.minecraft.core.ClientAsset;
//#endif
import net.minecraft.resources.Identifier;
//#if MC >= 1.21.9
import net.minecraft.world.entity.player.PlayerModelType;
//#endif
import net.minecraft.world.entity.player.PlayerSkin;
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

	public static void reload() {
		Minecraft mc = Minecraft.getInstance();
		for (Identifier id : LOADED.values()) {
			try {
				mc.getTextureManager().release(id);
				mc.getTextureManager().release(texturePath(id));
			} catch (Throwable ignored) {
			}
		}
		LOADED.clear();
		FAILED.clear();
	}

	public static Identifier texturePathFor(String name) {
		Identifier id = texture(name);
		return id == null ? null : texturePath(id);
	}

	private static Identifier texturePath(Identifier id) {
		return Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/" + id.getPath() + ".png");
	}

	private static String sanitize(String name) {
		StringBuilder b = new StringBuilder();
		for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
			b.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' ? c : '_');
		}
		return b.toString();
	}

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
			Identifier id = Identifier.fromNamespaceAndPath(WhiteClient.MOD_ID, "skins/" + sanitize(name));
			DynamicTexture tex = new DynamicTexture(() -> "whiteclient_skin_" + name, img);
			Minecraft mc = Minecraft.getInstance();
			mc.getTextureManager().register(texturePath(id), tex);
			LOADED.put(name, id);
			return id;
		} catch (Throwable t) {
			FAILED.put(name, "couldn't read file");
			WhiteClient.LOGGER.warn("[WHITECLIENT] skin {} failed: {}", name, t.toString());
			return null;
		}
	}

	private static NativeImage upgradeLegacy(NativeImage old) {
		NativeImage img = new NativeImage(64, 64, true);
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 64; x++) {
				img.setPixel(x, y, old.getPixel(x, y));
			}
		}
		for (int y = 16; y < 32; y++) {
			for (int x = 0; x < 16; x++) {
				img.setPixel(x + 16, y + 32, old.getPixel(x, y));
			}
			for (int x = 40; x < 56; x++) {
				img.setPixel(x - 8, y + 32, old.getPixel(x, y));
			}
		}
		old.close();
		return img;
	}

	public static PlayerSkin apply(PlayerSkin original) {
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
			//#if MC >= 1.21.9
			return original.with(PlayerSkin.Patch.create(
					body != null ? Optional.of(new ClientAsset.ResourceTexture(body)) : Optional.empty(),
					cape != null ? Optional.of(new ClientAsset.ResourceTexture(cape)) : Optional.empty(),
					Optional.empty(),
					body != null ? Optional.of(PlayerModelType.byLegacyServicesName(slim ? "slim" : "default")) : Optional.empty()));
			//#else
			//$ return new PlayerSkin(body != null ? body : original.texture(), body != null ? null : original.textureUrl(),
			//$ 		cape != null ? cape : original.capeTexture(), original.elytraTexture(),
			//$ 		body != null ? (slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE) : original.model(), original.secure());
			//#endif
		} catch (Throwable t) {
			return original;
		}
	}
}
