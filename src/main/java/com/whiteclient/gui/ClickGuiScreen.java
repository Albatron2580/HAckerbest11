package com.whiteclient.gui;

import com.whiteclient.WhiteClient;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.modules.Theme;
import com.whiteclient.render.Colors;
import com.whiteclient.setting.ActionSetting;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.InfoSetting;
import com.whiteclient.setting.ModeSetting;
import com.whiteclient.setting.NumberSetting;
import com.whiteclient.setting.Setting;
import com.whiteclient.setting.TextSetting;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

public class ClickGuiScreen extends Screen {
	private enum View { HOME, WINDOW }

	private static final int SIDEBAR = 104;
	private static final int HEADER = 28;
	private static final int FOOTER = 18;
	private static final int ROW = 22;

	private static float winX = Float.NaN, winY, winW = 470, winH = 290;
	private static Category lastCategory = Category.COSMETICS;

	private final Screen parent;
	private View view = View.HOME;
	private Category category = lastCategory;
	private Module selected;
	private boolean themePage;
	private boolean searching;
	private String search = "";

	private final Map<Object, Float> anims = new HashMap<>();
	private final List<Hit> hits = new ArrayList<>();
	private final Set<ColorSetting> openPickers = new HashSet<>();
	private final Map<ColorSetting, Float> hues = new HashMap<>();
	private final float[] stars = new float[160 * 3];
	private long opened = System.nanoTime();
	private long lastFrame = System.nanoTime();
	private float viewSwitch = 1f;
	private long dealAt = System.nanoTime();
	private long closingAt;

	private final float[] scroll = new float[2];
	private final float[] scrollTarget = new float[2];
	private final float[] maxScroll = new float[2];
	private final float[][] scrollArea = new float[2][4];

	private int dragMode;
	private double dragDx, dragDy;
	private NumberSetting slider;
	private float sliderX, sliderW;
	private ColorSetting picking;
	private int pickChannel;
	private float pickX, pickW;
	private TextSetting editing;
	private String editBuffer = "";
	private Supplier<SkinTextures> skin;

	private record Hit(float x1, float y1, float x2, float y2, Object owner, Action action, float[] clip) {
	}

	@FunctionalInterface
	private interface Action {
		void run(double mx, double my, int button);
	}

	public ClickGuiScreen(Screen parent) {
		super(Text.literal("WHITECLIENT"));
		this.parent = parent;
		Random r = new Random(7);
		for (int i = 0; i < 160; i++) {
			stars[i * 3] = r.nextFloat();
			stars[i * 3 + 1] = r.nextFloat() * r.nextFloat();
			stars[i * 3 + 2] = r.nextFloat() * 100;
		}
	}

	public ClickGuiScreen(Screen parent, Category category, Module expand) {
		this(parent);
		this.view = View.WINDOW;
		this.category = category;
		this.selected = expand;
	}

	public ClickGuiScreen openTheme(boolean picker) {
		view = View.WINDOW;
		themePage = true;
		selected = null;
		if (picker) {
			openPickers.add(Theme.get().accent);
		}
		return this;
	}

	public ClickGuiScreen openSearch(String query) {
		view = View.HOME;
		searching = true;
		search = query;
		return this;
	}

	@Override
	protected void init() {
		if (Float.isNaN(winX)) {
			winW = Math.min(470, width - 20);
			winH = Math.min(290, height - 20);
			winX = (width - winW) / 2f;
			winY = (height - winH) / 2f;
		}
		clampWindow();
		try {
			skin = client.getSkinProvider().supplySkinTextures(client.getGameProfile(), false);
		} catch (Throwable t) {
			skin = null;
		}
	}

	private void clampWindow() {
		winW = Math.max(300, Math.min(winW, width - 8));
		winH = Math.max(190, Math.min(winH, height - 8));
		winX = Math.max(4, Math.min(winX, width - winW - 4));
		winY = Math.max(4, Math.min(winY, height - winH - 4));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		if (animations() && view == View.HOME && !searching && closingAt == 0) {
			closingAt = System.nanoTime();
			return;
		}
		finishClose();
	}

	private void finishClose() {
		commitEdit();
		WhiteClient.get().config().save();
		lastCategory = category;
		if (client != null) {
			client.setScreen(parent);
		}
	}

	private float anim(Object key, float target, float dt, float speed) {
		float v = anims.getOrDefault(key, target);
		v += (target - v) * Math.min(1f, dt * speed);
		if (Math.abs(target - v) < 0.002f) {
			v = target;
		}
		anims.put(key, v);
		return v;
	}

	private static boolean animations() {
		Theme th = Theme.get();
		return th == null || th.cardAnimations.isOn();
	}

	private float dealT(float delay, float dur) {
		if (!animations()) {
			return 1f;
		}
		float t = ((System.nanoTime() - dealAt) / 1.0e9f - delay) / dur;
		return Math.max(0f, Math.min(1f, t));
	}

	private float closeK() {
		if (closingAt == 0) {
			return 0f;
		}
		return Math.max(0f, Math.min(1f, (System.nanoTime() - closingAt) / 1.0e9f / 0.24f));
	}

	private float closeFade() {
		return 1f - Math.max(0f, Math.min(1f, (closeK() - 0.6f) / 0.4f));
	}

	private static final float SLIDE = 0.6f;

	private static float slide(float t) {
		float u = Math.min(1f, t / SLIDE);
		float w = 1f - u;
		return 0.22f * u + 0.78f * (1f - w * w * w);
	}

	private static float impact(float t) {
		if (t <= SLIDE || t >= 1f) {
			return 0f;
		}
		float v = (t - SLIDE) / (1f - SLIDE);
		return (float) (Math.sin(v * Math.PI * 1.5) * (1 - v) * (1 - v));
	}

	private static float squash(float t) {
		if (t <= SLIDE || t >= 1f) {
			return 0f;
		}
		float v = (t - SLIDE) / (1f - SLIDE);
		return (float) Math.sin(Math.PI * Math.min(1f, v * 2.4f)) * (1f - v);
	}

	private static int shoveRank(int i, int n) {
		return 2 * Math.min(i, n - 1 - i) + (i * 2 > n - 1 ? 1 : 0);
	}

	private static int pusherOf(int i, int n) {
		float c = (n - 1) / 2f;
		int j = i < c ? i + 1 : i - 1;
		if (i == c || j == c || (i < c) != (j < c)) {
			return -1;
		}
		return j;
	}

