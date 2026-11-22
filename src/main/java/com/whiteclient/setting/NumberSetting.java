package com.whiteclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class NumberSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double step;
	private final String suffix;

	public NumberSetting(String name, double defaultValue, double min, double max, double step) {
		this(name, defaultValue, min, max, step, "");
	}

	public NumberSetting(String name, double defaultValue, double min, double max, double step, String suffix) {
		super(name, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step;
		this.suffix = suffix;
	}

	public double getMin() {
		return min;
	}

	public double getMax() {
		return max;
	}

	public float getFloat() {
		return value.floatValue();
	}

	public int getInt() {
		return (int) Math.round(value);
	}

	@Override
	public void set(Double v) {
		double clamped = Math.max(min, Math.min(max, v));
		if (step > 0) {
			clamped = Math.round((clamped - min) / step) * step + min;
			clamped = Math.max(min, Math.min(max, clamped));
		}
		this.value = clamped;
	}

	/** Sets the value from a 0..1 slider fraction. */
	public void setFraction(double fraction) {
		set(min + (max - min) * Math.max(0, Math.min(1, fraction)));
	}

	public double getFraction() {
		return max == min ? 0 : (value - min) / (max - min);
	}

	public String format() {
		String s;
		if (step >= 1) {
			s = Integer.toString((int) Math.round(value));
		} else if (step >= 0.1) {
			s = String.format(java.util.Locale.ROOT, "%.1f", value);
		} else {
			s = String.format(java.util.Locale.ROOT, "%.2f", value);
		}
		return s + suffix;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement element) {
		if (element != null && element.isJsonPrimitive()) {
			set(element.getAsDouble());
		}
	}
}
