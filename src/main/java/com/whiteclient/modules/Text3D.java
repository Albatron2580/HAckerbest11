package com.whiteclient.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import com.whiteclient.WhiteClient;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.music.Lyrics;
import com.whiteclient.music.NowPlaying;
import com.whiteclient.render.Colors;
import com.whiteclient.render.WorldContext;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.ModeSetting;
import com.whiteclient.setting.NumberSetting;
import com.whiteclient.setting.TextSetting;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;

public class Text3D extends Module {
	public final ModeSetting source = add(new ModeSetting("Source", "Spotify Lyrics", "Spotify Lyrics", "Custom Text"));
	public final TextSetting message = add(new TextSetting("Custom Text", "Welcome to WHITECLIENT the best visuals", 160).onChange(this::restart));
	public final BoolSetting idleText = add(new BoolSetting("Custom Text When No Music", false));
	public final BoolSetting songTitle = add(new BoolSetting("Show Song Title", true));
	public final NumberSetting wordsPerGroup = add(new NumberSetting("Words Per Group", 3, 1, 6, 1));
	public final NumberSetting wordDelay = add(new NumberSetting("Word Delay", 0.32, 0.08, 1.5, 0.02, "s"));
	public final NumberSetting holdTime = add(new NumberSetting("Hold Time", 1.6, 0.2, 6.0, 0.1, "s"));
	public final NumberSetting scale = add(new NumberSetting("Size", 3.0, 0.5, 8.0, 0.1, "x"));
	public final NumberSetting depth = add(new NumberSetting("Depth", 12, 2, 20, 1));
	public final NumberSetting distance = add(new NumberSetting("Distance", 6.0, 2.0, 16.0, 0.5));
	public final NumberSetting height = add(new NumberSetting("Height", 0.9, -2.0, 4.0, 0.1));
	public final ModeSetting placement = add(new ModeSetting("Placement", "Follow Camera", "Follow Camera", "World"));
	public final ColorSetting front = add(new ColorSetting("Front Color", 0xFFFFFF));
	public final ColorSetting side = add(new ColorSetting("Side Color", 0x9AA6C2));
	public final ColorSetting highlight = add(new ColorSetting("Highlight Color", 0xBFE0FF));
	public final BoolSetting highlightWord = add(new BoolSetting("Highlight Current Word", true));
	public final BoolSetting outline = add(new BoolSetting("Outline", true));
	public final NumberSetting glow = add(new NumberSetting("Glow", 0.35, 0.0, 1.0, 0.05));
	public final BoolSetting uppercase = add(new BoolSetting("Uppercase", true));
	public final BoolSetting wobble = add(new BoolSetting("Wobble", true));
	public final BoolSetting loop = add(new BoolSetting("Loop Custom Text", true));

	private static final float POP_TIME = 0.26f;
	private static final float EXIT_TIME = 0.45f;
	private static final int MAX_ROW_CHARS = 16;

	private static final class Group {
		final List<String> words = new ArrayList<>();
		final List<Float> appear = new ArrayList<>();
		final List<Float> tilt = new ArrayList<>();
		String subtitle;
		float created;
		float exitAt = -1;
		float autoExitAfter = -1;
		Vec3 anchor;
		boolean follow;
		int highlight = -1;
		boolean lyrics;
	}

	private final Random random = new Random();
	private final List<Group> groups = new ArrayList<>();
	private float lastTime = -1;

	private List<String> customWords = new ArrayList<>();
	private int customIndex;
	private float customNextAt = -1;
	private boolean customFinished;

	private String lastTrack;
	private int lastLine = Integer.MIN_VALUE;

	public Text3D() {
		super("3D Text", "3D words that pop up - synced to your Spotify lyrics", Category.MUSIC, true);
	}

	public void setMessage(String text) {
		message.set(text);
		source.set("Custom Text");
		restart();
	}

	public void restart() {
		for (Group g : groups) {
			exit(g, lastTime < 0 ? 0 : lastTime);
		}
		customWords = new ArrayList<>();
		customIndex = 0;
		customNextAt = -1;
		customFinished = false;
		lastTrack = null;
		lastLine = Integer.MIN_VALUE;
	}

	@Override
	protected void onEnable() {
		groups.clear();
		restart();
	}

	private String fmt(String s) {
		return uppercase.isOn() ? s.toUpperCase(Locale.ROOT) : s;
	}

	private static void exit(Group g, float now) {
		if (g.exitAt < 0) {
			g.exitAt = now;
		}
	}

