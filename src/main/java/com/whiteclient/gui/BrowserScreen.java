package com.whiteclient.gui;

import com.whiteclient.browser.WebView;
import com.whiteclient.modules.Browser;
import com.whiteclient.modules.Theme;
import com.whiteclient.render.Colors;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * The floating browser window: drag it by the top bar, resize it from the corner, pin it so it stays
 * on screen in-game. Type a web address or anything to search Google.
 */
public class BrowserScreen extends Screen {
	private static final float BAR = 24;
	private static final String[][] QUICK = {
			{"Google", "https://www.google.com"},
			{"YouTube", "https://www.youtube.com"},
			{"Wikipedia", "https://www.wikipedia.org"},
			{"Minecraft Wiki", "https://minecraft.wiki"},
			{"Modrinth", "https://modrinth.com"},
			{"Twitch", "https://www.twitch.tv"}
	};

	private final Screen parent;
	private final Browser mod;
	private TextFieldWidget address;
	private float wx, wy, ww, wh;
	private int drag; // 0 none, 1 move, 2 resize, 3 page
	private double dragDx, dragDy;
	private long opened;
	private String shownUrl = "";
	/** Keys go to the web page (after clicking it); otherwise WASD / space / shift move you while the window is open. */
	private boolean pageFocus;
	private final List<Hit> hits = new ArrayList<>();

