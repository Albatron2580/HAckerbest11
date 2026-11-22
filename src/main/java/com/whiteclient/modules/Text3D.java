package com.whiteclient.modules;

import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.render.WorldContext;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.ModeSetting;
import com.whiteclient.setting.NumberSetting;
import com.whiteclient.setting.TextSetting;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Big blocky extruded 3D words that pop into the world one by one,
 * like the 3D-lyrics edits. Set the text in the menu or with /whiteclient text ...
 */
public class Text3D extends Module {
	public final TextSetting message = add(new TextSetting("Text", "Welcome to WHITECLIENT the best visuals", 160).onChange(this::restart));
	public final NumberSetting wordsPerGroup = add(new NumberSetting("Words Per Group", 3, 1, 6, 1));
	public final NumberSetting wordDelay = add(new NumberSetting("Word Delay", 0.38, 0.08, 1.5, 0.02, "s"));
	public final NumberSetting holdTime = add(new NumberSetting("Hold Time", 1.6, 0.2, 6.0, 0.1, "s"));
	public final NumberSetting scale = add(new NumberSetting("Size", 3.0, 0.5, 8.0, 0.1, "x"));
	public final NumberSetting depth = add(new NumberSetting("Depth", 12, 2, 20, 1));
	public final NumberSetting distance = add(new NumberSetting("Distance", 6.0, 2.0, 16.0, 0.5));
	public final ColorSetting front = add(new ColorSetting("Front Color", 0x3D8BFF));
	public final ColorSetting accent = add(new ColorSetting("Accent Color", 0x33E6C8));
	public final BoolSetting accentLast = add(new BoolSetting("Accent Last Word", true));
	public final ModeSetting placement = add(new ModeSetting("Placement", "World", "World", "Camera"));
	public final BoolSetting uppercase = add(new BoolSetting("Uppercase", true));
	public final BoolSetting wobble = add(new BoolSetting("Wobble", true));
	public final BoolSetting loop = add(new BoolSetting("Loop", true));

	private static final float POP_TIME = 0.22f;
	private static final float OUT_TIME = 0.35f;

	private final Random random = new Random();
	private List<String> words = new ArrayList<>();
	private int groupStart;
	private float groupClock = -1f;
	private float lastTime = -1f;
	private boolean finished;
	private Vec3d anchor;
	private float anchorYaw;

	public Text3D() {
		super("3D Text", "Big 3D words popping up in the world", Category.WORLD, true);
	}

	public void setMessage(String text) {
		message.set(text);
		restart();
	}

	public void restart() {
		groupStart = 0;
		groupClock = -1f;
		finished = false;
		anchor = null;
		words = new ArrayList<>();
	}

	@Override
	protected void onEnable() {
		restart();
	}

	private void splitWords() {
		words = new ArrayList<>();
		String text = message.get();
		if (uppercase.isOn()) {
			text = text.toUpperCase(Locale.ROOT);
		}
		for (String w : text.trim().split("\\s+")) {
			if (!w.isEmpty()) {
				words.add(w);
			}
		}
	}

	@Override
	public void renderWorld(WorldContext ctx) {
		if (words.isEmpty()) {
			splitWords();
			if (words.isEmpty()) {
				return;
			}
		}
		float dt = lastTime < 0 ? 0 : Math.min(0.1f, ctx.time - lastTime);
		lastTime = ctx.time;
		if (finished) {
			return;
		}
		int per = wordsPerGroup.getInt();
		int count = Math.min(per, words.size() - groupStart);
		float delay = wordDelay.getFloat();
		float showFor = count * delay + holdTime.getFloat();

		if (groupClock < 0) {
			groupClock = 0;
			placeAnchor(ctx);
		} else {
			groupClock += dt;
		}
		if (groupClock > showFor + OUT_TIME) {
			groupStart += per;
			if (groupStart >= words.size()) {
				if (!loop.isOn()) {
					finished = true;
					return;
				}
				groupStart = 0;
			}
			groupClock = 0;
			placeAnchor(ctx);
			count = Math.min(per, words.size() - groupStart);
			showFor = count * delay + holdTime.getFloat();
		}

		float out = groupClock > showFor ? (groupClock - showFor) / OUT_TIME : 0f;
		float outScale = 1f - easeIn(Math.min(1f, out));
		if (outScale <= 0.01f) {
			return;
		}
		drawGroup(ctx, groupStart, count, delay, outScale);
	}

	private static float easeIn(float t) {
		return t * t;
	}

	/** Overshooting pop: 0 -> 1.15 -> 1. */
	private static float pop(float t) {
		if (t <= 0) {
			return 0;
		}
		if (t >= 1) {
			return 1;
		}
		float c1 = 1.70158f * 1.4f, c3 = c1 + 1;
		float x = t - 1;
		return 1 + c3 * x * x * x + c1 * x * x;
	}

