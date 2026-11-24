package com.whiteclient.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Lyrics {
	public record Line(long timeMs, String text) {
	}

	public static final Lyrics NONE = new Lyrics(List.of(), false);

	private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2}(?:[.:]\\d{1,3})?)]");

	private final List<Line> lines;
	private final boolean instrumental;

	public Lyrics(List<Line> lines, boolean instrumental) {
		this.lines = lines;
		this.instrumental = instrumental;
	}

	public boolean isEmpty() {
		return lines.isEmpty();
	}

	public boolean instrumental() {
		return instrumental;
	}

	public List<Line> lines() {
		return lines;
	}

	public int indexAt(long posMs) {
		int lo = 0, hi = lines.size() - 1, ans = -1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (lines.get(mid).timeMs() <= posMs) {
				ans = mid;
				lo = mid + 1;
			} else {
				hi = mid - 1;
			}
		}
		return ans;
	}

	public long endOf(int index) {
		if (index + 1 < lines.size()) {
			return lines.get(index + 1).timeMs();
		}
		return lines.get(index).timeMs() + 4000;
	}

	public static Lyrics parseLrc(String lrc) {
		if (lrc == null || lrc.isBlank()) {
			return NONE;
		}
		List<Line> out = new ArrayList<>();
		for (String raw : lrc.split("\\r?\\n")) {
			Matcher m = STAMP.matcher(raw);
			List<Long> times = new ArrayList<>();
			int end = 0;
			while (m.find() && m.start() == end) {
				long min = Long.parseLong(m.group(1));
				String secPart = m.group(2).replace(':', '.');
				double sec = Double.parseDouble(secPart);
				times.add(min * 60_000L + Math.round(sec * 1000));
				end = m.end();
			}
			if (times.isEmpty()) {
				continue;
			}
			String text = raw.substring(end).trim();
			for (long t : times) {
				out.add(new Line(t, text));
			}
		}
		out.sort((a, b) -> Long.compare(a.timeMs(), b.timeMs()));
		return out.isEmpty() ? NONE : new Lyrics(Collections.unmodifiableList(out), false);
	}
}
