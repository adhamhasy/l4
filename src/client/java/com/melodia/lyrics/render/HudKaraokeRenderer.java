package com.melodia.lyrics.render;

import com.melodia.lyrics.config.LyricsConfig;
import com.melodia.lyrics.lyrics.LyricLine;
import com.melodia.lyrics.lyrics.Lyrics;
import com.melodia.lyrics.lyrics.LyricsService;
import com.melodia.lyrics.media.MediaManager;
import com.melodia.lyrics.media.MelodiaSnapshot;
import com.melodia.lyrics.media.TrackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional flat overlay (off by default — the original mod is 3D only). Handy when you want
 * lyrics pinned to the HUD instead of floating in the world.
 */
public final class HudKaraokeRenderer implements HudElement {
	private static final int SHADOW = 0xA0000000;
	private static final int NEXT = 0x80FFFFFF;
	private static final int TITLE = 0xFFB3B3B3;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		LyricsConfig cfg = LyricsConfig.getInstance();
		if (!cfg.showHud || !cfg.showLyrics) {
			return;
		}
		TrackInfo track = MediaManager.getInstance().getCurrentTrack();
		if (track == null || !track.isValidTrack()) {
			return;
		}
		if (cfg.autoHide && !track.isPlaying()) {
			return;
		}
		Lyrics lyrics = LyricsService.getInstance().lyrics();
		if (lyrics.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		if (font == null) {
			return;
		}
		long positionMs = track.getCurrentEstimatedPositionMs(cfg.timeOffsetMs);
		LyricLine current = lyrics.lineAt(positionMs);
		if (current == null) {
			return;
		}
		LyricLine next = lyrics.lineAfter(positionMs);
		int accent = AccentColor.resolve(cfg, track) & 0xFFFFFF;
		int dimmed = dimColor(accent, 0.45f);

		float scale = Math.max(0.4f, Math.min(3.0f, cfg.hudScale));
		int wrapWidth = Math.max(80, Math.round(cfg.hudMaxWidth / scale));
		int lineHeight = font.lineHeight + 1;

		List<String> rows = wrap(font, current.text(), wrapWidth);
		List<String> upcoming = cfg.showNextLinePreview && next != null
				? wrap(font, next.text(), wrapWidth)
				: List.of();
		String title = track.getTitle().isBlank()
				? null
				: "\u266a " + track.getTitle() + (track.getArtist().isBlank() ? "" : " \u2014 " + track.getArtist());

		int blockWidth = 0;
		if (title != null) {
			blockWidth = Math.max(blockWidth, font.width(title));
		}
		for (String l : rows) {
			blockWidth = Math.max(blockWidth, font.width(l));
		}
		for (String l : upcoming) {
			blockWidth = Math.max(blockWidth, font.width(l));
		}
		if (blockWidth <= 0) {
			return;
		}
		int blockHeight = (title != null ? lineHeight : 0)
				+ rows.size() * lineHeight
				+ (upcoming.isEmpty() ? 0 : 4 + upcoming.size() * lineHeight);

		int screenW = g.guiWidth();
		int screenH = g.guiHeight();
		float ax, ay;
		int align;
		switch (cfg.hudAnchor == null ? "above_hotbar" : cfg.hudAnchor) {
			case "top" -> { ax = screenW / 2f; ay = 12f; align = 1; }
			case "bottom" -> { ax = screenW / 2f; ay = screenH - 12f - blockHeight * scale; align = 1; }
			case "top_left" -> { ax = 8f; ay = 10f; align = 0; }
			case "top_right" -> { ax = screenW - 8f - blockWidth * scale; ay = 10f; align = 2; }
			default -> { ax = screenW / 2f; ay = screenH - 74f - blockHeight * scale; align = 1; }
		}

		g.pose().pushMatrix();
		g.pose().translate(ax, ay);
		g.pose().scale(scale);
		if (align == 1) {
			g.pose().translate(-blockWidth / 2f, 0f);
		}
		if (cfg.hudBackground) {
			int pad = 4;
			int bg = Math.max(0, Math.min(255, cfg.hudBackgroundOpacity)) << 24;
			g.fill(-pad, -pad, blockWidth + pad, blockHeight + pad, bg);
		}

		int y = 0;
		if (title != null) {
			text(g, font, title, x0(font, title, align, blockWidth), y, TITLE);
			y += lineHeight;
		}
		long elapsed = positionMs - current.startMs();
		long window = Math.max(1L, current.durationMs());
		int evenRead = cfg.activeLineHighlight
				? Math.round((float) current.text().length()
						* Math.max(0.0f, Math.min(1.0f, (float) elapsed / (float) window)))
				: -1;
		for (String row : rows) {
			drawRow(g, font, row, align, blockWidth, y, accent, dimmed,
					wordsReadFor(track.snapshot(), positionMs, current), evenRead);
			y += lineHeight;
		}
		if (!upcoming.isEmpty()) {
			y += 4;
			for (String l : upcoming) {
				text(g, font, l, x0(font, l, align, blockWidth), y, NEXT);
				y += lineHeight;
			}
		}
		g.pose().popMatrix();
	}

