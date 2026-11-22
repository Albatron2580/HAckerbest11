package com.whiteclient.gui;

import com.whiteclient.WhiteClient;
import com.whiteclient.modules.MainMenu;
import com.whiteclient.modules.Wardrobe;
import com.whiteclient.music.NowPlaying;
import com.whiteclient.render.Colors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerWarningScreen;
import net.minecraft.client.gui.screen.option.LanguageOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.realms.gui.screen.RealmsMainScreen;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** WHITECLIENT main menu: animated background, logo, rounded buttons and an icon dock. */
public class WhiteTitleScreen extends Screen {
	private static final int BTN = 0xA60E0E14;
	private static final int BTN_HOVER = 0xCC1E1E26;
	private static final int TEXT = 0xFFF4F4F7;
	private static final int SUB = 0xFF9A9AA6;

	private final Map<String, Float> anims = new HashMap<>();
	private final List<Hit> hits = new ArrayList<>();
	private long opened = System.nanoTime();
	private long lastFrame = System.nanoTime();
	private Supplier<SkinTextures> skin;
	private SkinPreview walker;
	private int walkerW, walkerH;
	/** Screenshot tests: fixed seconds into the walk-in animation (NaN = real time). */
	public static float debugWalkTime = Float.NaN;
	/** Direction the model faces while walking in (degrees for the skin widget). */
	public static float walkYaw = 90f;

	private record Hit(float x1, float y1, float x2, float y2, Runnable action) {
	}

	@FunctionalInterface
	private interface IconPainter {
		void paint(DrawContext ctx, float cx, float cy, float size, int color);
	}

	public WhiteTitleScreen() {
		super(Text.translatable("narrator.screen.title"));
	}

	@Override
	protected void init() {
		try {
			skin = client.getSkinProvider().supplySkinTextures(client.getGameProfile(), false);
		} catch (Throwable t) {
			skin = null;
		}
		walkerH = (int) Math.min(200, height * 0.6f);
		walkerW = (int) (walkerH * 0.6f);
		try {
			walker = new SkinPreview(walkerW, walkerH, () -> {
				SkinTextures base = null;
				try {
					base = skin != null ? skin.get() : null;
				} catch (Throwable ignored) {
				}
				if (base == null) {
					base = DefaultSkinHelper.getSteve();
				}
				return Wardrobe.apply(base);
			});
		} catch (Throwable t) {
			walker = null;
		}
	}

