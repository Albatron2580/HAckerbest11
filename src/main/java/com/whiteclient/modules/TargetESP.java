package com.whiteclient.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.render.GlowBatch;
import com.whiteclient.render.V3;
import com.whiteclient.render.WorldContext;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.ModeSetting;
import com.whiteclient.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class TargetESP extends Module {
	public static final String[] STYLES = {"Orbit", "Spiral", "Shackles", "Cards", "Ring", "Ghosts", "Constellation"};

	public final ModeSetting style = add(new ModeSetting("Style", "Shackles", STYLES));
	public final ModeSetting target = add(new ModeSetting("Target", "Hit Or Look", "Hit Or Look", "Last Hit", "Crosshair"));
	public final BoolSetting players = add(new BoolSetting("Players", true));
	public final BoolSetting mobs = add(new BoolSetting("Mobs", true));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFF));
	public final ColorSetting color2 = add(new ColorSetting("Second Color", 0x9FB8FF));
	public final BoolSetting hurtFlash = add(new BoolSetting("Hurt Flash", true));
	public final NumberSetting speed = add(new NumberSetting("Speed", 1.0, 0.2, 3.0, 0.1, "x"));
	public final NumberSetting size = add(new NumberSetting("Size", 1.0, 0.5, 2.0, 0.05, "x"));
	public final NumberSetting glow = add(new NumberSetting("Glow", 1.0, 0.2, 2.0, 0.05, "x"));
	public final NumberSetting hold = add(new NumberSetting("Keep After Hit", 4, 1, 10, 0.5, "s"));

	public static Entity forcedTarget;

	private Entity shown;
	private float alpha;
	private Vec3 smoothPos;
	private float smoothW = 0.6f, smoothH = 1.8f;
	private float spin;

	public TargetESP() {
		super("Target ESP", "Effects that spin around your target", Category.PVP, true);
	}

	private boolean valid(Entity e, Minecraft mc) {
		if (!(e instanceof LivingEntity living) || e == mc.player || !living.isAlive() || Pets.isPet(e)) {
			return false;
		}
		if (e.isInvisible()) {
			return false;
		}
		boolean isPlayer = e instanceof Player;
		if (isPlayer ? !players.isOn() : !mobs.isOn()) {
			return false;
		}
		return mc.player == null || e.distanceToSqr(mc.player) < 48 * 48;
	}

	private Entity pick(Minecraft mc) {
		if (forcedTarget != null && forcedTarget.isAlive()) {
			return forcedTarget;
		}
		Entity hit = com.whiteclient.combat.CombatTracker.lastHit((long) (hold.get() * 1000));
		if (hit != null && (mc.level == null || mc.level.getEntity(hit.getId()) != hit || !valid(hit, mc))) {
			hit = null;
		}
		Entity look = valid(mc.crosshairPickEntity, mc) ? mc.crosshairPickEntity : null;
		return switch (target.get()) {
			case "Last Hit" -> hit;
			case "Crosshair" -> look;
			default -> hit != null ? hit : look;
		};
	}

	@Override
	public void renderWorld(WorldContext ctx) {
		Minecraft mc = ctx.client;
		Entity now = pick(mc);
		float dt = ctx.frameDelta;
		if (now != null) {
			if (shown != now) {
				if (shown == null || alpha < 0.05f) {
					shown = now;
					smoothPos = null;
				}
			}
		}
		boolean showing = now != null && now == shown;
		alpha += ((showing ? 1f : 0f) - alpha) * Math.min(1f, dt * (showing ? 8f : 5f));
		if (shown == null || alpha < 0.01f) {
			if (!showing) {
				shown = now;
				smoothPos = null;
			}
			return;
		}
		Vec3 p = shown.getPosition(ctx.tickDelta);
		if (smoothPos == null) {
			smoothPos = p;
			smoothW = shown.getBbWidth();
			smoothH = shown.getBbHeight();
		} else {
			float k = Math.min(1f, dt * 20f);
			smoothPos = smoothPos.add(p.subtract(smoothPos).scale(k));
			smoothW += (shown.getBbWidth() - smoothW) * k;
			smoothH += (shown.getBbHeight() - smoothH) * k;
		}
		spin += dt * speed.getFloat();

		int c1 = color.rgb(), c2 = color2.rgb();
		if (hurtFlash.isOn() && shown instanceof LivingEntity living && living.hurtTime > 0) {
			float f = living.hurtTime / 10f;
			c1 = Colors.mix(c1, 0xFF4D6A, f);
			c2 = Colors.mix(c2, 0xFF4D6A, f);
		}
		float a = alpha * glow.getFloat();
		V3 base = new V3(ctx.rx(smoothPos.x), ctx.ry(smoothPos.y), ctx.rz(smoothPos.z));
		float r = (smoothW * 0.5f + 0.45f) * size.getFloat();
		float h = smoothH;
		switch (style.get()) {
			case "Orbit" -> orbit(ctx.glow, base, r, h, c1, c2, a);
			case "Spiral" -> spiral(ctx.glow, base, r, h, c1, c2, a);
			case "Cards" -> cards(ctx, base, r, h, c1, c2, a);
			case "Ring" -> ring(ctx.glow, base, r, h, c1, c2, a);
			case "Ghosts" -> ghosts(ctx.glow, base, r, h, c1, c2, a);
			case "Constellation" -> constellation(ctx.glow, base, r, h, c1, c2, a);
			default -> shackles(ctx.glow, base, r, h, c1, c2, a);
		}
	}

	private static V3 circle(V3 c, float r, double ang) {
		return new V3(c.x() + (float) Math.cos(ang) * r, c.y(), c.z() + (float) Math.sin(ang) * r);
	}

	private void orbit(GlowBatch g, V3 base, float r, float h, int c1, int c2, float a) {
		int orbs = 3;
		for (int i = 0; i < orbs; i++) {
			float phase = (float) (Math.PI * 2 * i / orbs);
			float speedMul = 2.4f;
			float y = h * (0.25f + 0.25f * i) + (float) Math.sin(spin * 1.7 + i * 2.1) * 0.12f;
			int col = i % 2 == 0 ? c1 : c2;
			int tail = 26;
			V3 prev = null;
			for (int k = 0; k <= tail; k++) {
				double ang = spin * speedMul + phase - k * 0.06;
				float yy = y + (float) Math.sin((spin - k * 0.03) * 1.7 + i * 2.1) * 0.12f - (float) Math.sin(spin * 1.7 + i * 2.1) * 0.12f;
				V3 pt = circle(base.add(V3.UP, yy), r, ang);
				if (prev != null) {
					float f = 1f - k / (float) tail;
					g.glowLine(prev.x(), prev.y(), prev.z(), pt.x(), pt.y(), pt.z(), 0.13f * (0.35f + 0.65f * f), col, a * f);
				}
				prev = pt;
			}
			V3 head = circle(base.add(V3.UP, y), r, spin * speedMul + phase);
			g.disc(head.x(), head.y(), head.z(), 0.34f, col, 0.6f * a, 16);
			g.disc(head.x(), head.y(), head.z(), 0.1f, Colors.mix(col, 0xFFFFFF, 0.7f), Math.min(1f, a), 10);
		}
	}

	private void spiral(GlowBatch g, V3 base, float r, float h, int c1, int c2, float a) {
		int seg = 46;
		float turns = 2.2f;
		for (int strand = 0; strand < 2; strand++) {
			int col = strand == 0 ? c1 : c2;
			V3 prev = null;
			for (int i = 0; i <= seg; i++) {
				float u = i / (float) seg;
				double ang = u * Math.PI * 2 * turns + spin * 2.2 + strand * Math.PI;
				float rr = r * (0.75f + 0.25f * (float) Math.sin(u * Math.PI));
				V3 pt = circle(base.add(V3.UP, u * (h + 0.15f)), rr, ang);
				if (prev != null) {
					float fade = (float) Math.sin(u * Math.PI);
					g.glowLine(prev.x(), prev.y(), prev.z(), pt.x(), pt.y(), pt.z(), 0.07f, col, a * (0.25f + 0.75f * fade));
				}
				prev = pt;
			}
			for (int s = 0; s < 3; s++) {
				float u = (float) ((spin * 0.35 + s / 3.0 + strand * 0.17) % 1.0);
				double ang = u * Math.PI * 2 * turns + spin * 2.2 + strand * Math.PI;
				float rr = r * (0.75f + 0.25f * (float) Math.sin(u * Math.PI));
				V3 pt = circle(base.add(V3.UP, u * (h + 0.15f)), rr, ang);
				g.disc(pt.x(), pt.y(), pt.z(), 0.14f, col, 0.6f * a * (float) Math.sin(u * Math.PI), 10);
			}
		}
	}

	private void shackles(GlowBatch g, V3 base, float r, float h, int c1, int c2, float a) {
		for (int chain = 0; chain < 2; chain++) {
			V3 axis = chain == 0 ? new V3(1, 0, 0) : new V3(0, 0, 1);
			float tilt = (chain == 0 ? 0.34f : -0.34f);
			float cy = h * (chain == 0 ? 0.64f : 0.36f);
			V3 center = base.add(V3.UP, cy);
			float rot = spin * (chain == 0 ? 0.8f : -0.8f);
			float rr = r * 1.02f;
			float linkLen = 0.2f * size.getFloat();
			int links = Math.max(12, Math.round((float) (Math.PI * 2 * rr) / (linkLen * 0.72f)));
			int col = chain == 0 ? c1 : c2;
			for (int i = 0; i < links; i++) {
				double ang = Math.PI * 2 * i / links + rot;
				V3 radial = new V3((float) Math.cos(ang), 0, (float) Math.sin(ang)).rotate(axis, tilt);
				V3 tangent = new V3((float) -Math.sin(ang), 0, (float) Math.cos(ang)).rotate(axis, tilt);
				V3 normal = radial.cross(tangent).normalize();
				V3 pos = center.add(radial, rr);
				V3 side = i % 2 == 0 ? normal : radial;
				float pulse = 0.75f + 0.25f * (float) Math.sin(spin * 4 - i * 0.6);
				link(g, pos, tangent, side, linkLen, linkLen * 0.52f, col, a * (i % 2 == 0 ? 1f : 0.78f) * pulse);
			}
			g.disc(center.x(), center.y(), center.z(), rr * 1.5f, col, 0.06f * a, 16);
		}
	}

	private void link(GlowBatch g, V3 c, V3 along, V3 side, float len, float wid, int col, float a) {
		int seg = 14;
		V3 prev = null;
		for (int i = 0; i <= seg; i++) {
			double t = Math.PI * 2 * i / seg;
			float ca = (float) Math.cos(t), sa = (float) Math.sin(t);
			float sx = Math.signum(ca) * (float) Math.pow(Math.abs(ca), 0.7);
			V3 pt = c.add(along, sx * len * 0.5f).add(side, sa * wid * 0.5f);
			if (prev != null) {
				g.glowLine(prev.x(), prev.y(), prev.z(), pt.x(), pt.y(), pt.z(), 0.06f, col, a);
			}
			prev = pt;
		}
	}

	private void cards(WorldContext ctx, V3 base, float r, float h, int c1, int c2, float a) {
		GlowBatch g = ctx.glow;
		int count = 5;
		String[] suits = {"♠", "♥", "♦", "♣", "★"};
		Font tr = ctx.client.font;
		PoseStack ms = ctx.matrices;
		int light = LightTexture.FULL_BRIGHT;
		float cw = 0.34f * size.getFloat(), ch = 0.48f * size.getFloat();
		for (int i = 0; i < count; i++) {
			double ang = spin * 1.1 + Math.PI * 2 * i / count;
			float y = h * 0.5f + (float) Math.sin(spin * 2 + i * 1.3) * 0.18f;
			float rr = r + 0.25f;
			V3 center = circle(base.add(V3.UP, y), rr, ang);
			V3 out = new V3((float) Math.cos(ang), 0, (float) Math.sin(ang));
			V3 tan = new V3((float) -Math.sin(ang), 0, (float) Math.cos(ang));
			float tilt = (float) Math.sin(spin * 1.5 + i) * 0.25f;
			V3 up = V3.UP.rotate(out, tilt);
			tan = tan.rotate(out, tilt);
			int col = i % 2 == 0 ? c1 : c2;
			V3 p1 = center.add(tan, -cw / 2).add(up, -ch / 2);
			V3 p2 = center.add(tan, cw / 2).add(up, -ch / 2);
			V3 p3 = center.add(tan, cw / 2).add(up, ch / 2);
			V3 p4 = center.add(tan, -cw / 2).add(up, ch / 2);
			int fill = Colors.argb(col, 0.24f * a);
			int fillTop = Colors.argb(Colors.mix(col, 0xFFFFFF, 0.5f), 0.4f * a);
			g.quad(p1.x(), p1.y(), p1.z(), fill, p2.x(), p2.y(), p2.z(), fill, p3.x(), p3.y(), p3.z(), fillTop, p4.x(), p4.y(), p4.z(), fillTop);
			p1.line(g, p2, 0.035f, col, a);
			p2.line(g, p3, 0.035f, col, a);
			p3.line(g, p4, 0.035f, col, a);
			p4.line(g, p1, 0.035f, col, a);
			g.disc(center.x(), center.y(), center.z(), ch * 0.9f, col, 0.08f * a, 12);

			if (a > 0.15f) {
				ms.pushPose();
				V3 tp = center.add(out, 0.01f);
				ms.translate(tp.x(), tp.y(), tp.z());
				float yaw = (float) Math.atan2(out.x(), out.z());
				ms.mulPose(new Quaternionf().rotationY(yaw).rotateZ(-tilt));
				float s = 0.034f * size.getFloat();
				ms.scale(s, -s, s);
				Matrix4f m = ms.last().pose();
				String suit = suits[i % suits.length];
				boolean red = i % suits.length == 1 || i % suits.length == 2;
				int tc = Colors.argb(red ? 0xFF4D6A : 0x15151B, Math.min(1f, alpha));
				ctx.draw.text(tr, suit, -tr.width(suit) / 2f, -4f, tc, m, light);
				ms.popPose();
			}
		}
	}

	private void ring(GlowBatch g, V3 base, float r, float h, int c1, int c2, float a) {
		float u = 0.5f + 0.5f * (float) Math.sin(spin * 2.4);
		float vel = (float) Math.cos(spin * 2.4);
		float y = u * h;
		int seg = 48;
		float curtain = 0.55f * Math.abs(vel) + 0.18f;
		float dir = vel > 0 ? -1 : 1;
		V3 c = base.add(V3.UP, y);
		V3 prev = null;
		for (int i = 0; i <= seg; i++) {
			double ang = Math.PI * 2 * i / seg + spin;
			V3 pt = circle(c, r, ang);
			if (prev != null) {
				int col = Colors.mix(c1, c2, 0.5f + 0.5f * (float) Math.sin(ang * 2));
				g.glowLine(prev.x(), prev.y(), prev.z(), pt.x(), pt.y(), pt.z(), 0.11f, col, a * 1.2f);
				int top = Colors.argb(col, 0.5f * a);
				int bottom = Colors.argb(col, 0f);
				g.quad(prev.x(), prev.y(), prev.z(), top, pt.x(), pt.y(), pt.z(), top,
						pt.x(), pt.y() + dir * curtain, pt.z(), bottom, prev.x(), prev.y() + dir * curtain, prev.z(), bottom);
			}
			prev = pt;
		}
	}

	private void ghosts(GlowBatch g, V3 base, float r, float h, int c1, int c2, float a) {
		int count = 4;
		for (int i = 0; i < count; i++) {
			int col = i % 2 == 0 ? c1 : c2;
			int trail = 14;
			for (int k = trail; k >= 0; k--) {
				float t = spin - k * 0.045f;
				double ang = t * (1.6 + i * 0.25) + i * Math.PI * 0.5;
				float y = h * (0.3f + 0.5f * (0.5f + 0.5f * (float) Math.sin(t * (1.1 + i * 0.3) + i)));
				float rr = r * (0.9f + 0.25f * (float) Math.sin(t * 2.3 + i));
				V3 pt = circle(base.add(V3.UP, y), rr, ang);
				float f = 1f - k / (float) (trail + 1);
				g.disc(pt.x(), pt.y(), pt.z(), 0.06f + 0.22f * f, col, a * 0.42f * f, 12);
				if (k == 0) {
					g.disc(pt.x(), pt.y(), pt.z(), 0.06f, 0xFFFFFF, Math.min(1f, a), 8);
				}
			}
		}
	}

	private static final float[][] POINTS = new float[14][];

	static {
		for (int i = 0; i < POINTS.length; i++) {
			float y = 1f - (i + 0.5f) / POINTS.length * 2f;
			float rad = (float) Math.sqrt(1 - y * y);
			float th = (float) (i * Math.PI * (3 - Math.sqrt(5)));
			POINTS[i] = new float[]{(float) Math.cos(th) * rad, y, (float) Math.sin(th) * rad};
		}
	}

	private void constellation(GlowBatch g, V3 base, float r, float h, int c1, int c2, float a) {
		V3 center = base.add(V3.UP, h * 0.55f);
		float rx = r * 1.15f, ry = h * 0.62f;
		V3[] pts = new V3[POINTS.length];
		float cs = (float) Math.cos(spin * 0.6), sn = (float) Math.sin(spin * 0.6);
		for (int i = 0; i < pts.length; i++) {
			float[] p = POINTS[i];
			float x = p[0] * cs - p[2] * sn;
			float z = p[0] * sn + p[2] * cs;
			pts[i] = new V3(center.x() + x * rx, center.y() + p[1] * ry, center.z() + z * rx);
		}
		for (int i = 0; i < pts.length; i++) {
			for (int j = i + 1; j < pts.length; j++) {
				float[] p = POINTS[i], q = POINTS[j];
				float d = (p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1]) + (p[2] - q[2]) * (p[2] - q[2]);
				if (d < 0.62f) {
					float tw = 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(spin * 2 + i + j));
					g.glowLine(pts[i].x(), pts[i].y(), pts[i].z(), pts[j].x(), pts[j].y(), pts[j].z(), 0.05f, Colors.mix(c1, c2, 0.5f), 0.9f * a * tw);
				}
			}
			float tw = 0.5f + 0.5f * (float) Math.sin(spin * 3 + i * 1.7);
			int col = i % 2 == 0 ? c1 : c2;
			g.disc(pts[i].x(), pts[i].y(), pts[i].z(), 0.18f + 0.06f * tw, col, a * (0.45f + 0.3f * tw), 12);
			g.disc(pts[i].x(), pts[i].y(), pts[i].z(), 0.035f, 0xFFFFFF, Math.min(1f, a), 6);
		}
	}
}
