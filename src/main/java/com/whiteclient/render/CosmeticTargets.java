package com.whiteclient.render;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

public final class CosmeticTargets {
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private CosmeticTargets() {
	}

	public static boolean visible(Player p) {
		if (!p.isInvisible()) {
			return true;
		}
		for (EquipmentSlot slot : ARMOR) {
			if (p.hasItemInSlot(slot)) {
				return true;
			}
		}
		return false;
	}

	public static List<Player> collect(WorldContext ctx, boolean self, boolean selfFirstPerson, boolean others, double maxDist) {
		List<Player> out = new ArrayList<>();
		Minecraft mc = ctx.client;
		if (mc.player == null || mc.level == null) {
			return out;
		}
		if (self && (ctx.thirdPerson() || selfFirstPerson) && visible(mc.player)) {
			out.add(mc.player);
		}
		if (others) {
			double max2 = maxDist * maxDist;
			for (Player p : mc.level.players()) {
				if (p == mc.player || p.isSpectator() || !p.isAlive() || !visible(p)) {
					continue;
				}
				double dx = p.getX() - ctx.cam.x, dy = p.getY() - ctx.cam.y, dz = p.getZ() - ctx.cam.z;
				if (dx * dx + dy * dy + dz * dz > max2) {
					continue;
				}
				out.add(p);
			}
		}
		return out;
	}
}
