package com.melodia.lyrics.lyrics;

import com.melodia.lyrics.media.MelodiaSnapshot;
import com.melodia.lyrics.media.TrackInfo;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Owns the synced lyrics for the current track: disk cache first, then lrclib, then — when
 * Melodia itself is showing a line for the same song — a bridge-built fallback so the 3D
 * lyrics still work even if lrclib has nothing for the song.
 */
public final class LyricsService {
	private static final Pattern UNSAFE_FILENAME = Pattern.compile("[^a-zA-Z0-9._-]+");
	private static final int MAX_CACHE_NAME = 120;
	private static LyricsService instance;

	private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "MelodiaLyrics-Fetch");
		thread.setDaemon(true);
		return thread;
	});
	private final AtomicLong generation = new AtomicLong();
	private volatile Lyrics lyrics = Lyrics.EMPTY;
	private volatile String currentKey = "";
	private volatile boolean loading = false;
	private volatile boolean usedBridge = false;

	private LyricsService() {
	}

	public static synchronized LyricsService getInstance() {
		if (instance == null) {
			instance = new LyricsService();
		}
		return instance;
	}

	public Lyrics lyrics() {
		return lyrics;
	}

	public boolean isLoading() {
		return loading;
	}

	/** True when the displayed lyrics came from Melodia's own player rather than lrclib. */
	public boolean usedBridge() {
		return usedBridge;
	}

	public void onTrack(TrackInfo track) {
		if (track == null || !track.isValidTrack()) {
			reset();
			return;
		}
		String key = track.getTrackKey();
		if (key.equals(currentKey)) {
			return;
		}
		currentKey = key;
		lyrics = Lyrics.EMPTY;
		loading = true;
		long mine = generation.incrementAndGet();
		String artist = track.getArtist();
		String title = track.getTitle();
		String album = track.getAlbum();
		long durationMs = track.getDurationMs();
		MelodiaSnapshot bridge = track.snapshot();
		worker.execute(() -> {
			Lyrics parsed = load(key, artist, title, album, durationMs, bridge);
			if (generation.get() == mine) {
				lyrics = parsed;
				loading = false;
			}
		});
	}

	private void reset() {
		generation.incrementAndGet();
		currentKey = "";
		lyrics = Lyrics.EMPTY;
		loading = false;
		usedBridge = false;
	}

	private static Lyrics load(String key, String artist, String title, String album, long durationMs,
			MelodiaSnapshot bridge) {
		Path cached = cacheFile(key);
		if (cached != null && Files.isReadable(cached)) {
			try {
				return LrcParser.parse(key, Files.readString(cached, StandardCharsets.UTF_8));
			} catch (IOException ignored) {
			}
		}
		String lrc = LrclibClient.fetchSynced(artist, title, album, durationMs);
		if (lrc != null && !lrc.isBlank()) {
			Lyrics parsed = LrcParser.parse(key, lrc);
			if (!parsed.isEmpty() && cached != null) {
				try {
					Files.createDirectories(cached.getParent());
					Files.writeString(cached, lrc, StandardCharsets.UTF_8);
				} catch (IOException ignored) {
				}
			}
			getInstance().usedBridge = false;
			return parsed;
		}
		Lyrics bridged = fromBridge(key, bridge);
		if (bridged != null) {
			getInstance().usedBridge = true;
			return bridged;
		}
		getInstance().usedBridge = false;
		return Lyrics.EMPTY;
	}

	/**
	 * Builds a minimal but position-accurate Lyrics (current + next line) from what Melodia
	 * reports, used when lrclib has no synced lyrics for the song.
	 */
	private static Lyrics fromBridge(String key, MelodiaSnapshot bridge) {
		if (bridge == null || !bridge.hasLyrics() || bridge.line() == null || bridge.line().isBlank()) {
			return null;
		}
		List<LyricLine> lines = new ArrayList<>();
		long start = Math.max(0L, bridge.lineStartMs());
		long end = bridge.nextStartMs() > start ? bridge.nextStartMs()
				: Math.max(start + 1L, bridge.lineEndMs() > start ? bridge.lineEndMs() : start + 5000L);
		lines.add(new LyricLine(start, end, bridge.line()));
		if (bridge.next() != null && !bridge.next().isBlank()) {
			long nextStart = Math.max(end, bridge.nextStartMs() > 0 ? bridge.nextStartMs() : end);
			lines.add(new LyricLine(nextStart, nextStart + 5000L, bridge.next()));
		}
		return Lyrics.of(key, lines);
	}

	private static Path cacheFile(String key) {
		try {
			String name = UNSAFE_FILENAME.matcher(key).replaceAll("_");
			if (name.length() > MAX_CACHE_NAME) {
				name = name.substring(0, MAX_CACHE_NAME);
			}
			return FabricLoader.getInstance().getConfigDir()
					.resolve("melodia_lyrics")
					.resolve("cache")
					.resolve(name + ".lrc");
		} catch (Exception failed) {
			return null;
		}
	}
}
