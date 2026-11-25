package com.whiteclient.hud;

import net.minecraft.client.Minecraft;

public final class ClickCounter {
	private static final long[] LEFT = new long[64];
	private static final long[] RIGHT = new long[64];
	private static int leftHead, rightHead;

	private ClickCounter() {
	}

	public static void click(int button) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.screen != null || mc.player == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (button == 0) {
			LEFT[leftHead++ & 63] = now;
		} else if (button == 1) {
			RIGHT[rightHead++ & 63] = now;
		}
	}

	private static int count(long[] arr) {
		long now = System.currentTimeMillis();
		int n = 0;
		for (long t : arr) {
			if (now - t <= 1000) {
				n++;
			}
		}
		return n;
	}

	public static int left() {
		return count(LEFT);
	}

	public static int right() {
		return count(RIGHT);
	}
}
