package com.whiteclient.modules;

import com.whiteclient.WhiteClient;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.NumberSetting;
import com.whiteclient.setting.TextSetting;

/** Every colour of the WHITECLIENT menus. */
public class Theme extends Module {
	public final ColorSetting accent = add(new ColorSetting("Accent", 0xFFFFFF));
	public final ColorSetting background = add(new ColorSetting("Background", 0x09090D));
	public final ColorSetting panel = add(new ColorSetting("Panel", 0x111116));
	public final ColorSetting card = add(new ColorSetting("Card", 0x1A1A21));
	public final ColorSetting text = add(new ColorSetting("Text", 0xF4F4F6));
	public final ColorSetting subtext = add(new ColorSetting("Subtext", 0x8C8C98));
	public final ColorSetting toggleOn = add(new ColorSetting("Toggle On", 0xFFFFFF));
	public final NumberSetting opacity = add(new NumberSetting("Panel Opacity", 0.94, 0.5, 1.0, 0.01));
	public final NumberSetting roundness = add(new NumberSetting("Roundness", 9, 0, 14, 0.5));
	public final BoolSetting stars = add(new BoolSetting("Home Stars", true));
	/** Cards fly out of one stack when the menu opens, and back into it when it closes. */
	public final BoolSetting cardAnimations = add(new BoolSetting("Card Stack Animations", true));

	private static final Theme FALLBACK = new Theme();

	public Theme() {
		super("Theme", "Change every colour of the menus", Category.CLIENT, true);
	}

	public static Theme get() {
		WhiteClient wc = WhiteClient.get();
		Theme t = wc == null ? null : wc.modules().get(Theme.class);
		return t == null ? FALLBACK : t;
	}

	public int accent(float a) {
		return Colors.argb(accent.rgb(), a);
	}

	public int panel() {
		return Colors.argb(panel.rgb(), opacity.getFloat());
	}

	public int bg(float a) {
		return Colors.argb(background.rgb(), a);
	}

	public int card(float a) {
		return Colors.argb(card.rgb(), a);
	}

	public int text() {
		return text.argb();
	}

	public int sub() {
		return subtext.argb();
	}

	/** Perceived brightness 0..1 of an RGB colour. */
	public static float lum(int c) {
		return (((c >> 16) & 0xFF) * 0.299f + ((c >> 8) & 0xFF) * 0.587f + (c & 0xFF) * 0.114f) / 255f;
	}

	/** Colour that stays readable on top of the accent colour. */
	public int onAccent() {
		return lum(accent.rgb()) > 0.6f ? 0xFF0B0B0F : 0xFFFFFFFF;
	}
}
