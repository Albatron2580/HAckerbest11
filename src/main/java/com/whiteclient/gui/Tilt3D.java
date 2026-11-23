package com.whiteclient.gui;

import com.whiteclient.mixin.HandledScreenAccessor;
import com.whiteclient.modules.InventoryTheme;
import com.whiteclient.render.Colors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.Window;
import org.joml.Matrix3f;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

/**
 * 3D inventories: the container panel is drawn as a thick, tilted slab floating over the world, with a
 * swing-in animation when it opens and a gentle tilt that follows the mouse.
 *
 * <p>The tilt is a real 3D rotation seen straight on (no vanishing point), which keeps it an exact 2D
 * transform: everything the inventory draws (slots, items, text, buttons of other mods) is tilted the same
 * way, and the mouse is mapped back through the inverse, so every slot is clicked exactly where you see it.
 */
public final class Tilt3D {
	private static Screen screen;
	private static long openedAt, lastNs;
	private static float followX, followY;

	/** The transform is valid for the screen being drawn / clicked right now. */
	private static boolean active;
	/** True while the panel is being drawn inside the tilt (tooltips drawn later are mapped instead). */
	public static boolean inside;

	// real = C + A * (virtual - C)  with A = [a c; b d]
	private static float a = 1, b, c, d = 1, cx, cy;
	private static float depthX, depthY;

	private static Boolean blocked;

	private Tilt3D() {
	}

	/** Item list mods draw beside the inventory and expect the normal mouse position: keep the inventory flat then. */
	private static boolean blocked() {
		if (blocked == null) {
			FabricLoader l = FabricLoader.getInstance();
			blocked = l.isModLoaded("roughlyenoughitems") || l.isModLoaded("jei") || l.isModLoaded("emi");
		}
		return blocked;
	}

	public static boolean wants(Screen s) {
		if (!(s instanceof HandledScreen<?>) || blocked()) {
			return false;
		}
		InventoryTheme t = InventoryTheme.get();
		return t != null && t.isEnabled() && t.tilt.isOn();
	}

	public static boolean active(Screen s) {
		return active && s == screen && s != null;
	}