	private Group newGroup(WorldContext ctx, List<String> words, float now) {
		Group g = new Group();
		g.created = now;
		g.follow = placement.is("Follow Camera");
		g.words.addAll(words);
		for (int i = 0; i < words.size(); i++) {
			g.tilt.add((random.nextFloat() * 2 - 1) * 0.28f);
		}
		g.anchor = targetAnchor(ctx, !g.follow);
		groups.add(g);
		return g;
	}

	@Override
	public void renderWorld(WorldContext ctx) {
		float now = ctx.time;
		float dt = lastTime < 0 ? 0 : Math.min(0.1f, now - lastTime);
		lastTime = now;

		boolean showedMusic = false;
		if (source.is("Spotify Lyrics")) {
			showedMusic = updateLyrics(ctx, now);
		}
		if (source.is("Custom Text") || (!showedMusic && idleText.isOn())) {
			updateCustom(ctx, now);
		} else if (!customWords.isEmpty()) {
			customWords = new ArrayList<>();
		}

		Iterator<Group> it = groups.iterator();
		while (it.hasNext()) {
			Group g = it.next();
			if (g.autoExitAfter > 0 && g.exitAt < 0 && now - g.created > g.autoExitAfter) {
				exit(g, now);
			}
			if (g.exitAt >= 0 && now - g.exitAt > EXIT_TIME + 0.1f * g.words.size()) {
				it.remove();
				continue;
			}
			if (g.follow) {
				Vec3 target = targetAnchor(ctx, false);
				double k = 1 - Math.exp(-dt * 5.0);
				g.anchor = g.anchor.add(target.subtract(g.anchor).scale(k));
			}
			drawGroup(ctx, g, now);
		}
	}

	private boolean updateLyrics(WorldContext ctx, float now) {
		WhiteClient wc = WhiteClient.get();
		NowPlaying np = wc.music().current();
		if (np == null) {
			if (lastTrack != null) {
				groups.forEach(g -> exit(g, now));
				lastTrack = null;
				lastLine = Integer.MIN_VALUE;
			}
			return false;
		}
		Spotify spotify = wc.modules().get(Spotify.class);
		long offset = spotify == null ? 0 : (long) spotify.lyricsOffset.get().doubleValue();
		Lyrics lyrics = wc.music().lyrics();

		if (!np.key().equals(lastTrack)) {
			groups.forEach(g -> exit(g, now));
			lastTrack = np.key();
			lastLine = Integer.MIN_VALUE;
			boolean willHaveLyrics = lyrics == null || (!lyrics.isEmpty() && np.positionKnown());
			if (songTitle.isOn() && np.title() != null) {
				List<String> words = split(fmt(np.title()));
				if (!words.isEmpty()) {
					Group g = newGroup(ctx, words, now);
					for (int i = 0; i < words.size(); i++) {
						g.appear.add(i * 0.12f);
					}
					g.subtitle = np.artist() == null ? null : fmt(np.artist());
					g.autoExitAfter = willHaveLyrics ? 2.8f : 6f;
				}
			}
			return true;
		}
		if (lyrics == null || lyrics.isEmpty() || !np.positionKnown()) {
			return true;
		}
		long pos = np.positionMs() + offset;
		int idx = lyrics.indexAt(pos);
		if (idx == lastLine) {
			return true;
		}
		lastLine = idx;
		for (Group g : groups) {
			if (g.lyrics || g.autoExitAfter > 0) {
				exit(g, now);
			}
		}
		if (idx < 0) {
			return true;
		}
		Lyrics.Line line = lyrics.lines().get(idx);
		List<String> words = split(fmt(line.text()));
		if (words.isEmpty()) {
			return true;
		}
		long lineEnd = lyrics.endOf(idx);
		long dur = Math.max(400, Math.min(lineEnd - line.timeMs(), 7000));
		float spread = dur * 0.7f / words.size();
		Group g = newGroup(ctx, words, now);
		g.lyrics = true;
		for (int i = 0; i < words.size(); i++) {
			long at = line.timeMs() + (long) (i * spread);
			g.appear.add(Math.max(0f, (at - pos) / 1000f));
		}
		return true;
	}

