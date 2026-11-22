package com.whiteclient.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

/** Everything a module needs to draw in the world for one frame. */
public final class WorldContext {
	public final MinecraftClient client;
	public final MatrixStack matrices;
	public final VertexConsumerProvider consumers;
	/** Camera position; subtract it from world positions to get render coordinates. */
	public final Vec3d cam;
	public final Quaternionf camRotation;
	public final float tickDelta;
	/** Seconds since the client started (smooth, for animations). */
	public final float time;
	/** Seconds since the previous frame (clamped). */
	public final float frameDelta;
	public final GlowBatch glow;

	public WorldContext(MinecraftClient client, MatrixStack matrices, VertexConsumerProvider consumers, Vec3d cam,
						Quaternionf camRotation, float tickDelta, float time, float frameDelta, GlowBatch glow) {
		this.client = client;
		this.matrices = matrices;
		this.consumers = consumers;
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

	/** True when the local player is seen from the outside (F5). */
	public boolean thirdPerson() {
		return client.options != null && !client.options.getPerspective().isFirstPerson();
	}
}
