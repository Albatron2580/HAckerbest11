package com.whiteclient.gui;

import com.whiteclient.WhiteClient;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.modules.Aura;
import com.whiteclient.modules.BlockTrail;
import com.whiteclient.modules.Halo;
import com.whiteclient.modules.Pets;
import com.whiteclient.modules.Theme;
import com.whiteclient.modules.Wardrobe;
import com.whiteclient.modules.Wings;
import com.whiteclient.render.Colors;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.Setting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class WardrobeScreen extends Screen {
	private enum Tab { SKINS, COSMETICS, PETS }

	private final Screen parent;
	private Tab tab = Tab.SKINS;
	private SkinPreview preview;
	private Supplier<PlayerSkin> baseSkin;
	private List<String> skins = new ArrayList<>();
	private final Map<String, Float> anims = new HashMap<>();
	private final List<Hit> hits = new ArrayList<>();
	private long lastFrame = System.nanoTime();
	private final long opened = System.nanoTime();
	private float scroll, scrollTarget, maxScroll;
	private float listX, listY, listW, listH;

	private record Hit(float x1, float y1, float x2, float y2, Runnable action) {
	}

	public WardrobeScreen(Screen parent) {
		super(Component.literal("Wardrobe"));
		this.parent = parent;
	}

	public void showTab(int index) {
		tab = Tab.values()[Math.max(0, Math.min(Tab.values().length - 1, index))];
		scroll = scrollTarget = 0;
	}

	@Override
	protected void init() {
		try {
			baseSkin = minecraft.getSkinManager().createLookup(minecraft.getGameProfile(), false);
		} catch (Throwable t) {
			baseSkin = null;
		}
		skins = Wardrobe.available();
		int ph = Math.max(120, height - 96);
		int pw = Math.max(70, (int) (ph * 0.55f));
		Supplier<PlayerSkin> s = () -> {
			PlayerSkin base = null;
			try {
				base = baseSkin != null ? baseSkin.get() : null;
			} catch (Throwable ignored) {
			}
			if (base == null) {
				base = DefaultPlayerSkin.getDefaultSkin();
			}
			return Wardrobe.apply(base);
		};
		try {
			preview = new SkinPreview(pw, ph, s);
			preview.setRotation(0, -25);
			addRenderableWidget(preview.widget());
		} catch (Throwable t) {
			preview = null;
		}
		layout();
	}

	private float panelX() {
		return 16;
	}

	private float panelW() {
		return Math.min(190, width * 0.32f);
	}

	private void layout() {
		if (preview != null) {
			int pw = preview.widget().getWidth();
			preview.widget().setPosition((int) (panelX() + panelW() / 2f - pw / 2f), 52);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		WhiteClient.get().config().save();
		minecraft.setScreen(parent);
	}

	private float anim(String key, float target, float dt) {
		float v = anims.getOrDefault(key, target);
		v += (target - v) * Math.min(1f, dt * 14f);
		anims.put(key, v);
		return v;
	}

	private static boolean in(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private void hit(float x, float y, float w, float h, Runnable r) {
		hits.add(new Hit(x, y, x + w, y + h, r));
	}

	@Override
	public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		Theme th = Theme.get();
		ctx.fillGradient(0, 0, width, height, th.bg(0.985f), Colors.argb(Colors.shade(th.background.rgb(), 1.7f), 0.99f));
		float t = WhiteClient.get().time();
		float cx = panelX() + panelW() / 2f, cy = height * 0.48f;
		for (int i = 6; i >= 1; i--) {
			float r = i * 18;
			Render2D.circle(ctx, cx, cy, r, Colors.argb(th.accent.rgb(), 0.012f + 0.004f * (float) Math.sin(t + i)));
		}
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1.0e9f);
		lastFrame = now;
		scroll += (scrollTarget - scroll) * Math.min(1f, dt * 14f);
		hits.clear();
		Theme th = Theme.get();
		float open = Math.min(1f, (now - opened) / 3.0e8f);
		float k = 1f - (1f - open) * (1f - open);

		Render2D.roundRect(ctx, 16, 10, 20, 20, 6, th.card(0.8f));
		Icons.shirt(ctx, 26, 20, 10, th.text());
		UiFont.drawScaled(ctx, font, "Wardrobe", 42, 10, 1.25f, th.text());
		UiFont.drawScaled(ctx, font, "Everything is free  ·  no pay-to-win", 42, 23, 0.75f, th.sub());

		float px = panelX(), pw = panelW(), py = 40, ph = height - 64;
		Render2D.roundRect(ctx, px, py, pw, ph, 10, th.card(0.45f * k));
		Render2D.roundRectOutline(ctx, px, py, pw, ph, 10, 1, th.accent(0.1f));
		String name = minecraft.getUser().getName();
		UiFont.drawCentered(ctx, font, name, px + pw / 2f, py + 4, th.text());
		UiFont.drawScaled(ctx, font, "drag to rotate", px + pw / 2f - UiFont.width(font, "drag to rotate") * 0.36f, py + ph - 11, 0.72f, th.sub());

		float rx = px + pw + 14, rw = width - rx - 16;
		String[] tabs = {"Skins", "Cosmetics", "Pets"};
		float tx = rx;
		for (int i = 0; i < tabs.length; i++) {
			float tw = UiFont.width(font, tabs[i]) + 22;
			boolean sel = tab.ordinal() == i;
			boolean hover = in(mouseX, mouseY, tx, py, tw, 18);
			float a = anim("tab" + i, sel ? 1f : 0f, dt);
			Render2D.roundRect(ctx, tx, py, tw, 18, 9, Colors.mixArgb(th.card(hover ? 0.8f : 0.5f), th.accent(0.95f), a));
			UiFont.draw(ctx, font, tabs[i], tx + 11, py + 5, Colors.mixArgb(th.text(), th.onAccent(), a));
			final Tab target = Tab.values()[i];
			hit(tx, py, tw, 18, () -> {
				tab = target;
				scroll = scrollTarget = 0;
			});
			tx += tw + 6;
		}
		String sup = "Support";
		float sw = UiFont.width(font, sup) + 26;
		float sx = width - 16 - sw;
		boolean hs = in(mouseX, mouseY, sx, 10, sw, 18);
		Render2D.roundRect(ctx, sx, 10, sw, 18, 9, hs ? th.accent(1f) : th.card(0.75f));
		Render2D.roundRectOutline(ctx, sx, 10, sw, 18, 9, 1, th.accent(0.35f));
		Icons.heart(ctx, sx + 11, 19, 8, hs ? th.onAccent() : 0xFFFF6B8A);
		UiFont.draw(ctx, font, sup, sx + 19, 15, hs ? th.onAccent() : th.text());
		hit(sx, 10, sw, 18, () -> minecraft.setScreen(new SupportScreen(this)));
		String done = "Done";
		float dw = UiFont.width(font, done) + 20;
		float dx = sx - 6 - dw;
		boolean hd = in(mouseX, mouseY, dx, 10, dw, 18);
		Render2D.roundRect(ctx, dx, 10, dw, 18, 9, th.card(hd ? 1f : 0.75f));
		UiFont.draw(ctx, font, done, dx + 10, 15, th.text());
		hit(dx, 10, dw, 18, this::onClose);

		listX = rx;
		listY = py + 26;
		listW = rw;
		listH = height - listY - 24;
		ctx.enableScissor((int) listX - 2, (int) listY, (int) (listX + listW + 2), (int) (listY + listH));
		float contentH = switch (tab) {
			case SKINS -> drawSkins(ctx, mouseX, mouseY, dt);
			case COSMETICS -> drawCosmetics(ctx, mouseX, mouseY, dt);
			case PETS -> drawPets(ctx, mouseX, mouseY, dt);
		};
		ctx.disableScissor();
		maxScroll = Math.max(0, contentH - listH);
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		UiFont.drawScaled(ctx, font, "Custom skins are only visible to you.  Esc to go back.", rx, height - 14, 0.75f, Colors.mulAlpha(th.sub(), 0.8f));

		if (preview != null) {
			float t = WhiteClient.get().time();
			preview.pose(0, 0, t);
		}
		super.render(ctx, mouseX, mouseY, delta);
	}

	private void listHit(float x, float y, float w, float h, Runnable r) {
		float y1 = Math.max(y, listY), y2 = Math.min(y + h, listY + listH);
		if (y2 > y1) {
			hits.add(new Hit(x, y1, x + w, y2, r));
		}
	}

	private boolean visible(float y, float h) {
		return y + h > listY && y < listY + listH;
	}

	private float drawSkins(GuiGraphics ctx, int mx, int my, float dt) {
		Theme th = Theme.get();
		Wardrobe w = Wardrobe.get();
		float y0 = listY - scroll;
		float card = 58, gap = 8;
		int cols = Math.max(1, (int) ((listW + gap) / (card + gap)));
		List<String> items = new ArrayList<>();
		items.add("");
		items.addAll(skins);
		for (int i = 0; i < items.size(); i++) {
			String s = items.get(i);
			float x = listX + (i % cols) * (card + gap);
			float y = y0 + (i / cols) * (card + 14 + gap);
			if (!visible(y, card + 14)) {
				continue;
			}
			boolean sel = w != null && w.skin.get().equals(s);
			boolean hover = in(mx, my, x, y, card, card + 14) && in(mx, my, listX, listY, listW, listH);
			float h = anim("skin" + s, hover ? 1f : 0f, dt);
			Render2D.roundRect(ctx, x, y - h, card, card + 14, 8, th.card(0.55f + 0.35f * h));
			Render2D.roundRectOutline(ctx, x, y - h, card, card + 14, 8, 1, th.accent(sel ? 0.9f : 0.08f + 0.3f * h));
			int face = 32;
			int fx = (int) (x + card / 2f - face / 2f), fy = (int) (y - h + 9);
			try {
				if (s.isEmpty()) {
					if (baseSkin != null) {
						PlayerFaceRenderer.draw(ctx, baseSkin.get(), fx, fy, face);
					}
				} else {
					Identifier tex = Wardrobe.texturePathFor(s);
					if (tex != null) {
						PlayerFaceRenderer.draw(ctx, tex, fx, fy, face, true, false, 0xFFFFFFFF);
					} else {
						Render2D.roundRect(ctx, fx, fy, face, face, 4, 0x40FF4D6A);
						UiFont.drawCentered(ctx, font, "!", fx + face / 2f, fy + 12, 0xFFFF8095);
					}
				}
			} catch (Throwable ignored) {
			}
			String label = s.isEmpty() ? "Default" : s;
			while (UiFont.width(font, label) * 0.8f > card - 6 && label.length() > 2) {
				label = label.substring(0, label.length() - 1);
			}
			UiFont.drawScaled(ctx, font, label, x + card / 2f - UiFont.width(font, label) * 0.4f, y - h + 48, 0.8f, th.text());
			if (sel) {
				UiFont.drawScaled(ctx, font, "wearing", x + card / 2f - UiFont.width(font, "wearing") * 0.34f, y - h + 59, 0.68f, th.sub());
			} else if (!s.isEmpty() && Wardrobe.error(s) != null) {
				UiFont.drawScaled(ctx, font, "64x64 only", x + card / 2f - UiFont.width(font, "64x64 only") * 0.34f, y - h + 59, 0.68f, 0xFFFF8095);
			}
			final String pick = s;
			listHit(x, y, card, card + 14, () -> {
				if (w != null) {
					w.skin.set(pick);
					if (!w.isEnabled()) {
						w.toggle();
					}
				}
			});
		}
		int rows = (items.size() + cols - 1) / cols;
		float by = y0 + rows * (card + 14 + gap) + 4;
		String[] labels = {"Open Skins Folder", "Reload", "Slim Arms: " + (w != null && w.slim.isOn() ? "On" : "Off")};
		float bx = listX;
		for (int i = 0; i < labels.length; i++) {
			float bw = UiFont.width(font, labels[i]) + 18;
			boolean hover = in(mx, my, bx, by, bw, 18);
			Render2D.roundRect(ctx, bx, by, bw, 18, 9, i == 0 ? th.accent(hover ? 1f : 0.88f) : th.card(hover ? 1f : 0.7f));
			UiFont.draw(ctx, font, labels[i], bx + 9, by + 5, i == 0 ? th.onAccent() : th.text());
			final int idx = i;
			listHit(bx, by, bw, 18, () -> {
				switch (idx) {
					case 0 -> {
						try {
							Util.getPlatform().openFile(Wardrobe.folder().toFile());
						} catch (Throwable ignored) {
						}
					}
					case 1 -> {
						Wardrobe.reload();
						skins = Wardrobe.available();
					}
					default -> {
						if (w != null) {
							w.slim.toggle();
						}
					}
				}
			});
			bx += bw + 6;
		}
		float ty = by + 26;
		String help = "How to add a skin: put a 64x64 skin .png in .minecraft/config/whiteclient/skins, then press Reload and click it. "
				+ "Any skin PNG works (download one from a skin site). It is free and stays on your PC.";
		ty = wrapped(ctx, help, listX, ty, listW, 0.78f, th.sub());
		return ty - y0 + 10;
	}

	private float wrapped(GuiGraphics ctx, String text, float x, float y, float maxW, float scale, int color) {
		String[] words = text.split(" ");
		StringBuilder line = new StringBuilder();
		for (String w : words) {
			String test = line.isEmpty() ? w : line + " " + w;
			if (UiFont.width(font, test) * scale > maxW && !line.isEmpty()) {
				UiFont.drawScaled(ctx, font, line.toString(), x, y, scale, color);
				y += 10 * scale + 2;
				line = new StringBuilder(w);
			} else {
				line = new StringBuilder(test);
			}
		}
		if (!line.isEmpty()) {
			UiFont.drawScaled(ctx, font, line.toString(), x, y, scale, color);
			y += 10 * scale + 2;
		}
		return y;
	}

	private void drawToggle(GuiGraphics ctx, float x, float y, float t) {
		Theme th = Theme.get();
		float w = 20, h = 11;
		int track = Colors.mixArgb(Colors.argb(Colors.mix(th.card.rgb(), 0xFFFFFF, 0.08f), 1f), th.toggleOn.argb(), t);
		Render2D.roundRect(ctx, x, y, w, h, h / 2f, track);
		int onKnob = Theme.lum(th.toggleOn.rgb()) > 0.6f ? 0xFF0E0E12 : 0xFFFFFFFF;
		Render2D.circle(ctx, x + 5.5f + (w - 11f) * t, y + h / 2f, 3.6f, Colors.mixArgb(0xFF8C8C98, onKnob, t));
	}

	private float drawCosmetics(GuiGraphics ctx, int mx, int my, float dt) {
		Theme th = Theme.get();
		var mm = WhiteClient.get().modules();
		List<Module> list = new ArrayList<>();
		for (Class<? extends Module> c : List.of(Halo.class, Wings.class, Aura.class, com.whiteclient.modules.Capes.class, com.whiteclient.modules.ChinaHat.class,
				com.whiteclient.modules.Trail.class, com.whiteclient.modules.JumpCircles.class, BlockTrail.class, Pets.class)) {
			Module m = mm.get(c);
			if (m != null) {
				list.add(m);
			}
		}
		float y = listY - scroll;
		float rowH = 40;
		for (Module m : list) {
			if (visible(y, rowH)) {
				boolean hover = in(mx, my, listX, y, listW, rowH - 4);
				Render2D.roundRect(ctx, listX, y, listW, rowH - 4, 8, th.card(hover ? 0.85f : 0.6f));
				UiFont.draw(ctx, font, m.getName(), listX + 10, y + 7, th.text());
				UiFont.drawScaled(ctx, font, m.getDescription(), listX + 10, y + 19, 0.75f, th.sub());
				float t = anim("on" + m.getName(), m.isEnabled() ? 1f : 0f, dt);
				drawToggle(ctx, listX + listW - 30, y + 12, t);
				listHit(listX + listW - 34, y, 34, rowH - 4, m::toggle);
				ColorSetting color = null;
				for (Setting<?> s : m.getSettings()) {
					if (s instanceof ColorSetting c) {
						color = c;
						break;
					}
				}
				float sx = listX + listW - 40;
				if (color != null && listW > 300) {
					int[] presets = {0xFFFFFF, 0xBFD8FF, 0xF2C6FF, 0xFFD1A8, 0x9CFFD8, 0xFF7AD9};
					for (int i = presets.length - 1; i >= 0; i--) {
						sx -= 14;
						int p = presets[i];
						boolean sel = color.rgb() == p;
						Render2D.circle(ctx, sx + 5, y + 17.5f, sel ? 5.5f : 4.5f, 0xFF000000 | p);
						if (sel) {
							Render2D.roundRectOutline(ctx, sx - 2, y + 10.5f, 14, 14, 7, 1, th.accent(0.9f));
						}
						final ColorSetting cs = color;
						listHit(sx - 1, y + 11, 12, 13, () -> cs.set(p));
					}
				}
				String more = "Settings";
				float mw = UiFont.width(font, more) * 0.75f + 12;
				float mxp = sx - 10 - mw;
				boolean hm = in(mx, my, mxp, y + 11, mw, 13);
				Render2D.roundRect(ctx, mxp, y + 11, mw, 13, 6, th.card(hm ? 1f : 0.5f));
				UiFont.drawScaled(ctx, font, more, mxp + 6, y + 14.5f, 0.75f, th.text());
				final Module target = m;
				listHit(mxp, y + 11, mw, 13, () -> minecraft.setScreen(new ClickGuiScreen(this, Category.COSMETICS, target)));
			}
			y += rowH;
		}
		return y - (listY - scroll);
	}

	private float drawPets(GuiGraphics ctx, int mx, int my, float dt) {
		Theme th = Theme.get();
		Pets pets = WhiteClient.get().modules().get(Pets.class);
		float y0 = listY - scroll;
		if (pets == null) {
			return 0;
		}
		Render2D.roundRect(ctx, listX, y0, listW, 26, 8, th.card(0.6f));
		Icons.paw(ctx, listX + 14, y0 + 13, 9, th.text());
		UiFont.draw(ctx, font, "Pet", listX + 26, y0 + 9, th.text());
		UiFont.drawScaled(ctx, font, "made of light · only you see it", listX + 48, y0 + 10, 0.75f, th.sub());
		drawToggle(ctx, listX + listW - 30, y0 + 7.5f, anim("petOn", pets.isEnabled() ? 1f : 0f, dt));
		listHit(listX + listW - 34, y0, 34, 26, pets::toggle);

		float card = 62, gap = 8;
		int cols = Math.max(1, (int) ((listW + gap) / (card + gap)));
		String[] types = Pets.TYPES;
		float gy = y0 + 34;
		for (int i = 0; i < types.length; i++) {
			String type = types[i];
			float x = listX + (i % cols) * (card + gap);
			float y = gy + (i / cols) * (34 + gap);
			if (!visible(y, 34)) {
				continue;
			}
			boolean sel = pets.pet.is(type);
			boolean hover = in(mx, my, x, y, card, 34);
			float h = anim("pet" + type, hover ? 1f : 0f, dt);
			Render2D.roundRect(ctx, x, y - h, card, 34, 8, sel ? th.accent(0.92f) : th.card(0.55f + 0.35f * h));
			int col = sel ? th.onAccent() : th.text();
			if (!Pets.isGround(type)) {
				Icons.sparkle(ctx, x + card / 2f, y - h + 12, 8, col);
			} else {
				Icons.paw(ctx, x + card / 2f, y - h + 12, 8, col);
			}
			UiFont.drawScaled(ctx, font, type, x + card / 2f - UiFont.width(font, type) * 0.4f, y - h + 22, 0.8f, col);
			listHit(x, y, card, 34, () -> {
				pets.pet.set(type);
				if (!pets.isEnabled()) {
					pets.toggle();
				}
			});
		}
		int rows = (types.length + cols - 1) / cols;
		float end = gy + rows * (34 + gap);
		float after = wrapped(ctx, "Name, size, colours and floating over your head when you stand still: Right Shift → Cosmetics → Pets.", listX, end + 2, listW, 0.78f, th.sub());
		return after - y0 + 6;
	}

	private static void openLink(String url) {
		try {
			if (url != null && !url.isBlank()) {
				Util.getPlatform().openUri(URI.create(url.trim()));
			}
		} catch (Throwable ignored) {
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double mx = click.x(), my = click.y();
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (mx >= h.x1() && mx < h.x2() && my >= h.y1() && my < h.y2()) {
				h.action().run();
				return true;
			}
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (in(mouseX, mouseY, listX, listY, listW, listH)) {
			scrollTarget = (float) Math.max(0, Math.min(maxScroll, scrollTarget - verticalAmount * 24));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (input.input() == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(input);
	}
}