	private void updateCustom(WorldContext ctx, float now) {
		if (customWords.isEmpty()) {
			customWords = split(fmt(message.get()));
			customIndex = 0;
			customNextAt = now;
			customFinished = false;
		}
		if (customWords.isEmpty() || customFinished || now < customNextAt) {
			return;
		}
		int per = wordsPerGroup.getInt();
		int count = Math.min(per, customWords.size() - customIndex);
		float delay = wordDelay.getFloat();
		Group g = newGroup(ctx, customWords.subList(customIndex, customIndex + count), now);
		for (int i = 0; i < count; i++) {
			g.appear.add(i * delay);
		}
		g.autoExitAfter = count * delay + holdTime.getFloat();
		customNextAt = now + g.autoExitAfter + EXIT_TIME * 0.6f;
		customIndex += per;
		if (customIndex >= customWords.size()) {
			customIndex = 0;
			if (!loop.isOn()) {
				customFinished = true;
			}
		}
	}

	private static List<String> split(String text) {
		List<String> out = new ArrayList<>();
		if (text == null) {
			return out;
		}
		for (String w : text.trim().split("\\s+")) {
			if (!w.isEmpty()) {
				out.add(w);
			}
		}
		return out;
	}

	private static double cameraToPlayer(WorldContext ctx) {
		if (!ctx.thirdPerson() || ctx.client.player == null) {
			return 0;
		}
		Vec3 p = ctx.client.player.getPosition(ctx.tickDelta);
		double dx = p.x - ctx.cam.x;
		double dy = p.y + ctx.client.player.getEyeHeight() - ctx.cam.y;
		double dz = p.z - ctx.cam.z;
		return Math.min(12, Math.sqrt(dx * dx + dy * dy + dz * dz));
	}

	private Vec3 targetAnchor(WorldContext ctx, boolean randomSide) {
		Vec3 cam = ctx.cam;
		double yaw = Math.toRadians(ctx.client.gameRenderer.getMainCamera().yRot());
		double pitch = Math.toRadians(ctx.client.gameRenderer.getMainCamera().xRot()) * 0.6;
		double d = distance.get() + cameraToPlayer(ctx);
		double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
		double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
		double sideOff = randomSide ? (random.nextDouble() * 2 - 1) * 1.2 : 0;
		double up = height.get() * (d / 6.0) + (randomSide ? random.nextDouble() * 0.6 : 0);
		return new Vec3(cam.x + fx * d + rx * sideOff, cam.y + fy * d + up, cam.z + fz * d + rz * sideOff);
	}

	private static float pop(float t) {
		if (t <= 0) {
			return 0;
		}
		if (t >= 1) {
			return 1;
		}
		float c1 = 2.2f, c3 = c1 + 1;
		float x = t - 1;
		return 1 + c3 * x * x * x + c1 * x * x;
	}

