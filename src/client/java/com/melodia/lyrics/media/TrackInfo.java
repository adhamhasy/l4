package com.melodia.lyrics.media;

import java.util.Locale;
import java.util.Objects;

public final class TrackInfo {
	private final String title;
	private final String artist;
	private final String album;
	private final long durationMs;
	private final long positionMs;
	private final long sampledAtMs;
	private final boolean playing;
	/** Provider-known sync correction (Melodia's own offset), added on top of the user's. */
	private final long providerOffsetMs;
	private final byte[] artBytes;
	private final String artUrl;
	private final MelodiaSnapshot snapshot;

	public TrackInfo(String title, String artist, String album, long durationMs, long positionMs,
			boolean playing, byte[] artBytes, String artUrl, long sampledAtMs,
			long providerOffsetMs, MelodiaSnapshot snapshot) {
		this.title = title == null ? "" : title;
		this.artist = artist == null ? "" : artist;
		this.album = album == null ? "" : album;
		this.durationMs = Math.max(0L, durationMs);
		this.positionMs = Math.max(0L, positionMs);
		this.playing = playing;
		this.providerOffsetMs = providerOffsetMs;
		this.artBytes = artBytes;
		this.artUrl = artUrl;
		this.snapshot = snapshot == null ? MelodiaSnapshot.none() : snapshot;
		this.sampledAtMs = sampledAtMs > 0L ? sampledAtMs : System.currentTimeMillis();
	}

	public String getTitle() {
		return title;
	}

	public String getArtist() {
		return artist;
	}

	public String getAlbum() {
		return album;
	}

	public long getDurationMs() {
		return durationMs;
	}

	public long getPositionMs() {
		return positionMs;
	}

	public boolean isPlaying() {
		return playing;
	}

	public byte[] getAlbumArtBytes() {
		return artBytes;
	}

	public String getAlbumArtUrl() {
		return artUrl;
	}

	public MelodiaSnapshot snapshot() {
		return snapshot;
	}

	public long getSampledAtMs() {
		return sampledAtMs;
	}

	public long getCurrentEstimatedPositionMs(long userOffsetMs) {
		long position = positionMs;
		if (playing) {
			position += Math.max(0L, System.currentTimeMillis() - sampledAtMs);
		}
		position += userOffsetMs + providerOffsetMs;
		if (durationMs > 0L) {
			position = Math.min(position, durationMs);
		}
		return Math.max(0L, position);
	}

	public boolean isValidTrack() {
		return !title.isBlank();
	}

	public String getTrackKey() {
		return TrackInfo.keyOf(artist, title);
	}

	public static String keyOf(String artist, String title) {
		return ((artist == null ? "" : artist) + " - " + (title == null ? "" : title))
				.toLowerCase(Locale.ROOT).trim();
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof TrackInfo info)) {
			return false;
		}
		return title.equals(info.title)
				&& artist.equals(info.artist)
				&& positionMs / 500L == info.positionMs / 500L
				&& playing == info.playing;
	}

	@Override
	public int hashCode() {
		return Objects.hash(title, artist, positionMs / 500L, playing);
	}

	@Override
	public String toString() {
		return "TrackInfo[" + artist + " - " + title + ", " + positionMs + "/" + durationMs
				+ "ms, playing=" + playing + "]";
	}
}
