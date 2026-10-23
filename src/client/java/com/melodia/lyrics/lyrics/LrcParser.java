package com.melodia.lyrics.lyrics;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Standard .lrc parsing: shared timestamps per line, offset tag, stripped word timings. */
public final class LrcParser {
	private static final Pattern TIMESTAMP = Pattern.compile("\\[(\\d{1,3}):([0-5]?\\d)(?:[.:](\\d{1,3}))?]");
	private static final Pattern OFFSET_TAG = Pattern.compile("\\[offset:\\s*([+-]?\\d+)\\s*]", Pattern.CASE_INSENSITIVE);
	private static final Pattern WORD_TIMING = Pattern.compile("<\\d{1,3}:[0-5]?\\d(?:[.:]\\d{1,3})?>");
	private static final long TRAILING_LINE_MS = 5000L;

	private LrcParser() {
	}

	public static Lyrics parse(String trackKey, String lrc) {
		if (lrc == null || lrc.isBlank()) {
			return Lyrics.EMPTY;
		}
		long offsetMs = readOffset(lrc);
		List<long[]> starts = new ArrayList<>();
		List<String> texts = new ArrayList<>();
		for (String raw : lrc.split("\\r?\\n")) {
			String line = raw.trim();
			if (line.isEmpty()) {
				continue;
			}
			Matcher stamps = TIMESTAMP.matcher(line);
			int textFrom = 0;
			List<Long> onThisLine = new ArrayList<>();
			while (stamps.find() && stamps.start() == textFrom) {
				onThisLine.add(toMillis(stamps));
				textFrom = stamps.end();
			}
			if (onThisLine.isEmpty()) {
				continue;
			}
			String text = WORD_TIMING.matcher(line.substring(textFrom)).replaceAll("").trim();
			for (long start : onThisLine) {
				starts.add(new long[] {Math.max(0L, start + offsetMs), texts.size()});
			}
			texts.add(text);
		}
		if (starts.isEmpty()) {
			return Lyrics.EMPTY;
		}
		starts.sort((a, b) -> Long.compare(a[0], b[0]));
		List<LyricLine> lines = new ArrayList<>(starts.size());
		for (int i = 0; i < starts.size(); ++i) {
			long start = starts.get(i)[0];
			long end = i + 1 < starts.size() ? starts.get(i + 1)[0] : start + TRAILING_LINE_MS;
			lines.add(new LyricLine(start, Math.max(start, end), texts.get((int) starts.get(i)[1])));
		}
		return Lyrics.of(trackKey, lines);
	}

	private static long readOffset(String lrc) {
		Matcher matcher = OFFSET_TAG.matcher(lrc);
		if (!matcher.find()) {
			return 0L;
		}
		try {
			return Long.parseLong(matcher.group(1));
		} catch (NumberFormatException ignored) {
			return 0L;
		}
	}

	private static long toMillis(Matcher stamp) {
		long minutes = Long.parseLong(stamp.group(1));
		long seconds = Long.parseLong(stamp.group(2));
		String digits = stamp.group(3);
		if (digits == null) {
			return minutes * 60000L + seconds * 1000L;
		}
		long value = Long.parseLong(digits);
		long fraction = switch (digits.length()) {
			case 1 -> value * 100L;
			case 2 -> value * 10L;
			default -> value;
		};
		return minutes * 60000L + seconds * 1000L + fraction;
	}
}