	private void placeAnchor(WorldContext ctx) {
		Vec3d cam = ctx.cam;
		float yaw = ctx.client.gameRenderer.getCamera().getYaw();
		double r = Math.toRadians(yaw);
		double fx = -Math.sin(r), fz = Math.cos(r);
		double rx = -Math.cos(r), rz = -Math.sin(r);
		double side = (random.nextDouble() * 2 - 1) * 1.2;
		double up = 0.4 + random.nextDouble() * 0.9;
		double d = distance.get() + cameraToPlayer(ctx);
		anchor = new Vec3d(cam.x + fx * d + rx * side, cam.y + up - 0.6, cam.z + fz * d + rz * side);
		// face the camera: local +Z points from the text towards the camera
		anchorYaw = (float) Math.atan2(cam.x - anchor.x, cam.z - anchor.z);
	}

	/** In third person the text is pushed out past the player so it never spawns on top of you. */
	private static double cameraToPlayer(WorldContext ctx) {
		if (!ctx.thirdPerson() || ctx.client.player == null) {
			return 0;
		}
		Vec3d p = ctx.client.player.getLerpedPos(ctx.tickDelta);
		double dx = p.x - ctx.cam.x;
		double dy = p.y + ctx.client.player.getStandingEyeHeight() - ctx.cam.y;
		double dz = p.z - ctx.cam.z;
		return Math.min(12, Math.sqrt(dx * dx + dy * dy + dz * dz));
	}

	private void drawGroup(WorldContext ctx, int start, int count, float delay, float outScale) {
		TextRenderer tr = ctx.client.textRenderer;
		MatrixStack ms = ctx.matrices;
		Vec3d cam = ctx.cam;

		// layout: up to 2 words per row, rows stacked
		int perRow = count <= 2 ? count : 2;
		int rows = (count + perRow - 1) / perRow;
		float space = tr.getWidth(" ") + 2;
		float lineH = tr.fontHeight + 3;

		ms.push();
		if (placement.is("Camera")) {
			double r = Math.toRadians(ctx.client.gameRenderer.getCamera().getYaw());
			double pr = Math.toRadians(ctx.client.gameRenderer.getCamera().getPitch());
			double d = distance.get() + cameraToPlayer(ctx);
			double fx = -Math.sin(r) * Math.cos(pr), fy = -Math.sin(pr), fz = Math.cos(r) * Math.cos(pr);
			ms.translate(fx * d, fy * d, fz * d);
			ms.multiply(new Quaternionf(ctx.camRotation));
		} else {
			if (anchor == null) {
				placeAnchor(ctx);
			}
			ms.translate(anchor.x - cam.x, anchor.y - cam.y, anchor.z - cam.z);
			ms.multiply(new Quaternionf().rotationY(anchorYaw));
		}
		if (wobble.isOn()) {
			ms.multiply(new Quaternionf().rotationY((float) Math.sin(ctx.time * 0.9) * 0.16f)
					.rotateX((float) Math.sin(ctx.time * 0.7 + 1.3) * 0.07f));
		}
		float s = 0.025f * scale.getFloat() * outScale;
		ms.scale(s, -s, s);

		float totalH = rows * lineH;
		int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
		int layers = depth.getInt();
		float step = 0.32f;
		int frontRgb = front.rgb();
		int accentRgb = accent.rgb();

		for (int row = 0; row < rows; row++) {
			int first = row * perRow;
			int last = Math.min(count, first + perRow);
			float rowW = 0;
			for (int i = first; i < last; i++) {
				rowW += tr.getWidth(words.get(start + i)) + (i > first ? space : 0);
			}
			float x = -rowW / 2f;
			float y = -totalH / 2f + row * lineH;
			for (int i = first; i < last; i++) {
				String word = words.get(start + i);
				float ww = tr.getWidth(word);
				float appearT = (groupClock - i * delay) / POP_TIME;
				float p = pop(appearT);
				if (p > 0.001f) {
					boolean isAccent = accentLast.isOn() && i == count - 1 && count > 1;
					int rgb = isAccent ? accentRgb : frontRgb;
					float wordScale = isAccent ? 0.82f : 1f;
					ms.push();
					ms.translate(x + ww / 2f, y + tr.fontHeight / 2f, 0);
					ms.scale(p * wordScale, p * wordScale, p * wordScale);
					drawExtruded(tr, ms, ctx, word, -ww / 2f, -tr.fontHeight / 2f, rgb, layers, step, light);
					ms.pop();
				}
				x += ww + space;
			}
		}
		ms.pop();
	}

	private static void drawExtruded(TextRenderer tr, MatrixStack ms, WorldContext ctx, String word, float x, float y,
									 int rgb, int layers, float step, int light) {
		// back layers: darker the further back
		for (int l = layers; l >= 1; l--) {
			float k = l / (float) layers;
			int side = Colors.shade(rgb, 0.52f - 0.24f * k);
			ms.push();
			ms.translate(0, 0, -l * step);
			Matrix4f m = ms.peek().getPositionMatrix();
			tr.draw(word, x, y, 0xFF000000 | side, false, m, ctx.consumers, TextRenderer.TextLayerType.NORMAL, 0, light);
			ms.pop();
		}
		// bright front face with a subtle top highlight
		Matrix4f m = ms.peek().getPositionMatrix();
		tr.draw(word, x, y, 0xFF000000 | Colors.mix(rgb, 0xFFFFFF, 0.12f), false, m, ctx.consumers, TextRenderer.TextLayerType.NORMAL, 0, light);
	}
}
