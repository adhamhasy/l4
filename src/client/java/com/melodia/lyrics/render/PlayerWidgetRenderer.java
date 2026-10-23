package com.melodia.lyrics.render;

import com.melodia.lyrics.config.LyricsConfig;
import com.melodia.lyrics.config.LyricsConfig.WidgetPosition;
import com.melodia.lyrics.media.TrackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The corner now-playing card from Show the Lyrics: title • artist, with an accent progress bar.
 */
public final class PlayerWidgetRenderer {
	private static final int WIDGET_WIDTH = 250;
	private static final int ROW_HEIGHT = 18;
	private static final int MARGIN = 10;
	private static final float PROGRESS_WIDTH_SHARE = 0.65f;
	private static final int PROGRESS_HEIGHT = 2;
	private static final int PROGRESS_TRACK = 0x45FFFFFF;

	private PlayerWidgetRenderer() {
	}

	public static void render(GuiGraphicsExtractor context, TrackInfo track, LyricsConfig config) {
		if (!config.showPlayerWidget || track == null || !track.isValidTrack()) {
			return;
		}
		if (config.autoHide && !track.isPlaying()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		Font textRenderer = client.font;
		if (textRenderer == null) {
			return;
		}
		float scale = config.playerWidgetScale > 0.4f ? config.playerWidgetScale : 1.0f;
		int width = (int) (WIDGET_WIDTH * scale);
		int height = (int) (18.0f * scale);
		int screenWidth = client.getWindow().getGuiScaledWidth();
		int screenHeight = client.getWindow().getGuiScaledHeight();
		WidgetPosition position = config.widgetPosition();
		int x = switch (position) {
			case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - width - MARGIN;
			case CENTER -> (screenWidth - width) / 2;
			default -> MARGIN;
		};
		int y = switch (position) {
			case BOTTOM_RIGHT, BOTTOM_LEFT -> screenHeight - height - MARGIN;
			default -> MARGIN;
		};
		int alpha = clamp(Math.round(config.playerWidgetAlpha * 255.0f), 10, 255);
		int accent = alpha << 24 | AccentColor.resolve(config, track) & 0xFFFFFF;
		int textColor = alpha << 24 | 0xFFFFFF;
		String label = track.getTitle() + " \u00a77\u2022 \u00a7r" + track.getArtist();
		if (textRenderer.width(label) > width) {
			label = textRenderer.plainSubstrByWidth(label, Math.max(0, width - 6)) + "\u2026";
		}
		context.text(textRenderer, label, x, y + 3, textColor, true);
		renderProgress(context, track, config, x, y + 14,
				Math.round((float) width * PROGRESS_WIDTH_SHARE), accent, alpha);
	}

	private static void renderProgress(GuiGraphicsExtractor context, TrackInfo track,
			LyricsConfig config, int x, int y, int width, int accent, int alpha) {
		if (width <= 0) {
			return;
		}
		context.fill(x, y, x + width, y + PROGRESS_HEIGHT, alpha << 24 | 0xFFFFFF);
		long duration = track.getDurationMs();
		if (duration <= 0L) {
			return;
		}
		float played = Math.min(1.0f,
				(float) track.getCurrentEstimatedPositionMs(config.timeOffsetMs) / (float) duration);
		int filled = (int) ((float) width * played);
		if (filled > 0) {
			context.fill(x, y, x + filled, y + PROGRESS_HEIGHT, accent);
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
