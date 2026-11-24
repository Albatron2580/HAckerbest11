package com.whiteclient.modules;

import com.whiteclient.WhiteClient;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InventoryTheme extends Module {
	public final ColorSetting panel = add(new ColorSetting("Panel Color", 0x16161D));
	public final ColorSetting slot = add(new ColorSetting("Slot Color", 0x0B0B10));
	public final ColorSetting edge = add(new ColorSetting("Edge Color", 0xFFFFFF));
	public final NumberSetting opacity = add(new NumberSetting("Opacity", 0.9, 0.4, 1.0, 0.02));
	public final NumberSetting edgeGlow = add(new NumberSetting("Edge Glow", 0.18, 0.0, 0.6, 0.02));
	public final BoolSetting lightTitles = add(new BoolSetting("Light Titles", true));
	public final BoolSetting tooltips = add(new BoolSetting("Themed Tooltips", true));

	private static final String[] TEXTURES = {
			"inventory", "generic_54", "crafting_table", "furnace", "blast_furnace", "smoker", "shulker_box", "dispenser",
			"hopper", "anvil", "enchanting_table", "brewing_stand", "grindstone", "stonecutter", "cartography_table", "loom",
			"smithing", "beacon", "horse", "villager", "crafter", "gamemode_switcher",
			"creative_inventory/tab_items", "creative_inventory/tab_inventory", "creative_inventory/tab_item_search"
	};

	private final List<Identifier> applied = new ArrayList<>();
	private String appliedKey = "";

	public InventoryTheme() {
		super("Inventory Theme", "Chests and inventories in your WHITECLIENT colours and opacity", Category.MENU, true);
	}

	public static InventoryTheme get() {
		WhiteClient wc = WhiteClient.get();
		return wc == null ? null : wc.modules().get(InventoryTheme.class);
	}

	public static boolean themedTooltips() {
		InventoryTheme t = get();
		return t != null && t.isEnabled() && t.tooltips.isOn();
	}

	public static void drawTooltip(net.minecraft.client.gui.DrawContext ctx, int x, int y, int w, int h) {
		InventoryTheme t = get();
		int panel = t != null ? com.whiteclient.render.Colors.mix(t.panel.rgb(), 0x000000, 0.35f) : 0x0E0E13;
		int edge = t != null ? t.edge.rgb() : 0xFFFFFF;
		float px = x - 4, py = y - 4, pw = w + 8, ph = h + 8;
		com.whiteclient.gui.Render2D.roundRect(ctx, px, py, pw, ph, 4, com.whiteclient.render.Colors.argb(panel, 0.94f));
		com.whiteclient.gui.Render2D.roundRectOutline(ctx, px, py, pw, ph, 4, 1, com.whiteclient.render.Colors.argb(edge, 0.32f));
	}

	public static Integer titleColor() {
		InventoryTheme t = get();
		if (t == null || !t.isEnabled() || !t.lightTitles.isOn()) {
			return null;
		}
		return 0xFFE9E9EF;
	}

	private String key() {
		return panel.rgb() + ":" + slot.rgb() + ":" + edge.rgb() + ":" + opacity.get() + ":" + edgeGlow.get();
	}

	public static void ensure() {
		InventoryTheme t = get();
		if (t == null) {
			return;
		}
		try {
			if (t.isEnabled()) {
				if (!t.key().equals(t.appliedKey)) {
					t.apply();
				}
			} else if (!t.applied.isEmpty()) {
				t.restore();
			}
		} catch (Throwable e) {
			WhiteClient.LOGGER.warn("[WHITECLIENT] inventory theme failed", e);
		}
	}

	@Override
	protected void onDisable() {
		try {
			restore();
		} catch (Throwable ignored) {
		}
	}

	private void restore() {
		MinecraftClient mc = MinecraftClient.getInstance();
		for (Identifier id : applied) {
			try {
				mc.getTextureManager().destroyTexture(id);
			} catch (Throwable ignored) {
			}
		}
		applied.clear();
		appliedKey = "";
	}

	private void apply() throws Exception {
		MinecraftClient mc = MinecraftClient.getInstance();
		restore();
		for (String name : TEXTURES) {
			Identifier id = Identifier.ofVanilla("textures/gui/container/" + name + ".png");
			Optional<Resource> res = mc.getResourceManager().getResource(id);
			if (res.isEmpty()) {
				continue;
			}
			NativeImage img;
			try (InputStream in = res.get().getInputStream()) {
				img = NativeImage.read(in);
			}
			recolor(img);
			NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "whiteclient_theme_" + name, img);
			mc.getTextureManager().registerTexture(id, tex);
			applied.add(id);
		}
		appliedKey = key();
	}

	private static int mix(int a, int b, float t) {
		int r = (int) (((a >> 16) & 0xFF) + ((((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t));
		int g = (int) (((a >> 8) & 0xFF) + ((((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t));
		int bl = (int) ((a & 0xFF) + (((b & 0xFF) - (a & 0xFF)) * t));
		return (r << 16) | (g << 8) | bl;
	}

	private void recolor(NativeImage img) {
		int p = panel.rgb(), s = slot.rgb();
		float op = opacity.getFloat();
		float eg = edgeGlow.getFloat();
		int highlight = mix(p, edge.rgb(), eg);
		int dark = mix(p, 0x000000, 0.55f);
		int slotShadow = mix(s, 0x000000, 0.45f);
		int slotLight = mix(s, edge.rgb(), eg * 0.45f);
		int[] grey = {0, 55, 85, 139, 198, 255};
		int[] rgb = {mix(p, edge.rgb(), eg * 0.6f), slotShadow, dark, s, p, highlight};
		float[] alpha = {Math.min(1f, op + 0.05f), Math.min(1f, op + 0.08f), op, Math.min(1f, op + 0.06f), op, op};
		int w = img.getWidth(), h = img.getHeight();
		int[] orig = new int[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				orig[y * w + x] = img.getColorArgb(x, y);
			}
		}
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int c = orig[y * w + x];
				int a = (c >>> 24) & 0xFF;
				if (a == 0) {
					continue;
				}
				int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
				int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
				if (max - min > 14) {
					continue;
				}
				int v = (r + g + b) / 3;
				int k = 0;
				while (k < grey.length - 2 && v > grey[k + 1]) {
					k++;
				}
				float t = Math.max(0f, Math.min(1f, (v - grey[k]) / (float) (grey[k + 1] - grey[k])));
				int out = mix(rgb[k], rgb[k + 1], t);
				if (k == 4 && t > 0.98f && isSlotEdge(orig, w, h, x, y)) {
					out = slotLight;
				}
				float al = alpha[k] + (alpha[k + 1] - alpha[k]) * t;
				int na = Math.round(a * al);
				img.setColorArgb(x, y, (na << 24) | out);
			}
		}
	}

	private static boolean isSlotEdge(int[] px, int w, int h, int x, int y) {
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				int nx = x + dx, ny = y + dy;
				if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
					continue;
				}
				int c = px[ny * w + nx];
				int v = (((c >> 16) & 0xFF) + ((c >> 8) & 0xFF) + (c & 0xFF)) / 3;
				if (Math.abs(v - 139) < 6) {
					return true;
				}
			}
		}
		return false;
	}
}
