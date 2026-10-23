package com.melodia.lyrics.media;

import java.util.List;

/**
 * Extra knowledge taken from Melodia's status file: what the player itself is showing as the
 * current line (with word timings) and the album accent colour.
 *
 * <p>Used for the karaoke fill and as a fallback when lrclib has no synced lyrics for the track.
 */
public record MelodiaSnapshot(
		String line,
		long lineStartMs,
		long lineEndMs,
		String next,
		long nextStartMs,
		List<WordT> words,
		int accentRgb,
		boolean hasLyrics) {

	public record WordT(long tMs, String text) {
	}

	public static MelodiaSnapshot none() {
		return new MelodiaSnapshot("", 0L, 0L, "", 0L, List.of(), 0xFF1DB954, false);
	}
}
