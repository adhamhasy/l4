package com.melodia.lyrics.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Configuration for Melodia Lyrics.
 *
 * <p>The lyric/3D/widget fields mirror Show the Lyrics so tuning guides apply, while the
 * {@code melodiaStatusFile} field points the mod at Melodia's live status bridge instead of
 * a Windows media session.
 */
public final class LyricsConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static LyricsConfig instance;

	public WidgetPosition playerWidgetPosition = WidgetPosition.TOP_LEFT;
	public boolean showPlayerWidget = true;
	public float playerWidgetScale = 1.0f;
	public float playerWidgetAlpha = 0.95f;
	public boolean autoHide = false;
	public boolean showLyrics = true;
	public String accentColor = "ICE";
	public int lyricsColorHex = 13500410;
	public long timeOffsetMs = 120L;
	public double textDistance3D = 15.0;
	public double speedLead3D = 0.5;
	public double anchorFollow3D = 1.0;
	public double sideSpread3D = 50.0;
	public boolean keepOnScreen3D = false;
	public double textHeightOffset3D = 2.5;
	public String lyricsAnimation = "TYPEWRITER";
	public int lyricsWrapWidth = 110;
	public String lyricsFont = "DEFAULT";
	public float lyricsFontScale = 1.25f;
	public boolean boldActiveLyric = false;
	public boolean lyricsUppercase = false;
	public boolean lyricsGlowEffect = false;
	public int lyricsOutlineColorHex = 0xFFFFFF;
	public boolean lyricsDropShadow = true;
	public float lyricsBgAlpha = 0.0f;
	public boolean activeLineHighlight = false;
	public boolean showInstrumentalCountdown = false;
	public boolean showNextLinePreview = false;

	// Melodia specifics ------------------------------------------------------
	/** Absolute path to Melodia's now_playing.json; empty auto-detects via XDG_CONFIG_HOME. */
	public String melodiaStatusFile = "";
	/** Optional flat-HUD karaoke overlay (off by default; the original renders 3D only). */
	public boolean showHud = false;
	public String hudAnchor = "above_hotbar";
	public float hudScale = 1.0f;
	public int hudMaxWidth = 560;
	public boolean hudBackground = true;
	public int hudBackgroundOpacity = 110;

	public static LyricsConfig getInstance() {
		if (instance == null) {
			load();
		}
		return instance;
	}

	public static synchronized void load() {
		Path file = configFile();
		if (Files.isReadable(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				instance = GSON.fromJson(reader, LyricsConfig.class);
			} catch (Exception unreadable) {
				instance = null;
			}
		}
		if (instance == null) {
			instance = new LyricsConfig();
			save();
		}
		instance.migrate();
	}

	/** Discards the cached instance and re-reads the config file. */
	public static synchronized void reload() {
		instance = null;
		load();
	}

	public static synchronized void save() {
		if (instance == null) {
			instance = new LyricsConfig();
		}
		Path file = configFile();
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException failed) {
			try {
				Files.deleteIfExists(tmp);
			} catch (IOException ignored) {
			}
		}
	}

	private void migrate() {
		// Old palette names -> current palette names (mirrors the original migration table).
		String name = accentColor == null ? "" : accentColor.toUpperCase(Locale.ROOT);
		accentColor = switch (name) {
			case "", "GREEN" -> "MINT";
			case "CYAN" -> "ICE";
			case "GOLD" -> "HONEY";
			case "PURPLE" -> "LAVENDER";
			case "RED" -> "CORAL";
			case "WHITE" -> "CREAM";
			default -> name;
		};
		if (playerWidgetPosition == null) {
			playerWidgetPosition = WidgetPosition.TOP_LEFT;
		}
		if (hudAnchor == null || hudAnchor.isBlank()) {
			hudAnchor = "above_hotbar";
		}
	}

	public WidgetPosition widgetPosition() {
		return playerWidgetPosition == null ? WidgetPosition.TOP_LEFT : playerWidgetPosition;
	}

	public enum WidgetPosition {
		TOP_LEFT,
		TOP_RIGHT,
		BOTTOM_LEFT,
		BOTTOM_RIGHT,
		CENTER
	}

	private static Path configFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("melodia-lyrics.json");
	}
}