	private void drawGroup(WorldContext ctx, Group g, float now) {
		Font tr = ctx.client.font;
		PoseStack ms = ctx.matrices;
		Vec3 cam = ctx.cam;
		float t = now - g.created;
		int n = g.words.size();

		g.highlight = -1;
		if (g.lyrics) {
			for (int i = 0; i < n; i++) {
				if (t >= g.appear.get(i)) {
					g.highlight = i;
				}
			}
		}

		List<int[]> rows = new ArrayList<>();
		int start = 0, chars = 0;
		for (int i = 0; i < n; i++) {
			int len = g.words.get(i).length();
			if (i > start && chars + 1 + len > MAX_ROW_CHARS) {
				rows.add(new int[]{start, i});
				start = i;
				chars = 0;
			}
			chars += (i > start ? 1 : 0) + len;
		}
		rows.add(new int[]{start, n});

		float space = tr.width(" ") + 2;
		float lineH = tr.lineHeight + 4;
		float subScale = 0.55f;
		float totalH = rows.size() * lineH + (g.subtitle != null ? lineH * subScale : 0);

		ms.pushPose();
		double ax = g.anchor.x - cam.x, ay = g.anchor.y - cam.y, az = g.anchor.z - cam.z;
		ms.translate(ax, ay, az);
		double horiz = Math.sqrt(ax * ax + az * az);
		float yaw = (float) Math.atan2(-ax, -az);
		Quaternionf rot = new Quaternionf().rotationY(yaw);
		if (g.follow) {
			rot.rotateX((float) -Math.atan2(-ay, horiz));
		}
		if (wobble.isOn()) {
			rot.rotateY((float) Math.sin(now * 0.9 + g.created) * 0.14f)
					.rotateX((float) Math.sin(now * 0.7 + 1.3 + g.created) * 0.06f);
		}
		ms.mulPose(rot);
		float s = 0.025f * scale.getFloat();
		ms.scale(s, -s, s);

		int light = LightTexture.FULL_BRIGHT;
		int layers = depth.getInt();
		int frontRgb = front.rgb();
		int sideRgb = side.rgb();
		int hiRgb = highlight.rgb();
		float glowA = glow.getFloat();

		for (int r = 0; r < rows.size(); r++) {
			int[] row = rows.get(r);
			float rowW = 0;
			for (int i = row[0]; i < row[1]; i++) {
				rowW += tr.width(g.words.get(i)) + (i > row[0] ? space : 0);
			}
			float x = -rowW / 2f;
			float y = -totalH / 2f + r * lineH;
			for (int i = row[0]; i < row[1]; i++) {
				String word = g.words.get(i);
				float ww = tr.width(word);
				float p = pop((t - g.appear.get(i)) / POP_TIME);
				float exitK = 1f;
				float lift = 0f;
				if (g.exitAt >= 0) {
					float et = (now - g.exitAt - i * 0.06f) / EXIT_TIME;
					et = Math.max(0f, Math.min(1f, et));
					exitK = 1f - et * et;
					lift = et * 10f;
				}
				float k = p * exitK;
				if (k > 0.01f) {
					boolean hi = highlightWord.isOn() && i == g.highlight && g.exitAt < 0;
					int rgb = hi ? hiRgb : frontRgb;
					float extra = hi ? 1.07f : 1f;
					ms.pushPose();
					ms.translate(x + ww / 2f, y + tr.lineHeight / 2f - lift + (1f - p) * 6f, 0);
					ms.mulPose(new Quaternionf().rotationZ(g.tilt.get(i) * (1f - p)));
					ms.scale(k * extra, k * extra, k * extra);
					if (glowA > 0.01f) {
						Vector3f c = ms.last().pose().transformPosition(new Vector3f(0, 0, -2f));
						float radius = Math.max(0.3f, ww * s * k * 0.75f);
						ctx.glow.disc(c.x, c.y, c.z, radius, hi ? hiRgb : 0xFFFFFF, glowA * 0.45f * k, 16);
					}
					drawExtruded(tr, ms, ctx, word, -ww / 2f, -tr.lineHeight / 2f, rgb, sideRgb, layers, light, outline.isOn());
					ms.popPose();
				}
				x += ww + space;
			}
		}
		if (g.subtitle != null && !g.subtitle.isBlank()) {
			float p = pop((t - 0.25f) / POP_TIME);
			float exitK = g.exitAt >= 0 ? 1f - Math.min(1f, (now - g.exitAt) / EXIT_TIME) : 1f;
			float k = p * exitK * subScale;
			if (k > 0.01f) {
				float sw = tr.width(g.subtitle);
				ms.pushPose();
				ms.translate(0, -totalH / 2f + rows.size() * lineH + lineH * subScale * 0.5f, 0);
				ms.scale(k, k, k);
				drawExtruded(tr, ms, ctx, g.subtitle, -sw / 2f, -tr.lineHeight / 2f, hiRgb, sideRgb, Math.max(2, layers / 2), light, outline.isOn());
				ms.popPose();
			}
		}
		ms.popPose();
	}

	private static void drawExtruded(Font tr, PoseStack ms, WorldContext ctx, String word, float x, float y,
									 int rgb, int sideRgb, int layers, int light, boolean outline) {
		float step = 0.32f;
		for (int l = layers; l >= 1; l--) {
			float k = l / (float) layers;
			int c = Colors.shade(sideRgb, 1.0f - 0.55f * k);
			ms.pushPose();
			ms.translate(0, 0, -l * step);
			Matrix4f m = ms.last().pose();
			ctx.draw.text(tr, word, x, y, 0xFF000000 | c, m, light);
			ms.popPose();
		}
		if (outline) {
			int dark = 0xFF000000 | Colors.shade(sideRgb, 0.25f);
			ms.pushPose();
			ms.translate(0, 0, -0.12f);
			Matrix4f m = ms.last().pose();
			float o = 0.75f;
			ctx.draw.text(tr, word, x - o, y, dark, m, light);
			ctx.draw.text(tr, word, x + o, y, dark, m, light);
			ctx.draw.text(tr, word, x, y - o, dark, m, light);
			ctx.draw.text(tr, word, x, y + o, dark, m, light);
			ms.popPose();
		}
		Matrix4f m = ms.last().pose();
		ctx.draw.text(tr, word, x, y, 0xFF000000 | rgb, m, light);
	}
}
