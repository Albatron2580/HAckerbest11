package com.whiteclient.gui;

import com.whiteclient.WhiteClient;
//#if MC >= 1.21.9
import com.whiteclient.modules.Browser;
//#endif
import com.whiteclient.modules.MainMenu;
import com.whiteclient.modules.MenuBackground;
import com.whiteclient.modules.Wardrobe;
import com.whiteclient.render.Colors;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractStringWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class PauseStyle {
	@FunctionalInterface
	private interface IconPainter {
		void paint(GuiGraphics ctx, float cx, float cy, float size, int color);
	}

	private record Item(int index, float scale, boolean accent) {
	}

	private static final Map<AbstractWidget, Item> ITEMS = new WeakHashMap<>();
	private static final Map<AbstractWidget, IconPainter> ICONS = new WeakHashMap<>();
	private static final Map<AbstractWidget, Float> HOVER = new WeakHashMap<>();
	private static final Map<AbstractWidget, Boolean> BADGES = new WeakHashMap<>();
	private static final Map<Screen, Long> OPENED = new WeakHashMap<>();

	private static SkinPreview player;
	private static int playerW, playerH;
	private static long lastNanos = System.nanoTime();
	private static float frameDt;

	private static float colX, logoR, logoY, titleY, menuHalf;
	private static boolean compact;
	private static Screen current;

	private PauseStyle() {
	}

	public static boolean enabled() {
		WhiteClient wc = WhiteClient.get();
		if (wc == null) {
			return false;
		}
		MenuBackground bg = wc.modules().get(MenuBackground.class);
		return bg != null && bg.isEnabled() && bg.pauseLayout.is("Showcase");
	}

	public static boolean owns(Object widget) {
		return widget instanceof AbstractWidget w && (ITEMS.containsKey(w) || ICONS.containsKey(w) || BADGES.containsKey(w))
				&& Minecraft.getInstance().screen == current;
	}

	public static boolean isCurrent(Screen s) {
		return s == current && current != null;
	}

	private static float intro(Screen s, float delay) {
		Long t0 = OPENED.get(s);
		if (t0 == null) {
			return 1f;
		}
		float t = (System.nanoTime() - t0) / 1.0e9f - delay;
		float k = Math.max(0f, Math.min(1f, t / 0.35f));
		return 1f - (1f - k) * (1f - k) * (1f - k);
	}

	public static void layout(Minecraft client, Screen screen) {
		List<AbstractWidget> all = Screens.getButtons(screen);
		List<AbstractWidget> items = new ArrayList<>();
		List<AbstractStringWidget> texts = new ArrayList<>();
		for (AbstractWidget w : all) {
			if (w instanceof AbstractStringWidget t) {
				texts.add(t);
			} else if (w.visible && w instanceof AbstractButton) {
				items.add(w);
			}
		}
		if (items.isEmpty()) {
			return;
		}
		current = screen;
		OPENED.putIfAbsent(screen, System.nanoTime());
		for (AbstractStringWidget t : texts) {
			t.visible = false;
		}
		items.sort(Comparator.comparingInt(AbstractWidget::getY).thenComparingInt(AbstractWidget::getX));

		MenuBackground bg = WhiteClient.get().modules().get(MenuBackground.class);
		if (bg != null && bg.pauseHideExtras.isOn()) {
			for (AbstractWidget w : List.copyOf(items)) {
				String key = keyOf(w);
				if (key.equals("gui.advancements") || key.equals("gui.stats") || key.equals("menu.playerReporting")) {
					items.remove(w);
					all.remove(w);
				}
			}
		}
		AbstractWidget clientButton = null;
		for (AbstractWidget w : items) {
			if (isClientButton(w)) {
				clientButton = w;
				break;
			}
		}
		if (clientButton != null) {
			items.remove(clientButton);
			if (bg != null && bg.pauseClientButton.is("Top")) {
				items.add(0, clientButton);
			} else {
				items.add(clientButton);
			}
		}

		Font tr = client.font;
		int width = screen.width, height = screen.height;
		int n = items.size();
		float bottom = height - 20;
		boolean[] gapAfter = new boolean[n];
		if (n > 3) {
			for (int i = 0; i < n - 1; i++) {
				AbstractWidget a = items.get(i), b = items.get(i + 1);
				gapAfter[i] = isBack(a) || (i == 0 && isClientButton(a)) || isQuit(b) || isClientButton(b);
			}
		}
		int gaps = 0;
		for (boolean g : gapAfter) {
			gaps += g ? 1 : 0;
		}
		float gapPx = 5;

		compact = false;
		logoR = Math.max(8, Math.min(17, height * 0.05f));
		logoY = Math.max(logoR * 1.5f + 6, height * 0.11f);
		titleY = logoY + logoR * 1.55f + 4;
		float top = titleY + 16 + 12;
		float step = (bottom - top - gaps * gapPx) / n;
		if (step < 15) {
			compact = true;
			logoR = 6.5f;
			logoY = 15;
			titleY = logoY - 5;
			top = 32;
			gapPx = 3;
			step = (bottom - top - gaps * gapPx) / n;
		}
		step = Math.max(11.5f, Math.min(21, step));
		float scale = step >= 16 ? 1.12f : 1.0f;
		float total = n * step + gaps * gapPx;
		if (top + total < bottom) {
			top += (bottom - top - total) * 0.3f;
		}

		colX = width / 2f;
		float maxW = 0;
		float y = top;
		for (int i = 0; i < n; i++) {
			AbstractWidget w = items.get(i);
			String label = w.getMessage() == null ? "" : w.getMessage().getString();
			boolean accent = label.equalsIgnoreCase("WHITECLIENT") || ButtonStyle.isAccent(w);
			float tw = UiFont.width(tr, label) * scale + (accent ? 12 : 0);
			int hw = Math.round(tw + 24), hh = Math.round(10 * scale + 8);
			w.setWidth(hw);
			w.setHeight(hh);
			w.setX(Math.round(colX - hw / 2f));
			w.setY(Math.round(y + (step - hh) / 2f));
			ITEMS.put(w, new Item(i, scale, accent));
			if (w instanceof SpriteIconButton) {
				w.setTooltip(null);
			}
			maxW = Math.max(maxW, hw);
			y += step;
			if (gapAfter[i]) {
				y += gapPx;
			}
		}
		float titleW = compact ? UiFont.width(tr, "WHITECLIENT") * 1.1f + 26 : tr.width(UiFont.title("WHITECLIENT"));
		menuHalf = Math.max(maxW, titleW) / 2f + 10;

		List<Object[]> icons = new ArrayList<>();
		icons.add(new Object[]{"Wardrobe", (IconPainter) Icons::shirt, (Runnable) () -> client.setScreen(new WardrobeScreen(screen))});
		//#if MC >= 1.21.9
		Browser browser = WhiteClient.get().modules().get(Browser.class);
		if (browser != null) {
			icons.add(new Object[]{"Browser", (IconPainter) Icons::globe, (Runnable) () -> client.setScreen(new BrowserScreen(screen, browser))});
		}
		//#endif
		int is = 20, ig = 5;
		int ix = width - 10 - icons.size() * is - (icons.size() - 1) * ig;
		for (Object[] d : icons) {
			Runnable action = (Runnable) d[2];
			Button b = Button.builder(Component.literal((String) d[0]), x -> action.run())
					.bounds(ix, 10, is, is).tooltip(Tooltip.create(Component.literal((String) d[0]))).build();
			ICONS.put(b, (IconPainter) d[1]);
			all.add(b);
			ix += is + ig;
		}
		float[] ub = com.whiteclient.update.AutoUpdater.readyVersion() != null ? UpdateBadge.bounds(tr, width, height, true) : null;
		if (ub != null) {
			Button b = Button.builder(Component.literal(""), x -> client.setScreen(new UpdateReadyScreen(screen)))
					.bounds(Math.round(ub[0]), Math.round(ub[1]), Math.round(ub[2]), Math.round(ub[3])).build();
			BADGES.put(b, Boolean.TRUE);
			all.add(b);
		}

		int ph = (int) Math.min(300, height * 0.62f), pw = (int) (ph * 0.6f);
		if (player == null || ph != playerH || pw != playerW) {
			try {
				player = new SkinPreview(pw, ph, () -> {
					PlayerSkin base = null;
					try {
						Minecraft mc = Minecraft.getInstance();
						base = mc.player != null ? mc.player.getSkin() : null;
					} catch (Throwable ignored) {
					}
					if (base == null) {
						base = DefaultPlayerSkin.getDefaultSkin();
					}
					return Wardrobe.apply(base);
				});
				playerW = pw;
				playerH = ph;
			} catch (Throwable t) {
				player = null;
			}
		}
	}

	private static String keyOf(AbstractWidget w) {
		Component m = w.getMessage();
		if (m == null) {
			return "";
		}
		if (m.getContents() instanceof TranslatableContents t) {
			return t.getKey();
		}
		return m.getString();
	}

	private static boolean isClientButton(AbstractWidget w) {
		String label = w.getMessage() == null ? "" : w.getMessage().getString();
		return ButtonStyle.isAccent(w) || label.equalsIgnoreCase("WHITECLIENT");
	}

	private static boolean isBack(AbstractWidget w) {
		return keyOf(w).equals("menu.returnToGame");
	}

	private static boolean isQuit(AbstractWidget w) {
		String k = keyOf(w);
		return k.equals("menu.returnToMenu") || k.equals("menu.disconnect") || k.equals("menu.quit");
	}

	public static void drawScene(GuiGraphics ctx, Screen s, int mouseX, int mouseY, float delta) {
		if (s != current) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		Font tr = client.font;
		int width = s.width, height = s.height;
		float time = WhiteClient.get().time();
		long now = System.nanoTime();
		frameDt = Math.min(0.1f, (now - lastNanos) / 1.0e9f);
		lastNanos = now;
		float fade = intro(s, 0f);

		ctx.fillGradient(0, 0, width, height, Colors.argb(0x000000, 0.28f * fade), Colors.argb(0x000000, 0.62f * fade));
		Render2D.hGradient(ctx, 0, 0, width * 0.6f, height, Colors.argb(0x000000, 0.42f * fade), 0x00000000);
		Render2D.hGradient(ctx, width * 0.62f, 0, width * 0.38f, height, 0x00000000, Colors.argb(0x05050A, 0.35f * fade));

		MainMenu settings = WhiteClient.get().modules().get(MainMenu.class);

		boolean big = settings == null || settings.bigPlanet.isOn();
		float br = Math.max(40, Math.min(150, height * 0.26f));
		float bx = Math.min(width * 0.15f, width / 2f - menuHalf - br * Render2D.BIG_RING * 1.1f - 8);
		float by = height * 0.6f + (float) Math.sin(time * 0.35f) * 3;
		if (big) {
			float ba = intro(s, 0.03f);
			Render2D.bigPlanet(ctx, bx, by + (1 - ba) * 12, br, time, ba * 0.92f);
		}

		float modelX = modelX(width);
		if (settings == null || settings.planets.isOn()) {
			float pa = intro(s, 0.15f);
			float sideW = width / 2f - menuHalf;
			for (int i = 0; i < 8; i++) {
				float seed = i * 12.9898f + 3.1f;
				float fx = frac(seed * 0.37f);
				float sp = 0.010f + 0.012f * frac(seed * 0.13f);
				float fy = frac(seed * 0.61f + time * sp);
				float px = (i % 2 == 0 ? sideW * fx : width - sideW * fx) + (float) Math.sin(time * 0.25f + seed) * 10;
				float py = height * (1.12f - fy * 1.24f);
				float r = 2.2f + 3.2f * frac(seed * 0.91f);
				float edge = Math.min(1f, Math.min(fy * 6f, (1f - fy) * 6f));
				float a = (0.2f + 0.35f * frac(seed * 0.23f)) * edge * pa;
				if (big) {
					float ex = (px - bx) / (br * 1.9f), ey = (py - by) / (br * 1.15f);
					float d = ex * ex + ey * ey;
					if (d < 1f) {
						a *= Math.max(0f, (d - 0.7f) / 0.3f);
					}
				}
				if (py < 50) {
					a *= Math.max(0f, (py - 34) / 16f);
				}
				float dxm = Math.abs(px - modelX) / (playerW * 0.55f + r * 2f);
				if (player != null && dxm < 1f && py > height * 0.12f) {
					a *= Math.max(0f, (dxm - 0.75f) * 4f);
				}
				if (a > 0.02f) {
					Render2D.planetLogo(ctx, px, py, r, time * (0.6f + frac(seed) * 0.8f) + seed, (int) (255 * a));
				}
			}
		}

		if (player != null && (settings == null || settings.player.isOn()) && modelX - playerW * 0.5f > width / 2f + menuHalf - 6) {
			try {
				drawPlayer(ctx, s, client, tr, modelX, mouseX, mouseY, delta, time);
			} catch (Throwable t) {
				player = null;
			}
		}

		float li = intro(s, 0.05f);
		Pose2D m = Pose2D.of(ctx);
		m.pushMatrix();
		m.translate(0, (1 - li) * 8 + (float) Math.sin(time * 1.2) * 1.0f);
		if (compact) {
			float tw = UiFont.width(tr, "WHITECLIENT") * 1.1f;
			float gx = colX - (tw + 26) / 2f;
			Render2D.planetLogo(ctx, gx + 11, logoY, logoR, time, (int) (255 * li));
			UiFont.drawScaled(ctx, tr, "WHITECLIENT", gx + 26, titleY, 1.1f, Colors.withAlpha(0xFFFFFF, (int) (255 * li)));
		} else {
			for (int g = 12; g >= 1; g--) {
				Render2D.circle(ctx, colX, logoY, logoR * (1.2f + g * 0.22f), Colors.withAlpha(0xFFFFFF, (int) (3 * li)));
			}
			Render2D.planetLogo(ctx, colX, logoY, logoR, time, (int) (255 * li));
			String word = "WHITECLIENT";
			float tw = tr.width(UiFont.title(word));
			ctx.drawString(tr, UiFont.title(word), Math.round(colX - tw / 2f), Math.round(titleY), Colors.withAlpha(0xFFFFFF, (int) (255 * li)), false);
		}
		m.popMatrix();

		float ci = intro(s, 0.2f);
		String where = whereText(client);
		if (where != null) {
			float cw = UiFont.width(tr, where) + 28;
			float cx = 10 - (1 - ci) * 12;
			Render2D.roundRect(ctx, cx, 10, cw, 20, 6, Colors.argb(0x0B0B10, 0.78f * ci));
			Render2D.roundRectOutline(ctx, cx, 10, cw, 20, 6, 1, Colors.withAlpha(0xFFFFFF, (int) (30 * ci)));
			Icons.globe(ctx, cx + 11, 20, 9, Colors.withAlpha(0xD6D6DE, (int) (255 * ci)));
			UiFont.draw(ctx, tr, where, cx + 21, 15, Colors.withAlpha(0xEDEDF2, (int) (255 * ci)));
		}

		float fi = intro(s, 0.3f);
		UiFont.draw(ctx, tr, "whiteclient " + WhiteClient.VERSION + "  ·  " + WhiteClient.gameVersion(), 10, height - 14, Colors.withAlpha(0x9A9AA6, (int) (220 * fi)));
		float[] ub = UpdateBadge.bounds(tr, width, height, true);
		boolean overBadge = ub != null && mouseX >= ub[0] && mouseX < ub[0] + ub[2] && mouseY >= ub[1] && mouseY < ub[1] + ub[3];
		UpdateBadge.draw(ctx, tr, width, height, time, overBadge, true);
	}

	private static float modelX(int width) {
		float right = width / 2f + menuHalf;
		return Math.max(right + playerW * 0.5f, (right + width) / 2f);
	}

	private static void drawPlayer(GuiGraphics ctx, Screen s, Minecraft client, Font tr, float cxp,
								   int mouseX, int mouseY, float delta, float time) {
		float k = intro(s, 0.08f);
		float mouseLook = ((mouseX - cxp) / (float) s.width) * 26f;
		player.setRotation(-4f, -22f + mouseLook);
		player.pose(0, 0, time);
		int x = Math.round(cxp - playerW / 2f + (1 - k) * 18);
		float footY = s.height - Math.max(26, s.height * 0.1f);
		int y = Math.round(footY - playerH);
		for (int i = 4; i >= 1; i--) {
			Render2D.roundRect(ctx, cxp - playerW * 0.18f * i, y + playerH - 6 - i, playerW * 0.36f * i, 3 + 2 * i, 4 + i, Colors.argb(0x000000, 0.08f * k));
		}
		player.render(ctx, x, y, mouseX, mouseY, delta);
		String name = client.getUser().getName();
		float ny = y - 8 - 6 * (1 - k);
		float nw = UiFont.width(tr, name) * 1.1f;
		float logoW = 17;
		float gx = cxp - (logoW + nw) / 2f;
		Render2D.planetLogo(ctx, gx + 6.5f, ny + 5.5f, 3.4f, time, (int) (255 * k));
		UiFont.drawScaled(ctx, tr, name, gx + logoW, ny, 1.1f, Colors.withAlpha(0xEDEDF2, (int) (255 * k)));
		float ul = (logoW + nw + 6) * k;
		Render2D.roundRect(ctx, cxp - ul / 2f, ny + 12, ul, 1.2f, 0.6f, Colors.withAlpha(0xFFFFFF, (int) (170 * k)));
	}

	private static String whereText(Minecraft client) {
		try {
			if (client.isLocalServer() && client.getSingleplayerServer() != null) {
				return "Singleplayer  ·  " + client.getSingleplayerServer().getWorldData().getLevelName();
			}
			ServerData info = client.getCurrentServer();
			if (info != null) {
				String a = info.ip == null ? "" : info.ip;
				return a.isEmpty() ? info.name : a;
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	public static void drawWidget(GuiGraphics ctx, AbstractWidget w) {
		Minecraft client = Minecraft.getInstance();
		Font tr = client.font;
		boolean hot = w.active && (w.isHovered() || w.isFocused());
		float h = HOVER.getOrDefault(w, 0f);
		h += ((hot ? 1f : 0f) - h) * Math.min(1f, frameDt * 14f);
		HOVER.put(w, h);
		float x = w.getX(), y = w.getY(), width = w.getWidth(), height = w.getHeight();

		IconPainter icon = ICONS.get(w);
		if (icon != null) {
			float ii = current != null ? intro(current, 0.3f) : 1f;
			Render2D.roundRect(ctx, x, y, width, height, 6, Colors.mulAlpha(Colors.mixArgb(0xCC0B0B10, 0xE6202028, h), ii));
			Render2D.roundRectOutline(ctx, x, y, width, height, 6, 1, Colors.withAlpha(0xFFFFFF, (int) (255 * ii * (0.08f + 0.35f * h))));
			icon.paint(ctx, x + width / 2f, y + height / 2f, 9, Colors.withAlpha(Colors.mix(0xA6A6B2, 0xFFFFFF, h), (int) (255 * ii)));
			return;
		}
		Item item = ITEMS.get(w);
		if (item == null) {
			return;
		}
		float k = current != null ? intro(current, 0.1f + item.index() * 0.035f) : 1f;
		String label = w.getMessage() == null ? "" : w.getMessage().getString();
		float scale = item.scale();
		float s2 = scale + 0.08f * h;
		float iconW = item.accent() ? 12 : 0;
		float w2 = UiFont.width(tr, label) * s2 + iconW;
		float cx = x + width / 2f;
		if (h > 0.01f) {
			Render2D.roundRect(ctx, cx - w2 / 2f - 12, y, w2 + 24, height, 7, Colors.withAlpha(0xFFFFFF, (int) (18 * h * k)));
		}
		int base = !w.active ? 0x6A6A74 : Colors.mix(item.accent() ? 0xF2F2F6 : 0xC9C9D1, 0xFFFFFF, h);
		float tx = cx - w2 / 2f - (1 - k) * 14;
		float ty = y + 4 - (s2 - scale) * 4;
		if (item.accent()) {
			Render2D.planetLogo(ctx, tx + 4, ty + 4.5f * s2, 3.4f, WhiteClient.get().time(), (int) (255 * k));
		}
		UiFont.drawScaled(ctx, tr, label, tx + iconW, ty, s2, Colors.withAlpha(base, (int) (255 * k)));
		float ul = w2 * h;
		if (ul > 0.5f) {
			Render2D.roundRect(ctx, cx - ul / 2f, ty + 10 * s2 + 1, ul, 1f, 0.5f, Colors.withAlpha(0xFFFFFF, (int) (200 * h)));
		}
	}

	private static float frac(float v) {
		return v - (float) Math.floor(v);
	}
}
