package com.whiteclient.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.List;

public final class CosmeticTargets {
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private CosmeticTargets() {
	}

	public static boolean visible(PlayerEntity p) {
		if (!p.isInvisible()) {
			return true;
		}
		for (EquipmentSlot slot : ARMOR) {
			if (p.hasStackEquipped(slot)) {
				return true;
			}
		}
		return false;
	}

	public static List<PlayerEntity> collect(WorldContext ctx, boolean self, boolean selfFirstPerson, boolean others, double maxDist) {
		List<PlayerEntity> out = new ArrayList<>();
		MinecraftClient mc = ctx.client;
		if (mc.player == null || mc.world == null) {
			return out;
		}
		if (self && (ctx.thirdPerson() || selfFirstPerson) && visible(mc.player)) {
			out.add(mc.player);
		}
		if (others) {
			double max2 = maxDist * maxDist;
			for (PlayerEntity p : mc.world.getPlayers()) {
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
