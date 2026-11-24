package com.whiteclient.hud;

public final class HudLayout {
	public static final String[] POSITIONS = {"Top Left", "Top Right", "Bottom Left", "Bottom Right", "Left", "Right"};
	private static final float[] CURSOR = new float[6];
	private static int screenW = 320, screenH = 240;

	private HudLayout() {
	}

	public static void begin(int w, int h) {
		screenW = w;
		screenH = h;
		CURSOR[0] = CURSOR[1] = 6;
		CURSOR[2] = CURSOR[3] = h - 6;
		CURSOR[4] = CURSOR[5] = h * 0.3f;
	}

	private static int index(String pos) {
		for (int i = 0; i < POSITIONS.length; i++) {
			if (POSITIONS[i].equals(pos)) {
				return i;
			}
		}
		return 0;
	}

	private static boolean bottom(int i) {
		return i == 2 || i == 3;
	}

	public static void reserve(String pos, float y1, float y2) {
		int i = index(pos);
		if (bottom(i)) {
			CURSOR[i] = Math.min(CURSOR[i], y1 - 4);
		} else {
			CURSOR[i] = Math.max(CURSOR[i], y2 + 4);
		}
	}

	public static float[] place(String pos, float w, float h) {
		int i = index(pos);
		boolean right = i == 1 || i == 3 || i == 5;
		float x = right ? screenW - 6 - w : 6;
		float y;
		if (bottom(i)) {
			y = CURSOR[i] - h;
			CURSOR[i] = y - 4;
		} else {
			y = i >= 4 ? Math.max(CURSOR[i], CURSOR[i - 4]) : CURSOR[i];
			CURSOR[i] = y + h + 4;
		}
		return new float[]{x, y};
	}

	public static int width() {
		return screenW;
	}

	public static int height() {
		return screenH;
	}
}
