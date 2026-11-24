package com.whiteclient.modules;

import com.whiteclient.WhiteClient;
import com.whiteclient.gui.Render2D;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.music.Lyrics;
import com.whiteclient.music.MusicService;
import com.whiteclient.music.NowPlaying;
import com.whiteclient.music.SpotifyAuth;
import com.whiteclient.render.Colors;
import com.whiteclient.setting.ActionSetting;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.InfoSetting;
import com.whiteclient.setting.ModeSetting;
import com.whiteclient.setting.NumberSetting;
import com.whiteclient.setting.TextSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.net.URI;

public class Spotify extends Module {
	public static final String DASHBOARD = "https://developer.spotify.com/dashboard";

	public final InfoSetting statusInfo = add(new InfoSetting("Status", this::statusText));
	public final TextSetting clientId = add(new TextSetting("Client ID", "", 64));
	public final ActionSetting account = add(new ActionSetting("Spotify Account", this::accountButton, this::accountAction));
	public final ActionSetting copyRedirect = add(new ActionSetting("Redirect URI", () -> "Copy", () -> {
		MinecraftClient.getInstance().keyboard.setClipboard(SpotifyAuth.REDIRECT_URI);
		chat("§7Copied §f" + SpotifyAuth.REDIRECT_URI);
	}));
	public final ActionSetting guide = add(new ActionSetting("Create Spotify App", () -> "Open", () -> openUrl(DASHBOARD)));
	public final BoolSetting noLogin = add(new BoolSetting("No-Login Mode (Windows)", true));
	public final BoolSetting card = add(new BoolSetting("Now Playing Card", true));
	public final ModeSetting cardPosition = add(new ModeSetting("Card Position", "Top Left", "Top Left", "Top Right", "Bottom Left", "Bottom Right"));
	public final BoolSetting cardLyrics = add(new BoolSetting("Lyrics In Card", true));
	public final NumberSetting cardScale = add(new NumberSetting("Card Scale", 1.0, 0.6, 1.6, 0.05, "x"));
	public final NumberSetting lyricsOffset = add(new NumberSetting("Lyrics Offset", 0, -3000, 3000, 50, "ms"));

	private float shown;
	private long lastFrame = System.nanoTime();

	public Spotify() {
		super("Spotify", "Now playing + synced lyrics from Spotify", Category.MUSIC, true);
	}

	private MusicService music() {
		return WhiteClient.get().music();
	}

	private String statusText() {
		MusicService m = music();
		NowPlaying np = m.current();
		if (np != null) {
			return (np.playing() ? "▶ " : "❚❚ ") + np.artist() + " - " + np.title();
		}
		if (m.auth().isLoginRunning()) {
			return m.auth().status();
		}
		return m.status();
	}

	private String accountButton() {
		SpotifyAuth auth = music().auth();
		if (auth.isLoginRunning()) {
			return "Waiting...";
		}
		return auth.isLoggedIn() ? "Logout" : "Login";
	}

	private void accountAction() {
		SpotifyAuth auth = music().auth();
		if (auth.isLoggedIn()) {
			auth.logout();
			chat("§7Logged out of Spotify.");
			return;
		}
		login(clientId.get());
	}

	public void login(String id) {
		if (id == null || id.isBlank()) {
			chat("§cPaste your Spotify Client ID first. §7Create an app at §f" + DASHBOARD
					+ "§7 with redirect URI §f" + SpotifyAuth.REDIRECT_URI);
			return;
		}
		clientId.set(id.trim());
		String url = music().auth().startLogin(id.trim(), this::chat);
		if (url != null) {
			MinecraftClient.getInstance().keyboard.setClipboard(url);
			openUrl(url);
			chat("§7Opened Spotify login in your browser (link also copied to clipboard).");
		}
	}

	private static void openUrl(String url) {
		try {
			Util.getOperatingSystem().open(URI.create(url));
		} catch (Throwable t) {
			MinecraftClient.getInstance().keyboard.setClipboard(url);
		}
	}

	private void chat(String msg) {
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.execute(() -> {
			if (mc.inGameHud != null) {
				mc.inGameHud.getChatHud().addMessage(Text.literal("§f[WHITECLIENT] " + msg));
			}
		});
	}

	private static String time(long ms) {
		long s = Math.max(0, ms / 1000);
		return (s / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", s % 60);
	}

	private static String fit(TextRenderer tr, String s, float maxW) {
		if (s == null) {
			return "";
		}
		if (tr.getWidth(s) <= maxW) {
			return s;
		}
		while (s.length() > 1 && tr.getWidth(s + "…") > maxW) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "…";
	}

	@Override
	public void renderHud(DrawContext ctx, float tickDelta) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1.0e9f);
		lastFrame = now;
		NowPlaying np = music().current();
		shown += ((np != null && card.isOn() ? 1f : 0f) - shown) * Math.min(1f, dt * 8f);
		if (np == null || shown < 0.02f) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		TextRenderer tr = mc.textRenderer;

