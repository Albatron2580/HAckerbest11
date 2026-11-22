package com.whiteclient.gui;

import com.whiteclient.WhiteClient;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.modules.MenuBackground;
import com.whiteclient.render.Colors;
import com.whiteclient.setting.ActionSetting;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.InfoSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.ModeSetting;
import com.whiteclient.setting.NumberSetting;
import com.whiteclient.setting.Setting;
import com.whiteclient.setting.TextSetting;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The WHITECLIENT menu (Right Shift): white/black, sidebar categories, module cards with settings. */
public class ClickGuiScreen extends Screen {
	private static final int PANEL = 0xF20B0B0F;
	private static final int SIDEBAR = 0xFF101015;
	private static final int CARD = 0xFF16161C;
	private static final int CARD_HOVER = 0xFF1C1C24;
	private static final int TEXT = 0xFFF4F4F6;
	private static final int SUB = 0xFF8C8C98;
	private static final int DIM = 0xFF5A5A66;
	private static final int LINE = 0x18FFFFFF;
	private static final int SIDEBAR_W = 112;
	private static final int HEADER_H = 34;

	private static Category lastCategory = Category.VISUALS;

	private final Screen parent;
	private Category selected = lastCategory;
	private final Set<Module> expanded = new HashSet<>();
	private final Map<Object, Float> anims = new HashMap<>();
	private final List<Hit> hits = new ArrayList<>();

	private float scroll;
	private float scrollTarget;
	private float maxScroll;
	private float open;
	private long lastFrame = System.nanoTime();

	private NumberSetting dragging;
	private float dragX;
	private float dragW;
	private TextSetting editing;
	private String editBuffer = "";

	private int px, py, pw, ph;
	private float clipX1, clipY1, clipX2, clipY2;

	private record Hit(float x1, float y1, float x2, float y2, boolean clipped, Object owner, Action action) {
	}

	@FunctionalInterface
	private interface Action {
		void run(double mouseX, double mouseY, int button);
	}

	public ClickGuiScreen(Screen parent) {
		super(Text.literal("WHITECLIENT"));
		this.parent = parent;
	}

	/** Opens on a given category with one module already expanded. */
	public ClickGuiScreen(Screen parent, Category category, Module expand) {
		this(parent);
		this.selected = category;
		if (expand != null) {
			expanded.add(expand);
		}
	}

	@Override
	protected void init() {
		pw = Math.min(470, width - 24);
		ph = Math.min(290, height - 24);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		commitEdit();
		WhiteClient.get().config().save();
		lastCategory = selected;
		if (client != null) {
			client.setScreen(parent);
		}
	}

	// ------------------------------------------------------------------ animation helpers

	private float anim(Object key, float target, float dt, float speed) {
		float v = anims.getOrDefault(key, target);
		v += (target - v) * Math.min(1f, dt * speed);
		if (Math.abs(target - v) < 0.002f) {
			v = target;
		}
		anims.put(key, v);
		return v;
	}

	private void hit(float x1, float y1, float x2, float y2, boolean clipped, Action action) {
		hits.add(new Hit(x1, y1, x2, y2, clipped, null, action));
	}

	private void hit(float x1, float y1, float x2, float y2, Object owner, Action action) {
		hits.add(new Hit(x1, y1, x2, y2, true, owner, action));
	}

	private static boolean inside(double mx, double my, float x1, float y1, float x2, float y2) {
		return mx >= x1 && mx < x2 && my >= y1 && my < y2;
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		MenuBackground bg = WhiteClient.get().modules().get(MenuBackground.class);
		if (bg != null && bg.isEnabled() && bg.clickGui.isOn()) {
			MenuBackgroundRenderer.draw(context, width, height, 0.9f * open);
		} else {
			context.fillGradient(0, 0, width, height, Colors.withAlpha(0x000000, (int) (120 * open)), Colors.withAlpha(0x000000, (int) (190 * open)));
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1.0e9f);
		lastFrame = now;
		open = Math.min(1f, open + dt * 6f);
		float ease = 1f - (float) Math.pow(1f - open, 3);
		float time = WhiteClient.get().time();

		scroll += (scrollTarget - scroll) * Math.min(1f, dt * 14f);
		hits.clear();

		Matrix3x2fStack m = context.getMatrices();
		m.pushMatrix();
		float s = 0.94f + 0.06f * ease;
		m.translate(width / 2f, height / 2f);
		m.scale(s, s);
		m.translate(-width / 2f, -height / 2f);

		Render2D.shadow(context, px, py, pw, ph, 10, 8, 120);
		Render2D.roundRectBordered(context, px, py, pw, ph, 10, PANEL, 0x26FFFFFF);

		drawSidebar(context, mouseX, mouseY, dt, time);
		drawContent(context, mouseX, mouseY, dt);

		m.popMatrix();
		super.render(context, mouseX, mouseY, deltaTicks);
	}

