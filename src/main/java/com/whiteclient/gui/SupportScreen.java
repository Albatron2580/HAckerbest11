package com.whiteclient.gui;

import com.whiteclient.WhiteClient;
import com.whiteclient.render.Colors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.List;

/**
 * Support WHITECLIENT with a donation. Donations are voluntary and unlock nothing: everything stays free.
 * The button opens the team's PayPal.me page in your normal browser (PayPal.me keeps the owner's email private).
 */
public class SupportScreen extends Screen {
	/** The team's PayPal.me link, e.g. https://www.paypal.com/paypalme/Name (empty until it is set). */
	public static final String PAYPAL_ME = "";

	private static final int[] AMOUNTS = {2, 5, 10};
	private final Screen parent;
	private int amount = 5;
	private long opened;
	private float cardX, cardY, cardW, cardH;
	private String note = "";
	private long noteUntil;

	public SupportScreen(Screen parent) {
		super(Text.literal("Support WHITECLIENT"));
		this.parent = parent;
	}

	public static boolean ready() {
		return !PAYPAL_ME.isBlank();
	}

	/** paypal.me links take the amount as the last part: paypal.me/Name/5EUR */
	private String link() {
		String base = PAYPAL_ME.endsWith("/") ? PAYPAL_ME.substring(0, PAYPAL_ME.length() - 1) : PAYPAL_ME;
		return amount > 0 ? base + "/" + amount + "EUR" : base;
	}

	@Override
	protected void init() {
		if (opened == 0) {
			opened = System.nanoTime();
		}
		cardW = Math.min(310, width - 30);
		cardH = 208;
		cardX = width / 2f - cardW / 2f;
		cardY = Math.max(10, height / 2f - cardH / 2f - 6);

		// amount chips
		int chipW = 44, gap = 6;
		int n = AMOUNTS.length + 1;
		int rowW = n * chipW + (n - 1) * gap;
		int cx0 = Math.round(width / 2f - rowW / 2f);
		int cy = Math.round(cardY + 104);
		for (int i = 0; i < n; i++) {
			final int value = i < AMOUNTS.length ? AMOUNTS[i] : 0;
			String label = value > 0 ? "€" + value : "Other";
			ButtonWidget chip = ButtonWidget.builder(Text.literal(label), b -> amount = value)
					.dimensions(cx0 + i * (chipW + gap), cy, chipW, 18).build();
			addDrawableChild(chip);
		}
		// the big button
		int bw = Math.round(Math.min(220, cardW - 40));
		ButtonWidget donate = ButtonWidget.builder(Text.literal(ready() ? "Donate with PayPal" : "PayPal link coming soon"), b -> openPayPal())
				.dimensions(Math.round(width / 2f - bw / 2f), Math.round(cardY + 130), bw, 22).build();
		donate.active = ready();
		ButtonStyle.markAccent(donate);
		addDrawableChild(donate);

		int sw = 100;
		ButtonWidget copy = ButtonWidget.builder(Text.literal("Copy Link"), b -> {
			client.keyboard.setClipboard(link());
			note = "Link copied";
			noteUntil = System.currentTimeMillis() + 2000;
		}).dimensions(Math.round(width / 2f - sw - 3), Math.round(cardY + cardH - 30), sw, 18).build();
		copy.active = ready();
		addDrawableChild(copy);
		addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close())
				.dimensions(Math.round(width / 2f + 3), Math.round(cardY + cardH - 30), sw, 18).build());
	}

	private void openPayPal() {
		if (!ready()) {
			return;
		}
		try {
			Util.getOperatingSystem().open(URI.create(link()));
			note = "Opened in your browser - thank you!";
		} catch (Throwable t) {
			note = "Couldn't open your browser: use Copy Link";
		}
		noteUntil = System.currentTimeMillis() + 3000;
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		if (client != null && client.world == null) {
			MenuScene.draw(ctx, this, cardW / 2f);
		} else {
			super.renderBackground(ctx, mouseX, mouseY, delta);
		}
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		float k = Math.min(1f, (System.nanoTime() - opened) / 1.0e9f / 0.35f);
		k = 1f - (1f - k) * (1f - k) * (1f - k);
		float time = WhiteClient.get().time();

		// card
		for (int g = 6; g >= 1; g--) {
			float e = g * 2.4f;
			Render2D.roundRect(ctx, cardX - e, cardY - e, cardW + e * 2, cardH + e * 2, 12 + e, Colors.argb(0xFFFFFF, 0.012f * k));
		}
		Render2D.roundRect(ctx, cardX, cardY, cardW, cardH, 12, Colors.argb(0x0B0B10, 0.92f * k));
		Render2D.roundRectOutline(ctx, cardX, cardY, cardW, cardH, 12, 1, Colors.argb(0xFFFFFF, 0.18f * k));

		// beating heart + title
		float beat = 1f + 0.08f * (float) Math.pow(Math.max(0, Math.sin(time * 3.2)), 8);
		float cx = width / 2f;
		Render2D.circle(ctx, cx, cardY + 24, 14 * beat, Colors.argb(0xFF6B8A, 0.16f * k));
		Icons.heart(ctx, cx, cardY + 24, 12 * beat, Colors.argb(0xFF6B8A, k));
		String title = "Support WHITECLIENT";
		float tw = UiFont.width(textRenderer, title) * 1.35f;
		UiFont.drawScaled(ctx, textRenderer, title, cx - tw / 2f, cardY + 42, 1.35f, Colors.argb(0xFFFFFF, k));

		// message
		String msg = "WHITECLIENT is free and stays free. If you like it, you can support the team with a donation. It helps us keep adding new stuff.";
		List<OrderedText> lines = textRenderer.wrapLines(Text.literal(msg), (int) (cardW - 36));
		float ly = cardY + 62;
		for (OrderedText line : lines) {
			int lw = textRenderer.getWidth(line);
			ctx.drawText(textRenderer, line, Math.round(cx - lw / 2f), Math.round(ly), Colors.argb(0xB8B8C2, k), false);
			ly += 10;
		}

		super.render(ctx, mouseX, mouseY, delta);

		// selected amount: a light ring around the chip
		for (var child : children()) {
			if (child instanceof ButtonWidget b) {
				String l = b.getMessage().getString();
				boolean sel = (amount == 0 && l.equals("Other")) || l.equals("€" + amount);
				if (sel && b.getY() < cardY + 125) {
					Render2D.roundRectOutline(ctx, b.getX() - 1.5f, b.getY() - 1.5f, b.getWidth() + 3, b.getHeight() + 3, 7, 1.2f, Colors.argb(0xFFFFFF, 0.85f * k));
				}
			}
		}

		String small = System.currentTimeMillis() < noteUntil ? note
				: ready() ? "Optional - donating doesn't unlock anything. Opens PayPal in your browser."
				: "The team is setting up their PayPal link. Thanks for wanting to help!";
		float sw = UiFont.width(textRenderer, small) * 0.8f;
		UiFont.drawScaled(ctx, textRenderer, small, cx - sw / 2f, cardY + 158, 0.8f, Colors.argb(0x9A9AA6, k));
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}
}
