package com.whiteclient.gui;

import com.whiteclient.WhiteClient;
import com.whiteclient.modules.MenuButtons;
import com.whiteclient.render.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ClickableWidget;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Draws the rounded see-through button background (label is still drawn by vanilla). */
public final class ButtonStyle {
	private static final Map<ClickableWidget, Float> HOVER = new WeakHashMap<>();
	private static final Set<ClickableWidget> ACCENT = Collections.newSetFromMap(new WeakHashMap<>());
	private static long lastNanos = System.nanoTime();
	private static float frameDt;

	private ButtonStyle() {
	}

	/** Marks a button (our WHITECLIENT button) to get the bright white accent outline. */
	public static void markAccent(ClickableWidget widget) {
		ACCENT.add(widget);
	}

	public static boolean isAccent(ClickableWidget widget) {
		return ACCENT.contains(widget);
	}

	public static boolean shouldStyle() {
		WhiteClient wc = WhiteClient.get();
		if (wc == null) {
			return false;
		}
		MenuButtons settings = wc.modules().get(MenuButtons.class);
		if (settings == null || !settings.isEnabled()) {
			return false;
		}
		Screen screen = MinecraftClient.getInstance().currentScreen;
		if (screen == null) {
			return false;
		}
		if (settings.where.is("All Menus")) {
			return true;
		}
		return screen instanceof GameMenuScreen || screen instanceof TitleScreen;
	}

	/** A rounded track with a filled part and a light knob, and the label in the middle. */
	public static void drawSlider(DrawContext ctx, ClickableWidget w, double value) {
		MenuButtons settings = WhiteClient.get().modules().get(MenuButtons.class);
		long now = System.nanoTime();
		if (now - lastNanos > 1_000_000) {
			frameDt = Math.min(0.1f, (now - lastNanos) / 1.0e9f);
			lastNanos = now;
		}
		boolean hot = w.active && (w.isHovered() || w.isFocused());
		float h = HOVER.getOrDefault(w, 0f);
		h += ((hot ? 1f : 0f) - h) * Math.min(1f, frameDt * 14f);
		HOVER.put(w, h);

		float x = w.getX(), y = w.getY(), width = w.getWidth(), height = w.getHeight();
		float r = Math.min(settings.roundness.getFloat(), height / 2f);
		float op = settings.opacity.getFloat();
		Render2D.roundRect(ctx, x, y, width, height, r, w.active ? Colors.argb(0x0B0B10, op) : Colors.argb(0x0B0B10, op * 0.5f));
		float v = (float) Math.max(0, Math.min(1, value));
		float knobW = 6;
		float kx = x + 2 + v * (width - 4 - knobW);
		// filled part up to the knob
		if (kx + knobW / 2f > x + 2) {
			Render2D.roundRect(ctx, x + 1, y + 1, kx + knobW / 2f - x - 1, height - 2, Math.max(0, r - 1), Colors.argb(0xFFFFFF, 0.10f + 0.08f * h));
		}
		if (settings.border.isOn()) {
			Render2D.roundRectOutline(ctx, x, y, width, height, r, 1, Colors.argb(0xFFFFFF, 0.16f + 0.3f * h));
		}
		Render2D.roundRect(ctx, kx, y + 2, knobW, height - 4, Math.min(3, (height - 4) / 2f), Colors.argb(0xFFFFFF, w.active ? 0.75f + 0.25f * h : 0.35f));
		net.minecraft.client.font.TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		ctx.drawCenteredTextWithShadow(tr, w.getMessage(), Math.round(x + width / 2f), Math.round(y + (height - 8) / 2f),
				w.active ? 0xFFFFFFFF : 0xFFA0A0A8);
	}

	public static void draw(DrawContext ctx, ClickableWidget w) {
		MenuButtons settings = WhiteClient.get().modules().get(MenuButtons.class);
		long now = System.nanoTime();
		if (now - lastNanos > 1_000_000) {
			frameDt = Math.min(0.1f, (now - lastNanos) / 1.0e9f);
			lastNanos = now;
		}
		boolean hot = w.active && (w.isHovered() || w.isFocused());
		float h = HOVER.getOrDefault(w, 0f);
		h += ((hot ? 1f : 0f) - h) * Math.min(1f, frameDt * 14f);
		HOVER.put(w, h);

		float x = w.getX();
		float y = w.getY();
		float width = w.getWidth();
		float height = w.getHeight();
		float r = Math.min(settings.roundness.getFloat(), height / 2f);
		float op = settings.opacity.getFloat();
		boolean accent = ACCENT.contains(w);

		int base = Colors.argb(0x0B0B10, op);
		int hover = Colors.argb(0xFFFFFF, 0.20f + op * 0.12f);
		int fill = w.active ? Colors.mixArgb(base, hover, h) : Colors.argb(0x0B0B10, op * 0.5f);
		Render2D.roundRect(ctx, x, y, width, height, r, fill);
		if (settings.sheen.isOn()) {
			Render2D.roundRectGradient(ctx, x, y, width, height * 0.5f, r, Colors.argb(0xFFFFFF, 0.10f + 0.06f * h), 0x00FFFFFF);
		}
		if (settings.border.isOn() || accent) {
			float a = accent ? 0.75f + 0.25f * h : (w.active ? 0.22f + 0.6f * h : 0.08f);
			Render2D.roundRectOutline(ctx, x, y, width, height, r, accent ? 1.2f : 1f, Colors.argb(0xFFFFFF, a));
		}
	}
}