	private record Hit(float x, float y, float w, float h, Runnable action) {
		boolean in(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	public BrowserScreen(Screen parent, Browser mod) {
		super(Text.literal("Browser"));
		this.parent = parent;
		this.mod = mod;
	}

	@Override
	protected void init() {
		WebView.startEngine();
		float[] r = mod.rect(width, height);
		wx = r[0];
		wy = r[1];
		ww = r[2];
		wh = r[3];
		if (address == null) {
			address = new TextFieldWidget(textRenderer, 0, 0, 100, 12, Text.literal("Address"));
			address.setMaxLength(2048);
			address.setDrawsBackground(false);
			address.setText(mod.lastUrl.get().isBlank() ? mod.home.get() : mod.lastUrl.get());
			address.setPlaceholder(Text.literal("Search Google or type a web address"));
			address.setCursorToStart(false);
		}
		addDrawableChild(address);
		layoutAddress();
		opened = System.currentTimeMillis();
		WebView v = mod.view();
		if (v != null) {
			v.focus(true);
		}
	}

	private float addrX() {
		return wx + 8 + 4 * 17 + 6;
	}

	private float addrW() {
		return Math.max(40, ww - (addrX() - wx) - 2 * 18 - 14);
	}

	private void layoutAddress() {
		address.setX(Math.round(addrX() + 8));
		address.setY(Math.round(wy + (BAR - 9) / 2f + 1));
		address.setWidth(Math.round(addrW() - 14));
	}

	private float pageX() {
		return wx + 4;
	}

	private float pageY() {
		return wy + BAR + 2;
	}

	private float pageW() {
		return ww - 8;
	}

	private float pageH() {
		return wh - BAR - 6;
	}

	private boolean inPage(double mx, double my) {
		return mx >= pageX() && mx < pageX() + pageW() && my >= pageY() && my < pageY() + pageH();
	}

	private int scale() {
		return Math.max(1, client.getWindow().getScaleFactor());
	}

	private void go(String typed) {
		String url = Browser.toUrl(typed);
		mod.lastUrl.set(url);
		address.setText(url);
		address.setCursorToStart(false);
		WebView v = mod.view();
		if (v != null) {
			v.load(url);
		} else if (!WebView.installed()) {
			openOutside(url);
		}
		setFocused(null);
		address.setFocused(false);
	}

	private static void openOutside(String url) {
		try {
			Util.getOperatingSystem().open(URI.create(url));
		} catch (Throwable ignored) {
		}
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		if (client.world == null) {
			ctx.fillGradient(0, 0, width, height, 0xFF0B0B10, 0xFF16161D);
		}
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		Theme th = Theme.get();
		hits.clear();
		float k = Math.min(1f, (System.currentTimeMillis() - opened) / 180f);
		float ease = 1f - (1f - k) * (1f - k);
		Matrix3x2fStack m = ctx.getMatrices();
		m.pushMatrix();
		m.translate(0, (1 - ease) * 8);

		// window
		Render2D.shadow(ctx, wx, wy, ww, wh, 11, 8, (int) (90 * ease));
		Render2D.roundRect(ctx, wx, wy, ww, wh, 11, Colors.argb(Colors.mix(th.panel.rgb(), 0x000000, 0.25f), 0.97f * ease));
		Render2D.roundRectOutline(ctx, wx, wy, ww, wh, 11, 1, Colors.argb(0xFFFFFF, 0.08f * ease));

		// top bar: back, forward, reload, home | address | pin, close
		WebView v = mod.view();
		float by = wy + BAR / 2f;
		String[] nav = {"back", "forward", "reload", "home"};
		for (int i = 0; i < nav.length; i++) {
			float cx = wx + 14 + i * 17;
			boolean hov = Math.abs(mouseX - cx) < 8 && Math.abs(mouseY - by) < 8;
			if (hov) {
				Render2D.circle(ctx, cx, by, 8, Colors.argb(0xFFFFFF, 0.1f));
			}
			int c = Colors.argb(0xFFFFFF, hov ? 0.95f : 0.62f);
			switch (i) {
				case 0 -> Icons.back(ctx, cx, by, 8, c);
				case 1 -> Icons.forward(ctx, cx, by, 8, c);
				case 2 -> reloadIcon(ctx, cx, by, c);
				default -> homeIcon(ctx, cx, by, c);
			}
			final int idx = i;
			hits.add(new Hit(cx - 8, by - 8, 16, 16, () -> navigate(idx)));
		}
		float ax = addrX(), aw = addrW();
		boolean focused = address.isFocused();
		Render2D.roundRect(ctx, ax, wy + 4, aw, BAR - 8, (BAR - 8) / 2f, Colors.argb(0xFFFFFF, focused ? 0.12f : 0.07f));
		if (focused) {
			Render2D.roundRectOutline(ctx, ax, wy + 4, aw, BAR - 8, (BAR - 8) / 2f, 1, Colors.argb(th.accent.rgb(), 0.6f));
		}
		if (v != null && !focused) {
			String u = v.url();
			if (!u.isBlank() && !u.equals(shownUrl)) {
				shownUrl = u;
				address.setText(u);
				address.setCursorToStart(false);
				mod.lastUrl.set(u);
			}
		}
		float px = wx + ww - 14 - 18, cx2 = wx + ww - 14;
		boolean pinHover = Math.abs(mouseX - px) < 8 && Math.abs(mouseY - by) < 8;
		boolean closeHover = Math.abs(mouseX - cx2) < 8 && Math.abs(mouseY - by) < 8;
		Render2D.circle(ctx, px, by, 7.5f, mod.pin.isOn() ? Colors.argb(th.accent.rgb(), 0.9f) : Colors.argb(0xFFFFFF, pinHover ? 0.14f : 0.06f));
		pinIcon(ctx, px, by, mod.pin.isOn() ? th.onAccent() : Colors.argb(0xFFFFFF, 0.8f));
		if (closeHover) {
			Render2D.circle(ctx, cx2, by, 7.5f, Colors.argb(0xFF4D5E, 0.85f));
		}
		Icons.close(ctx, cx2, by, 7, Colors.argb(0xFFFFFF, closeHover ? 1f : 0.7f));
		hits.add(new Hit(px - 8, by - 8, 16, 16, () -> mod.pin.set(!mod.pin.get())));
		hits.add(new Hit(cx2 - 8, by - 8, 16, 16, () -> {
			mod.closePage();
			close();
		}));

		// loading bar
		if (v != null && v.loading()) {
			float t = (System.currentTimeMillis() % 1100) / 1100f;
			float lw = ww * 0.3f;
			float lx = wx + (ww + lw) * t - lw;
			ctx.enableScissor(Math.round(wx + 4), Math.round(wy + BAR - 1), Math.round(wx + ww - 4), Math.round(wy + BAR + 1));
			Render2D.roundRect(ctx, lx, wy + BAR - 1, lw, 2, 1, Colors.argb(th.accent.rgb(), 0.9f));
			ctx.disableScissor();
		}

		// page
		float pX = pageX(), pY = pageY(), pW = pageW(), pH = pageH();
		Render2D.roundRect(ctx, pX, pY, pW, pH, 8, 0xFF0E0E13);
		if (!Browser.drawPage(ctx, v, pX, pY, pW, pH, 1f)) {
			drawPlaceholder(ctx, mouseX, mouseY, pX, pY, pW, pH, th);
		}
		// hint: typing vs walking
		if (client.world != null) {
			String hint = address.isFocused() ? "Enter to open · Esc to stop typing"
					: pageFocus ? "Typing on the page · Esc or click outside it to walk again"
					: "Walk with WASD · click the page to type · B or Esc to close (it stays pinned)";
			float hw = UiFont.width(textRenderer, hint) * 0.7f + 14;
			float hx = wx + ww / 2f - hw / 2f, hy = wy + wh + 4;
			if (hy + 12 < height) {
				Render2D.roundRect(ctx, hx, hy, hw, 11, 5.5f, Colors.argb(0x0B0B10, 0.85f));
				UiFont.drawScaled(ctx, textRenderer, hint, hx + 7, hy + 2.5f, 0.7f, Colors.argb(0xFFFFFF, 0.8f));
			}
		}
		// resize grip
		Icons.resize(ctx, wx + ww - 8, wy + wh - 8, 7, Colors.argb(0xFFFFFF, 0.35f));
		m.popMatrix();

		super.render(ctx, mouseX, mouseY, delta);
	}

	private void drawPlaceholder(DrawContext ctx, int mouseX, int mouseY, float x, float y, float w, float h, Theme th) {
		float cx = x + w / 2f;
		float top = y + Math.max(10, h * 0.12f);
		Icons.globe(ctx, cx, top + 10, 16, Colors.argb(0xFFFFFF, 0.85f));
		String title, body;
		boolean needMod = !WebView.installed();
		if (needMod) {
			title = "Web pages need MCEF Modern";
			body = "Put the free MCEF Modern mod (real Chrome engine, open source) in your mods folder next to WHITECLIENT and restart. "
					+ "This window then shows real websites: Google, YouTube, wikis. Until then, links open in your normal browser.";
		} else if (WebView.error() != null) {
			title = "The browser could not start";
			body = "MCEF Modern reported an error: " + WebView.error().getClass().getSimpleName()
					+ ". Restart the game; if it keeps happening, reinstall MCEF Modern.";
		} else {
			title = "Setting up the browser";
			body = "First start only: downloading the Chrome engine (" + WebView.progress() + "). You can keep playing; this window fills in by itself.";
		}
		UiFont.drawCentered(ctx, textRenderer, title, cx, top + 30, th.text());
		float ty = top + 44;
		for (String line : wrap(body, (int) (w - 30) / 0.8f)) {
			UiFont.drawScaled(ctx, textRenderer, line, cx - UiFont.width(textRenderer, line) * 0.4f, ty, 0.8f, th.sub());
			ty += 9;
		}
		if (!needMod && WebView.error() == null) {
			float pct = Math.max(0f, WebView.percent()) / 100f;
			float bw = Math.min(180, w - 40);
			Render2D.roundRect(ctx, cx - bw / 2, ty + 6, bw, 4, 2, Colors.argb(0xFFFFFF, 0.1f));
			Render2D.roundRect(ctx, cx - bw / 2, ty + 6, Math.max(4, bw * pct), 4, 2, Colors.argb(th.accent.rgb(), 0.9f));
			ty += 18;
		}
		ty += 6;
		if (needMod) {
			ty = button(ctx, mouseX, mouseY, cx, ty, "Get MCEF Modern (free)", true, () -> openOutside(WebView.MCEF_PAGE), th) + 6;
		}
		// quick links
		float qw = 66, qh = 18, gap = 6;
		int cols = Math.max(1, Math.min(QUICK.length, (int) ((w - 20 + gap) / (qw + gap))));
		float qx0 = cx - (cols * qw + (cols - 1) * gap) / 2f;
		for (int i = 0; i < QUICK.length; i++) {
			float qx = qx0 + (i % cols) * (qw + gap), qy = ty + (i / cols) * (qh + gap);
			if (qy + qh > y + h - 4) {
				break;
			}
			boolean hov = mouseX >= qx && mouseX < qx + qw && mouseY >= qy && mouseY < qy + qh;
			Render2D.roundRect(ctx, qx, qy, qw, qh, 6, Colors.argb(0xFFFFFF, hov ? 0.14f : 0.06f));
			String label = QUICK[i][0];
			UiFont.drawScaled(ctx, textRenderer, label, qx + qw / 2f - UiFont.width(textRenderer, label) * 0.39f, qy + 5.5f, 0.78f, th.text());
			final String url = QUICK[i][1];
			hits.add(new Hit(qx, qy, qw, qh, () -> go(url)));
		}
	}

	private float button(DrawContext ctx, int mx, int my, float cx, float y, String label, boolean primary, Runnable action, Theme th) {
		float w = UiFont.width(textRenderer, label) + 24, h = 18;
		float x = cx - w / 2f;
		boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
		Render2D.roundRect(ctx, x, y, w, h, 9, primary ? Colors.argb(th.accent.rgb(), hov ? 1f : 0.88f) : Colors.argb(0xFFFFFF, hov ? 0.14f : 0.07f));
		UiFont.drawCentered(ctx, textRenderer, label, cx, y + 5, primary ? th.onAccent() : th.text());
		hits.add(new Hit(x, y, w, h, action));
		return y + h;
	}

	private List<String> wrap(String text, float maxW) {
		List<String> lines = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		for (String word : text.split(" ")) {
			String test = cur.length() == 0 ? word : cur + " " + word;
			if (UiFont.width(textRenderer, test) > maxW && cur.length() > 0) {
				lines.add(cur.toString());
				cur = new StringBuilder(word);
			} else {
				cur = new StringBuilder(test);
			}
		}
		if (cur.length() > 0) {
			lines.add(cur.toString());
		}
		return lines;
	}

	private static void reloadIcon(DrawContext ctx, float cx, float cy, int c) {
		float r = 3.6f;
		for (int i = 0; i < 9; i++) {
			double a0 = Math.toRadians(-60 + i * 30), a1 = Math.toRadians(-60 + (i + 1) * 30);
			Render2D.line(ctx, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r, cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, 1.1f, c);
		}
		float ex = cx + (float) Math.cos(Math.toRadians(-60)) * r, ey = cy + (float) Math.sin(Math.toRadians(-60)) * r;
		Render2D.polygon(ctx, new float[]{ex - 2.2f, ey - 1.6f, ex + 1.8f, ey - 1.8f, ex + 0.2f, ey + 1.9f}, c);
	}

	private static void homeIcon(DrawContext ctx, float cx, float cy, int c) {
		Render2D.polygon(ctx, new float[]{cx, cy - 4.2f, cx + 4.2f, cy - 0.4f, cx - 4.2f, cy - 0.4f}, c);
		Render2D.roundRect(ctx, cx - 3, cy - 0.6f, 6, 4.4f, 0.6f, c);
	}

	private static void pinIcon(DrawContext ctx, float cx, float cy, int c) {
		Render2D.circle(ctx, cx, cy - 1.2f, 2.4f, c);
		Render2D.line(ctx, cx, cy, cx, cy + 4f, 1.1f, c);
	}

	private void navigate(int idx) {
		WebView v = mod.view();
		switch (idx) {
			case 0 -> {
				if (v != null) {
					v.back();
				}
			}
			case 1 -> {
				if (v != null) {
					v.forward();
				}
			}
			case 2 -> {
				if (v != null) {
					v.reload();
				}
			}
			default -> go(mod.home.get());
		}
	}

	// ------------------------------------------------------------------ input

	private double pageXpx(double mx) {
		return (mx - pageX()) * scale();
	}

	private double pageYpx(double my) {
		return (my - pageY()) * scale();
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		double mx = click.x(), my = click.y();
		pageFocus = false;
		if (click.button() == 0) {
			for (Hit h : hits) {
				if (h.in(mx, my)) {
					h.action().run();
					return true;
				}
			}
		}
		if (address.isMouseOver(mx, my) || (mx >= addrX() && mx < addrX() + addrW() && my >= wy + 4 && my < wy + BAR - 4)) {
			setFocused(address);
			address.setFocused(true);
			return super.mouseClicked(click, doubled);
		}
		address.setFocused(false);
		setFocused(null);
		if (mx >= wx + ww - 12 && mx < wx + ww && my >= wy + wh - 12 && my < wy + wh) {
			drag = 2;
			dragDx = wx + ww - mx;
			dragDy = wy + wh - my;
			return true;
		}
		if (mx >= wx && mx < wx + ww && my >= wy && my < wy + BAR) {
			drag = 1;
			dragDx = mx - wx;
			dragDy = my - wy;
			return true;
		}
		WebView v = mod.view();
		pageFocus = v != null && inPage(mx, my);
		if (v != null && inPage(mx, my)) {
			drag = 3;
			v.focus(true);
			v.mouseDown(click, pageXpx(mx), pageYpx(my), doubled);
			return true;
		}
		if (mx < wx || mx >= wx + ww || my < wy || my >= wy + wh) {
			return super.mouseClicked(click, doubled);
		}
		return true;
	}

	@Override
	public boolean mouseReleased(Click click) {
		int was = drag;
		drag = 0;
		if (was == 1 || was == 2) {
			mod.setRect(wx, wy, ww, wh, width, height);
			return true;
		}
		WebView v = mod.view();
		if (v != null && (was == 3 || inPage(click.x(), click.y()))) {
			v.mouseUp(click, pageXpx(click.x()), pageYpx(click.y()));
			return true;
		}
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		double mx = click.x(), my = click.y();
		if (drag == 1) {
			wx = (float) Math.max(0, Math.min(width - ww, mx - dragDx));
			wy = (float) Math.max(0, Math.min(height - wh, my - dragDy));
			layoutAddress();
			return true;
		}
		if (drag == 2) {
			ww = (float) Math.max(170, Math.min(width - wx, mx + dragDx - wx));
			wh = (float) Math.max(120, Math.min(height - wy, my + dragDy - wy));
			layoutAddress();
			return true;
		}
		WebView v = mod.view();
		if (drag == 3 && v != null) {
			v.mouseMove(pageXpx(mx), pageYpx(my));
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public void mouseMoved(double mx, double my) {
		WebView v = mod.view();
		if (v != null && inPage(mx, my)) {
			v.mouseMove(pageXpx(mx), pageYpx(my));
		}
		super.mouseMoved(mx, my);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
		WebView v = mod.view();
		if (v != null && inPage(mx, my)) {
			v.scroll(pageXpx(mx), pageYpx(my), vertical);
			return true;
		}
		return super.mouseScrolled(mx, my, horizontal, vertical);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		int key = input.getKeycode();
		if (address.isFocused()) {
			if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
				go(address.getText());
				return true;
			}
			if (key == GLFW.GLFW_KEY_ESCAPE) {
				address.setFocused(false);
				setFocused(null);
				return true;
			}
			return super.keyPressed(input);
		}
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			if (pageFocus) {
				pageFocus = false;
				return true;
			}
			close();
			return true;
		}
		WebView v = mod.view();
		if (v != null && pageFocus) {
			v.keyDown(input);
			return true;
		}
		if (key == GLFW.GLFW_KEY_B) {
			close();
			return true;
		}
		return true;
	}

	@Override
	public boolean keyReleased(KeyInput input) {
		WebView v = mod.view();
		if (v != null && !address.isFocused() && pageFocus) {
			v.keyUp(input);
			return true;
		}
		return super.keyReleased(input);
	}

	@Override
	public boolean charTyped(CharInput input) {
		WebView v = mod.view();
		if (v != null && !address.isFocused() && pageFocus) {
			v.type(input);
			return true;
		}
		return super.charTyped(input);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private net.minecraft.client.option.KeyBinding[] moveKeys() {
		var o = client.options;
		return new net.minecraft.client.option.KeyBinding[]{o.forwardKey, o.backKey, o.leftKey, o.rightKey, o.jumpKey, o.sneakKey, o.sprintKey};
	}

	/** While you are not typing, you can keep walking around with the window open (it stays in place). */
	@Override
	public void tick() {
		super.tick();
		if (client.world == null || client.player == null) {
			return;
		}
		boolean walk = !address.isFocused() && !pageFocus;
		for (net.minecraft.client.option.KeyBinding k : moveKeys()) {
			boolean down = false;
			if (walk) {
				try {
					net.minecraft.client.util.InputUtil.Key bound = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(k);
					if (bound.getCategory() == net.minecraft.client.util.InputUtil.Type.KEYSYM) {
						down = net.minecraft.client.util.InputUtil.isKeyPressed(client.getWindow(), bound.getCode());
					}
				} catch (Throwable ignored) {
				}
			}
			k.setPressed(down);
		}
	}

	@Override
	public void removed() {
		for (net.minecraft.client.option.KeyBinding k : moveKeys()) {
			k.setPressed(false);
		}
		super.removed();
	}

	@Override
	public void close() {
		mod.setRect(wx, wy, ww, wh, width, height);
		WebView v = mod.view();
		if (v != null) {
			v.focus(false);
		}
		if (!mod.pin.isOn()) {
			mod.closePage();
		}
		client.setScreen(parent);
	}
}
