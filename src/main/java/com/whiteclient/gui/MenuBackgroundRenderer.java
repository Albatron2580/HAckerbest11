package com.whiteclient.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.whiteclient.WhiteClient;
import com.whiteclient.modules.MenuBackground;
import com.whiteclient.render.BackgroundShader;
import com.whiteclient.render.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.OptionalDouble;

/**
 * Animated menu background. Pixels are computed by {@link BackgroundShader} on a
 * worker thread and uploaded into a small dynamic texture that is stretched over the screen.
 */
public final class MenuBackgroundRenderer {
	private static final Identifier TEXTURE_ID = Identifier.of(WhiteClient.MOD_ID, "dynamic/menu_background");
	private static final int TEX_W = 384;
	private static final int TEX_H = 216;

	private static final Object LOCK = new Object();
	private static int[] front = new int[TEX_W * TEX_H];
	private static int[] work = new int[TEX_W * TEX_H];
	private static boolean fresh;
	private static boolean hasFrame;
	private static volatile long lastRequestMs;
	/** Shader mode wanted by the screen currently drawing (-1 = Menu Background setting). */
	private static volatile int requestedMode = -1;
	private static Thread worker;
	private static SmoothTexture texture;

	private MenuBackgroundRenderer() {
	}

	/** Texture that samples with linear filtering so the low-res image looks smooth. */
	private static final class SmoothTexture extends NativeImageBackedTexture {
		private GpuSampler linear;
		private boolean samplerFailed;

		SmoothTexture() {
			super(() -> "whiteclient_menu_background", TEX_W, TEX_H, false);
		}

		@Override
		public GpuSampler getSampler() {
			if (linear == null && !samplerFailed) {
				try {
					linear = RenderSystem.getDevice().createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
							FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
				} catch (Throwable t) {
					samplerFailed = true;
					WhiteClient.LOGGER.warn("[WHITECLIENT] linear sampler unavailable, using default", t);
				}
			}
			return linear != null ? linear : super.getSampler();
		}
	}

	private static void ensureWorker() {
		if (worker != null && worker.isAlive()) {
			return;
		}
		worker = new Thread(MenuBackgroundRenderer::workerLoop, "WHITECLIENT background");
		worker.setDaemon(true);
		worker.setPriority(Thread.MIN_PRIORITY + 1);
		worker.start();
	}

	private static void workerLoop() {
		long start = System.nanoTime();
		while (true) {
			try {
				long now = System.currentTimeMillis();
				if (now - lastRequestMs > 1500) {
					Thread.sleep(60);
					continue;
				}
				MenuBackground settings = WhiteClient.get().modules().get(MenuBackground.class);
				int forced = requestedMode;
				int mode = forced >= 0 ? forced : settings.shaderMode();
				int color = settings.color.rgb();
				float speed = settings.speed.getFloat();
				float intensity = settings.intensity.getFloat();
				double t = (System.nanoTime() - start) / 1.0e9;
				long frameStart = System.currentTimeMillis();
				BackgroundShader.render(work, TEX_W, TEX_H, t, mode, color, speed, intensity);
				synchronized (LOCK) {
					int[] tmp = front;
					front = work;
					work = tmp;
					fresh = true;
				}
				long spent = System.currentTimeMillis() - frameStart;
				Thread.sleep(Math.max(4, 33 - spent));
			} catch (InterruptedException e) {
				return;
			} catch (Throwable t) {
				WhiteClient.LOGGER.error("[WHITECLIENT] background worker error", t);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					return;
				}
			}
		}
	}

	private static void upload() {
		if (texture == null) {
			texture = new SmoothTexture();
			MinecraftClient.getInstance().getTextureManager().registerTexture(TEXTURE_ID, texture);
		}
		synchronized (LOCK) {
			if (!fresh) {
				return;
			}
			NativeImage image = texture.getImage();
			if (image == null) {
				return;
			}
			int[] src = front;
			for (int y = 0; y < TEX_H; y++) {
				int row = y * TEX_W;
				for (int x = 0; x < TEX_W; x++) {
					image.setColorArgb(x, y, src[row + x]);
				}
			}
			fresh = false;
		}
		texture.upload();
		hasFrame = true;
	}

	/** Draws the animated background over the whole screen. Never throws. */
	public static void draw(DrawContext ctx, int width, int height, float alpha) {
		draw(ctx, width, height, alpha, -1);
	}

	/** Same, with a specific {@link com.whiteclient.render.BackgroundShader} mode (-1 = setting). */
	public static void draw(DrawContext ctx, int width, int height, float alpha, int mode) {
		requestedMode = mode;
		try {
			drawUnsafe(ctx, width, height, alpha);
		} catch (Throwable t) {
			if (!loggedFailure) {
				loggedFailure = true;
				WhiteClient.LOGGER.error("[WHITECLIENT] menu background failed, falling back to a plain colour", t);
			}
			ctx.fill(0, 0, width, height, Colors.argb(0x0A0604, alpha));
		}
	}

	private static boolean loggedFailure;

	private static void drawUnsafe(DrawContext ctx, int width, int height, float alpha) {
		lastRequestMs = System.currentTimeMillis();
		ensureWorker();
		try {
			upload();
		} catch (Throwable t) {
			WhiteClient.LOGGER.error("[WHITECLIENT] background upload failed", t);
		}
		if (!hasFrame) {
			ctx.fill(0, 0, width, height, Colors.argb(0x0A0604, alpha));
			return;
		}
		// crop the texture so it keeps its aspect ratio
		float texAspect = TEX_W / (float) TEX_H;
		float screenAspect = width / (float) Math.max(1, height);
		int regionW = TEX_W;
		int regionH = TEX_H;
		float u = 0;
		float v = 0;
		if (screenAspect > texAspect) {
			regionH = Math.max(1, Math.round(TEX_W / screenAspect));
			v = (TEX_H - regionH) / 2f;
		} else {
			regionW = Math.max(1, Math.round(TEX_H * screenAspect));
			u = (TEX_W - regionW) / 2f;
		}
		ctx.drawTexture(RenderPipelines.GUI_TEXTURED, TEXTURE_ID, 0, 0, u, v, width, height, regionW, regionH,
				TEX_W, TEX_H, Colors.argb(0xFFFFFF, alpha));
	}
}
