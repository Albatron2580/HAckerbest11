//#file MC >= 1.21.9
package com.whiteclient.modules;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
//#if MC >= 1.21.11
import com.mojang.blaze3d.textures.GpuSampler;
//#endif
import com.mojang.blaze3d.textures.GpuTextureView;
import com.whiteclient.WhiteClient;
import com.whiteclient.browser.WebView;
import com.whiteclient.gui.BrowserScreen;
import com.whiteclient.gui.Render2D;
import com.whiteclient.gui.UiFont;
import com.whiteclient.mixin.DrawContextInvoker;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.NumberSetting;
import com.whiteclient.setting.TextSetting;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;

public class Browser extends Module {
	public final TextSetting home = add(new TextSetting("Home Page", "https://www.google.com", 300));
	public final BoolSetting pin = add(new BoolSetting("Stay On Screen", true));
	public final NumberSetting opacity = add(new NumberSetting("Opacity", 1.0, 0.3, 1.0, 0.05));
	public final NumberSetting zoom = add(new NumberSetting("Page Zoom", 0.9, 0.5, 1.5, 0.05, "x"));
	public final NumberSetting winX = add(new NumberSetting("Window X", 0.5, 0, 1, 0.001).visibleWhen(() -> false));
	public final NumberSetting winY = add(new NumberSetting("Window Y", 0.06, 0, 1, 0.001).visibleWhen(() -> false));
	public final NumberSetting winW = add(new NumberSetting("Window Width", 0.47, 0.15, 1, 0.001).visibleWhen(() -> false));
	public final NumberSetting winH = add(new NumberSetting("Window Height", 0.62, 0.15, 1, 0.001).visibleWhen(() -> false));
	public final TextSetting lastUrl = add(new TextSetting("Last Page", "", 2000).visibleWhen(() -> false));

	private WebView view;
	//#if MC >= 1.21.11
	private static GpuSampler sampler;
	//#endif

	public Browser() {
		super("Browser", "Floating web browser window: press B (stays on screen while you play)", Category.HUD, true);
	}

	public static Browser get() {
		WhiteClient wc = WhiteClient.get();
		return wc == null ? null : wc.modules().get(Browser.class);
	}

	public WebView view() {
		if (view == null && WebView.ready()) {
			String start = lastUrl.get().isBlank() ? home.get() : lastUrl.get();
			view = WebView.create(start);
		}
		return view;
	}

	public boolean hasPage() {
		return view != null;
	}

	public void closePage() {
		if (view != null) {
			String u = view.url();
			if (!u.isBlank()) {
				lastUrl.set(u);
			}
			view.close();
			view = null;
		}
	}

	public void open() {
		Minecraft mc = Minecraft.getInstance();
		WebView.startEngine();
		mc.setScreen(new BrowserScreen(mc.screen, this));
	}

	@Override
	protected void onDisable() {
		closePage();
	}

	public static String toUrl(String typed) {
		String s = typed.trim();
		if (s.isEmpty()) {
			return "https://www.google.com";
		}
		if (s.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) {
			return s;
		}
		if (!s.contains(" ") && (s.contains(".") || s.startsWith("localhost"))) {
			return "https://" + s;
		}
		return "https://www.google.com/search?q=" + URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	public float[] rect(int screenW, int screenH) {
		float w = Math.max(160, winW.getFloat() * screenW);
		float h = Math.max(110, winH.getFloat() * screenH);
		w = Math.min(w, screenW - 4);
		h = Math.min(h, screenH - 4);
		float x = Math.max(2, Math.min(screenW - w - 2, winX.getFloat() * screenW));
		float y = Math.max(2, Math.min(screenH - h - 2, winY.getFloat() * screenH));
		return new float[]{x, y, w, h};
	}

	public void setRect(float x, float y, float w, float h, int screenW, int screenH) {
		winX.set((double) (x / screenW));
		winY.set((double) (y / screenH));
		winW.set((double) (w / screenW));
		winH.set((double) (h / screenH));
	}

	public static boolean drawPage(GuiGraphics ctx, WebView v, float x, float y, float w, float h, float alpha) {
		if (v == null) {
			return false;
		}
		Browser b = get();
		if (b != null) {
			v.zoom(b.zoom.get());
		}
		Minecraft mc = Minecraft.getInstance();
		int s = Math.max(1, mc.getWindow().getGuiScale());
		int x0 = Math.round(x), y0 = Math.round(y), x1 = Math.round(x + w), y1 = Math.round(y + h);
		v.resize((x1 - x0) * s, (y1 - y0) * s);
		GpuTextureView tex = v.texture();
		if (tex == null) {
			return false;
		}
		try {
			//#if MC >= 1.21.11
			if (sampler == null) {
				sampler = RenderSystem.getDevice().createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
						FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
			}
			((DrawContextInvoker) ctx).whiteclient$texturedQuad(RenderPipelines.GUI_TEXTURED, tex, sampler, x0, y0, x1, y1,
					0f, 1f, 0f, 1f, Colors.argb(0xFFFFFF, alpha));
			//#else
			//$ tex.texture().setTextureFilter(FilterMode.LINEAR, false);
			//$ tex.texture().setAddressMode(AddressMode.CLAMP_TO_EDGE);
			//$ ((DrawContextInvoker) ctx).whiteclient$texturedQuad(RenderPipelines.GUI_TEXTURED, tex, x0, y0, x1, y1,
			//$ 		0f, 1f, 0f, 1f, Colors.argb(0xFFFFFF, alpha));
			//#endif
			return true;
		} catch (Throwable t) {
			WhiteClient.LOGGER.debug("[WHITECLIENT] browser draw failed", t);
			return false;
		}
	}

	@Override
	public void renderHud(GuiGraphics ctx, float tickDelta) {
		Minecraft mc = Minecraft.getInstance();
		if (!pin.isOn() || view == null || mc.screen != null) {
			return;
		}
		float[] r = rect(ctx.guiWidth(), ctx.guiHeight());
		float a = opacity.getFloat();
		Render2D.shadow(ctx, r[0], r[1], r[2], r[3], 9, 6, (int) (70 * a));
		Render2D.roundRect(ctx, r[0], r[1], r[2], r[3], 9, Colors.argb(0x0B0B0F, 0.92f * a));
		float bar = 13;
		String host = host(view.url());
		UiFont.drawScaled(ctx, mc.font, host, r[0] + 9, r[1] + 3.5f, 0.72f, Colors.argb(0xFFFFFF, 0.75f * a));
		String hint = "B to use";
		UiFont.drawScaled(ctx, mc.font, hint, r[0] + r[2] - 9 - UiFont.width(mc.font, hint) * 0.72f, r[1] + 3.5f, 0.72f,
				Colors.argb(0xFFFFFF, 0.45f * a));
		drawPage(ctx, view, r[0] + 3, r[1] + bar, r[2] - 6, r[3] - bar - 3, a);
	}

	public static String host(String url) {
		if (url == null || url.isBlank()) {
			return "Browser";
		}
		String s = url.replaceFirst("(?i)^[a-z]+://", "");
		int i = s.indexOf('/');
		s = i > 0 ? s.substring(0, i) : s;
		return s.startsWith("www.") ? s.substring(4) : s;
	}
}
