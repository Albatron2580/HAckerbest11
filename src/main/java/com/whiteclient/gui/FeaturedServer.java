package com.whiteclient.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.whiteclient.WhiteClient;
import com.whiteclient.mixin.MultiplayerScreenAccessor;
import com.whiteclient.modules.MenuBackground;
import com.whiteclient.render.Colors;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractStringWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class FeaturedServer {
	public static final String NAME = "VexVanilla";
	public static final String ADDRESS = "vexvanilla.net";
	private static final Identifier ICON = Identifier.fromNamespaceAndPath(WhiteClient.MOD_ID, "featured_server_icon");
	private static final int CARD_H = 58;
	private static final int MOTD_W = 236;
	private static final int PLAY_W = 64;

	private static JoinMultiplayerScreen screen;
	private static ServerData info;
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

	private static boolean isVex(ServerData s) {
		if (s == null || s.ip == null) {
			return false;
		}
		String a = s.ip.trim().toLowerCase(Locale.ROOT);
		int colon = a.indexOf(':');
		if (colon >= 0) {
			a = a.substring(0, colon);
		}
		return a.equals(ADDRESS) || a.endsWith("." + ADDRESS);
	}

	private static ServerData.Type otherType() {
		ServerData.Type[] all = ServerData.Type.values();
		for (ServerData.Type t : all) {
			if (t.name().equals("OTHER")) {
				return t;
			}
		}
		return all[all.length - 1];
	}

	public static void setup(Minecraft client, JoinMultiplayerScreen s) {
		if (!(s instanceof MultiplayerScreenAccessor acc)) {
			return;
		}
		MenuBackground bg = WhiteClient.get().modules().get(MenuBackground.class);
		ServerList list = s.getServers();
		int idx = -1;
		for (int i = 0; i < list.size(); i++) {
			if (isVex(list.get(i))) {
				idx = i;
				break;
			}
		}
		boolean changed = false;
		if (idx < 0 && bg != null && !bg.featuredAdded.isOn()) {
			list.add(new ServerData(NAME, ADDRESS, otherType()), false);
			idx = list.size() - 1;
			bg.featuredAdded.set(true);
			WhiteClient.get().config().save();
			changed = true;
		}
		if (idx > 0) {
			for (int i = idx; i > 0; i--) {
				list.swap(i, i - 1);
			}
			idx = 0;
			changed = true;
		}
		if (changed) {
			list.save();
			acc.whiteclient$serverListWidget().updateOnlineServers(list);
		}
		ServerData listed = idx == 0 ? list.get(0) : null;
		if (listed != null) {
			info = listed;
		} else if (info == null || !isVex(info)) {
			info = new ServerData(NAME, ADDRESS, otherType());
		}
		screen = s;

		int header = 30 + CARD_H + 10;
		//#if MC < 1.21.9
		//$ ((com.whiteclient.mixin.ScreenTitleAccessor) s).whiteclient$setTitle(Component.empty());
		//#endif
		//#if MC >= 1.21.9
		HeaderAndFooterLayout layout = acc.whiteclient$layout();
		layout.setHeaderHeight(header);
		//#endif
		for (AbstractWidget wdg : Screens.getButtons(s)) {
			if (wdg instanceof AbstractStringWidget t && wdg.getY() < 40) {
				t.visible = false;
			}
		}
		//#if MC >= 1.21.9
		layout.arrangeElements();
		acc.whiteclient$serverListWidget().updateSize(s.width, layout);
		//#else
		//$ acc.whiteclient$serverListWidget().updateSizeAndPosition(s.width, s.height - 64 - header, header);
		//#endif

		w = Math.min(440, s.width - 20);
		h = CARD_H;
		x = s.width / 2f - w / 2f;
		y = 30;
		final ServerData target = info;
		Button play = Button.builder(Component.literal("Play"), b -> acc.whiteclient$connect(target))
				.bounds(Math.round(x + w - 10 - PLAY_W), Math.round(y + 9), PLAY_W, 20).build();
		ButtonStyle.markAccent(play);
		Screens.getButtons(s).add(play);
	}

	public static void draw(GuiGraphics ctx, JoinMultiplayerScreen s, int mouseX, int mouseY) {
		if (s != screen || info == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Font tr = mc.font;
		float time = WhiteClient.get().time();
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastNs) / 1.0e9f);
		lastNs = now;
		boolean over = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		hover += ((over ? 1f : 0f) - hover) * Math.min(1f, dt * 10f);

		String title = "Multiplayer";
		float tw = UiFont.width(tr, title) * 1.2f;
		float gx = s.width / 2f - (tw + 18) / 2f;
		Render2D.planetLogo(ctx, gx + 6, 15, 5, time, 255);
		UiFont.drawScaled(ctx, tr, title, gx + 18, 10, 1.2f, 0xFFF2F2F6);

		for (int g = 5; g >= 1; g--) {
			float e = g * 2.2f;
			Render2D.roundRect(ctx, x - e, y - e, w + e * 2, h + e * 2, 9 + e, Colors.withAlpha(0xFFFFFF, (int) ((2 + 3 * hover) * (6 - g) / 5f)));
		}
		Render2D.roundRect(ctx, x, y, w, h, 9, 0xE40C0C12);
		Render2D.roundRectOutline(ctx, x, y, w, h, 9, 1, Colors.withAlpha(0xFFFFFF, (int) (45 + 60 * hover)));

		int ix = Math.round(x + 9), iy = Math.round(y + (h - 32) / 2f);
		updateIcon(mc);
		Render2D.roundRect(ctx, ix - 1, iy - 1, 34, 34, 6, 0xFF16161D);
		if (iconReady) {
			Gfx.blit(ctx, ICON, ix, iy, 0f, 0f, 32, 32, 64, 64, 64, 64, 0xFFFFFFFF);
		} else {
			Render2D.planetLogo(ctx, ix + 16, iy + 16, 8, time, 255);
		}

		float tx = ix + 42;
		UiFont.drawScaled(ctx, tr, NAME, tx, y + 8, 1.15f, 0xFFFFFFFF);
		float nw = UiFont.width(tr, NAME) * 1.15f;
		String tag = "PARTNERED";
		float tagW = UiFont.width(tr, tag) * 0.8f + 10;
		Render2D.roundRect(ctx, tx + nw + 6, y + 8, tagW, 11, 5.5f, 0x33FFFFFF);
		UiFont.drawScaled(ctx, tr, tag, tx + nw + 11, y + 10, 0.8f, 0xFFEDEDF2);

		String status = statusText();
		float rightEdge = x + w - 10;
		float colLeft = rightEdge - PLAY_W;
		if (status != null) {
			float sw = UiFont.width(tr, status) * 0.9f;
			float sx = Math.min(rightEdge - sw, rightEdge - PLAY_W / 2f - sw / 2f);
			colLeft = Math.min(colLeft, sx);
			UiFont.drawScaled(ctx, tr, status, sx, y + 36, 0.9f, statusColor());
		}

		int motdW = (int) Math.min(MOTD_W, colLeft - 8 - tx);
		Component motd = info.motd;
		if (motd != null && !motd.getString().isBlank() && info.state() != null && info.state().name().equals("SUCCESSFUL")) {
			List<FormattedCharSequence> lines = tr.split(motd, Math.max(40, motdW));
			for (int i = 0; i < Math.min(2, lines.size()); i++) {
				ctx.drawString(tr, lines.get(i), Math.round(tx), Math.round(y + 24 + i * 10), 0xFFB0B0BA, false);
			}
		} else {
			UiFont.draw(ctx, tr, ADDRESS, tx, y + 27, 0xFF9A9AA6);
		}
	}

	private static String statusText() {
		ServerData.State st = info.state();
		String n = st == null ? "INITIAL" : st.name();
		switch (n) {
			case "SUCCESSFUL" -> {
				String players = info.status == null ? "" : info.status.getString();
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
		ServerData.State st = info.state();
		String n = st == null ? "" : st.name();
		return switch (n) {
			case "SUCCESSFUL" -> 0xFF8BE39A;
			case "UNREACHABLE", "INCOMPATIBLE" -> 0xFFE38B8B;
			default -> 0xFF9A9AA6;
		};
	}

	private static void updateIcon(Minecraft mc) {
		byte[] bytes = info.getIconBytes();
		if (bytes == null || Arrays.equals(bytes, iconBytes)) {
			return;
		}
		iconBytes = bytes;
		try {
			NativeImage img = NativeImage.read(new ByteArrayInputStream(bytes));
			mc.getTextureManager().register(ICON, new DynamicTexture(() -> "whiteclient_featured_server", img));
			iconReady = true;
		} catch (Throwable t) {
			iconReady = false;
		}
	}
}
