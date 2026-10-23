package com.melodia.lyrics.lyrics;

public record LyricLine(long startMs, long endMs, String text) {
	public long durationMs() {
		return Math.max(0L, endMs - startMs);
	}

	public boolean covers(long positionMs) {
		return positionMs >= startMs && positionMs < endMs;
	}

	public float progress(long positionMs) {
		long span = durationMs();
		if (span <= 0L) {
			return positionMs >= startMs ? 1.0f : 0.0f;
		}
		float raw = (float) (positionMs - startMs) / (float) span;
		return Math.max(0.0f, Math.min(1.0f, raw));
	}

	public boolean hasText() {
		return text != null && !text.isBlank();
	}
}
