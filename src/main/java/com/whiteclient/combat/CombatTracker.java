package com.whiteclient.combat;

import com.whiteclient.WhiteClient;
import com.whiteclient.modules.Pets;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CombatTracker {
	public interface HitListener {
		void onHit(LivingEntity target, Vec3 hitPos, boolean crit);
	}

	public interface KillListener {
		void onKill(LivingEntity target, Vec3 pos);
	}

	private static final List<HitListener> HIT = new CopyOnWriteArrayList<>();
	private static final List<KillListener> KILL = new CopyOnWriteArrayList<>();
	private static final List<Recent> RECENT = new ArrayList<>();

	private static LivingEntity lastHit;
	private static long lastHitAt;
	private static double lastReach;
	private static long lastReachAt;
	private static int combo;
	private static long comboAt;
	private static int lastPlayerHurt;
	private static long lastKillAt;
	private static long lastCritAt;
	private static int lastCritId = Integer.MIN_VALUE;
	private static boolean registered;

	private record Recent(LivingEntity entity, long at) {
	}

	private CombatTracker() {
	}

	public static void onHit(HitListener l) {
		HIT.add(l);
	}

	public static void onKill(KillListener l) {
		KILL.add(l);
	}

	public static void init() {
		if (registered) {
			return;
		}
		registered = true;
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			try {
				Minecraft mc = Minecraft.getInstance();
				if (world.isClientSide() && player == mc.player && entity instanceof LivingEntity living && !Pets.isPet(entity)) {
					hit(player, living);
				}
			} catch (Throwable t) {
				WhiteClient.LOGGER.debug("[WHITECLIENT] combat tracker", t);
			}
			return InteractionResult.PASS;
		});
	}

	public static void hit(Player player, LivingEntity target) {
		long now = System.currentTimeMillis();
		lastHit = target;
		lastHitAt = now;
		Vec3 eye = player.getEyePosition();
		AABB box = target.getBoundingBox();
		Vec3 closest = new Vec3(Mth.clamp(eye.x, box.minX, box.maxX), Mth.clamp(eye.y, box.minY, box.maxY),
				Mth.clamp(eye.z, box.minZ, box.maxZ));
		lastReach = eye.distanceTo(closest);
		lastReachAt = now;
		combo = now - comboAt < 2500 ? combo + 1 : 1;
		comboAt = now;
		boolean crit = player.fallDistance > 0 && !player.onGround() && !player.onClimbable() && !player.isInWater()
				&& player.getDeltaMovement().y < 0;
		if (crit) {
			lastCritAt = now;
			lastCritId = target.getId();
		}
		synchronized (RECENT) {
			RECENT.removeIf(r -> r.entity() == target);
			RECENT.add(new Recent(target, now));
		}
		Vec3 pos = new Vec3(closest.x, Mth.clamp(eye.y, box.minY + 0.3, box.maxY - 0.1), closest.z);
		for (HitListener l : HIT) {
			try {
				l.onHit(target, pos, crit);
			} catch (Throwable ignored) {
			}
		}
	}

	public static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			synchronized (RECENT) {
				RECENT.clear();
			}
			lastHit = null;
			combo = 0;
			return;
		}
		int hurt = mc.player.hurtTime;
		if (hurt > lastPlayerHurt && hurt >= 8) {
			combo = 0;
		}
		lastPlayerHurt = hurt;
		long now = System.currentTimeMillis();
		if (now - comboAt > 3000) {
			combo = 0;
		}
		List<LivingEntity> killed = new ArrayList<>();
		synchronized (RECENT) {
			Iterator<Recent> it = RECENT.iterator();
			while (it.hasNext()) {
				Recent r = it.next();
				LivingEntity e = r.entity();
				if (now - r.at() > 3000) {
					it.remove();
				} else if (e.isDeadOrDying() || e.getHealth() <= 0) {
					killed.add(e);
					it.remove();
				}
			}
		}
		for (LivingEntity e : killed) {
			lastKillAt = now;
			Vec3 pos = e.position();
			for (KillListener l : KILL) {
				try {
					l.onKill(e, pos);
				} catch (Throwable ignored) {
				}
			}
		}
	}

	public static LivingEntity lastHit(long maxAgeMs) {
		if (lastHit == null || System.currentTimeMillis() - lastHitAt > maxAgeMs || lastHit.isRemoved()) {
			return null;
		}
		return lastHit;
	}

	public static long lastHitAt() {
		return lastHitAt;
	}

	public static double lastReach() {
		return lastReach;
	}

	public static long lastReachAt() {
		return lastReachAt;
	}

	public static int combo() {
		return combo;
	}

	public static long comboAt() {
		return comboAt;
	}

	public static long lastKillAt() {
		return lastKillAt;
	}

	public static boolean recentCrit(Entity e) {
		return e.getId() == lastCritId && System.currentTimeMillis() - lastCritAt < 400;
	}
}
