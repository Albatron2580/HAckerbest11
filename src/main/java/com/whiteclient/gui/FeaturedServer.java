package com.whiteclient.gui;

import com.whiteclient.WhiteClient;
import com.whiteclient.mixin.MultiplayerScreenAccessor;
import com.whiteclient.modules.MenuBackground;
import com.whiteclient.render.Colors;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.AbstractTextWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * VexVanilla as the main server of the multiplayer screen: a big "partnered" card at the top with its icon, message,
 * players and ping and a Play button, and the server kept first in your server list.
 */
public final class FeaturedServer {
	public static final String NAME = "VexVanilla";
	public static final String ADDRESS = "vexvanilla.net";
	private static final Identifier ICON = Identifier.of(WhiteClient.MOD_ID, "featured_server_icon");
	private static final int CARD_H = 58;
	/** Same width vanilla uses for server messages, so their lines (and centring spaces) break the same way. */
	private static final int MOTD_W = 236;
	private static final int PLAY_W = 64;

	private static MultiplayerScreen screen;
	private static ServerInfo info;
	private static float x, y, w, h;
	private static byte[] iconBytes;
	private static boolean iconReady;
	private static float hover;
	private static long lastNs = System.nanoTime();

	private FeaturedServer() {
	}

	public static boolean enabled() {
		MenuBackground bg = WhiteClient.get().modules().get(MenuBackground.class);
		return bg != null && bg.isEnabled() && bg.featuredServer.isOn();
	}

	public static boolean isCurrent(Object s) {
		return s == screen && screen != null;
	}

	private static boolean isVex(ServerInfo s) {
		if (s == null || s.address == null) {
			return false;
		}
		String a = s.address.trim().toLowerCase(Locale.ROOT);
		int colon = a.indexOf(':');
		if (colon >= 0) {
			a = a.substring(0, colon);
		}
		return a.equals(ADDRESS) || a.endsWith("." + ADDRESS);
	}

	private static ServerInfo.ServerType otherType() {
		ServerInfo.ServerType[] all = ServerInfo.ServerType.values();
		for (ServerInfo.ServerType t : all) {
			if (t.name().equals("OTHER")) {
				return t;
			}
		}
		return all[all.length - 1];
	}

	/** After the screen has built itself: pin VexVanilla first in the list, make room for the card, add Play. */
	public static void setup(MinecraftClient client, MultiplayerScreen s) {
		if (!(s instanceof MultiplayerScreenAccessor acc)) {
			return;
		}
		MenuBackground bg = WhiteClient.get().modules().get(MenuBackground.class);
		ServerList list = s.getServerList();
		int idx = -1;
		for (int i = 0; i < list.size(); i++) {
			if (isVex(list.get(i))) {
				idx = i;
				break;
			}
		}
		boolean changed = false;
		if (idx < 0 && bg != null && !bg.featuredAdded.isOn()) {
			// add it once; if you delete it from the list later it stays deleted (the card stays)
			list.add(new ServerInfo(NAME, ADDRESS, otherType()), false);
			idx = list.size() - 1;
			bg.featuredAdded.set(true);
			WhiteClient.get().config().save();
			changed = true;
		}
		if (idx > 0) {
			for (int i = idx; i > 0; i--) {
				list.swapEntries(i, i - 1);
			}
			idx = 0;
			changed = true;
		}
		if (changed) {
			list.saveFile();
			acc.whiteclient$serverListWidget().setServers(list);
		}
		ServerInfo listed = idx == 0 ? list.get(0) : null;
		if (listed != null) {
			info = listed;
		} else if (info == null || !isVex(info)) {
			info = new ServerInfo(NAME, ADDRESS, otherType());
		}
		screen = s;

		// room for the card under the title
		ThreePartsLayoutWidget layout = acc.whiteclient$layout();
		int header = 30 + CARD_H + 10;
		layout.setHeaderHeight(header);
		for (ClickableWidget wdg : Screens.getButtons(s)) {
			if (wdg instanceof AbstractTextWidget t && wdg.getY() < 40) {
				t.visible = false; // our own title replaces "Play Multiplayer"
			}
		}
		layout.refreshPositions();
		acc.whiteclient$serverListWidget().position(s.width, layout);

		w = Math.min(440, s.width - 20);
		h = CARD_H;
		x = s.width / 2f - w / 2f;
		y = 30;
		final ServerInfo target = info;
		ButtonWidget play = ButtonWidget.builder(Text.literal("Play"), b -> acc.whiteclient$connect(target))
				.dimensions(Math.round(x + w - 10 - PLAY_W), Math.round(y + 9), PLAY_W, 20).build();
		ButtonStyle.markAccent(play);
		Screens.getButtons(s).add(play);
	}