	private void drawSidebar(DrawContext ctx, int mx, int my, float dt, float time) {
		Render2D.roundRect(ctx, px, py, SIDEBAR_W, ph, 10, SIDEBAR);
		ctx.fill(px + SIDEBAR_W - 10, py, px + SIDEBAR_W, py + ph, SIDEBAR);
		ctx.fill(px + SIDEBAR_W, py + 8, px + SIDEBAR_W + 1, py + ph - 8, LINE);

		// logo
		Render2D.logo(ctx, px + 20, py + 19, 9, time);
		Render2D.text(ctx, textRenderer, "WHITE", px + 34, py + 11, TEXT);
		Render2D.text(ctx, textRenderer, "CLIENT", px + 34 + textRenderer.getWidth("WHITE"), py + 11, SUB);
		Render2D.textScaled(ctx, textRenderer, "visuals · 1.21.11", px + 34, py + 22, 0.75f, DIM);

		float y = py + 46;
		for (Category c : Category.values()) {
			boolean sel = c == selected;
			boolean hover = inside(mx, my, px + 8, y, px + SIDEBAR_W - 8, y + 22);
			float a = anim("cat" + c.name(), sel ? 1f : 0f, dt, 14f);
			if (a > 0.01f) {
				Render2D.roundRect(ctx, px + 8, y, SIDEBAR_W - 16, 22, 6, Colors.argb(0xFFFFFF, 0.95f * a));
			} else if (hover) {
				Render2D.roundRect(ctx, px + 8, y, SIDEBAR_W - 16, 22, 6, 0x12FFFFFF);
			}
			int color = Colors.mixArgb(hover ? TEXT : SUB, 0xFF0B0B0F, a);
			Render2D.text(ctx, textRenderer, c.title, px + 18, y + 7, color);
			int count = 0;
			for (Module mod : WhiteClient.get().modules().byCategory(c)) {
				if (mod.isEnabled()) {
					count++;
				}
			}
			String cnt = Integer.toString(count);
			Render2D.text(ctx, textRenderer, cnt, px + SIDEBAR_W - 18 - textRenderer.getWidth(cnt), y + 7, Colors.mixArgb(DIM, 0xFF6A6A74, a));
			final Category cat = c;
			hit(px + 8, y, px + SIDEBAR_W - 8, y + 22, false, (x, yy, b) -> {
				if (selected != cat) {
					commitEdit();
					selected = cat;
					scroll = scrollTarget = 0;
				}
			});
			y += 26;
		}

		Render2D.textScaled(ctx, textRenderer, "RSHIFT  close", px + 14, py + ph - 26, 0.75f, DIM);
		Render2D.textScaled(ctx, textRenderer, "/whiteclient text", px + 14, py + ph - 16, 0.75f, DIM);
	}