	/** True when the mouse should be mapped for the screen that is open now. */
	public static boolean mapsInput() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return active && screen != null && mc.currentScreen == screen;
	}

	/** Called once per frame before the screen draws. */
	public static void frame(Screen s) {
		if (!wants(s)) {
			active = false;
			screen = null;
			return;
		}
		long now = System.nanoTime();
		if (s != screen) {
			screen = s;
			openedAt = now;
			followX = followY = 0;
			lastNs = now;
		}
		float dt = Math.min(0.1f, (now - lastNs) / 1.0e9f);
		lastNs = now;
		InventoryTheme t = InventoryTheme.get();
		float strength = t.tiltStrength.getFloat();

		MinecraftClient mc = MinecraftClient.getInstance();
		Window w = mc.getWindow();
		double mx = mc.mouse.getX() * w.getScaledWidth() / Math.max(1, w.getWidth());
		double my = mc.mouse.getY() * w.getScaledHeight() / Math.max(1, w.getHeight());
		float tx = 0, ty = 0;
		if (t.followMouse.isOn()) {
			tx = (float) Math.max(-0.5, Math.min(0.5, mx / Math.max(1, s.width) - 0.5));
			ty = (float) Math.max(-0.5, Math.min(0.5, my / Math.max(1, s.height) - 0.5));
		}
		float k = Math.min(1f, dt * 5f);
		followX += (tx - followX) * k;
		followY += (ty - followY) * k;

		float open = Math.min(1f, (now - openedAt) / 1.0e9f / 0.38f);
		float e = 1f - (1f - open) * (1f - open) * (1f - open);

		float yaw = (float) Math.toRadians((-22f + followX * 10f) * strength + (1f - e) * 34f);
		float pitch = (float) Math.toRadians((14f - followY * 8f) * strength - (1f - e) * 16f);
		float roll = (float) Math.toRadians(-3.2f * strength * e);
		float scale = 0.96f * (0.9f + 0.1f * e);
		Matrix3f r = new Matrix3f().rotateZ(roll).rotateX(pitch).rotateY(yaw);
		a = r.m00 * scale;
		b = r.m01 * scale;
		c = r.m10 * scale;
		d = r.m11 * scale;
		// the back face of the slab, as seen on screen
		float thick = 13f * scale * Math.max(0.4f, strength);
		depthX = -r.m20 * thick;
		depthY = -r.m21 * thick;

		if (s instanceof HandledScreenAccessor h) {
			cx = h.whiteclient$x() + h.whiteclient$backgroundWidth() / 2f;
			cy = h.whiteclient$y() + h.whiteclient$backgroundHeight() / 2f;
		} else {
			cx = s.width / 2f;
			cy = s.height / 2f;
		}
		active = true;
	}

	public static void push(DrawContext ctx) {
		Matrix3x2fStack m = ctx.getMatrices();
		m.pushMatrix();
		m.translate(cx, cy);
		m.mul(new Matrix3x2f(a, b, c, d, 0, 0));
		m.translate(-cx, -cy);
	}

	public static void pop(DrawContext ctx) {
		ctx.getMatrices().popMatrix();
	}

	// ------------------------------------------------------------------ mapping

	public static double toVirtualX(double rx, double ry) {
		double det = a * d - b * c;
		return cx + (d * (rx - cx) - c * (ry - cy)) / det;
	}

	public static double toVirtualY(double rx, double ry) {
		double det = a * d - b * c;
		return cy + (-b * (rx - cx) + a * (ry - cy)) / det;
	}

	public static double toRealX(double vx, double vy) {
		return cx + a * (vx - cx) + c * (vy - cy);
	}

	public static double toRealY(double vx, double vy) {
		return cy + b * (vx - cx) + d * (vy - cy);
	}

	// ------------------------------------------------------------------ slab

	/** Thickness and a soft shadow under the panel, drawn inside the tilt right before the panel itself. */
	public static void drawSlab(DrawContext ctx, Screen s) {
		if (!(s instanceof HandledScreenAccessor h)) {
			return;
		}
		InventoryTheme t = InventoryTheme.get();
		float x = h.whiteclient$x(), y = h.whiteclient$y();
		float w = h.whiteclient$backgroundWidth(), hh = h.whiteclient$backgroundHeight();
		double det = a * d - b * c;
		// screen-space offsets turned into panel space
		float sx = (float) ((d * depthX - c * depthY) / det), sy = (float) ((-b * depthX + a * depthY) / det);
		float shX = (float) ((d * 10 - c * 18) / det), shY = (float) ((-b * 10 + a * 18) / det);

		// soft shadow on the world behind
		for (int i = 7; i >= 1; i--) {
			float g = i * 2.6f;
			Render2D.roundRect(ctx, x + shX - g, y + shY - g, w + g * 2, hh + g * 2, 6 + g, Colors.argb(0x000000, 0.06f));
		}
		// the slab's thickness: dark layers from the back to the front, with a faint light rim at the back
		int panel = t != null ? t.panel.rgb() : 0x16161D;
		int edge = t != null ? t.edge.rgb() : 0xFFFFFF;
		int layers = 12;
		for (int i = layers; i >= 1; i--) {
			float f = i / (float) layers;
			float ox = sx * f, oy = sy * f;
			int col = Colors.mix(Colors.mix(panel, 0xFFFFFF, 0.06f), 0x000000, 0.2f + 0.55f * f);
			Render2D.roundRect(ctx, x + ox, y + oy, w, hh, 4, Colors.argb(col, 0.96f));
			if (i == layers) {
				Render2D.roundRectOutline(ctx, x + ox, y + oy, w, hh, 4, 1, Colors.argb(edge, 0.22f));
			}
		}
		Render2D.roundRectOutline(ctx, x - 0.5f, y - 0.5f, w + 1, hh + 1, 4.5f, 1, Colors.argb(edge, 0.3f));
	}

	/** Light falling on the front of the panel: a soft sheen from the top and a bright top edge. Drawn over the background, under the items. */
	public static void drawSheen(DrawContext ctx, Screen s) {
		if (!(s instanceof HandledScreenAccessor h)) {
			return;
		}
		InventoryTheme t = InventoryTheme.get();
		int edge = t != null ? t.edge.rgb() : 0xFFFFFF;
		int x = h.whiteclient$x(), y = h.whiteclient$y();
		int w = h.whiteclient$backgroundWidth(), hh = h.whiteclient$backgroundHeight();
		ctx.fillGradient(x + 2, y + 2, x + w - 2, y + (int) (hh * 0.42f), Colors.argb(edge, 0.075f), 0x00000000);
		Render2D.roundRect(ctx, x + 4, y + 0.5f, w - 8, 1f, 0.5f, Colors.argb(edge, 0.45f));
		Render2D.roundRect(ctx, x + 0.5f, y + 4, 1f, hh * 0.6f, 0.5f, Colors.argb(edge, 0.25f));
	}
}
