package com.whiteclient.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Tiny frame-time profiler: rolling average milliseconds per module / render step (for finding lag). */
public final class Perf {
	private Perf() {
	}

	private static final Map<String, double[]> STATS = new HashMap<>();

	public static void add(String key, long nanos) {
		double ms = nanos / 1.0e6;
		double[] s = STATS.get(key);
		if (s == null) {
			STATS.put(key, new double[]{ms, ms, 1});
			return;
		}
		s[0] = s[0] * 0.95 + ms * 0.05;
		s[1] = Math.max(s[1] * 0.995, ms);
		s[2]++;
	}

	public static void reset() {
		STATS.clear();
	}

	/** The most expensive entries, e.g. "Snow:world 0.42ms (max 1.10)". */
	public static String report(int top) {
		List<Map.Entry<String, double[]>> list = new ArrayList<>(STATS.entrySet());
		list.sort((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]));
		StringBuilder sb = new StringBuilder();
		double total = 0;
		for (Map.Entry<String, double[]> e : list) {
			total += e.getValue()[0];
		}
		sb.append(String.format(Locale.ROOT, "total %.2fms |", total));
		for (int i = 0; i < Math.min(top, list.size()); i++) {
			Map.Entry<String, double[]> e = list.get(i);
			sb.append(String.format(Locale.ROOT, " %s %.2fms (max %.1f)", e.getKey(), e.getValue()[0], e.getValue()[1]));
			if (i + 1 < Math.min(top, list.size())) {
				sb.append(',');
			}
		}
		return sb.toString();
	}
}