		String line = null;
		if (cardLyrics.isOn() && np.positionKnown()) {
			Lyrics lyrics = music().lyrics();
			if (lyrics != null && !lyrics.isEmpty()) {
				int idx = lyrics.indexAt(np.positionMs() + (long) lyricsOffset.get().doubleValue());
				if (idx >= 0 && !lyrics.lines().get(idx).text().isBlank()) {
					line = lyrics.lines().get(idx).text();
				}
			}
		}

		float sc = cardScale.getFloat();
		float w = 190;
		float h = line != null ? 58 : 46;
		int sw = ctx.getScaledWindowWidth();
		int sh = ctx.getScaledWindowHeight();
		boolean right = cardPosition.get().endsWith("Right");
		boolean bottom = cardPosition.get().startsWith("Bottom");
		float x = right ? sw / sc - w - 6 : 6;
		float y = bottom ? sh / sc - h - 6 : 6;
		if (!bottom && System.currentTimeMillis() - Watermark.boundsFrameMs < 500) {
			float cx1 = x * sc, cx2 = (x + w) * sc;
			if (cx1 < Watermark.boundsX2 && cx2 > Watermark.boundsX1 && y * sc < Watermark.boundsY2) {
				y = Watermark.boundsY2 / sc + 4;
			}
		}
		com.whiteclient.hud.HudLayout.reserve(cardPosition.get(), y * sc, (y + h) * sc);
		float slide = (1 - shown) * 20 * (right ? 1 : -1);

		var m = ctx.getMatrices();
		m.pushMatrix();
		m.scale(sc, sc);
		m.translate(slide, 0);
		int a = (int) (shown * 255);
		Render2D.roundRect(ctx, x, y, w, h, 9, Colors.withAlpha(0x0D0D11, (int) (a * 0.82f)));
		Render2D.roundRectOutline(ctx, x, y, w, h, 9, 1, Colors.withAlpha(0xFFFFFF, (int) (a * 0.16f)));

		Identifier cover = music().cover().texture();
		float cs = 34;
		if (cover != null) {
			ctx.drawTexture(RenderPipelines.GUI_TEXTURED, cover, (int) (x + 6), (int) (y + 6), 0f, 0f, (int) cs, (int) cs, 64, 64, 64, 64,
					Colors.withAlpha(0xFFFFFF, a));
		} else {
			Render2D.roundRect(ctx, x + 6, y + 6, cs, cs, 6, Colors.withAlpha(0x26262E, a));
			Render2D.textCentered(ctx, tr, "♪", x + 6 + cs / 2f, y + 6 + cs / 2f - 4, Colors.withAlpha(0xFFFFFF, a));
		}
		float tx = x + 6 + cs + 8;
		float tw = w - (tx - x) - 8;
		Render2D.text(ctx, tr, fit(tr, np.title(), tw), tx, y + 8, Colors.withAlpha(0xFFFFFF, a));
		Render2D.textScaled(ctx, tr, fit(tr, np.artist(), tw / 0.8f), tx, y + 19, 0.8f, Colors.withAlpha(0xA8A8B4, a));

		float barY = y + 33;
		long pos = np.positionMs();
		float frac = np.durationMs() > 0 ? Math.min(1f, pos / (float) np.durationMs()) : 0f;
		Render2D.roundRect(ctx, tx, barY, tw, 3, 1.5f, Colors.withAlpha(0x34343E, a));
		if (np.durationMs() > 0 || np.positionKnown()) {
			Render2D.roundRect(ctx, tx, barY, Math.max(3, tw * frac), 3, 1.5f, Colors.withAlpha(0xFFFFFF, a));
		}
		String times = np.durationMs() > 0 ? time(pos) + " / " + time(np.durationMs()) : (np.positionKnown() ? time(pos) : "");
		if (!np.playing()) {
			times = "paused  " + times;
		}
		Render2D.textScaled(ctx, tr, times, tx, barY + 5, 0.65f, Colors.withAlpha(0x8C8C98, a));

		if (line != null) {
			Render2D.textScaled(ctx, tr, fit(tr, line, (w - 14) / 0.8f), x + 7, y + h - 12, 0.8f, Colors.withAlpha(0xE8ECFF, a));
		}
		m.popMatrix();
	}
}