	private void shoveCard(DrawContext ctx, float fx, float fy, float sx, float sy, float t, float stackTilt, float nudge) {
		float dx = fx - sx, dy = fy - sy;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		float ux = len > 0.01f ? dx / len : 0f, uy = len > 0.01f ? dy / len : 1f;
		float back = closeK();
		float keep = 1f - back * back;
		float e = slide(t) * keep;
		float bump = impact(t) * Math.min(6f, 2f + len * 0.04f) * (1f - back);
		float sq = squash(t) * 0.07f * (1f - back);
		float px = sx + dx * e + ux * bump + Math.signum(dx) * nudge * (1f - back);
		float py = sy + dy * e + uy * bump;
		float ang = stackTilt * (1f - e) + ux * 0.035f * impact(t) * (1f - back);
		float scX = 1f - sq * Math.abs(ux) + sq * 0.7f * Math.abs(uy);
		float scY = 1f - sq * Math.abs(uy) + sq * 0.7f * Math.abs(ux);
		Matrix3x2fStack m = ctx.getMatrices();
		m.pushMatrix();
		m.translate(px, py);
		m.rotate(ang);
		m.scale(scX, scY);
		m.translate(-fx, -fy);
	}

	private static boolean in(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private void hit(float x, float y, float w, float h, Object owner, float[] clip, Action a) {
		hits.add(new Hit(x, y, x + w, y + h, owner, a, clip));
	}

	private void go(View v) {
		commitEdit();
		view = v;
		viewSwitch = 0f;
		dealAt = System.nanoTime();
		scroll[0] = scroll[1] = scrollTarget[0] = scrollTarget[1] = 0;
	}

	private static void openLink(String url) {
		try {
			if (url != null && !url.isBlank()) {
				Util.getOperatingSystem().open(URI.create(url.trim()));
			}
		} catch (Throwable ignored) {
		}
	}

	private List<Module> modules(Category c) {
		return WhiteClient.get().modules().byCategory(c);
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		Theme th = Theme.get();
		float open = Math.min(1f, (System.nanoTime() - opened) / 2.5e8f) * (1f - closeK());
		if (view == View.HOME) {
			ctx.fillGradient(0, 0, width, height, th.bg(0.86f * open), Colors.argb(Colors.shade(th.background.rgb(), 1.6f), 0.93f * open));
			if (th.stars.isOn()) {
				drawNightSky(ctx, open);
			}
		} else {
			ctx.fillGradient(0, 0, width, height, Colors.argb(0, 0.35f * open), Colors.argb(0, 0.55f * open));
		}
	}

	private void drawNightSky(DrawContext ctx, float alpha) {
		float t = WhiteClient.get().time();
		for (int band = 0; band < 3; band++) {
			int slices = 48;
			float baseY = height * (0.18f + band * 0.09f);
			for (int i = 0; i < slices; i++) {
				float x0 = width * i / (float) slices;
				float x1 = width * (i + 1) / (float) slices;
				float u = i / (float) slices;
				float y = baseY + (float) Math.sin(u * 6.0 + t * 0.35 + band * 1.7) * height * 0.04f;
				float hgt = height * (0.10f + 0.05f * (float) Math.sin(u * 4.0 - t * 0.25 + band));
				float a = 0.05f * alpha * (0.6f + 0.4f * (float) Math.sin(u * 9 + t * 0.5 + band * 2));
				ctx.fillGradient(Math.round(x0), Math.round(y - hgt), Math.round(x1), Math.round(y), Colors.argb(0xFFFFFF, 0), Colors.argb(0xDDE6FF, a));
				ctx.fillGradient(Math.round(x0), Math.round(y), Math.round(x1), Math.round(y + hgt * 0.4f), Colors.argb(0xDDE6FF, a), Colors.argb(0xFFFFFF, 0));
			}
		}
		for (int i = 0; i < 160; i++) {
			float x = stars[i * 3] * width;
			float y = stars[i * 3 + 1] * height;
			float tw = 0.4f + 0.6f * (0.5f + 0.5f * (float) Math.sin(t * 1.5 + stars[i * 3 + 2]));
			ctx.fill(Math.round(x), Math.round(y), Math.round(x) + 1, Math.round(y) + 1, Colors.argb(0xFFFFFF, tw * alpha * 0.8f));
		}
		for (int layer = 0; layer < 2; layer++) {
			int cols = 120;
			int color = layer == 0 ? Colors.argb(0x15151B, alpha) : Colors.argb(0x0B0B10, alpha);
			for (int i = 0; i < cols; i++) {
				float x0 = width * i / (float) cols, x1 = width * (i + 1) / (float) cols;
				float u = i / (float) cols;
				float hgt = height * (layer == 0 ? 0.22f : 0.14f) * (0.55f + 0.25f * (float) Math.sin(u * (layer == 0 ? 7 : 11) + layer)
						+ 0.2f * (float) Math.sin(u * (layer == 0 ? 17 : 23) + 2 * layer));
				ctx.fill(Math.round(x0), Math.round(height - hgt), Math.round(x1), height, color);
			}
		}
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float deltaTicks) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1.0e9f);
		lastFrame = now;
		viewSwitch = Math.min(1f, viewSwitch + dt * 5f);
		if (closingAt != 0 && closeK() >= 1f) {
			finishClose();
			return;
		}
		for (int i = 0; i < 2; i++) {
			scroll[i] += (scrollTarget[i] - scroll[i]) * Math.min(1f, dt * 14f);
		}
		hits.clear();
		scrollArea[0] = new float[4];
		scrollArea[1] = new float[4];
		if (view == View.HOME) {
			drawHome(ctx, mouseX, mouseY, dt);
		} else {
			drawWindow(ctx, mouseX, mouseY, dt);
		}
		super.render(ctx, mouseX, mouseY, deltaTicks);
	}

	private void categoryIcon(DrawContext ctx, Category c, float cx, float cy, float s, int col, int cut) {
		switch (c) {
			case COSMETICS -> Icons.sparkle(ctx, cx, cy, s, col);
			case PVP -> Icons.shield(ctx, cx, cy, s, col, cut);
			case WORLD -> Icons.globe(ctx, cx, cy, s, col);
			case MUSIC -> Icons.note(ctx, cx, cy, s, col);
			case HUD -> Icons.hud(ctx, cx, cy, s, col);
			case MENU -> Icons.list(ctx, cx, cy, s, col);
			default -> Icons.gear(ctx, cx, cy, s, col);
		}
	}

	private void drawHome(DrawContext ctx, int mx, int my, float dt) {
		Theme th = Theme.get();
		float k = 1f - (float) Math.pow(1f - viewSwitch, 3);
		float t = WhiteClient.get().time();
		float cx = width / 2f;

		String title = "WHITECLIENT";
		float titleY = Math.max(22, height * 0.17f);
		float tw = textRenderer.getWidth(UiFont.title(title));
		Matrix3x2fStack m = ctx.getMatrices();
		m.pushMatrix();
		m.translate(0, (1 - k) * -8);
		float logoW = 30;
		float lx = cx - (logoW + 6 + tw) / 2f;
		Render2D.planetLogo(ctx, lx + logoW / 2f, titleY - 1, 7f, t, (int) (255 * k));
		ctx.drawText(textRenderer, UiFont.title(title), Math.round(lx + logoW + 6), Math.round(titleY + 2), Colors.argb(th.text.rgb(), k), false);
		m.popMatrix();

		if (searching) {
			drawSearch(ctx, mx, my, dt, titleY + 34);
			return;
		}

		List<Category> cats = new ArrayList<>();
		for (Category c : Category.values()) {
			if (c.listed()) {
				cats.add(c);
			}
		}
		float gap = 9;
		float cs = Math.min(64, (width - 40 - gap * (cats.size() - 1)) / cats.size());
		float rowW = cats.size() * cs + (cats.size() - 1) * gap;
		float x0 = cx - rowW / 2f;
		float y0 = titleY + 40;
		float r = th.roundness.getFloat();
		float stackX = cx, stackY = y0 + cs / 2f;
		int n = cats.size();
		Integer[] order = new Integer[n];
		final float[] tOf = new float[n];
		final int[] rank = new int[n];
		for (int i = 0; i < n; i++) {
			order[i] = i;
			rank[i] = shoveRank(i, n);
			tOf[i] = dealT(0.05f + rank[i] * 0.075f, 0.42f);
		}
		java.util.Arrays.sort(order, (p, q) -> {
			int gp = tOf[p] >= 1f ? 0 : tOf[p] <= 0f ? 1 : 2, gq = tOf[q] >= 1f ? 0 : tOf[q] <= 0f ? 1 : 2;
			if (gp != gq) {
				return Integer.compare(gp, gq);
			}
			return Integer.compare(rank[q], rank[p]);
		});
		float fade = closeFade();
		for (int oi = 0; oi < n; oi++) {
			int i = order[oi];
			Category c = cats.get(i);
			float ci = (animations() ? Math.min(1f, k * 1.6f) : Math.max(0f, Math.min(1f, (k - i * 0.05f) / 0.6f))) * fade;
			float x = x0 + i * (cs + gap);
			float y = animations() ? y0 : y0 + (1 - ci) * 14;
			int pusher = pusherOf(i, n);
			float nudge = pusher < 0 ? 0f : impact(tOf[pusher]) * 4.5f;
			float tilt = (float) Math.toRadians(((rank[i] * 7) % 5 - 2) * 2.4f);
			shoveCard(ctx, x + cs / 2f, y + cs / 2f, stackX, stackY - (n - 1 - rank[i]) * 0.9f, tOf[i], tilt, nudge);
			boolean hover = in(mx, my, x, y, cs, cs);
			float h = anim("card" + c, hover ? 1f : 0f, dt, 12f);
			float grow = h * 4;
			float bx = x - grow / 2, by = y - grow / 2 - h * 2, bs = cs + grow;
			Render2D.roundRect(ctx, bx, by, bs, bs, r, Colors.mulAlpha(Colors.mixArgb(th.card(0.55f), Colors.argb(Colors.mix(th.card.rgb(), 0xFFFFFF, 0.12f), 0.75f), h), ci));
			Render2D.roundRectOutline(ctx, bx, by, bs, bs, r, 1, Colors.argb(th.accent.rgb(), (0.16f + 0.5f * h) * ci));
			int iconC = Colors.argb(Colors.mix(th.subtext.rgb(), th.text.rgb(), 0.6f + 0.4f * h), ci);
			categoryIcon(ctx, c, bx + bs / 2f, by + bs * 0.4f, bs * 0.26f, iconC, Colors.argb(th.card.rgb(), ci));
			UiFont.drawCentered(ctx, textRenderer, c.title, bx + bs / 2f, by + bs * 0.64f, Colors.argb(th.text.rgb(), ci));
			float ul = 8 + 6 * h;
			Render2D.roundRect(ctx, bx + bs / 2f - ul / 2f, by + bs * 0.64f + 11, ul, 1.2f, 0.6f, Colors.argb(th.accent.rgb(), (0.35f + 0.6f * h) * ci));
			int on = 0;
			for (Module mod : modules(c)) {
				if (mod.isEnabled()) {
					on++;
				}
			}
			UiFont.drawScaled(ctx, textRenderer, on + "/" + modules(c).size(), bx + bs - 18, by + 5, 0.7f, Colors.argb(th.subtext.rgb(), ci));
			ctx.getMatrices().popMatrix();
			final Category cat = c;
			hit(x, y, cs, cs, null, null, (a, b, btn) -> {
				category = cat;
				selected = null;
				themePage = false;
				go(View.WINDOW);
			});
		}

		String[] labels = {"Wardrobe", "Browser", "Search", "Theme", "Support"};
		float sw = 48, sh = 32, sg = 8;
		float sx0 = cx - (labels.length * sw + (labels.length - 1) * sg) / 2f;
		float sy = y0 + cs + 22;
		float[] btT = new float[labels.length];
		for (int i = 0; i < labels.length; i++) {
			btT[i] = dealT(0.05f + n * 0.075f + shoveRank(i, labels.length) * 0.07f, 0.38f);
		}
		Integer[] bOrder = new Integer[labels.length];
		for (int i = 0; i < labels.length; i++) {
			bOrder[i] = i;
		}
		final int nbs = labels.length;
		java.util.Arrays.sort(bOrder, (p, q) -> {
			int gp = btT[p] >= 1f ? 0 : btT[p] <= 0f ? 1 : 2, gq = btT[q] >= 1f ? 0 : btT[q] <= 0f ? 1 : 2;
			if (gp != gq) {
				return Integer.compare(gp, gq);
			}
			return Integer.compare(shoveRank(q, nbs), shoveRank(p, nbs));
		});
		for (int bi = 0; bi < labels.length; bi++) {
			int i = bOrder[bi];
			float si = (animations() ? Math.min(1f, k * 1.6f) : Math.max(0f, Math.min(1f, (k - 0.3f - i * 0.05f) / 0.5f))) * fade;
			float x = sx0 + i * (sw + sg);
			float y = animations() ? sy : sy + (1 - si) * 10;
			int nb = labels.length;
			float bt = btT[i];
			int pusher = pusherOf(i, nb);
			float nudge = pusher < 0 ? 0f : impact(btT[pusher]) * 3.5f;
			float tilt = (float) Math.toRadians(((shoveRank(i, nb) * 7) % 5 - 2) * 3f);
			shoveCard(ctx, x + sw / 2f, y + sh / 2f, cx, sy + sh / 2f - (nb - 1 - shoveRank(i, nb)) * 0.8f, bt, tilt, nudge);
			boolean hover = in(mx, my, x, y, sw, sh);
			float h = anim("small" + i, hover ? 1f : 0f, dt, 12f);
			Render2D.roundRect(ctx, x, y - h, sw, sh, r * 0.75f, Colors.mulAlpha(Colors.mixArgb(th.card(0.5f), Colors.argb(Colors.mix(th.card.rgb(), 0xFFFFFF, 0.12f), 0.7f), h), si));
			Render2D.roundRectOutline(ctx, x, y - h, sw, sh, r * 0.75f, 1, Colors.argb(th.accent.rgb(), (0.12f + 0.45f * h) * si));
			int ic = Colors.argb(Colors.mix(th.subtext.rgb(), th.text.rgb(), 0.5f + 0.5f * h), si);
			float icx = x + sw / 2f, icy = y - h + 11;
			switch (i) {
				case 0 -> Icons.shirt(ctx, icx, icy, 9, ic);
				case 1 -> Icons.globe(ctx, icx, icy, 9, ic);
				case 2 -> Icons.search(ctx, icx, icy, 9, ic);
				case 3 -> Icons.palette(ctx, icx, icy, 9, ic, Colors.argb(th.card.rgb(), si));
				default -> Icons.heart(ctx, icx, icy, 9, ic);
			}
			UiFont.drawScaled(ctx, textRenderer, labels[i], icx - UiFont.width(textRenderer, labels[i]) * 0.36f, y - h + 20, 0.72f, Colors.argb(th.text.rgb(), si));
			ctx.getMatrices().popMatrix();
			final int idx = i;
			hit(x, y, sw, sh, null, null, (a, b, btn) -> {
				switch (idx) {
					case 0 -> client.setScreen(new WardrobeScreen(this));
					case 1 -> {
						com.whiteclient.modules.Browser br = com.whiteclient.modules.Browser.get();
						if (br != null) {
							com.whiteclient.browser.WebView.startEngine();
							client.setScreen(new BrowserScreen(this, br));
						}
					}
					case 2 -> {
						searching = true;
						search = "";
					}
					case 3 -> {
						themePage = true;
						selected = null;
						go(View.WINDOW);
					}
					default -> client.setScreen(new SupportScreen(this));
				}
			});
		}
		UiFont.drawCentered(ctx, textRenderer, "Right Shift to close  ·  everything is free", cx, height - 16, Colors.argb(th.subtext.rgb(), 0.7f * k));
	}

	private void drawSearch(DrawContext ctx, int mx, int my, float dt, float y) {
		Theme th = Theme.get();
		float w = Math.min(260, width - 40);
		float x = (width - w) / 2f;
		Render2D.roundRect(ctx, x, y, w, 20, 10, th.card(0.8f));
		Render2D.roundRectOutline(ctx, x, y, w, 20, 10, 1, th.accent(0.6f));
		Icons.search(ctx, x + 12, y + 10, 9, th.sub());
		boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
		UiFont.draw(ctx, textRenderer, search.isEmpty() ? "Search modules..." : search + (blink ? "_" : ""), x + 22, y + 6, search.isEmpty() ? th.sub() : th.text());
		hit(x + w - 18, y, 18, 20, null, null, (a, b, btn) -> searching = false);
		Icons.close(ctx, x + w - 10, y + 10, 7, th.sub());

		String q = search.toLowerCase(Locale.ROOT).trim();
		float ry = y + 28;
		int shown = 0;
		for (Module mod : WhiteClient.get().modules().all()) {
			if (!q.isEmpty() && !mod.getName().toLowerCase(Locale.ROOT).contains(q) && !mod.getDescription().toLowerCase(Locale.ROOT).contains(q)) {
				continue;
			}
			if (ry + ROW > height - 24) {
				break;
			}
			boolean hover = in(mx, my, x, ry, w, ROW - 2);
			Render2D.roundRect(ctx, x, ry, w, ROW - 2, 6, hover ? th.card(0.95f) : th.card(0.6f));
			UiFont.draw(ctx, textRenderer, mod.getName(), x + 10, ry + 6, th.text());
			String cat = mod.getCategory().title;
			UiFont.drawScaled(ctx, textRenderer, cat, x + w - 10 - UiFont.width(textRenderer, cat) * 0.72f, ry + 7, 0.72f, th.sub());
			final Module target = mod;
			hit(x, ry, w, ROW - 2, null, null, (a, b, btn) -> {
				searching = false;
				category = target.getCategory();
				themePage = target.getCategory() == Category.CLIENT;
				selected = themePage ? null : target;
				go(View.WINDOW);
			});
			ry += ROW;
			shown++;
		}
		if (shown == 0) {
			UiFont.drawCentered(ctx, textRenderer, "No modules found", width / 2f, ry + 4, th.sub());
		}
	}

	private void drawWindow(DrawContext ctx, int mx, int my, float dt) {
		Theme th = Theme.get();
		float k = 1f - (float) Math.pow(1f - viewSwitch, 3);
		float r = th.roundness.getFloat();
		Matrix3x2fStack m = ctx.getMatrices();
		m.pushMatrix();
		float wcx = winX + winW / 2f, wcy = winY + winH / 2f;
		float s = 0.95f + 0.05f * k;
		m.translate(wcx, wcy);
		m.scale(s, s);
		m.translate(-wcx, -wcy);

		float x = winX, y = winY, w = winW, h = winH;
		Render2D.shadow(ctx, x, y, w, h, r, 8, 110);
		Render2D.roundRect(ctx, x, y, w, h, r, th.panel());
		Render2D.roundRectOutline(ctx, x, y, w, h, r, 1, th.accent(0.14f));

		Render2D.roundRect(ctx, x, y, SIDEBAR, h, r, Colors.argb(Colors.shade(th.panel.rgb(), 0.82f), th.opacity.getFloat()));
		ctx.fill((int) (x + SIDEBAR - r), (int) y, (int) (x + SIDEBAR), (int) (y + h), Colors.argb(Colors.shade(th.panel.rgb(), 0.82f), th.opacity.getFloat()));
		ctx.fill((int) (x + SIDEBAR), (int) (y + 8), (int) (x + SIDEBAR) + 1, (int) (y + h - 8), th.accent(0.08f));
		Render2D.logo(ctx, x + 15, y + 15, 6, WhiteClient.get().time());
		UiFont.draw(ctx, textRenderer, "WHITECLIENT", x + 26, y + 10, th.text());
		UiFont.drawScaled(ctx, textRenderer, "Modules", x + 12, y + 30, 0.72f, th.sub());
		float sy = y + 40;
		for (Category c : Category.values()) {
			if (!c.listed()) {
				continue;
			}
			boolean sel = c == category && !themePage;
			boolean hover = in(mx, my, x + 6, sy, SIDEBAR - 12, 17);
			float a = anim("side" + c, sel ? 1f : 0f, dt, 14f);
			if (a > 0.01f) {
				Render2D.roundRect(ctx, x + 6, sy, SIDEBAR - 12, 17, 5, th.accent(0.12f * a));
				Render2D.roundRect(ctx, x + 6, sy + 4, 2, 9, 1, th.accent(a));
			} else if (hover) {
				Render2D.roundRect(ctx, x + 6, sy, SIDEBAR - 12, 17, 5, th.accent(0.05f));
			}
			int col = Colors.mixArgb(th.sub(), th.text(), Math.max(a, hover ? 0.6f : 0f));
			categoryIcon(ctx, c, x + 18, sy + 8.5f, 8, col, Colors.argb(Colors.shade(th.panel.rgb(), 0.82f), 1f));
			UiFont.draw(ctx, textRenderer, c.title, x + 28, sy + 4, col);
			final Category cat = c;
			hit(x + 6, sy, SIDEBAR - 12, 17, null, null, (aa, b, btn) -> {
				if (category != cat || themePage) {
					category = cat;
					selected = null;
					themePage = false;
					go(View.WINDOW);
				}
			});
			sy += 19;
		}
		UiFont.drawScaled(ctx, textRenderer, "Client", x + 12, sy + 6, 0.72f, th.sub());
		sy += 16;
		String[] clientItems = {"Theme", "Wardrobe", "Home"};
		for (int i = 0; i < clientItems.length; i++) {
			boolean sel = i == 0 && themePage;
			boolean hover = in(mx, my, x + 6, sy, SIDEBAR - 12, 17);
			float a = anim("client" + i, sel ? 1f : 0f, dt, 14f);
			if (a > 0.01f) {
				Render2D.roundRect(ctx, x + 6, sy, SIDEBAR - 12, 17, 5, th.accent(0.12f * a));
				Render2D.roundRect(ctx, x + 6, sy + 4, 2, 9, 1, th.accent(a));
			} else if (hover) {
				Render2D.roundRect(ctx, x + 6, sy, SIDEBAR - 12, 17, 5, th.accent(0.05f));
			}
			int col = Colors.mixArgb(th.sub(), th.text(), Math.max(a, hover ? 0.6f : 0f));
			int bgc = Colors.argb(Colors.shade(th.panel.rgb(), 0.82f), 1f);
			switch (i) {
				case 0 -> Icons.palette(ctx, x + 18, sy + 8.5f, 8, col, bgc);
				case 1 -> Icons.shirt(ctx, x + 18, sy + 8.5f, 8, col);
				default -> Icons.back(ctx, x + 18, sy + 8.5f, 8, col);
			}
			UiFont.draw(ctx, textRenderer, clientItems[i], x + 28, sy + 4, col);
			final int idx = i;
			hit(x + 6, sy, SIDEBAR - 12, 17, null, null, (aa, b, btn) -> {
				if (idx == 0) {
					themePage = true;
					selected = null;
					go(View.WINDOW);
				} else if (idx == 1) {
					client.setScreen(new WardrobeScreen(this));
				} else {
					go(View.HOME);
				}
			});
			sy += 19;
		}
		if (h > 230) {
			String name = client.getSession().getUsername();
			Render2D.roundRect(ctx, x + 6, y + h - FOOTER - 24, SIDEBAR - 12, 20, 6, th.card(0.7f));
			if (skin != null) {
				try {
					PlayerSkinDrawer.draw(ctx, skin.get(), (int) (x + 10), (int) (y + h - FOOTER - 20), 12);
				} catch (Throwable ignored) {
				}
			}
			String shown = name;
			while (UiFont.width(textRenderer, shown) > SIDEBAR - 38 && shown.length() > 2) {
				shown = shown.substring(0, shown.length() - 1);
			}
			UiFont.draw(ctx, textRenderer, shown, x + 26, y + h - FOOTER - 18, th.text());
		}

		float cx0 = x + SIDEBAR + 10;
		float cw = w - SIDEBAR - 20;
		String crumb = themePage ? "Client" : "Modules";
		String page = themePage ? "Theme" : category.title;
		UiFont.draw(ctx, textRenderer, crumb + "  /  ", cx0, y + 10, th.sub());
		UiFont.draw(ctx, textRenderer, page, cx0 + UiFont.width(textRenderer, crumb + "  /  "), y + 10, th.text());
		hit(x + SIDEBAR, y, w - SIDEBAR - 48, HEADER - 4, "drag", null, (a, b, btn) -> startMove(a, b));
		float bx = x + w - 22;
		boolean hc = in(mx, my, bx, y + 6, 16, 16);
		Render2D.circle(ctx, bx + 8, y + 14, 8, th.accent(hc ? 0.2f : 0.07f));
		Icons.close(ctx, bx + 8, y + 14, 6, hc ? th.text() : th.sub());
		hit(bx, y + 6, 16, 16, null, null, (a, b, btn) -> close());
		float hb = bx - 20;
		boolean hh = in(mx, my, hb, y + 6, 16, 16);
		Render2D.circle(ctx, hb + 8, y + 14, 8, th.accent(hh ? 0.2f : 0.07f));
		Icons.back(ctx, hb + 8, y + 14, 6, hh ? th.text() : th.sub());
		hit(hb, y + 6, 16, 16, null, null, (a, b, btn) -> go(View.HOME));
		ctx.fill((int) cx0, (int) (y + HEADER), (int) (cx0 + cw), (int) (y + HEADER) + 1, th.accent(0.08f));

		float top = y + HEADER + 6;
		float bottom = y + h - FOOTER - 4;
		float ka = k;
		if (themePage) {
			drawSettingsColumn(ctx, Theme.get(), cx0, top, cw, bottom - top, mx, my, dt, ka, true);
		} else if (selected != null && selected.getCategory() == category) {
			float leftW = cw * 0.56f;
			drawSettingsColumn(ctx, selected, cx0, top, leftW - 6, bottom - top, mx, my, dt, ka, false);
			drawModuleList(ctx, cx0 + leftW + 4, top, cw - leftW - 4, bottom - top, mx, my, dt, 1);
		} else {
			drawModuleList(ctx, cx0, top, cw, bottom - top, mx, my, dt, 2);
		}

		float fy = y + h - FOOTER;
		ctx.fill((int) (x + SIDEBAR), (int) fy, (int) (x + w - 6), (int) fy + 1, th.accent(0.06f));
		boolean hd = in(mx, my, x + SIDEBAR + 4, fy, 60, FOOTER);
		Icons.drag(ctx, x + SIDEBAR + 12, fy + FOOTER / 2f, 8, hd ? th.text() : th.sub());
		UiFont.drawScaled(ctx, textRenderer, "drag", x + SIDEBAR + 20, fy + 6, 0.72f, hd ? th.text() : th.sub());
		hit(x + SIDEBAR + 4, fy, 60, FOOTER, "drag", null, (a, b, btn) -> startMove(a, b));
		String ver = "WHITECLIENT " + WhiteClient.VERSION;
		UiFont.drawScaled(ctx, textRenderer, ver, x + SIDEBAR + (w - SIDEBAR) / 2f - UiFont.width(textRenderer, ver) * 0.36f, fy + 6, 0.72f, Colors.mulAlpha(th.sub(), 0.6f));
		boolean hr = in(mx, my, x + w - 64, fy, 62, FOOTER);
		UiFont.drawScaled(ctx, textRenderer, "resize", x + w - 50, fy + 6, 0.72f, hr ? th.text() : th.sub());
		Icons.resize(ctx, x + w - 12, fy + FOOTER / 2f, 8, hr ? th.text() : th.sub());
		hit(x + w - 64, fy, 62, FOOTER, "resize", null, (a, b, btn) -> {
			dragMode = 2;
			dragDx = winX + winW - a;
			dragDy = winY + winH - b;
		});
		m.popMatrix();
	}

	private void startMove(double mx, double my) {
		dragMode = 1;
		dragDx = mx - winX;
		dragDy = my - winY;
	}

	private void drawModuleList(DrawContext ctx, float x, float y, float w, float h, int mx, int my, float dt, int columns) {
		Theme th = Theme.get();
		int col = 0;
		scrollArea[0] = new float[]{x, y, w, h};
		float[] clip = {x - 2, y, x + w + 2, y + h};
		ctx.enableScissor((int) clip[0], (int) clip[1], (int) clip[2], (int) clip[3]);
		List<Module> list = modules(category);
		float colW = (w - (columns - 1) * 6) / columns;
		float ry = y - scroll[0];
		float start = ry;
		for (int i = 0; i < list.size(); i++) {
			Module mod = list.get(i);
			float rx = x + col * (colW + 6);
			boolean sel = mod == selected;
			boolean hover = in(mx, my, rx, ry, colW, ROW - 3) && in(mx, my, clip[0], clip[1], clip[2] - clip[0], clip[3] - clip[1]);
			float hv = anim(mod.getName() + "#row", hover ? 1f : 0f, dt, 14f);
			Render2D.roundRect(ctx, rx, ry, colW, ROW - 3, 6, Colors.mixArgb(th.card(0.75f), th.card(1f), hv));
			if (sel) {
				Render2D.roundRectOutline(ctx, rx, ry, colW, ROW - 3, 6, 1, th.accent(0.7f));
			}
			UiFont.draw(ctx, textRenderer, mod.getName(), rx + 9, ry + 5, mod.isEnabled() ? th.text() : Colors.mixArgb(th.sub(), th.text(), 0.4f));
			float t = anim(mod.getName() + "#on", mod.isEnabled() ? 1f : 0f, dt, 14f);
			float tx = rx + colW - 27;
			drawToggle(ctx, tx, ry + 4, t);
			hit(tx - 2, ry, colW - (tx - 2 - rx), ROW - 3, null, clip, (a, b, btn) -> mod.toggle());
			hit(rx, ry, tx - 2 - rx, ROW - 3, null, clip, (a, b, btn) -> {
				commitEdit();
				selected = selected == mod ? null : mod;
				scroll[1] = scrollTarget[1] = 0;
			});
			col++;
			if (col >= columns) {
				col = 0;
				ry += ROW;
			}
		}
		if (col != 0) {
			ry += ROW;
		}
		maxScroll[0] = Math.max(0, (ry - start) - h);
		ctx.disableScissor();
	}

	private void drawToggle(DrawContext ctx, float x, float y, float t) {
		Theme th = Theme.get();
		float w = 20, h = 11;
		int track = Colors.mixArgb(Colors.argb(Colors.mix(th.card.rgb(), 0xFFFFFF, 0.08f), 1f), th.toggleOn.argb(), t);
		Render2D.roundRect(ctx, x, y, w, h, h / 2f, track);
		float knobX = x + 5.5f + (w - 11f) * t;
		int onKnob = Theme.lum(th.toggleOn.rgb()) > 0.6f ? 0xFF0E0E12 : 0xFFFFFFFF;
		Render2D.circle(ctx, knobX, y + h / 2f, 3.6f, Colors.mixArgb(0xFF8C8C98, onKnob, t));
	}

	private float settingHeight(Setting<?> s) {
		if (s instanceof NumberSetting) {
			return 24;
		}
		if (s instanceof ColorSetting c) {
			return openPickers.contains(c) ? 70 : 20;
		}
		if (s instanceof ModeSetting mode && mode.getModes().size() > 3) {
			int rows = (mode.getModes().size() + 2) / 3;
			return 16 + rows * 16;
		}
		if (s instanceof TextSetting) {
			return 22;
		}
		return 19;
	}

	private void drawSettingsColumn(DrawContext ctx, Module mod, float x, float y, float w, float h, int mx, int my, float dt, float k, boolean theme) {
		Theme th = Theme.get();
		scrollArea[1] = new float[]{x, y, w, h};
		Render2D.roundRect(ctx, x, y, w, h, 7, th.card(0.45f));
		float[] clip = {x, y + 1, x + w, y + h - 1};
		ctx.enableScissor((int) clip[0], (int) clip[1], (int) clip[2], (int) clip[3]);
		float sy = y + 8 - scroll[1];
		float start = sy;
		UiFont.draw(ctx, textRenderer, mod.getName(), x + 10, sy, th.text());
		UiFont.drawScaled(ctx, textRenderer, mod.getDescription(), x + 10, sy + 11, 0.72f, th.sub());
		if (!theme) {
			float t = anim(mod.getName() + "#on", mod.isEnabled() ? 1f : 0f, dt, 14f);
			drawToggle(ctx, x + w - 30, sy + 2, t);
			hit(x + w - 32, sy, 26, 14, null, clip, (a, b, btn) -> mod.toggle());
		}
		sy += 26;
		for (Setting<?> s : mod.getSettings()) {
			if (!s.isVisible()) {
				continue;
			}
			drawSetting(ctx, s, x + 10, sy, w - 20, mx, my, dt, clip);
			sy += settingHeight(s);
		}
		maxScroll[1] = Math.max(0, (sy - start + 8) - h);
		scrollTarget[1] = Math.max(0, Math.min(maxScroll[1], scrollTarget[1]));
		ctx.disableScissor();
	}

	private void drawSetting(DrawContext ctx, Setting<?> s, float x, float y, float w, int mx, int my, float dt, float[] clip) {
		Theme th = Theme.get();
		int label = Colors.mixArgb(th.sub(), th.text(), 0.75f);
		if (s instanceof BoolSetting b) {
			UiFont.draw(ctx, textRenderer, s.getName(), x, y + 4, label);
			drawToggle(ctx, x + w - 20, y + 3, anim(b, b.isOn() ? 1f : 0f, dt, 14f));
			hit(x, y, w, 16, null, clip, (a, c, btn) -> b.toggle());
		} else if (s instanceof NumberSetting n) {
			UiFont.draw(ctx, textRenderer, s.getName(), x, y + 2, label);
			String v = n.format();
			UiFont.draw(ctx, textRenderer, v, x + w - UiFont.width(textRenderer, v), y + 2, th.sub());
			float barY = y + 15;
			float f = anim(n, (float) n.getFraction(), dt, 18f);
			Render2D.roundRect(ctx, x, barY, w, 3, 1.5f, Colors.argb(Colors.mix(th.card.rgb(), 0xFFFFFF, 0.1f), 1f));
			Render2D.roundRect(ctx, x, barY, Math.max(3, w * f), 3, 1.5f, th.accent(1f));
			Render2D.circle(ctx, x + w * f, barY + 1.5f, slider == n ? 4.2f : 3.6f, th.accent(1f));
			hit(x - 3, y + 9, w + 6, 13, null, clip, (a, c, btn) -> {
				slider = n;
				sliderX = x;
				sliderW = w;
				n.setFraction((a - x) / w);
			});
		} else if (s instanceof ColorSetting c) {
			UiFont.draw(ctx, textRenderer, s.getName(), x, y + 4, label);
			String hex = String.format("#%06X", c.rgb());
			float hw = UiFont.width(textRenderer, hex) * 0.8f;
			UiFont.drawScaled(ctx, textRenderer, hex, x + w - 24 - hw, y + 5, 0.8f, th.sub());
			Render2D.roundRect(ctx, x + w - 18, y + 2, 18, 12, 4, c.argb());
			Render2D.roundRectOutline(ctx, x + w - 18, y + 2, 18, 12, 4, 1, th.accent(0.3f));
			hit(x, y, w, 16, c, clip, (a, b2, btn) -> {
				if (!openPickers.remove(c)) {
					openPickers.add(c);
				}
			});
			if (openPickers.contains(c)) {
				drawPicker(ctx, c, x, y + 19, w, clip);
			}
		} else if (s instanceof ModeSetting mode) {
			if (mode.getModes().size() > 3) {
				UiFont.draw(ctx, textRenderer, s.getName(), x, y + 2, label);
				List<String> modes = mode.getModes();
				float cw = (w - 8) / 3f;
				for (int i = 0; i < modes.size(); i++) {
					float px = x + (i % 3) * (cw + 4);
					float py = y + 14 + (i / 3) * 16;
					boolean sel = mode.is(modes.get(i));
					boolean hover = in(mx, my, px, py, cw, 14);
					Render2D.roundRect(ctx, px, py, cw, 14, 5, sel ? th.accent(0.95f) : (hover ? th.card(1f) : th.card(0.65f)));
					String name = modes.get(i);
					float ts = Math.min(0.8f, (cw - 6) / Math.max(1f, UiFont.width(textRenderer, name)));
					UiFont.drawScaled(ctx, textRenderer, name, px + cw / 2f - UiFont.width(textRenderer, name) * ts / 2f, py + 7 - 4.5f * ts, ts, sel ? th.onAccent() : th.text());
					final String pick = name;
					hit(px, py, cw, 14, null, clip, (a, b2, btn) -> mode.set(pick));
				}
			} else {
				UiFont.draw(ctx, textRenderer, s.getName(), x, y + 4, label);
				String v = mode.get();
				float vw = UiFont.width(textRenderer, v) + 18;
				boolean hover = in(mx, my, x + w - vw, y + 1, vw, 14);
				Render2D.roundRect(ctx, x + w - vw, y + 1, vw, 14, 5, hover ? th.card(1f) : th.card(0.65f));
				UiFont.draw(ctx, textRenderer, v, x + w - vw + 9, y + 4, th.text());
				hit(x, y, w, 16, null, clip, (a, b2, btn) -> mode.cycle(btn == 1 ? -1 : 1));
			}
		} else if (s instanceof ActionSetting action) {
			UiFont.draw(ctx, textRenderer, s.getName(), x, y + 4, label);
			String bt = action.buttonText();
			float bw = UiFont.width(textRenderer, bt) + 16;
			boolean hover = in(mx, my, x + w - bw, y, bw, 16);
			Render2D.roundRect(ctx, x + w - bw, y + 1, bw, 14, 7, th.accent(hover ? 1f : 0.88f));
			UiFont.draw(ctx, textRenderer, bt, x + w - bw + 8, y + 4, th.onAccent());
			hit(x + w - bw, y, bw, 16, null, clip, (a, b2, btn) -> action.run());
		} else if (s instanceof InfoSetting info) {
			UiFont.draw(ctx, textRenderer, s.getName(), x, y + 4, label);
			String v = info.text();
			float maxW = w - UiFont.width(textRenderer, s.getName()) - 12;
			if (UiFont.width(textRenderer, v) > maxW) {
				while (UiFont.width(textRenderer, v + "…") > maxW && v.length() > 1) {
					v = v.substring(0, v.length() - 1);
				}
				v = v + "…";
			}
			UiFont.draw(ctx, textRenderer, v, x + w - UiFont.width(textRenderer, v), y + 4, th.sub());
		} else if (s instanceof TextSetting text) {
			UiFont.draw(ctx, textRenderer, s.getName(), x, y + 6, label);
			float bw = Math.min(160, w * 0.58f);
			float bx = x + w - bw;
			boolean active = editing == text;
			Render2D.roundRect(ctx, bx, y + 2, bw, 15, 5, th.bg(0.9f));
			Render2D.roundRectOutline(ctx, bx, y + 2, bw, 15, 5, 1, th.accent(active ? 0.8f : 0.18f));
			String value = active ? editBuffer : text.get();
			String shownText = value;
			while (UiFont.width(textRenderer, shownText) > bw - 12 && shownText.length() > 1) {
				shownText = shownText.substring(1);
			}
			boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
			UiFont.draw(ctx, textRenderer, shownText + (active && blink ? "_" : ""), bx + 6, y + 5, active ? th.text() : Colors.mixArgb(th.sub(), th.text(), 0.6f));
			hit(bx, y, x + w - bx, 18, text, clip, (a, b2, btn) -> {
				if (editing != text) {
					editing = text;
					editBuffer = text.get();
				}
			});
		}
	}

	private float[] hsvOf(ColorSetting c) {
		float[] hsv = Colors.toHsv(c.rgb());
		if (hsv[1] < 0.01f || hsv[2] < 0.01f) {
			hsv[0] = hues.getOrDefault(c, 0.62f);
		} else {
			hues.put(c, hsv[0]);
		}
		return hsv;
	}

	private void drawPicker(DrawContext ctx, ColorSetting c, float x, float y, float w, float[] clip) {
		Theme th = Theme.get();
		float[] hsv = hsvOf(c);
		String[] names = {"H", "S", "B"};
		for (int ch = 0; ch < 3; ch++) {
			float by = y + ch * 12;
			UiFont.drawScaled(ctx, textRenderer, names[ch], x, by + 1, 0.75f, th.sub());
			float bx = x + 10, bw = w - 10;
			int steps = Math.max(32, (int) (bw / 2));
			for (int i = 0; i < steps; i++) {
				float u0 = i / (float) steps, u1 = (i + 1) / (float) steps;
				float u = (u0 + u1) / 2f;
				int col = switch (ch) {
					case 0 -> Colors.hsv(u, 1f, 1f);
					case 1 -> Colors.hsv(hsv[0], u, Math.max(0.25f, hsv[2]));
					default -> Colors.hsv(hsv[0], hsv[1], u);
				};
				ctx.fill(Math.round(bx + bw * u0), Math.round(by + 1), Math.round(bx + bw * u1), Math.round(by + 8), 0xFF000000 | col);
			}
			float knob = bx + bw * hsv[ch];
			Render2D.roundRect(ctx, knob - 1.5f, by - 0.5f, 3, 10, 1.5f, 0xFFFFFFFF);
			Render2D.roundRectOutline(ctx, knob - 1.5f, by - 0.5f, 3, 10, 1.5f, 0.6f, 0xFF000000);
			final int channel = ch;
			hit(bx - 2, by - 1, bw + 4, 11, c, clip, (a, b, btn) -> {
				picking = c;
				pickChannel = channel;
				pickX = bx;
				pickW = bw;
				applyPick(a);
			});
		}
		float py = y + 37;
		int count = ColorSetting.PRESETS.length;
		float size = Math.min(11, (w - (count - 1) * 3) / count);
		for (int i = 0; i < count; i++) {
			int preset = ColorSetting.PRESETS[i];
			float px = x + i * (size + 3);
			Render2D.roundRect(ctx, px, py, size, size, 3, preset);
			if ((preset & 0xFFFFFF) == c.rgb()) {
				Render2D.roundRectOutline(ctx, px - 1.5f, py - 1.5f, size + 3, size + 3, 4, 1, th.accent(1f));
			}
			hit(px, py, size, size, c, clip, (a, b, btn) -> c.set(preset));
		}
	}

	private void applyPick(double mx) {
		if (picking == null) {
			return;
		}
		float f = (float) Math.max(0, Math.min(1, (mx - pickX) / pickW));
		float[] hsv = hsvOf(picking);
		hsv[pickChannel] = pickChannel == 0 ? Math.min(0.999f, f) : f;
		hues.put(picking, hsv[0]);
		picking.set(Colors.hsv(hsv[0], hsv[1], hsv[2]));
	}

	private void commitEdit() {
		if (editing != null) {
			editing.set(editBuffer);
			editing = null;
		}
	}

	private double[] local(double mx, double my) {
		if (view != View.WINDOW) {
			return new double[]{mx, my};
		}
		float k = 1f - (float) Math.pow(1f - viewSwitch, 3);
		float s = 0.95f + 0.05f * k;
		float cx = winX + winW / 2f, cy = winY + winH / 2f;
		return new double[]{cx + (mx - cx) / s, cy + (my - cy) / s};
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		double[] p = local(click.x(), click.y());
		double mx = p[0], my = p[1];
		int button = click.button();
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (mx < h.x1() || mx >= h.x2() || my < h.y1() || my >= h.y2()) {
				continue;
			}
			if (h.clip() != null && (mx < h.clip()[0] || mx >= h.clip()[2] || my < h.clip()[1] || my >= h.clip()[3])) {
				continue;
			}
			if (editing != null && h.owner() != editing) {
				commitEdit();
			}
			h.action().run(mx, my, button);
			return true;
		}
		commitEdit();
		if (view == View.WINDOW && !in(mx, my, winX, winY, winW, winH)) {
			go(View.HOME);
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseReleased(Click click) {
		boolean was = slider != null || picking != null || dragMode != 0;
		slider = null;
		picking = null;
		dragMode = 0;
		return was || super.mouseReleased(click);
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (dragMode == 1) {
			winX = (float) (click.x() - dragDx);
			winY = (float) (click.y() - dragDy);
			clampWindow();
			return true;
		}
		if (dragMode == 2) {
			winW = (float) (click.x() + dragDx - winX);
			winH = (float) (click.y() + dragDy - winY);
			clampWindow();
			return true;
		}
		double[] p = local(click.x(), click.y());
		if (slider != null) {
			slider.setFraction((p[0] - sliderX) / sliderW);
			return true;
		}
		if (picking != null) {
			applyPick(p[0]);
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		double[] p = local(mouseX, mouseY);
		for (int i = 1; i >= 0; i--) {
			float[] a = scrollArea[i];
			if (view == View.WINDOW && in(p[0], p[1], a[0], a[1], a[2], a[3])) {
				scrollTarget[i] = (float) Math.max(0, Math.min(maxScroll[i], scrollTarget[i] - verticalAmount * 24));
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		int key = input.getKeycode();
		if (editing != null) {
			switch (key) {
				case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> commitEdit();
				case GLFW.GLFW_KEY_ESCAPE -> editing = null;
				case GLFW.GLFW_KEY_BACKSPACE -> {
					if (!editBuffer.isEmpty()) {
						editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
					}
				}
				default -> {
					if (input.isPaste() && client != null) {
						String clip = client.keyboard.getClipboard();
						if (clip != null) {
							String combined = editBuffer + clip.replace('\n', ' ');
							editBuffer = combined.length() > editing.getMaxLength() ? combined.substring(0, editing.getMaxLength()) : combined;
						}
					}
				}
			}
			return true;
		}
		if (searching) {
			if (key == GLFW.GLFW_KEY_ESCAPE) {
				searching = false;
			} else if (key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
				search = search.substring(0, search.length() - 1);
			}
			return true;
		}
		if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
			close();
			return true;
		}
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			if (view == View.WINDOW) {
				go(View.HOME);
			} else {
				close();
			}
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(CharInput input) {
		String s = input.asString();
		if (editing != null) {
			if (s != null && !s.isEmpty() && editBuffer.length() < editing.getMaxLength()) {
				editBuffer += s;
			}
			return true;
		}
		if (searching) {
			if (s != null && search.length() < 32) {
				search += s;
			}
			return true;
		}
		if (view == View.HOME && s != null && !s.isBlank()) {
			searching = true;
			search = s;
			return true;
		}
		return super.charTyped(input);
	}
}
