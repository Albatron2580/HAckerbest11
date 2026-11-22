package com.whiteclient.gui;

import com.whiteclient.WhiteClient;
import com.whiteclient.modules.MainMenu;
import com.whiteclient.modules.Wardrobe;
import com.whiteclient.music.NowPlaying;
import com.whiteclient.render.BackgroundShader;
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
	private boolean showcase = true;
	private int walkerW, walkerH;
	/** Screenshot tests: fixed seconds into the walk-in animation (NaN = real time). */
	public static float debugWalkTime = Float.NaN;
	/** Direction the model faces while walking in (degrees for the skin widget). */
	public static float walkYaw = -90f;

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
		MainMenu mm = WhiteClient.get().modules().get(MainMenu.class);
		showcase = mm == null || mm.layout.is("Showcase");
		walkerH = showcase ? (int) Math.min(360, height * 0.74f) : (int) Math.min(200, height * 0.6f);
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
		boolean left = !showcase && settings != null && settings.playerSide.is("Left");
		// stand in the free space next to the buttons
		float buttonsHalf = Math.min(214, width - 40) / 2f;
		float free = width / 2f - buttonsHalf;
		if (!showcase && free < walkerW * 0.8f + 8) {
			return;
		}
		float t = Float.isNaN(debugWalkTime) ? (System.nanoTime() - opened) / 1.0e9f - 0.35f : debugWalkTime;
		float walkDur = 2.1f;
		float targetCx = showcase ? showcaseModelX() : (left ? free / 2f : width - free / 2f);
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
		float footY = showcase ? height - Math.max(26, height * 0.1f) : height - 34;
		int y = Math.round(footY - walkerH + (float) Math.abs(Math.sin(phase)) * -1.5f * walk);
		// soft floor shadow + glow
		float sa = Math.min(1f, Math.max(0f, t / 0.6f));
		for (int i = 4; i >= 1; i--) {
			Render2D.roundRect(ctx, cxp - walkerW * 0.18f * i, y + walkerH - 6 - i, walkerW * 0.36f * i, 3 + 2 * i, 4 + i, Colors.argb(0x000000, 0.08f * sa));
		}
		walker.render(ctx, x, y, mouseX, mouseY, delta);
		// name tag above the head
		if (turn > 0.01f && showcase) {
			// name with a thin underline above the head (like a profile tag)
			String name = client.getSession().getUsername();
			float ny = y - 8 - 6 * (1 - turn);
			float nw = UiFont.width(textRenderer, name) * 1.1f;
			UiFont.drawScaled(ctx, textRenderer, name, cxp - nw / 2f, ny, 1.1f, Colors.withAlpha(TEXT, (int) (255 * turn)));
			float ul = (nw + 6) * turn;
			Render2D.roundRect(ctx, cxp - ul / 2f, ny + 12, ul, 1.2f, 0.6f, Colors.withAlpha(0xFFFFFF, (int) (170 * turn)));
		} else if (turn > 0.01f) {
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
		boolean silk = settings == null || settings.background.is("Silk");
		if (settings != null && settings.background.is("Panorama")) {
			renderPanoramaBackground(ctx, delta);
			applyBlur(ctx);
		} else if (silk) {
			MenuBackgroundRenderer.draw(ctx, width, height, 1f, BackgroundShader.SILK);
		} else {
			MenuBackgroundRenderer.draw(ctx, width, height, 1f);
		}
		if (silk) {
			// keep the left side calm so the menu text reads well
			Render2D.hGradient(ctx, 0, 0, width * 0.55f, height, 0x50000000, 0x00000000);
			ctx.fillGradient(0, height / 2, width, height, 0x00000000, 0x40000000);
		} else {
			ctx.fillGradient(0, 0, width, height, 0x55000000, 0xAA000000);
		}
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
		if (showcase) {
			renderShowcase(ctx, mouseX, mouseY, delta, dt, time);
			super.render(ctx, mouseX, mouseY, delta);
			return;
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

	private float showcaseModelX() {
		return width * 0.70f;
	}

	/** Left: big planet logo + text menu. Right: your player on silk curtains with little planets floating by. */
	private void renderShowcase(DrawContext ctx, int mouseX, int mouseY, float delta, float dt, float time) {
		MainMenu settings = WhiteClient.get().modules().get(MainMenu.class);

		// ---- floating planets (behind everything)
		if (settings == null || settings.planets.isOn()) {
			float pa = intro(0.2f);
			for (int i = 0; i < 11; i++) {
				float seed = i * 12.9898f;
				float fx = frac(seed * 0.37f);
				float sp = 0.010f + 0.012f * frac(seed * 0.13f);
				float fy = frac(seed * 0.61f + time * sp);
				float px = width * (0.5f + 0.48f * fx) + (float) Math.sin(time * 0.25f + seed) * 10;
				float py = height * (1.12f - fy * 1.24f);
				float r = 2.2f + 3.4f * frac(seed * 0.91f);
				float edge = Math.min(1f, Math.min(fy * 6f, (1f - fy) * 6f));
				float a = (0.22f + 0.4f * frac(seed * 0.23f)) * edge * pa;
				if (a > 0.02f) {
					Render2D.planetLogo(ctx, px, py, r, time * (0.6f + frac(seed) * 0.8f) + seed, (int) (255 * a));
				}
			}
		}

		// ---- player model on the right
		try {
			drawWalker(ctx, mouseX, mouseY, delta, time, settings);
		} catch (Throwable t) {
			walker = null;
		}

		// ---- logo + name (left)
		float colX = Math.max(96, Math.min(260, width * 0.25f));
		float r = Math.max(11, Math.min(22, height * 0.062f));
		float logoY = height * 0.3f;
		float li = intro(0.1f);
		Matrix3x2fStack m = ctx.getMatrices();
		m.pushMatrix();
		m.translate(0, (1 - li) * 10 + (float) Math.sin(time * 1.2) * 1.2f);
		for (int g = 14; g >= 1; g--) {
			Render2D.circle(ctx, colX, logoY, r * (1.2f + g * 0.22f), Colors.withAlpha(0xFFFFFF, (int) (3 * li)));
		}
		Render2D.planetLogo(ctx, colX, logoY, r, time, (int) (255 * li));
		String word = "WHITECLIENT";
		float tw = textRenderer.getWidth(UiFont.title(word));
		float titleY = logoY + r * 1.55f + 6;
		ctx.drawText(textRenderer, UiFont.title(word), Math.round(colX - tw / 2f), Math.round(titleY), Colors.withAlpha(0xFFFFFF, (int) (255 * li)), false);
		m.popMatrix();

		// ---- text menu
		boolean mp = client.isMultiplayerEnabled();
		String[] labels = {"Singleplayer", "Multiplayer", "Minecraft Settings", "Client", "Quit Game"};
		Runnable[] actions = {
				() -> client.setScreen(new SelectWorldScreen(this)),
				() -> {
					if (mp) {
						client.setScreen(client.options.skipMultiplayerWarning ? new MultiplayerScreen(this) : new MultiplayerWarningScreen(this));
					}
				},
				() -> client.setScreen(new OptionsScreen(this, client.options)),
				() -> client.setScreen(new ClickGuiScreen(this)),
				() -> client.scheduleStop()
		};
		float itemY = titleY + 34;
		float step = Math.max(17, Math.min(22, height * 0.06f));
		float scale = 1.12f;
		for (int i = 0; i < labels.length; i++) {
			float k = intro(0.22f + i * 0.06f);
			float y = itemY + i * step + (i == 2 ? 4 : 0) + (i >= 3 ? 4 : 0);
			float w = UiFont.width(textRenderer, labels[i]) * scale;
			boolean disabled = i == 1 && !mp;
			boolean hover = !disabled && in(mouseX, mouseY, colX - w / 2f - 12, y - 4, w + 24, 10 * scale + 8);
			float h = anim("item" + i, hover ? 1f : 0f, dt);
			float s2 = scale + 0.08f * h;
			float w2 = UiFont.width(textRenderer, labels[i]) * s2;
			float x = colX - w2 / 2f - (1 - k) * 16;
			if (h > 0.01f) {
				Render2D.roundRect(ctx, colX - w2 / 2f - 12, y - 4, w2 + 24, 10 * s2 + 8, 7, Colors.withAlpha(0xFFFFFF, (int) (16 * h * k)));
			}
			int base = disabled ? 0x6A6A74 : Colors.mix(0xC9C9D1, 0xFFFFFF, h);
			UiFont.drawScaled(ctx, textRenderer, labels[i], x, y - (s2 - scale) * 4, s2, Colors.withAlpha(base, (int) (255 * k)));
			float ul = w2 * h;
			if (ul > 0.5f) {
				Render2D.roundRect(ctx, colX - ul / 2f, y + 10 * s2 + 1, ul, 1f, 0.5f, Colors.withAlpha(0xFFFFFF, (int) (200 * h)));
			}
			if (!disabled) {
				hits.add(new Hit(colX - w / 2f - 12, y - 4, colX + w / 2f + 12, y + 10 * scale + 4, actions[i]));
			}
		}

		// ---- small icon buttons (top right)
		boolean modMenu = FabricLoader.getInstance().isModLoaded("modmenu");
		List<Object[]> icons = new ArrayList<>();
		icons.add(new Object[]{"Wardrobe", (IconPainter) Icons::shirt, (Runnable) () -> client.setScreen(new WardrobeScreen(this))});
		icons.add(new Object[]{"Language", (IconPainter) Icons::globe, (Runnable) () -> client.setScreen(new LanguageOptionsScreen(this, client.options, client.getLanguageManager()))});
		icons.add(new Object[]{"Realms", (IconPainter) Icons::cloud, (Runnable) () -> client.setScreen(new RealmsMainScreen(this))});
		if (modMenu) {
			icons.add(new Object[]{"Mods", (IconPainter) Icons::list, (Runnable) this::openModMenu});
		}
		float is = 20, ig = 5;
		float ix0 = width - 10 - icons.size() * is - (icons.size() - 1) * ig;
		float ii = intro(0.45f);
		String tip = null;
		float tipX = 0;
		for (int i = 0; i < icons.size(); i++) {
			Object[] d = icons.get(i);
			float x = ix0 + i * (is + ig);
			float y = 10 - (1 - ii) * 8;
			boolean hover = in(mouseX, mouseY, x, y, is, is);
			float h = anim("icon" + i, hover ? 1f : 0f, dt);
			Render2D.roundRect(ctx, x, y, is, is, 6, Colors.mulAlpha(Colors.mixArgb(BTN, BTN_HOVER, h), ii));
			Render2D.roundRectOutline(ctx, x, y, is, is, 6, 1, Colors.withAlpha(0xFFFFFF, (int) (255 * ii * (0.08f + 0.35f * h))));
			((IconPainter) d[1]).paint(ctx, x + is / 2f, y + is / 2f, 9, Colors.withAlpha(Colors.mix(0xA6A6B2, 0xFFFFFF, h), (int) (255 * ii)));
			hits.add(new Hit(x, y, x + is, y + is, (Runnable) d[2]));
			if (hover) {
				tip = (String) d[0];
				tipX = x + is / 2f;
			}
		}
		if (tip != null) {
			float tw2 = UiFont.width(textRenderer, tip) + 10;
			float tx = Math.min(width - 4 - tw2, tipX - tw2 / 2f);
			Render2D.roundRect(ctx, tx, 34, tw2, 13, 4, 0xE6101016);
			UiFont.draw(ctx, textRenderer, tip, tx + 5, 36, TEXT);
		}

		// ---- account chip (top left)
		float ci = intro(0.45f);
		if (settings == null || settings.account.isOn()) {
			String name = client.getSession().getUsername();
			float cw = UiFont.width(textRenderer, name) + 28;
			float cxp = 10 - (1 - ci) * 12;
			Render2D.roundRect(ctx, cxp, 10, cw, 20, 6, Colors.mulAlpha(BTN, ci));
			Render2D.roundRectOutline(ctx, cxp, 10, cw, 20, 6, 1, Colors.withAlpha(0xFFFFFF, (int) (30 * ci)));
			if (skin != null) {
				try {
					PlayerSkinDrawer.draw(ctx, Wardrobe.apply(skin.get()), (int) cxp + 4, 14, 12);
				} catch (Throwable ignored) {
				}
			}
			UiFont.draw(ctx, textRenderer, name, cxp + 21, 15, Colors.withAlpha(TEXT, (int) (255 * ci)));
		}

		// ---- now playing (top centre)
		NowPlaying np = WhiteClient.get().music().current();
		float pi = anim("music", np != null && (settings == null || settings.music.isOn()) ? 1f : 0f, dt * 0.6f);
		if (np != null && pi > 0.02f) {
			String s = np.title() + "  —  " + np.artist();
			int maxW = (int) Math.min(200, width * 0.3f);
			while (UiFont.width(textRenderer, s) > maxW && s.length() > 3) {
				s = s.substring(0, s.length() - 2);
			}
			float pw = UiFont.width(textRenderer, s) + 30;
			float px = width / 2f - pw / 2f;
			float py = 10 - (1 - pi) * 10;
			Render2D.roundRect(ctx, px, py, pw, 20, 10, Colors.mulAlpha(BTN, pi));
			Render2D.roundRectOutline(ctx, px, py, pw, 20, 10, 1, Colors.withAlpha(0xFFFFFF, (int) (40 * pi)));
			for (int b = 0; b < 3; b++) {
				float hgt = np.playing() ? 3 + 5 * (0.5f + 0.5f * (float) Math.sin(time * (6 + b * 1.7) + b)) : 3;
				Render2D.roundRect(ctx, px + 9 + b * 3.2f, py + 10 - hgt / 2f, 2, hgt, 1, Colors.withAlpha(0xFFFFFF, (int) (255 * pi)));
			}
			UiFont.draw(ctx, textRenderer, s, px + 22, py + 5, Colors.withAlpha(TEXT, (int) (255 * pi)));
		}

		// ---- footer
		float fi = intro(0.5f);
		UiFont.draw(ctx, textRenderer, "whiteclient " + WhiteClient.VERSION + "  ·  1.21.11", 10, height - 14, Colors.withAlpha(SUB, (int) (220 * fi)));
		String copy = "Copyright Mojang AB. Do not distribute!";
		UiFont.drawScaled(ctx, textRenderer, copy, width - 10 - UiFont.width(textRenderer, copy) * 0.8f, height - 12, 0.8f, Colors.withAlpha(SUB, (int) (150 * fi)));
	}

	private static float frac(float v) {
		return v - (float) Math.floor(v);
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