	private void drawContent(DrawContext ctx, int mx, int my, float dt) {
		float cx = px + SIDEBAR_W + 12;
		float cw = pw - SIDEBAR_W - 24;
		Render2D.text(ctx, textRenderer, selected.title, cx, py + 12, TEXT);
		Render2D.textScaled(ctx, textRenderer, selected.subtitle, cx + textRenderer.getWidth(selected.title) + 6, py + 13.5f, 0.75f, SUB);

		// close button
		float bx = px + pw - 24;
		float by = py + 8;
		boolean hoverClose = inside(mx, my, bx, by, bx + 16, by + 16);
		Render2D.circle(ctx, bx + 8, by + 8, 8, hoverClose ? 0x30FFFFFF : 0x14FFFFFF);
		Render2D.textCentered(ctx, textRenderer, "×", bx + 8, by + 4, hoverClose ? TEXT : SUB);
		hit(bx, by, bx + 16, by + 16, false, (x, y, b) -> close());

		clipX1 = cx - 2;
		clipY1 = py + HEADER_H;
		clipX2 = cx + cw + 2;
		clipY2 = py + ph - 8;
		ctx.fill((int) cx, (int) clipY1 - 1, (int) (cx + cw), (int) clipY1, LINE);
		ctx.enableScissor((int) clipX1, (int) clipY1, (int) clipX2, (int) clipY2);

		float y = clipY1 + 8 - scroll;
		float start = y;
		for (Module module : WhiteClient.get().modules().byCategory(selected)) {
			y += drawModule(ctx, module, cx, y, cw, mx, my, dt) + 6;
		}
		float contentHeight = y - start + 4;
		maxScroll = Math.max(0, contentHeight - (clipY2 - clipY1 - 8));
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		ctx.disableScissor();

		// scrollbar
		if (maxScroll > 0) {
			float trackH = clipY2 - clipY1 - 8;
			float barH = Math.max(18, trackH * trackH / (trackH + maxScroll));
			float barY = clipY1 + 4 + (trackH - barH) * (scroll / maxScroll);
			Render2D.roundRect(ctx, px + pw - 9, barY, 3, barH, 1.5f, 0x40FFFFFF);
		}
	}

	private float settingsHeight(Module module) {
		float h = 4;
		for (Setting<?> s : module.getSettings()) {
			if (s.isVisible()) {
				h += rowHeight(s);
			}
		}
		return h + 4;
	}

	private static float rowHeight(Setting<?> s) {
		if (s instanceof NumberSetting) {
			return 24;
		}
		if (s instanceof ColorSetting) {
			return 22;
		}
		if (s instanceof TextSetting) {
			return 22;
		}
		return 18;
	}

	private float drawModule(DrawContext ctx, Module module, float x, float y, float w, int mx, int my, float dt) {
		float head = 34;
		boolean isOpen = expanded.contains(module);
		float e = anim(module.getName() + "#open", isOpen ? 1f : 0f, dt, 12f);
		float total = head + (module.getSettings().isEmpty() ? 0 : settingsHeight(module) * e);
		boolean hover = inside(mx, my, x, y, x + w, y + head) && inside(mx, my, clipX1, clipY1, clipX2, clipY2);

		Render2D.roundRect(ctx, x, y, w, total, 7, hover ? CARD_HOVER : CARD);
		if (module.isEnabled()) {
			Render2D.roundRect(ctx, x, y + 9, 2, head - 18, 1, 0xFFFFFFFF);
		}
		Render2D.text(ctx, textRenderer, module.getName(), x + 11, y + 8, module.isEnabled() ? TEXT : 0xFFB4B4BE);
		Render2D.textScaled(ctx, textRenderer, module.getDescription(), x + 11, y + 20, 0.75f, SUB);

		float t = anim(module.getName() + "#on", module.isEnabled() ? 1f : 0f, dt, 14f);
		float tx = x + w - 30;
		float ty = y + (head - 11) / 2f;
		Render2D.toggle(ctx, tx, ty, t);
		hit(tx - 4, y, x + w, y + head, true, (mxx, myy, b) -> module.toggle());

		if (!module.getSettings().isEmpty()) {
			String chevron = isOpen ? "−" : "+";
			Render2D.textCentered(ctx, textRenderer, chevron, tx - 12, y + 13, hover ? TEXT : DIM);
			hit(x, y, tx - 4, y + head, true, (mxx, myy, b) -> {
				if (!expanded.remove(module)) {
					expanded.add(module);
				}
			});
		} else {
			hit(x, y, tx - 4, y + head, true, (mxx, myy, b) -> module.toggle());
		}

		if (e > 0.01f && !module.getSettings().isEmpty()) {
			ctx.enableScissor((int) x, (int) (y + head), (int) (x + w), (int) (y + total));
			ctx.fill((int) x + 10, (int) (y + head), (int) (x + w - 10), (int) (y + head) + 1, LINE);
			float ry = y + head + 4;
			for (Setting<?> s : module.getSettings()) {
				if (!s.isVisible()) {
					continue;
				}
				drawSetting(ctx, s, x + 11, ry, w - 22, mx, my, dt, e > 0.95f);
				ry += rowHeight(s);
			}
			ctx.disableScissor();
		}
		return total;
	}

