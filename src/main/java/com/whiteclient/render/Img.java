package com.whiteclient.render;

import com.mojang.blaze3d.platform.NativeImage;

public final class Img {
	private Img() {
	}

	public static int get(NativeImage img, int x, int y) {
		//#if MC >= 1.21.2
		return img.getPixel(x, y);
		//#else
		//$ return swap(img.getPixelRGBA(x, y));
		//#endif
	}

	public static void set(NativeImage img, int x, int y, int argb) {
		//#if MC >= 1.21.2
		img.setPixel(x, y, argb);
		//#else
		//$ img.setPixelRGBA(x, y, swap(argb));
		//#endif
	}

	private static int swap(int c) {
		return (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16);
	}
}
