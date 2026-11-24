package com.whiteclient.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

public final class WorldContext {
	public final MinecraftClient client;
	public final MatrixStack matrices;
	public final VertexConsumerProvider consumers;
	public final Vec3d cam;
	public final Quaternionf camRotation;
	public final float tickDelta;
	public final float time;
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

	public boolean thirdPerson() {
		return client.options != null && !client.options.getPerspective().isFirstPerson();
	}
}