	private void drawSetting(DrawContext ctx, Setting<?> s, float x, float y, float w, int mx, int my, float dt, boolean interactive) {
		int label = 0xFFD2D2DA;
		if (s instanceof BoolSetting b) {
			Render2D.text(ctx, textRenderer, s.getName(), x, y + 5, label);
			float t = anim(b, b.isOn() ? 1f : 0f, dt, 14f);
			Matrix3x2fStack m = ctx.getMatrices();
			m.pushMatrix();
			m.translate(x + w - 16, y + 4);
			m.scale(0.8f, 0.8f);
			Render2D.toggle(ctx, 0, 0, t);
			m.popMatrix();
			if (interactive) {
				hit(x, y, x + w, y + 16, true, (a, c, btn) -> b.toggle());
			}
		} else if (s instanceof NumberSetting n) {
			Render2D.text(ctx, textRenderer, s.getName(), x, y + 3, label);
			String v = n.format();
			Render2D.text(ctx, textRenderer, v, x + w - textRenderer.getWidth(v), y + 3, SUB);
			float barY = y + 15;
			float f = anim(n, (float) n.getFraction(), dt, 18f);
			Render2D.roundRect(ctx, x, barY, w, 3, 1.5f, 0xFF2A2A33);
			Render2D.roundRect(ctx, x, barY, Math.max(3, w * f), 3, 1.5f, 0xFFF2F2F5);
			Render2D.circle(ctx, x + w * f, barY + 1.5f, dragging == n ? 4.2f : 3.6f, 0xFFFFFFFF);
			if (interactive) {
				hit(x - 3, y + 10, x + w + 3, y + 22, true, (a, c, btn) -> {
					dragging = n;
					dragX = x;
					dragW = w;
					n.setFraction((a - x) / w);
				});
			}
		} else if (s instanceof ColorSetting c) {
			Render2D.text(ctx, textRenderer, s.getName(), x, y + 6, label);
			int count = ColorSetting.PRESETS.length;
			float size = 9;
			float gap = 3;
			float sx = x + w - count * (size + gap) + gap;
			for (int i = 0; i < count; i++) {
				int preset = ColorSetting.PRESETS[i];
				float bx = sx + i * (size + gap);
				boolean sel = (preset & 0xFFFFFF) == c.rgb();
				if (sel) {
					Render2D.roundRect(ctx, bx - 1.5f, y + 3.5f, size + 3, size + 3, 3.5f, 0xFFFFFFFF);
					Render2D.roundRect(ctx, bx - 0.5f, y + 4.5f, size + 1, size + 1, 2.5f, 0xFF0B0B0F);
				}
				Render2D.roundRect(ctx, bx, y + 5, size, size, 2.5f, preset);
				if (interactive) {
					hit(bx - 1, y + 3, bx + size + 1, y + 17, true, (a, b2, btn) -> c.set(preset));
				}
			}
			// current colour preview (custom colours from the config file show here)
			Render2D.roundRect(ctx, sx - 16, y + 5, 9, 9, 4.5f, c.argb());
		} else if (s instanceof ModeSetting mode) {
			Render2D.text(ctx, textRenderer, s.getName(), x, y + 5, label);
			String v = "‹ " + mode.get() + " ›";
			float vw = textRenderer.getWidth(v);
			boolean hover = inside(mx, my, x + w - vw - 6, y + 1, x + w, y + 16);
			Render2D.roundRect(ctx, x + w - vw - 8, y + 1, vw + 8, 14, 4, hover ? 0x22FFFFFF : 0x12FFFFFF);
			Render2D.text(ctx, textRenderer, v, x + w - vw - 4, y + 4, TEXT);
			if (interactive) {
				hit(x, y, x + w, y + 16, true, (a, b2, btn) -> mode.cycle(btn == 1 ? -1 : 1));
			}
		} else if (s instanceof ActionSetting action) {
			Render2D.text(ctx, textRenderer, s.getName(), x, y + 5, label);
			String bt = action.buttonText();
			float bw = textRenderer.getWidth(bt) + 16;
			boolean hover = inside(mx, my, x + w - bw, y, x + w, y + 16);
			Render2D.roundRect(ctx, x + w - bw, y + 1, bw, 14, 7, hover ? 0xFFFFFFFF : 0xFFE6E6EA);
			Render2D.text(ctx, textRenderer, bt, x + w - bw + 8, y + 4, 0xFF0B0B0F);
			if (interactive) {
				hit(x + w - bw, y, x + w, y + 16, true, (a, b2, btn) -> action.run());
			}
		} else if (s instanceof InfoSetting info) {
			Render2D.text(ctx, textRenderer, s.getName(), x, y + 5, label);
			String v = info.text();
			float maxW = w - textRenderer.getWidth(s.getName()) - 12;
			if (textRenderer.getWidth(v) > maxW) {
				while (textRenderer.getWidth(v + "\u2026") > maxW && v.length() > 1) {
					v = v.substring(0, v.length() - 1);
				}
				v = v + "\u2026";
			}
			Render2D.text(ctx, textRenderer, v, x + w - textRenderer.getWidth(v), y + 5, SUB);
		} else if (s instanceof TextSetting text) {
			Render2D.text(ctx, textRenderer, s.getName(), x, y + 6, label);
			float bw = Math.min(170, w * 0.6f);
			float bx = x + w - bw;
			boolean active = editing == text;
			Render2D.roundRectBordered(ctx, bx, y + 2, bw, 15, 4, 0xFF0E0E13, active ? 0xCCFFFFFF : 0x30FFFFFF);
			String value = active ? editBuffer : text.get();
			String shown = value;
			while (textRenderer.getWidth(shown) > bw - 12 && shown.length() > 1) {
				shown = shown.substring(1);
			}
			boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
			Render2D.text(ctx, textRenderer, shown + (active && blink ? "_" : ""), bx + 5, y + 6, active ? TEXT : 0xFFC8C8D0);
			if (interactive) {
				hit(bx, y, x + w, y + 18, text, (a, b2, btn) -> {
					if (editing != text) {
						editing = text;
						editBuffer = text.get();
					}
				});
			}
		}
	}

