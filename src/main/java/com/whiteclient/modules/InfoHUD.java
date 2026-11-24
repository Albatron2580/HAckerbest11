package com.whiteclient.modules;

import com.whiteclient.gui.Render2D;
import com.whiteclient.hud.ClickCounter;
import com.whiteclient.hud.Hud;
import com.whiteclient.hud.HudLayout;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ModeSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.math.Vec3d;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InfoHUD extends Module {
	public final ModeSetting position = add(new ModeSetting("Position", "Top Left", HudLayout.POSITIONS));
	public final BoolSetting coords = add(new BoolSetting("Coordinates", true));
	public final BoolSetting direction = add(new BoolSetting("Direction", true));
	public final BoolSetting speed = add(new BoolSetting("Speed", true));
	public final BoolSetting cps = add(new BoolSetting("CPS", true));
	public final BoolSetting fps = add(new BoolSetting("FPS", false));
	public final BoolSetting ping = add(new BoolSetting("Ping", false));
	public final BoolSetting clock = add(new BoolSetting("Clock", true));
	public final BoolSetting day = add(new BoolSetting("Day", false));

	private Vec3d lastPos;
	private float speedBps;

	public InfoHUD() {
		super("Info HUD", "Coords, direction, speed, CPS, clock...", Category.HUD, false);
	}

	@Override
	public void onTick(MinecraftClient mc) {
		if (mc.player == null) {
			lastPos = null;
			return;
		}
		Vec3d p = mc.player.getEntityPos();
		if (lastPos != null) {
			double dx = p.x - lastPos.x, dz = p.z - lastPos.z;
			float bps = (float) Math.sqrt(dx * dx + dz * dz) * 20f;
			speedBps += (bps - speedBps) * 0.5f;
		}
		lastPos = p;
	}

	private static String facing(float yaw) {
		float y = ((yaw % 360) + 360) % 360;
		String[] names = {"South +Z", "South West", "West -X", "North West", "North -Z", "North East", "East +X", "South East"};
		return names[Math.round(y / 45f) % 8];
	}

	@Override
	public void renderHud(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) {
			return;
		}
		List<String[]> rows = new ArrayList<>();
		if (coords.isOn()) {
			rows.add(new String[]{"XYZ", String.format(Locale.ROOT, "%d  %d  %d", (int) Math.floor(mc.player.getX()), (int) Math.floor(mc.player.getY()), (int) Math.floor(mc.player.getZ()))});
		}
		if (direction.isOn()) {
			rows.add(new String[]{"Facing", facing(mc.player.getYaw())});
		}
		if (speed.isOn()) {
			rows.add(new String[]{"Speed", String.format(Locale.ROOT, "%.1f m/s", speedBps)});
		}
		if (cps.isOn()) {
			rows.add(new String[]{"CPS", ClickCounter.left() + "  |  " + ClickCounter.right()});
		}
		if (fps.isOn()) {
			rows.add(new String[]{"FPS", String.valueOf(mc.getCurrentFps())});
		}
		if (ping.isOn()) {
			PlayerListEntry e = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
			rows.add(new String[]{"Ping", (e == null ? 0 : e.getLatency()) + " ms"});
		}
		if (clock.isOn()) {
			LocalTime t = LocalTime.now();
			rows.add(new String[]{"Time", String.format(Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute())});
		}
		if (day.isOn() && mc.world != null) {
			rows.add(new String[]{"Day", String.valueOf(mc.world.getTimeOfDay() / 24000L + 1)});
		}
		if (rows.isEmpty()) {
			return;
		}
		TextRenderer tr = mc.textRenderer;
		float labelW = 0, valueW = 0;
		for (String[] r : rows) {
			labelW = Math.max(labelW, tr.getWidth(r[0]));
			valueW = Math.max(valueW, tr.getWidth(r[1]));
		}
		float lh = 11;
		float w = 8 + labelW + 10 + valueW + 8, h = 6 + rows.size() * lh + 2;
		float[] p = HudLayout.place(position.get(), w, h);
		Hud.panel(ctx, p[0], p[1], w, h, 1f);
		float y = p[1] + 6;
		for (String[] r : rows) {
			Render2D.text(ctx, tr, r[0], p[0] + 8, y, Hud.SUB);
			Render2D.text(ctx, tr, r[1], p[0] + 8 + labelW + 10, y, Hud.TEXT);
			y += lh;
		}
	}
}
