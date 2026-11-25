package com.whiteclient.gui;

import com.whiteclient.WhiteClient;
import com.whiteclient.render.Colors;
import com.whiteclient.update.AutoUpdater;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class UpdateReadyScreen extends Screen {
	private final Screen parent;
	private long opened;
	private float cardX, cardY, cardW, cardH;

	public UpdateReadyScreen(Screen parent) {
		super(Component.literal("Update installed"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (opened == 0) {
			opened = System.nanoTime();
		}
		cardW = Math.min(300, width - 30);
		cardH = 184;
		cardX = width / 2f - cardW / 2f;
		cardY = Math.max(10, height / 2f - cardH / 2f - 6);

		int bw = Math.round(Math.min(210, cardW - 40));
		Button restart = Button.builder(Component.literal("Close Game Now"), b -> minecraft.stop())
				.bounds(Math.round(width / 2f - bw / 2f), Math.round(cardY + 106), bw, 22).build();
		ButtonStyle.markAccent(restart);
		addRenderableWidget(restart);
		int sw = 100;
		addRenderableWidget(Button.builder(Component.literal("Later"), b -> onClose())
				.bounds(Math.round(width / 2f - sw / 2f), Math.round(cardY + cardH - 30), sw, 18).build());
	}

	@Override
	public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		if (minecraft != null && minecraft.level == null) {
			MenuScene.draw(ctx, this, cardW / 2f);
		} else {
			super.renderBackground(ctx, mouseX, mouseY, delta);
		}
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		float k = Math.min(1f, (System.nanoTime() - opened) / 1.0e9f / 0.35f);
		k = 1f - (1f - k) * (1f - k) * (1f - k);
		float time = WhiteClient.get().time();

		for (int g = 6; g >= 1; g--) {
			float e = g * 2.4f;
			Render2D.roundRect(ctx, cardX - e, cardY - e, cardW + e * 2, cardH + e * 2, 12 + e, Colors.argb(0xFFFFFF, 0.012f * k));
		}
		Render2D.roundRect(ctx, cardX, cardY, cardW, cardH, 12, Colors.argb(0x0B0B10, 0.92f * k));
		Render2D.roundRectOutline(ctx, cardX, cardY, cardW, cardH, 12, 1, Colors.argb(0xFFFFFF, 0.18f * k));

		float cx = width / 2f, iy = cardY + 24;
		float pulse = 0.5f + 0.5f * (float) Math.sin(time * 3.0);
		Render2D.circle(ctx, cx, iy, 14 + pulse * 1.5f, Colors.argb(0x8BE39A, 0.16f * k));
		int green = Colors.argb(0x8BE39A, k);
		Render2D.line(ctx, cx - 5.5f, iy + 0.5f, cx - 1.5f, iy + 4.5f, 2.2f, green);
		Render2D.line(ctx, cx - 1.5f, iy + 4.5f, cx + 6f, iy - 4f, 2.2f, green);

		String version = AutoUpdater.readyVersion() == null ? WhiteClient.VERSION : AutoUpdater.readyVersion();
		String title = "Update " + version + " installed";
		float tw = UiFont.width(font, title) * 1.3f;
		UiFont.drawScaled(ctx, font, title, cx - tw / 2f, cardY + 44, 1.3f, Colors.argb(0xFFFFFF, k));

		String msg = "Close Minecraft and open it again from your launcher to start using it. "
				+ "Rejoining a server or world isn't enough, the game has to restart.";
		List<FormattedCharSequence> lines = font.split(Component.literal(msg), (int) (cardW - 36));
		float ly = cardY + 62;
		for (FormattedCharSequence line : lines) {
			int lw = font.width(line);
			ctx.drawString(font, line, Math.round(cx - lw / 2f), Math.round(ly), Colors.argb(0xB8B8C2, k), false);
			ly += 10;
		}

		super.render(ctx, mouseX, mouseY, delta);

		String small = "You can also keep playing, it updates whenever you close the game.";
		float sw = UiFont.width(font, small) * 0.75f;
		UiFont.drawScaled(ctx, font, small, cx - sw / 2f, cardY + 134, 0.75f, Colors.argb(0x9A9AA6, k));
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
