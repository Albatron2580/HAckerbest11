package com.whiteclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public final class WorldContext {
	public final Minecraft client;
	public final PoseStack matrices;
	public final WorldDraw draw;
	public final Vec3 cam;
	public final Quaternionf camRotation;
	public final float tickDelta;
	public final float time;
	public final float frameDelta;
	public final GlowBatch glow;

	public WorldContext(Minecraft client, PoseStack matrices, WorldDraw draw, Vec3 cam,
						Quaternionf camRotation, float tickDelta, float time, float frameDelta, GlowBatch glow) {
		this.client = client;
		this.matrices = matrices;
		this.draw = draw;
		this.cam = cam;
		this.camRotation = camRotation;
		this.tickDelta = tickDelta;
		this.time = time;
		this.frameDelta = frameDelta;
		this.glow = glow;
	}

	public float rx(double worldX) {
		return (float) (worldX - cam.x);
	}

	public float ry(double worldY) {
		return (float) (worldY - cam.y);
	}

	public float rz(double worldZ) {
		return (float) (worldZ - cam.z);
	}

	public boolean thirdPerson() {
		return client.options != null && !client.options.getCameraType().isFirstPerson();
	}
}