	/** Chars sung per Melodia's word timings, clamped to the on-screen rows; -1 if unusable. */
	private static int wordsReadFor(MelodiaSnapshot snapshot, long positionMs, LyricLine current) {
		if (snapshot == null || snapshot.words().isEmpty()
				|| snapshot.line() == null || snapshot.line().isBlank()
				|| current == null) {
			return -1;
		}
		String statusLine = snapshot.line().trim();
		String shownLine = current.text().trim();
		if (!statusLine.equalsIgnoreCase(shownLine)
				&& !statusLine.contains(shownLine)
				&& !shownLine.contains(statusLine)) {
			return -1;
		}
		List<MelodiaSnapshot.WordT> words = snapshot.words();
		int sung = 0;
		int currentWord = -1;
		for (int i = 0; i < words.size(); ++i) {
			if (words.get(i).tMs() <= positionMs) {
				currentWord = i;
			} else {
				break;
			}
		}
		for (int i = 0; i <= currentWord; ++i) {
			sung += words.get(i).text().length() + 1;
		}
		if (currentWord >= 0 && currentWord < words.size() - 1) {
			long from = words.get(currentWord).tMs();
			long to = words.get(currentWord + 1).tMs();
			if (to > from) {
				float frac = Math.max(0.0f, Math.min(1.0f, (float) (positionMs - from) / (float) (to - from)));
				sung += Math.round(words.get(currentWord).text().length() * frac);
			}
		}
		return sung;
	}

	private static void drawRow(GuiGraphicsExtractor g, Font font, String rowText, int align,
			int blockWidth, int y, int lit, int dimmed, int wordsRead, int evenRead) {
		if (rowText.isEmpty()) {
			return;
		}
		float x = x0(font, rowText, align, blockWidth);
		int read = wordsRead;
		if (read < 0) {
			read = evenRead;
		}
		if (read < 0) {
			text(g, font, rowText, x, y, lit);
			return;
		}
		int cut = Math.max(0, Math.min(read, rowText.length()));
		String done = rowText.substring(0, cut);
		String rest = rowText.substring(cut);
		if (!done.isEmpty()) {
			text(g, font, done, x, y, lit);
			x += font.width(done);
		}
		if (!rest.isEmpty()) {
			text(g, font, rest, x, y, dimmed);
		}
	}

	private static int dimColor(int rgb, float factor) {
		int r = Math.round((rgb >> 16 & 0xFF) * factor);
		int gr = Math.round((rgb >> 8 & 0xFF) * factor);
		int b = Math.round((rgb & 0xFF) * factor);
		return r << 16 | gr << 8 | b;
	}

	private static float x0(Font font, String text, int align, int blockWidth) {
		int w = font.width(text);
		return switch (align) {
			case 1 -> (blockWidth - w) / 2f;
			case 2 -> blockWidth - w;
			default -> 0f;
		};
	}

	private static void text(GuiGraphicsExtractor g, Font font, String s, float x, int y, int color) {
		int xi = Math.round(x);
		g.text(font, s, xi + 1, y + 1, SHADOW);
		g.text(font, s, xi, y, color);
	}

	private static List<String> wrap(Font font, String text, int maxWidth) {
		List<String> out = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return out;
		}
		for (String para : text.split("\\R")) {
			StringBuilder line = new StringBuilder();
			for (String word : para.trim().split("\\s+")) {
				if (word.isEmpty()) {
					continue;
				}
				String candidate = line.length() == 0 ? word : line + " " + word;
				if (line.length() > 0 && font.width(candidate) > maxWidth) {
					out.add(line.toString());
					line.setLength(0);
					line.append(word);
				} else {
					line.setLength(0);
					line.append(candidate);
				}
			}
			if (line.length() > 0) {
				out.add(line.toString());
			}
		}
		return out;
	}
}
