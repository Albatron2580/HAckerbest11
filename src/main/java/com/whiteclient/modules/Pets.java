package com.whiteclient.modules;

import com.whiteclient.WhiteClient;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.render.GlowBatch;
import com.whiteclient.render.V3;
import com.whiteclient.render.WorldContext;
import com.whiteclient.render.WorldText;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.ModeSetting;
import com.whiteclient.setting.NumberSetting;
import com.whiteclient.setting.TextSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class Pets extends Module {
	public static final String[] TYPES = {"Jellyfish", "Manta Ray", "Star Whale"};

	public final ModeSetting pet = add(new ModeSetting("Pet", "Jellyfish", TYPES));
	public final TextSetting name = add(new TextSetting("Name", "", 24));
	public final BoolSetting natural = add(new BoolSetting("Natural Colors", true));
	public final ColorSetting color = add(new ColorSetting("Glow Color", 0x7FB8FF).visibleWhen(() -> !natural.isOn()));
	public final ColorSetting color2 = add(new ColorSetting("Second Color", 0xE79BFF).visibleWhen(() -> !natural.isOn()));
	public final NumberSetting size = add(new NumberSetting("Size", 1.0, 0.5, 2.0, 0.05, "x"));
	public final ModeSetting side = add(new ModeSetting("Side", "Right", "Right", "Left"));
	public final NumberSetting distance = add(new NumberSetting("Distance", 1.5, 0.8, 3.0, 0.05));
	public final NumberSetting height = add(new NumberSetting("Fly Height", 1.55, 0.5, 2.8, 0.05));
	public final BoolSetting overHead = add(new BoolSetting("Float Over You When Still", true));
	public final NumberSetting glow = add(new NumberSetting("Glow", 1.0, 0.3, 2.0, 0.05, "x"));

	private double x, y, z, px, py, pz, vx, vy, vz;
	private float yaw, pyaw, phase, pphase, perchAmt, pperch;
	private int still;
	private boolean init;
	private float fade;
	private ClientLevel lastWorld;
	private String lastType = "";
	private Vec3 lastPlayer;

	public Pets() {
		super("Pets", "A glowing jellyfish, manta ray or star whale that floats with you", Category.COSMETICS, true);
	}

	public static boolean isPet(Entity e) {
		return false;
	}

	public static boolean isGround(String type) {
		return false;
	}

	@Override
	protected void onDisable() {
		init = false;
	}

	private int c1() {
		if (!natural.isOn()) {
			return color.rgb();
		}
		return switch (pet.get()) {
			case "Manta Ray" -> 0x5FE3FF;
			case "Star Whale" -> 0x8FA8FF;
			default -> 0x7FB8FF;
		};
	}

	private int c2() {
		if (!natural.isOn()) {
			return color2.rgb();
		}
		return switch (pet.get()) {
			case "Manta Ray" -> 0xB48CFF;
			case "Star Whale" -> 0xFFF3C4;
			default -> 0xE79BFF;
		};
	}

	private static double[] overHead(Player p, float td, double lift) {
		Vec3 pos = p.getPosition(td);
		float sc = p.getScale() * 0.9375f;
		boolean sneak = p.isCrouching();
		return new double[]{pos.x, pos.y + (sneak ? 1.55 : 1.9) * sc + lift, pos.z};
	}

	private double lift() {
		return switch (pet.get()) {
			case "Manta Ray" -> 0.82;
			case "Star Whale" -> 0.88;
			default -> 0.98;
		};
	}

	@Override
	public void onTick(Minecraft mc) {
		ClientLevel world = mc.level;
		Player player = mc.player;
		if (world == null || player == null) {
			init = false;
			lastPlayer = null;
			return;
		}
		if (world != lastWorld || !pet.get().equals(lastType)) {
			init = false;
			lastWorld = world;
			lastType = pet.get();
		}
		px = x;
		py = y;
		pz = z;
		pyaw = yaw;
		pphase = phase;
		pperch = perchAmt;

		Vec3 pl = player.position();
		boolean playerMoving = lastPlayer != null && pl.distanceToSqr(lastPlayer) > 0.0004;
		lastPlayer = pl;
		still = playerMoving ? 0 : still + 1;

		float body = player.getVisualRotationYInDegrees();
		V3 f = V3.forward(body), r = V3.right(body);
		float sideSign = side.is("Left") ? -1f : 1f;
		double h = height.get() * player.getScale();
		double d = distance.get();
		float t = WhiteClient.get().time();
		double tx = pl.x + r.x() * sideSign * d * 0.85 + f.x() * 0.1;
		double ty = pl.y + h;
		double tz = pl.z + r.z() * sideSign * d * 0.85 + f.z() * 0.1;
		double k, damp;
		float flapRate;
		boolean rest = overHead.isOn() && still > 40 && !player.isSpectator();
		switch (pet.get()) {
			case "Manta Ray" -> {
				tx += Math.cos(t * 0.55) * 0.55;
				tz += Math.sin(t * 0.55) * 0.55;
				ty += Math.sin(t * 0.9) * 0.16;
				k = 0.04;
				damp = 0.18;
				flapRate = 0.16f;
			}
			case "Star Whale" -> {
				double ang = t * 0.28 * sideSign;
				tx = pl.x + Math.cos(ang) * d * 1.35;
				tz = pl.z + Math.sin(ang) * d * 1.35;
				ty = pl.y + h + 0.55 + Math.sin(t * 0.7) * 0.15;
				k = 0.03;
				damp = 0.16;
				flapRate = 0.09f;
			}
			default -> {
				ty += Math.sin(t * 0.8) * 0.18;
				tx += Math.sin(t * 0.3) * 0.2;
				tz += Math.cos(t * 0.35) * 0.2;
				k = 0.035;
				damp = 0.18;
				flapRate = 0.13f;
			}
		}
		if (rest) {
			double[] spot = overHead(player, 1f, lift());
			tx = spot[0];
			ty = spot[1];
			tz = spot[2];
			k = 0.08;
			damp = 0.3;
		}
		if (!init || Math.sqrt(Math.pow(tx - x, 2) + Math.pow(ty - y, 2) + Math.pow(tz - z, 2)) > 18) {
			x = px = tx;
			y = py = ty;
			z = pz = tz;
			yaw = pyaw = body;
			vx = vy = vz = 0;
			fade = 0;
			init = true;
		}
		vx += (tx - x) * k - vx * damp;
		vy += (ty - y) * k - vy * damp;
		vz += (tz - z) * k - vz * damp;
		x += vx;
		y += vy;
		z += vz;
		double toTarget = Math.sqrt(Math.pow(tx - x, 2) + Math.pow(ty - y, 2) + Math.pow(tz - z, 2));
		perchAmt += ((rest && toTarget < 0.2 ? 1f : 0f) - perchAmt) * 0.15f;
		double hs = Math.sqrt(vx * vx + vz * vz);
		float want = hs > 0.004 ? (float) Math.toDegrees(Math.atan2(-vx, vz)) : yaw;
		if (perchAmt > 0.5f) {
			want = body;
		}
		yaw += Mth.wrapDegrees(want - yaw) * 0.12f;
		phase += flapRate * (1f + (float) hs * 6f);
		fade = Math.min(1f, fade + 0.06f);
	}

	private static float restScale(String type) {
		return switch (type) {
			case "Star Whale" -> 0.7f;
			case "Manta Ray" -> 0.8f;
			default -> 0.72f;
		};
	}

	@Override
	public void renderWorld(WorldContext ctx) {
		if (!init || ctx.client.player == null) {
			return;
		}
		float td = ctx.tickDelta;
		Player player = ctx.client.player;
		double lx = Mth.lerp(td, px, x), ly = Mth.lerp(td, py, y), lz = Mth.lerp(td, pz, z);
		float lyaw = pyaw + Mth.wrapDegrees(yaw - pyaw) * td;
		float pr = Mth.lerp(td, pperch, perchAmt);
		if (pr > 0.001f) {
			double[] spot = overHead(player, td, lift());
			lx = Mth.lerp(pr, lx, spot[0]);
			ly = Mth.lerp(pr, ly, spot[1]);
			lz = Mth.lerp(pr, lz, spot[2]);
		}
		float ph = Mth.lerp(td, pphase, phase);
		V3 base = new V3(ctx.rx(lx), ctx.ry(ly), ctx.rz(lz));
		float camDist = base.length();
		float a = glow.getFloat() * fade;
		if (!ctx.thirdPerson() && camDist < 1.2f) {
			a *= Math.max(0f, (camDist - 0.6f) / 0.6f);
		}
		if (a <= 0.01f) {
			return;
		}
		int c1 = c1(), c2 = c2();
		String type = pet.get();
		float s = size.getFloat() * Mth.lerp(pr, 1f, restScale(type));
		float t = ctx.time;
		GlowBatch g = ctx.glow;
		float tagHeight;
		switch (type) {
			case "Manta Ray" -> {
				manta(g, base, lyaw, ph, t, s, c1, c2, a);
				tagHeight = 0.25f * s;
			}
			case "Star Whale" -> {
				whale(g, base, lyaw, ph, t, s, c1, c2, a);
				tagHeight = 0.35f * s;
			}
			default -> {
				jellyfish(g, base, t, s, c1, c2, a);
				tagHeight = 0.5f * s;
			}
		}
		String n = name.get().trim();
		if (!n.isEmpty()) {
			WorldText.draw(ctx, base.x(), base.y() + tagHeight + 0.1f, base.z(), n, 0.9f, Colors.argb(0xFFFFFF, Math.min(1f, fade)), true);
		}
	}

	private void manta(GlowBatch g, V3 c, float yawDeg, float ph, float t, float s, int c1, int c2, float a) {
		V3 f = V3.forward(yawDeg), r = V3.right(yawDeg), u = V3.UP;
		float span = 0.62f * s, len = 0.36f * s;
		V3 nose = c.add(f, len * 0.55f);
		V3 tailRoot = c.add(f, -len * 0.5f);
		int n = 8;
		for (int side = -1; side <= 1; side += 2) {
			V3 prevLead = nose, prevTrail = tailRoot;
			for (int k = 1; k <= n; k++) {
				float x = k / (float) n;
				float flap = (float) Math.sin(ph * 2.2f - x * 1.7f) * 0.17f * s * (float) Math.pow(x, 1.4);
				float sweep = x * x;
				V3 lead = c.add(r, side * span * x).add(f, len * (0.55f - 0.6f * sweep)).add(u, flap);
				V3 trail = c.add(r, side * span * x * 0.92f).add(f, -len * (0.5f - 0.48f * x)).add(u, flap * 0.92f);
				if (k == n) {
					trail = lead;
				}
				int fill = Colors.argb(Colors.mix(c1, c2, x * 0.6f), 0.3f * a * (1f - 0.35f * x));
				g.quad(prevLead.x(), prevLead.y(), prevLead.z(), fill, lead.x(), lead.y(), lead.z(), fill,
						trail.x(), trail.y(), trail.z(), fill, prevTrail.x(), prevTrail.y(), prevTrail.z(), fill);
				prevLead.line(g, lead, 0.065f * s, Colors.mix(c1, 0xFFFFFF, 0.25f), a);
				prevTrail.line(g, trail, 0.045f * s, Colors.mix(c1, c2, 0.5f), 0.75f * a);
				if (k % 2 == 0) {
					lead.line(g, trail, 0.03f * s, Colors.mix(c1, c2, x), 0.45f * a);
				}
				if (k == 3 || k == 5) {
					V3 spot = lerp(lead, trail, 0.5f);
					g.disc(spot.x(), spot.y(), spot.z(), 0.025f * s, c2, 0.8f * a, 8);
				}
				prevLead = lead;
				prevTrail = trail;
			}
			V3 h0 = nose.add(r, side * 0.05f * s);
			h0.line(g, h0.add(f, 0.07f * s).add(r, side * 0.025f * s).add(u, -0.02f * s), 0.03f * s, c1, 0.8f * a);
			g.disc(nose.x() + r.x() * side * 0.045f * s, nose.y() + 0.01f * s, nose.z() + r.z() * side * 0.045f * s, 0.015f * s, 0xFFFFFF, a, 6);
		}
		nose.line(g, tailRoot, 0.07f * s, Colors.mix(c1, 0xFFFFFF, 0.4f), 0.9f * a);
		V3 prev = tailRoot;
		for (int k = 1; k <= 7; k++) {
			float sway = (float) Math.sin(ph * 2.2f - k * 0.6f) * 0.03f * s * k;
			V3 pt = tailRoot.add(f, -0.09f * s * k).add(r, sway).add(u, sway * 0.4f);
			prev.line(g, pt, (0.03f - 0.003f * k) * s, c1, (0.8f - 0.08f * k) * a);
			prev = pt;
		}
		g.disc(c.x(), c.y(), c.z(), 0.55f * s, c1, 0.12f * a, 14);
	}

	private void whale(GlowBatch g, V3 c, float yawDeg, float ph, float t, float s, int c1, int c2, float a) {
		V3 f = V3.forward(yawDeg), r = V3.right(yawDeg), u = V3.UP;
		float L = 0.85f * s;
		int rings = 8, seg = 10;
		V3[][] pts = new V3[rings][seg];
		V3[] centres = new V3[rings];
		for (int i = 0; i < rings; i++) {
			float x = i / (float) (rings - 1);
			float rad = 0.17f * s * (float) Math.sin(Math.PI * (0.12f + 0.82f * (1f - x * x * 0.85f)) ) * (1f - 0.75f * x * x);
			rad = Math.max(0.02f * s, rad);
			float bend = (float) Math.sin(ph * 2.0f - x * 2.4f) * 0.06f * s * x * x;
			V3 cen = c.add(f, L * (0.45f - x)).add(u, bend);
			centres[i] = cen;
			for (int j = 0; j < seg; j++) {
				double an = Math.PI * 2 * j / seg;
				pts[i][j] = cen.add(r, (float) Math.cos(an) * rad).add(u, (float) Math.sin(an) * rad * 0.85f);
			}
		}
		for (int i = 0; i < rings; i++) {
			float x = i / (float) (rings - 1);
			for (int j = 0; j < seg; j++) {
				V3 p0 = pts[i][j], p1 = pts[i][(j + 1) % seg];
				g.glowLine(p0.x(), p0.y(), p0.z(), p1.x(), p1.y(), p1.z(), 0.032f * s, Colors.mix(c1, c2, x * 0.3f), a * (0.85f - 0.3f * x));
				if (i + 1 < rings && (j % 2 == 0)) {
					V3 q = pts[i + 1][j];
					g.glowLine(p0.x(), p0.y(), p0.z(), q.x(), q.y(), q.z(), 0.026f * s, c1, a * 0.6f);
				}
			}
		}
		for (int i = 0; i < rings - 1; i++) {
			float x = i / (float) (rings - 1);
			g.disc(centres[i].x(), centres[i].y(), centres[i].z(), 0.2f * s * (1f - 0.7f * x), Colors.mix(c1, 0xFFFFFF, 0.3f), 0.22f * a, 10);
		}
		V3 end = centres[rings - 1];
		float beat = (float) Math.sin(ph * 2.0f - 2.6f) * 0.08f * s;
		for (int side = -1; side <= 1; side += 2) {
			V3 tip = end.add(f, -0.12f * s).add(r, side * 0.17f * s).add(u, beat);
			V3 back = end.add(f, -0.06f * s).add(u, beat * 0.6f);
			int fill = Colors.argb(c1, 0.32f * a);
			g.quad(end.x(), end.y(), end.z(), fill, tip.x(), tip.y(), tip.z(), fill, back.x(), back.y(), back.z(), fill, back.x(), back.y(), back.z(), fill);
			end.line(g, tip, 0.035f * s, c1, 0.85f * a);
			tip.line(g, back, 0.03f * s, c2, 0.6f * a);
			V3 root = centres[2].add(r, side * 0.1f * s).add(u, -0.06f * s);
			float paddle = (float) Math.sin(ph * 1.3f + side) * 0.03f * s;
			root.line(g, root.add(r, side * 0.14f * s).add(f, -0.06f * s).add(u, -0.05f * s + paddle), 0.04f * s, c1, 0.75f * a);
		}
		V3 eye = centres[1].add(r, 0.12f * s).add(u, 0.02f * s);
		V3 eye2 = centres[1].add(r, -0.12f * s).add(u, 0.02f * s);
		g.disc(eye.x(), eye.y(), eye.z(), 0.018f * s, 0xFFFFFF, a, 6);
		g.disc(eye2.x(), eye2.y(), eye2.z(), 0.018f * s, 0xFFFFFF, a, 6);
		for (int i = 0; i < 6; i++) {
			V3 back = centres[1 + i % (rings - 2)];
			float tw = 0.5f + 0.5f * (float) Math.sin(t * 3 + i * 1.7f);
			V3 st = back.add(u, 0.13f * s * (1f - 0.12f * i)).add(r, (float) Math.sin(i * 2.3f) * 0.05f * s);
			g.disc(st.x(), st.y(), st.z(), 0.03f * s, c2, a * (0.4f + 0.6f * tw), 6);
		}
		g.disc(centres[2].x(), centres[2].y(), centres[2].z(), 0.6f * s, c1, 0.1f * a, 14);
	}

	private static V3 lerp(V3 a, V3 b, float t) {
		return new V3(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t, a.z() + (b.z() - a.z()) * t);
	}

	private void jellyfish(GlowBatch g, V3 c, float t, float s, int c1, int c2, float a) {
		float pulse = 0.5f + 0.5f * (float) Math.sin(t * 2.6);
		float bellR = (0.27f + 0.035f * pulse) * s;
		float bellH = (0.24f - 0.035f * pulse) * s;
		V3 top = c.add(V3.UP, 0.12f * s);
		int rings = 5, seg = 18;
		V3[] rim = new V3[seg + 1];
		for (int ring = 1; ring <= rings; ring++) {
			float u = ring / (float) rings;
			float rr = bellR * (float) Math.sin(u * Math.PI * 0.5);
			float yy = -bellH * u * u;
			V3 prev = null;
			for (int i = 0; i <= seg; i++) {
				double ang = Math.PI * 2 * i / seg + t * 0.3;
				float wav = ring == rings ? 0.015f * s * (float) Math.sin(ang * 6 + t * 4) : 0;
				V3 pt = new V3(top.x() + (float) Math.cos(ang) * (rr + wav), top.y() + yy, top.z() + (float) Math.sin(ang) * (rr + wav));
				if (prev != null) {
					g.glowLine(prev.x(), prev.y(), prev.z(), pt.x(), pt.y(), pt.z(), (ring == rings ? 0.05f : 0.03f) * s, Colors.mix(c1, c2, u), a * (ring == rings ? 0.9f : 0.45f));
				}
				prev = pt;
				if (ring == rings) {
					rim[i] = pt;
				}
			}
		}
		g.disc(top.x(), top.y() - bellH * 0.5f, top.z(), bellR * 2.4f, c1, 0.28f * a * (0.7f + 0.3f * pulse), 16);
		g.disc(top.x(), top.y() - bellH * 0.4f, top.z(), bellR * 0.9f, 0xFFFFFF, 0.35f * a, 12);
		int tentacles = 7;
		for (int k = 0; k < tentacles; k++) {
			V3 start = rim[(k * seg / tentacles) % seg];
			V3 prev = start;
			int steps = 8;
			float len = (0.6f + 0.14f * (k % 3)) * s;
			for (int st = 1; st <= steps; st++) {
				float u = st / (float) steps;
				float sway = (float) Math.sin(t * 2.2 - u * 4 + k) * 0.06f * u * s;
				float sway2 = (float) Math.cos(t * 1.8 - u * 3 + k * 1.7) * 0.06f * u * s;
				float inward = 1f - 0.35f * u;
				V3 pt = new V3(c.x() + (start.x() - c.x()) * inward + sway, start.y() - len * u, c.z() + (start.z() - c.z()) * inward + sway2);
				g.glowLine(prev.x(), prev.y(), prev.z(), pt.x(), pt.y(), pt.z(), 0.028f * s, Colors.mix(c1, c2, 0.4f + 0.6f * u), a * (1f - u * 0.75f) * 0.8f);
				prev = pt;
			}
		}
	}
}
