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
 * Owns the synced lyrics for the current track.
 *
 * <p>Resolution order: disk cache → lrclib.net → Melodia's own player bridge. Retries an empty
 * result every few seconds so a song that started before Melodia finished loading its lyrics
 * (or before the network came back) still ends up with lyrics instead of staying blank.
 */
public final class LyricsService {
	private static final Pattern UNSAFE_FILENAME = Pattern.compile("[^a-zA-Z0-9._-]+");
	private static final int MAX_CACHE_NAME = 120;
	private static final long RETRY_MS = 4000L;
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
	private volatile String lastBridgeLine = "";
	private volatile long lastAttemptMs = 0L;

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

	public boolean usedBridge() {
		return usedBridge;
	}

	public void onTrack(TrackInfo track) {
		if (track == null || !track.isValidTrack()) {
			reset();
			return;
		}
		String key = track.getTrackKey();
		MelodiaSnapshot snapshot = track.snapshot();
		String bridgeLine = snapshot == null ? "" : snapshot.line();

		if (!key.equals(currentKey)) {
			currentKey = key;
			lyrics = Lyrics.EMPTY;
			usedBridge = false;
			lastBridgeLine = "";
			lastAttemptMs = System.currentTimeMillis();
			loading = true;
			long mine = generation.incrementAndGet();
			dispatch(mine, track, snapshot);
			return;
		}

		// Same track: refresh the bridge when Melodia advances to a new line, and retry an
		// empty result so a late-arriving lyric still shows up.
		if (bridgeLine != null && !bridgeLine.isBlank() && !bridgeLine.equals(lastBridgeLine)) {
			lastBridgeLine = bridgeLine;
			if (usedBridge || lyrics == Lyrics.EMPTY) {
				Lyrics bridged = fromBridge(key, snapshot);
				if (bridged != null) {
					lyrics = bridged;
					usedBridge = true;
				}
			}
		}
		if (lyrics == Lyrics.EMPTY && !loading
				&& System.currentTimeMillis() - lastAttemptMs >= RETRY_MS) {
			lastAttemptMs = System.currentTimeMillis();
			loading = true;
			long mine = generation.incrementAndGet();
			dispatch(mine, track, snapshot);
		}
	}

	private void dispatch(long mine, TrackInfo track, MelodiaSnapshot snapshot) {
		String artist = track.getArtist();
		String title = track.getTitle();
		String album = track.getAlbum();
		long durationMs = track.getDurationMs();
		worker.execute(() -> {
			Lyrics parsed = load(track.getTrackKey(), artist, title, album, durationMs, snapshot);
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
		lastBridgeLine = "";
		lastAttemptMs = 0L;
	}

	private static Lyrics load(String key, String artist, String title, String album, long durationMs,
			MelodiaSnapshot bridge) {
		Path cached = cacheFile(key);
		if (cached != null && Files.isReadable(cached)) {
			try {
				Lyrics parsed = LrcParser.parse(key, Files.readString(cached, StandardCharsets.UTF_8));
				if (!parsed.isEmpty()) {
					getInstance().usedBridge = false;
					getInstance().lastBridgeLine = "";
					return parsed;
				}
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
			getInstance().lastBridgeLine = bridge == null ? "" : bridge.line();
			return bridged;
		}
		getInstance().usedBridge = false;
		return Lyrics.EMPTY;
	}

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