	/** Title and the featured card, drawn over the background and under the buttons. */
	public static void draw(DrawContext ctx, MultiplayerScreen s, int mouseX, int mouseY) {
		if (s != screen || info == null) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		TextRenderer tr = mc.textRenderer;
		float time = WhiteClient.get().time();
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastNs) / 1.0e9f);
		lastNs = now;
		boolean over = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		hover += ((over ? 1f : 0f) - hover) * Math.min(1f, dt * 10f);

		// title: planet logo + MULTIPLAYER
		String title = "Multiplayer";
		float tw = UiFont.width(tr, title) * 1.2f;
		float gx = s.width / 2f - (tw + 18) / 2f;
		Render2D.planetLogo(ctx, gx + 6, 15, 5, time, 255);
		UiFont.drawScaled(ctx, tr, title, gx + 18, 10, 1.2f, 0xFFF2F2F6);

		// soft glow + card
		for (int g = 5; g >= 1; g--) {
			float e = g * 2.2f;
			Render2D.roundRect(ctx, x - e, y - e, w + e * 2, h + e * 2, 9 + e, Colors.withAlpha(0xFFFFFF, (int) ((2 + 3 * hover) * (6 - g) / 5f)));
		}
		Render2D.roundRect(ctx, x, y, w, h, 9, 0xE40C0C12);
		Render2D.roundRectOutline(ctx, x, y, w, h, 9, 1, Colors.withAlpha(0xFFFFFF, (int) (45 + 60 * hover)));

		// server icon (from the server itself), or the planet while it loads
		int ix = Math.round(x + 9), iy = Math.round(y + (h - 32) / 2f);
		updateIcon(mc);
		Render2D.roundRect(ctx, ix - 1, iy - 1, 34, 34, 6, 0xFF16161D);
		if (iconReady) {
			ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ICON, ix, iy, 0f, 0f, 32, 32, 64, 64, 64, 64, 0xFFFFFFFF);
		} else {
			Render2D.planetLogo(ctx, ix + 16, iy + 16, 8, time, 255);
		}

		// name and PARTNERED tag
		float tx = ix + 42;
		UiFont.drawScaled(ctx, tr, NAME, tx, y + 8, 1.15f, 0xFFFFFFFF);
		float nw = UiFont.width(tr, NAME) * 1.15f;
		String tag = "PARTNERED";
		float tagW = UiFont.width(tr, tag) * 0.8f + 10;
		Render2D.roundRect(ctx, tx + nw + 6, y + 8, tagW, 11, 5.5f, 0x33FFFFFF);
		UiFont.drawScaled(ctx, tr, tag, tx + nw + 11, y + 10, 0.8f, 0xFFEDEDF2);

		// players and ping under the Play button (right column), so nothing overlaps
		String status = statusText();
		float rightEdge = x + w - 10;
		float colLeft = rightEdge - PLAY_W;
		if (status != null) {
			float sw = UiFont.width(tr, status) * 0.9f;
			float sx = Math.min(rightEdge - sw, rightEdge - PLAY_W / 2f - sw / 2f);
			colLeft = Math.min(colLeft, sx);
			UiFont.drawScaled(ctx, tr, status, sx, y + 36, 0.9f, statusColor());
		}

		// the server's own message, broken into lines exactly like the vanilla server list does
		int motdW = (int) Math.min(MOTD_W, colLeft - 8 - tx);
		Text motd = info.label;
		if (motd != null && !motd.getString().isBlank() && info.getStatus() != null && info.getStatus().name().equals("SUCCESSFUL")) {
			List<OrderedText> lines = tr.wrapLines(motd, Math.max(40, motdW));
			for (int i = 0; i < Math.min(2, lines.size()); i++) {
				ctx.drawText(tr, lines.get(i), Math.round(tx), Math.round(y + 24 + i * 10), 0xFFB0B0BA, false);
			}
		} else {
			UiFont.draw(ctx, tr, ADDRESS, tx, y + 27, 0xFF9A9AA6);
		}
	}

	private static String statusText() {
		ServerInfo.Status st = info.getStatus();
		String n = st == null ? "INITIAL" : st.name();
		switch (n) {
			case "SUCCESSFUL" -> {
				String players = info.playerCountLabel == null ? "" : info.playerCountLabel.getString();
				return (players.isBlank() ? "" : players + " online  ·  ") + info.ping + " ms";
			}
			case "UNREACHABLE" -> {
				return "Offline";
			}
			case "INCOMPATIBLE" -> {
				return info.version == null ? "Other version" : info.version.getString();
			}
			default -> {
				int dots = (int) (System.currentTimeMillis() / 400 % 4);
				return "Pinging" + ".".repeat(dots);
			}
		}
	}

	private static int statusColor() {
		ServerInfo.Status st = info.getStatus();
		String n = st == null ? "" : st.name();
		return switch (n) {
			case "SUCCESSFUL" -> 0xFF8BE39A;
			case "UNREACHABLE", "INCOMPATIBLE" -> 0xFFE38B8B;
			default -> 0xFF9A9AA6;
		};
	}

	private static void updateIcon(MinecraftClient mc) {
		byte[] bytes = info.getFavicon();
		if (bytes == null || Arrays.equals(bytes, iconBytes)) {
			return;
		}
		iconBytes = bytes;
		try {
			NativeImage img = NativeImage.read(new ByteArrayInputStream(bytes));
			mc.getTextureManager().registerTexture(ICON, new NativeImageBackedTexture(() -> "whiteclient_featured_server", img));
			iconReady = true;
		} catch (Throwable t) {
			iconReady = false;
		}
	}
}
