package com.whiteclient.render;

public record V3(float x, float y, float z) {
	public static final V3 UP = new V3(0, 1, 0);

	public V3 add(V3 o) {
		return new V3(x + o.x, y + o.y, z + o.z);
	}

	public V3 sub(V3 o) {
		return new V3(x - o.x, y - o.y, z - o.z);
	}

	public V3 mul(float s) {
		return new V3(x * s, y * s, z * s);
	}

	public V3 add(V3 dir, float s) {
		return new V3(x + dir.x * s, y + dir.y * s, z + dir.z * s);
	}

	public float dot(V3 o) {
		return x * o.x + y * o.y + z * o.z;
	}

	public V3 cross(V3 o) {
		return new V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
	}

	public float length() {
		return (float) Math.sqrt(x * x + y * y + z * z);
	}

	public V3 normalize() {
		float l = length();
		return l < 1.0e-6f ? this : new V3(x / l, y / l, z / l);
	}

	public V3 rotate(V3 axis, float angle) {
		float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
		V3 kxv = axis.cross(this);
		float kdv = axis.dot(this);
		return new V3(
				x * c + kxv.x * s + axis.x * kdv * (1 - c),
				y * c + kxv.y * s + axis.y * kdv * (1 - c),
				z * c + kxv.z * s + axis.z * kdv * (1 - c));
	}

	public static V3 forward(float yawDeg) {
		double r = Math.toRadians(yawDeg);
		return new V3((float) -Math.sin(r), 0, (float) Math.cos(r));
	}

	public static V3 right(float yawDeg) {
		double r = Math.toRadians(yawDeg);
		return new V3((float) -Math.cos(r), 0, (float) -Math.sin(r));
	}

	public void line(GlowBatch glow, V3 to, float width, int rgb, float intensity) {
		glow.glowLine(x, y, z, to.x, to.y, to.z, width, rgb, intensity);
	}
}
