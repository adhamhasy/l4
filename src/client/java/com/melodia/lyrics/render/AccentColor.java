package com.melodia.lyrics.render;

import com.melodia.lyrics.config.LyricsConfig;
import com.melodia.lyrics.media.MelodiaSnapshot;
import com.melodia.lyrics.media.TrackInfo;

import java.awt.Color;
import java.util.Locale;

/**
 * Accent colour modes: named pastel palette, ALBUM (Melodia reports the album-art colour
 * itself through its status file), RAINBOW (cycles one step per new line) and CUSTOM.
 */
public final class AccentColor {
	public static final String[] PALETTE = {
			"CREAM", "HONEY", "BLUSH", "CORAL", "MINT", "ICE",
			"LAVENDER", "ORCHID", "SAGE", "SLATE"};
	private static final String[] RAINBOW_CYCLE = {
			"HONEY", "CORAL", "BLUSH", "ORCHID", "LAVENDER", "ICE", "MINT", "SAGE"};
	private static final float MIN_BRIGHTNESS = 0.55f;
	private static long rainbowStamp = Long.MIN_VALUE;
	private static int rainbowIndex = 0;

	private AccentColor() {
	}

	public static int paletteColor(String name) {
		return switch (name == null ? "" : name.toUpperCase(Locale.ROOT)) {
			case "HONEY" -> 15905597;
			case "BLUSH" -> 15894427;
			case "CORAL" -> 16745312;
			case "MINT" -> 8380605;
			case "ICE" -> 9423346;
			case "LAVENDER" -> 11773424;
			case "ORCHID" -> 14392288;
			case "SAGE" -> 11520416;
			case "SLATE" -> 13029595;
			default -> 16181185; // CREAM
		};
	}

	public static int resolve(LyricsConfig config, TrackInfo track) {
		String mode = config.accentColor == null ? "ALBUM" : config.accentColor.toUpperCase(Locale.ROOT);
		int rgb = switch (mode) {
			case "ALBUM" -> {
				if (track == null) {
					yield paletteColor("CREAM");
				}
				yield readable(albumColorOf(track));
			}
			case "CUSTOM" -> config.lyricsColorHex & 0xFFFFFF;
			case "RAINBOW" -> rainbow(config, track);
			default -> paletteColor(mode);
		};
		return 0xFF000000 | rgb;
	}

	private static int rainbow(LyricsConfig config, TrackInfo track) {
		com.melodia.lyrics.lyrics.LyricLine line;
		com.melodia.lyrics.lyrics.Lyrics lyrics =
				com.melodia.lyrics.lyrics.LyricsService.getInstance().lyrics();
		long stamp = System.currentTimeMillis() / 2500L;
		if (track != null
				&& (line = lyrics.lineAt(track.getCurrentEstimatedPositionMs(config.timeOffsetMs))) != null) {
			stamp = line.startMs();
		}
		if (stamp != rainbowStamp) {
			rainbowStamp = stamp;
			rainbowIndex = (rainbowIndex + 1) % RAINBOW_CYCLE.length;
		}
		return paletteColor(RAINBOW_CYCLE[rainbowIndex]);
	}

	private static int albumColorOf(TrackInfo track) {
		MelodiaSnapshot snapshot = track.snapshot();
		if (snapshot != null && snapshot.accentRgb() != 0) {
			return snapshot.accentRgb() & 0xFFFFFF;
		}
		if (track.getAlbumArtBytes() != null && track.getAlbumArtBytes().length > 0) {
			return extractDominant(track.getAlbumArtBytes());
		}
		return paletteColor("CREAM");
	}

	/** Dominant saturated colour of raw PNG cover-art bytes (mirrors the original sampler). */
	private static int extractDominant(byte[] artwork) {
		try (com.mojang.blaze3d.platform.NativeImage image =
				com.mojang.blaze3d.platform.NativeImage.read(new java.io.ByteArrayInputStream(artwork))) {
			int width = image.getWidth();
			int height = image.getHeight();
			int step = Math.max(1, Math.min(width, height) / 16);
			long totalRed = 0L;
			long totalGreen = 0L;
			long totalBlue = 0L;
			int counted = 0;
			int boldest = -1;
			float boldestSaturation = 0.0f;
			for (int x = 0; x < width; x += step) {
				for (int y = 0; y < height; y += step) {
					int argb = image.getPixel(x, y);
					if ((argb >> 24 & 0xFF) < 128) {
						continue;
					}
					int red = argb >> 16 & 0xFF;
					int green = argb >> 8 & 0xFF;
					int blue = argb & 0xFF;
					totalRed += red;
					totalGreen += green;
					totalBlue += blue;
					++counted;
					float[] hsb = Color.RGBtoHSB(red, green, blue, null);
					if (hsb[2] <= 0.3f || hsb[2] >= 0.95f || hsb[1] <= boldestSaturation) {
						continue;
					}
					boldestSaturation = hsb[1];
					boldest = red << 16 | green << 8 | blue;
				}
			}
			if (boldest != -1 && boldestSaturation >= 0.25f) {
				return boldest;
			}
			if (counted > 0) {
				return (int) (totalRed / counted) << 16
						| (int) (totalGreen / counted) << 8
						| (int) (totalBlue / counted);
			}
		} catch (Exception ignored) {
		}
		return paletteColor("CREAM");
	}

	private static int readable(int rgb) {
		float[] hsb = Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null);
		if (hsb[2] >= MIN_BRIGHTNESS) {
			return rgb;
		}
		return Color.HSBtoRGB(hsb[0], hsb[1], MIN_BRIGHTNESS) & 0xFFFFFF;
	}
}