	/** The player model walks in from the side, turns to you and idles (photo 1). */
	private void drawWalker(DrawContext ctx, int mouseX, int mouseY, float delta, float time, MainMenu settings) {
		if (walker == null || (settings != null && !settings.player.isOn())) {
			return;
		}
		boolean left = settings != null && settings.playerSide.is("Left");
		// stand in the free space next to the buttons
		float buttonsHalf = Math.min(214, width - 40) / 2f;
		float free = width / 2f - buttonsHalf;
		if (free < walkerW * 0.8f + 8) {
			return;
		}
		float t = Float.isNaN(debugWalkTime) ? (System.nanoTime() - opened) / 1.0e9f - 0.35f : debugWalkTime;
		float walkDur = 2.1f;
		float targetCx = left ? free / 2f : width - free / 2f;
		float startCx = left ? -walkerW : width + walkerW;
		float u = Math.max(0f, Math.min(1f, t / walkDur));
		float ease = 1f - (1f - u) * (1f - u);
		float cxp = startCx + (targetCx - startCx) * ease;
		float walk = u < 1f ? Math.min(1f, (1f - u) * 4f + 0.25f) : 0f;
		float turn = Math.max(0f, Math.min(1f, (t - walkDur + 0.15f) / 0.55f));
		turn = turn * turn * (3 - 2 * turn);
		float faceYaw = left ? 22f : -22f;
		float mouseLook = ((mouseX - cxp) / (float) width) * 26f * turn;
		float yaw = (left ? -walkYaw : walkYaw) * (1f - turn) + (faceYaw + mouseLook) * turn;
		float distance = (startCx - cxp);
		float phase = Math.abs(distance) / 7.5f;
		walker.setRotation(-4f * turn, yaw);
		walker.pose(phase, walk, time);
		int x = Math.round(cxp - walkerW / 2f);
		int y = Math.round(height - walkerH - 34 + (float) Math.abs(Math.sin(phase)) * -1.5f * walk);
		// soft floor shadow + glow
		float sa = Math.min(1f, Math.max(0f, t / 0.6f));
		for (int i = 4; i >= 1; i--) {
			Render2D.roundRect(ctx, cxp - walkerW * 0.18f * i, y + walkerH - 6 - i, walkerW * 0.36f * i, 3 + 2 * i, 4 + i, Colors.argb(0x000000, 0.08f * sa));
		}
		walker.render(ctx, x, y, mouseX, mouseY, delta);
		// name tag above the head
		if (turn > 0.01f) {
			String name = client.getSession().getUsername();
			float nw = UiFont.width(textRenderer, name) + 14;
			float ny = y - 4 - 6 * (1 - turn);
			Render2D.roundRect(ctx, cxp - nw / 2f, ny, nw, 14, 7, Colors.mulAlpha(BTN, turn));
			Render2D.roundRectOutline(ctx, cxp - nw / 2f, ny, nw, 14, 7, 1, Colors.withAlpha(0xFFFFFF, (int) (50 * turn)));
			UiFont.drawCentered(ctx, textRenderer, name, cxp, ny + 3, Colors.withAlpha(TEXT, (int) (255 * turn)));
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private float anim(String key, float target, float dt) {
		float v = anims.getOrDefault(key, 0f);
		v += (target - v) * Math.min(1f, dt * 12f);
		anims.put(key, v);
		return v;
	}

	private static boolean in(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	/** 0..1 intro progress for an element that starts after {@code delay} seconds. */
	private float intro(float delay) {
		float t = (System.nanoTime() - opened) / 1.0e9f - delay;
		float k = Math.max(0f, Math.min(1f, t / 0.45f));
		return 1f - (1f - k) * (1f - k) * (1f - k);
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		MainMenu settings = WhiteClient.get().modules().get(MainMenu.class);
		if (settings != null && settings.background.is("Panorama")) {
			renderPanoramaBackground(ctx, delta);
			applyBlur(ctx);
		} else {
			MenuBackgroundRenderer.draw(ctx, width, height, 1f);
		}
		ctx.fillGradient(0, 0, width, height, 0x55000000, 0xAA000000);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1.0e9f);
		lastFrame = now;
		float time = WhiteClient.get().time();
		hits.clear();

		float fade = intro(0f);
		if (fade < 1f) {
			ctx.fill(0, 0, width, height, Colors.argb(0x000000, 1f - fade));
		}

		float cx = width / 2f;
		float logoY = Math.max(40, height * 0.27f);

		// ---- logo
		float li = intro(0.1f);
		Matrix3x2fStack m = ctx.getMatrices();
		m.pushMatrix();
		m.translate(0, (1 - li) * 10 + (float) Math.sin(time * 1.3) * 1.2f);
		String word = "WHITECLIENT";
		float tw = textRenderer.getWidth(UiFont.title(word));
		float logoW = 38;
		float total = logoW + 6 + tw;
		float lx = cx - total / 2f;
		int la = (int) (255 * li);
		Render2D.planetLogo(ctx, lx + logoW / 2f, logoY, 8.5f, time, la);
		ctx.drawText(textRenderer, UiFont.title(word), Math.round(lx + logoW + 6), Math.round(logoY + 3), Colors.withAlpha(0xFFFFFF, la), false);
		String tag = "visuals client  ·  1.21.11";
		UiFont.drawCentered(ctx, textRenderer, tag, cx, logoY + 16, Colors.withAlpha(SUB, la));
		m.popMatrix();

		// ---- main buttons
		float bw = Math.min(214, width - 40);
		float bh = 22;
		float by = logoY + 38;
		boolean mp = client.isMultiplayerEnabled();
		mainButton(ctx, "single", "Singleplayer", Icons::person, cx - bw / 2f, by, bw, bh, mouseX, mouseY, dt, false, 0.18f,
				() -> client.setScreen(new SelectWorldScreen(this)));
		mainButton(ctx, "multi", "Multiplayer", (c, x, y, s, col) -> Icons.people(c, x, y, s, col, Colors.mixArgb(col, 0xFF000000, 0.45f)),
				cx - bw / 2f, by + (bh + 6), bw, bh, mouseX, mouseY, dt, false, 0.24f,
				() -> {
					if (mp) {
						client.setScreen(client.options.skipMultiplayerWarning ? new MultiplayerScreen(this) : new MultiplayerWarningScreen(this));
					}
				});
		mainButton(ctx, "wc", "WHITECLIENT", (c, x, y, s, col) -> Render2D.logo(c, x, y, s * 0.42f, time),
				cx - bw / 2f, by + (bh + 6) * 2, bw, bh, mouseX, mouseY, dt, true, 0.30f,
				() -> client.setScreen(new ClickGuiScreen(this)));

		// ---- icon dock
		boolean modMenu = FabricLoader.getInstance().isModLoaded("modmenu");
		List<Object[]> dock = new ArrayList<>();
		dock.add(new Object[]{"Options", (IconPainter) Icons::gear, (Runnable) () -> client.setScreen(new OptionsScreen(this, client.options))});
		dock.add(new Object[]{"Language", (IconPainter) Icons::globe, (Runnable) () -> client.setScreen(new LanguageOptionsScreen(this, client.options, client.getLanguageManager()))});
		dock.add(new Object[]{"Realms", (IconPainter) Icons::cloud, (Runnable) () -> client.setScreen(new RealmsMainScreen(this))});
		if (modMenu) {
			dock.add(new Object[]{"Mods", (IconPainter) Icons::list, (Runnable) this::openModMenu});
		}
		dock.add(new Object[]{"Quit", (IconPainter) Icons::power, (Runnable) () -> client.scheduleStop()});
		float ds = 22;
		float gap = 7;
		float dockW = dock.size() * ds + (dock.size() - 1) * gap;
		float dx = cx - dockW / 2f;
		float dy = by + (bh + 6) * 3 + 14;
		float di = intro(0.38f);
		String tooltip = null;
		float tipX = 0;
		for (int i = 0; i < dock.size(); i++) {
			Object[] d = dock.get(i);
			float x = dx + i * (ds + gap);
			float y = dy + (1 - di) * 8;
			boolean hover = in(mouseX, mouseY, x, y, ds, ds);
			float h = anim("dock" + i, hover ? 1f : 0f, dt);
			int a = (int) (255 * di);
			Render2D.roundRect(ctx, x, y - h, ds, ds, 6, Colors.mulAlpha(Colors.mixArgb(BTN, BTN_HOVER, h), di));
			Render2D.roundRectOutline(ctx, x, y - h, ds, ds, 6, 1, Colors.withAlpha(0xFFFFFF, (int) (a * (0.10f + 0.4f * h))));
			((IconPainter) d[1]).paint(ctx, x + ds / 2f, y - h + ds / 2f, 10, Colors.withAlpha(Colors.mix(0xA6A6B2, 0xFFFFFF, h), a));
			hits.add(new Hit(x, y, x + ds, y + ds, (Runnable) d[2]));
			if (hover) {
				tooltip = (String) d[0];
				tipX = x + ds / 2f;
			}
		}
		if (tooltip != null) {
			float tw2 = UiFont.width(textRenderer, tooltip) + 10;
			Render2D.roundRect(ctx, tipX - tw2 / 2f, dy - 18, tw2, 13, 4, 0xE6101016);
			UiFont.drawCentered(ctx, textRenderer, tooltip, tipX, dy - 16, TEXT);
		}

		// ---- account chip (top left)
		MainMenu settings = WhiteClient.get().modules().get(MainMenu.class);
		float ci = intro(0.45f);
		if (settings == null || settings.account.isOn()) {
			String name = client.getSession().getUsername();
			float cw = UiFont.width(textRenderer, name) + 28;
			float cxp = 10 - (1 - ci) * 12;
			Render2D.roundRect(ctx, cxp, 10, cw, 18, 6, Colors.mulAlpha(BTN, ci));
			Render2D.roundRectOutline(ctx, cxp, 10, cw, 18, 6, 1, Colors.withAlpha(0xFFFFFF, (int) (30 * ci)));
			if (skin != null) {
				try {
					PlayerSkinDrawer.draw(ctx, skin.get(), (int) cxp + 4, 13, 12);
				} catch (Throwable ignored) {
				}
			}
			UiFont.draw(ctx, textRenderer, name, cxp + 21, 14, Colors.withAlpha(TEXT, (int) (255 * ci)));
		}

		// ---- now playing pill (top right)
		NowPlaying np = WhiteClient.get().music().current();
		float pi = anim("music", np != null && (settings == null || settings.music.isOn()) ? 1f : 0f, dt * 0.6f);
		if (np != null && pi > 0.02f) {
			String s = np.title() + "  —  " + np.artist();
			int maxW = (int) Math.min(220, width * 0.4f);
			while (UiFont.width(textRenderer, s) > maxW && s.length() > 3) {
				s = s.substring(0, s.length() - 2);
			}
			float pw = UiFont.width(textRenderer, s) + 30;
			float px = width - 10 - pw + (1 - pi) * 14;
			Render2D.roundRect(ctx, px, 10, pw, 18, 9, Colors.mulAlpha(BTN, pi));
			Render2D.roundRectOutline(ctx, px, 10, pw, 18, 9, 1, Colors.withAlpha(0xFFFFFF, (int) (40 * pi)));
			// equalizer bars
			for (int b = 0; b < 3; b++) {
				float hgt = np.playing() ? 3 + 5 * (0.5f + 0.5f * (float) Math.sin(time * (6 + b * 1.7) + b)) : 3;
				Render2D.roundRect(ctx, px + 8 + b * 3.2f, 19 - hgt / 2f, 2, hgt, 1, Colors.withAlpha(0xFFFFFF, (int) (255 * pi)));
			}
			UiFont.draw(ctx, textRenderer, s, px + 22, 14, Colors.withAlpha(TEXT, (int) (255 * pi)));
		}

		// ---- walking player
		try {
			drawWalker(ctx, mouseX, mouseY, delta, time, settings);
		} catch (Throwable t) {
			walker = null;
		}

		// ---- footer
		float fi = intro(0.5f);
		UiFont.draw(ctx, textRenderer, "WHITECLIENT " + WhiteClient.VERSION, 10, height - 24, Colors.withAlpha(TEXT, (int) (200 * fi)));
		UiFont.draw(ctx, textRenderer, "Minecraft 1.21.11  ·  Fabric", 10, height - 14, Colors.withAlpha(SUB, (int) (200 * fi)));
		String copy = "Copyright Mojang AB. Do not distribute!";
		UiFont.draw(ctx, textRenderer, copy, width - 10 - UiFont.width(textRenderer, copy), height - 14, Colors.withAlpha(SUB, (int) (160 * fi)));

		super.render(ctx, mouseX, mouseY, delta);
	}

	private void mainButton(DrawContext ctx, String key, String label, IconPainter icon, float x, float y, float w, float h,
							int mx, int my, float dt, boolean accent, float delay, Runnable action) {
		float k = intro(delay);
		y += (1 - k) * 12;
		boolean hover = in(mx, my, x, y, w, h);
		float hv = anim(key, hover ? 1f : 0f, dt);
		int fill;
		int textColor;
		int iconColor;
		if (accent) {
			fill = Colors.mixArgb(0xEDF2F2F5, 0xFFFFFFFF, hv);
			textColor = 0xFF0B0B0F;
			iconColor = 0xFF0B0B0F;
		} else {
			fill = Colors.mixArgb(BTN, BTN_HOVER, hv);
			textColor = Colors.mixArgb(0xFFD6D6DE, 0xFFFFFFFF, hv);
			iconColor = Colors.mixArgb(0xFF9A9AA6, 0xFFFFFFFF, hv);
		}
		float lift = hv * 1.5f;
		Render2D.roundRect(ctx, x, y - lift, w, h, 6, Colors.mulAlpha(fill, k));
		if (!accent) {
			Render2D.roundRectOutline(ctx, x, y - lift, w, h, 6, 1, Colors.withAlpha(0xFFFFFF, (int) (255 * k * (0.08f + 0.35f * hv))));
		} else if (hv > 0.01f) {
			Render2D.roundRectOutline(ctx, x - 2, y - lift - 2, w + 4, h + 4, 8, 1, Colors.withAlpha(0xFFFFFF, (int) (120 * hv * k)));
		}
		float lw = UiFont.width(textRenderer, label);
		float groupW = 10 + 6 + lw;
		float gx = x + (w - groupW) / 2f;
		if (accent) {
			// logo is white by design, so draw a dark disc behind it on the white button
			Render2D.circle(ctx, gx + 5, y - lift + h / 2f, 6.5f, Colors.withAlpha(0x0B0B0F, (int) (255 * k)));
		}
		icon.paint(ctx, gx + 5, y - lift + h / 2f, 10, Colors.mulAlpha(iconColor, k));
		UiFont.draw(ctx, textRenderer, label, gx + 16, y - lift + (h - 9) / 2f, Colors.mulAlpha(textColor, k));
		hits.add(new Hit(x, y, x + w, y + h, action));
	}

	private void openModMenu() {
		try {
			Class<?> c = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
			client.setScreen((Screen) c.getConstructor(Screen.class).newInstance(this));
		} catch (Throwable t) {
			client.setScreen(new ClickGuiScreen(this));
		}
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (click.button() == 0) {
			for (int i = hits.size() - 1; i >= 0; i--) {
				Hit h = hits.get(i);
				if (click.x() >= h.x1() && click.x() < h.x2() && click.y() >= h.y1() && click.y() < h.y2()) {
					h.action().run();
					return true;
				}
			}
		}
		return super.mouseClicked(click, doubled);
	}
}
