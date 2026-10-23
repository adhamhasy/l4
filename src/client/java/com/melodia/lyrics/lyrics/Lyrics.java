package com.melodia.lyrics.lyrics;

import java.util.Collections;
import java.util.List;

public final class Lyrics {
	public static final Lyrics EMPTY = new Lyrics("", List.of());

	private final String trackKey;
	private final List<LyricLine> lines;

	Lyrics(String trackKey, List<LyricLine> lines) {
		this.trackKey = trackKey == null ? "" : trackKey;
		this.lines = List.copyOf(lines);
	}

	public static Lyrics of(String trackKey, List<LyricLine> lines) {
		return new Lyrics(trackKey, lines);
	}

	public String trackKey() {
		return trackKey;
	}

	public boolean isEmpty() {
		return lines.isEmpty();
	}

	public int size() {
		return lines.size();
	}

	public List<LyricLine> lines() {
		return lines;
	}

	public LyricLine lineAt(long positionMs) {
		int index = indexAt(positionMs);
		return index < 0 ? null : lines.get(index);
	}

	public LyricLine lineAfter(long positionMs) {
		int index = indexAt(positionMs) + 1;
		return index >= lines.size() ? null : lines.get(index);
	}

	public List<LyricLine> linesAfter(long positionMs, int limit) {
		if (limit <= 0 || lines.isEmpty()) {
			return List.of();
		}
		int from = indexAt(positionMs) + 1;
		if (from >= lines.size()) {
			return List.of();
		}
		int to = Math.min(lines.size(), from + limit);
		return Collections.unmodifiableList(lines.subList(from, to));
	}

	private int indexAt(long positionMs) {
		int low = 0;
		int high = lines.size() - 1;
		int found = -1;
		while (low <= high) {
			int mid = low + high >>> 1;
			if (lines.get(mid).startMs() <= positionMs) {
				found = mid;
				low = mid + 1;
			} else {
				high = mid - 1;
			}
		}
		return found;
	}
}
