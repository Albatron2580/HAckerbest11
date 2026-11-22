package com.whiteclient.modules;

import com.whiteclient.WhiteClient;
import com.whiteclient.gui.Render2D;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ModeSetting;
import com.whiteclient.setting.NumberSetting;
import com.whiteclient.setting.TextSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import org.joml.Matrix3x2fStack;

/** Dark pill at the top of the screen: logo / name / ping / FPS. */
public class Watermark extends Module {
	public final TextSetting name = add(new TextSetting("Name", "whiteclient", 24));
	public final ModeSetting position = add(new ModeSetting("Position", "Top Center", "Top Center", "Top Left", "Top Right"));
	public final BoolSetting ping = add(new BoolSetting("Show Ping", true));
	public final BoolSetting fps = add(new BoolSetting("Show FPS", true));
	public final NumberSetting scale = add(new NumberSetting("Scale", 1.0, 0.6, 1.6, 0.05, "x"));
	public final NumberSetting opacity = add(new NumberSetting("Opacity", 0.82, 0.2, 1.0, 0.01));

	private float shownFps = -1;

	public Watermark() {
		super("Watermark", "Name / ping / FPS pill at the top", Category.HUD, true);
	}

	@Override
	public void renderHud(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = MinecraftClient.getInstance();
		TextRenderer tr = mc.textRenderer;
		int currentFps = mc.getCurrentFps();
		shownFps = shownFps < 0 ? currentFps : shownFps + (currentFps - shownFps) * 0.08f;

		String title = name.get().isBlank() ? "whiteclient" : name.get();
		String sep = "  /  ";
		StringBuilder rest = new StringBuilder();
		if (ping.isOn()) {
			rest.append(sep).append(latency(mc)).append(" ms");
		}
		if (fps.isOn()) {
			rest.append(sep).append(Math.round(shownFps)).append(" FPS");
		}
		String tail = rest.toString();

		float s = scale.getFloat();
		float textW = tr.getWidth(title) + tr.getWidth(tail);
		float w = 22 + textW + 10;
		float h = 18;
		int sw = ctx.getScaledWindowWidth();
		float x;
		if (position.is("Top Left")) {
			x = 6;
		} else if (position.is("Top Right")) {
			x = sw / s - w - 6;
		} else {
			x = (sw / s - w) / 2f;
		}
		float y = 6;

		Matrix3x2fStack m = ctx.getMatrices();
		m.pushMatrix();
		m.scale(s, s);
		int bgAlpha = (int) (opacity.getFloat() * 255);
		Render2D.roundRect(ctx, x - 1, y - 1, w + 2, h + 2, (h + 2) / 2f, (Math.min(255, bgAlpha / 4) << 24) | 0xFFFFFF);
		Render2D.roundRect(ctx, x, y, w, h, h / 2f, (bgAlpha << 24) | 0x0D0D11);
		Render2D.logo(ctx, x + 11, y + h / 2f, 6.5f, WhiteClient.get().time());
		float tx = x + 22;
		Render2D.text(ctx, tr, title, tx, y + 5, 0xFFFFFFFF);
		Render2D.text(ctx, tr, tail, tx + tr.getWidth(title), y + 5, 0xFFA8A8B4);
		m.popMatrix();
	}

	private static int latency(MinecraftClient mc) {
		ClientPlayNetworkHandler handler = mc.getNetworkHandler();
		if (handler == null || mc.player == null) {
			return 0;
		}
		PlayerListEntry entry = handler.getPlayerListEntry(mc.player.getUuid());
		return entry == null ? 0 : entry.getLatency();
	}
}
