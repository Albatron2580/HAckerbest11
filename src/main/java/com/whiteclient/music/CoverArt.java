package com.whiteclient.music;

import com.mojang.blaze3d.platform.NativeImage;
import com.whiteclient.WhiteClient;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class CoverArt {
	public static final int SIZE = 64;
	public static final Identifier ID = Identifier.fromNamespaceAndPath(WhiteClient.MOD_ID, "dynamic/cover");
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6))
			.followRedirects(HttpClient.Redirect.NORMAL).build();

	private volatile String requestedUrl;
	private volatile int[] pending;
	private volatile boolean hasCover;
	private DynamicTexture texture;

	void request(String url) {
		if (url == null || url.equals(requestedUrl)) {
			if (url == null && requestedUrl != null) {
				requestedUrl = null;
				hasCover = false;
			}
			return;
		}
		requestedUrl = url;
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
					.header("User-Agent", LyricsService.USER_AGENT).GET().build();
			byte[] bytes = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray()).body();
			BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
			if (img == null) {
				return;
			}
			pending = toRoundedPixels(img);
		} catch (Throwable t) {
			WhiteClient.LOGGER.warn("[WHITECLIENT] cover download failed: {}", t.toString());
		}
	}

	private static int[] toRoundedPixels(BufferedImage img) {
		int[] out = new int[SIZE * SIZE];
		int w = img.getWidth(), h = img.getHeight();
		float radius = SIZE * 0.18f;
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				int x0 = x * w / SIZE, x1 = Math.max(x0 + 1, (x + 1) * w / SIZE);
				int y0 = y * h / SIZE, y1 = Math.max(y0 + 1, (y + 1) * h / SIZE);
				long r = 0, g = 0, b = 0, n = 0;
				for (int sy = y0; sy < y1; sy++) {
					for (int sx = x0; sx < x1; sx++) {
						int c = img.getRGB(sx, sy);
						r += (c >> 16) & 0xFF;
						g += (c >> 8) & 0xFF;
						b += c & 0xFF;
						n++;
					}
				}
				float cx = Math.min(x + 0.5f, SIZE - x - 0.5f);
				float cy = Math.min(y + 0.5f, SIZE - y - 0.5f);
				float alpha = 1f;
				if (cx < radius && cy < radius) {
					float dx = radius - cx, dy = radius - cy;
					float d = (float) Math.sqrt(dx * dx + dy * dy);
					alpha = Math.max(0f, Math.min(1f, radius - d + 0.5f));
				}
				int a = (int) (alpha * 255);
				out[y * SIZE + x] = (a << 24) | ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
			}
		}
		return out;
	}

	public Identifier texture() {
		int[] px = pending;
		if (px != null) {
			pending = null;
			try {
				if (texture == null) {
					texture = new DynamicTexture(() -> "whiteclient_cover", SIZE, SIZE, false);
					Minecraft.getInstance().getTextureManager().register(ID, texture);
				}
				NativeImage image = texture.getPixels();
				if (image != null) {
					for (int y = 0; y < SIZE; y++) {
						for (int x = 0; x < SIZE; x++) {
							image.setPixel(x, y, px[y * SIZE + x]);
						}
					}
					texture.upload();
					hasCover = true;
				}
			} catch (Throwable t) {
				WhiteClient.LOGGER.warn("[WHITECLIENT] cover upload failed: {}", t.toString());
			}
		}
		return hasCover && requestedUrl != null ? ID : null;
	}
}