	// ------------------------------------------------------------------ input

	private void commitEdit() {
		if (editing != null) {
			editing.set(editBuffer);
			editing = null;
		}
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		double mx = click.x();
		double my = click.y();
		int button = click.button();
		// undo the open-animation scale for hit testing
		float ease = 1f - (float) Math.pow(1f - open, 3);
		float s = 0.94f + 0.06f * ease;
		mx = width / 2f + (mx - width / 2f) / s;
		my = height / 2f + (my - height / 2f) / s;

		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (!inside(mx, my, h.x1(), h.y1(), h.x2(), h.y2())) {
				continue;
			}
			if (h.clipped() && !inside(mx, my, clipX1, clipY1, clipX2, clipY2)) {
				continue;
			}
			if (editing != null && h.owner() != editing) {
				commitEdit(); // clicking anything else confirms the text field
			}
			h.action().run(mx, my, button);
			return true;
		}
		commitEdit();
		if (!inside(mx, my, px, py, px + pw, py + ph)) {
			close();
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseReleased(Click click) {
		if (dragging != null) {
			dragging = null;
			return true;
		}
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (dragging != null) {
			float ease = 1f - (float) Math.pow(1f - open, 3);
			float s = 0.94f + 0.06f * ease;
			double mx = width / 2f + (click.x() - width / 2f) / s;
			dragging.setFraction((mx - dragX) / dragW);
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scrollTarget = (float) Math.max(0, Math.min(maxScroll, scrollTarget - verticalAmount * 28));
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
		if (key == GLFW.GLFW_KEY_RIGHT_SHIFT || key == GLFW.GLFW_KEY_ESCAPE) {
			close();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(CharInput input) {
		if (editing != null) {
			String s = input.asString();
			if (s != null && !s.isEmpty() && editBuffer.length() < editing.getMaxLength()) {
				editBuffer += s;
			}
			return true;
		}
		return super.charTyped(input);
	}
}
