package com.melodia.lyrics.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads Melodia's live player state from {@code ~/.config/melodia/now_playing.json}.
 *
 * <p>This plays the same role the mod's Windows media-session provider does: it is what makes
 * the mod platform-agnostic. Melodia rewrites the file a few times per second while running,
 * so position extrapolation behaves like a real media session sample.
 */
public final class MelodiaStatusProvider implements MediaProvider {
	/** Past this age without a rewrite we assume the player is gone or frozen. */
	private static final long FRESH_MS = 10_000L;
	private static volatile Path statusFile = null;

	@Override
	public void start() {
	}

	@Override
	public void stop() {
	}

	public static Path resolveFile() {
		Path cached = statusFile;
		if (cached != null) {
			return cached;
		}
		String configured = com.melodia.lyrics.config.LyricsConfig.getInstance().melodiaStatusFile;
		Path resolved;
		if (configured != null && !configured.isBlank()) {
			resolved = Paths.get(configured);
		} else {
			String override = System.getenv("MELODIA_STATUS_FILE");
			if (override != null && !override.isBlank()) {
				resolved = Paths.get(override);
			} else {
				String xdg = System.getenv("XDG_CONFIG_HOME");
				Path base = (xdg != null && !xdg.isBlank())
						? Paths.get(xdg)
						: Paths.get(System.getProperty("user.home"), ".config");
				resolved = base.resolve("melodia").resolve("now_playing.json");
			}
		}
		statusFile = resolved;
		return resolved;
	}

	/** Clears the memoised path (used when the config changes the status file location). */
	public static void invalidatePath() {
		statusFile = null;
	}

	@Override
	public TrackInfo poll() {
		Path file = resolveFile();
		if (!Files.isReadable(file)) {
			return null;
		}
		try {
			String json = Files.readString(file);
			JsonObject o = JsonParser.parseString(json).getAsJsonObject();
			if (!"melodia".equals(optStr(o, "app", ""))) {
				return null;
			}
			boolean playing = optBool(o, "playing", false);
			List<MelodiaSnapshot.WordT> wordList = new ArrayList<>();
			if (o.has("words") && o.get("words").isJsonArray()) {
				for (JsonElement e : o.getAsJsonArray("words")) {
					if (e.isJsonArray() && e.getAsJsonArray().size() >= 2) {
						JsonArray pair = e.getAsJsonArray();
						try {
							wordList.add(new MelodiaSnapshot.WordT(Math.round(pair.get(0).getAsDouble() * 1000.0),
									pair.get(1).getAsString()));
						} catch (Exception ignored) {
						}
					}
				}
			}
			MelodiaSnapshot snapshot = new MelodiaSnapshot(
					optStr(o, "line", ""),
					Math.round(optNum(o, "line_start", 0) * 1000.0),
					Math.round(optNum(o, "line_end", 0) * 1000.0),
					optStr(o, "next", ""),
					Math.round(optNum(o, "next_start", 0) * 1000.0),
					wordList,
					parseColor(optStr(o, "accent", "#1db954")),
					optBool(o, "has_lyrics", false));
			long positionMs = Math.round(optNum(o, "position", 0) * 1000.0);
			long lyricTimeMs = Math.round(optNum(o, "lyric_time", optNum(o, "position", 0)) * 1000.0);
			long providerOffset = lyricTimeMs - positionMs;
			// While paused Melodia stops rewriting; treat an old file as "paused" so the
			// widget's progress bar and (optional) autoHide behave sensibly.
			try {
				long age = System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis();
				if (age > FRESH_MS) {
					playing = false;
				}
			} catch (Exception ignored) {
			}
			return new TrackInfo(
					optStr(o, "title", ""),
					optStr(o, "artist", ""),
					optStr(o, "album", ""),
					Math.round(optNum(o, "duration", 0) * 1000.0),
					positionMs,
					playing,
					null,
					null,
					System.currentTimeMillis(),
					providerOffset,
					snapshot);
		} catch (Exception failed) {
			return null;
		}
	}

	private static boolean optBool(JsonObject o, String key, boolean fallback) {
		try {
			return o.get(key).getAsBoolean();
		} catch (Exception e) {
			return fallback;
		}
	}

	private static String optStr(JsonObject o, String key, String fallback) {
		try {
			return o.get(key).getAsString();
		} catch (Exception e) {
			return fallback;
		}
	}

	private static double optNum(JsonObject o, String key, double fallback) {
		try {
			return o.get(key).getAsDouble();
		} catch (Exception e) {
			return fallback;
		}
	}

	private static int parseColor(String value) {
		try {
			String h = value.startsWith("#") ? value.substring(1) : value;
			if (h.length() == 6) {
				h = "FF" + h;
			}
			return (int) (Long.parseLong(h, 16));
		} catch (Exception e) {
			return 0xFF1DB954;
		}
	}
}
