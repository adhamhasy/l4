package com.melodia.lyrics.render;

import com.melodia.lyrics.config.LyricsConfig;
import com.melodia.lyrics.lyrics.LyricLine;
import com.melodia.lyrics.lyrics.Lyrics;
import com.melodia.lyrics.lyrics.LyricsService;
import com.melodia.lyrics.media.MediaManager;
import com.melodia.lyrics.media.MelodiaSnapshot;
import com.melodia.lyrics.media.TrackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Replication of Show the Lyrics' world-space renderer: a billboarded line of text placed in
 * the world ahead of the player, alternating left/right of the look direction, with typewriter /
 * drop / rise / word animations and an optional karaoke fill (word-timed through Melodia).
 */
public final class Lyrics3DRenderer {
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int LINE_HEIGHT = 10;
	private static final int MIN_ROW_WIDTH = 40;
	private static final int MAX_ROW_WIDTH = 400;
	private static final float GLYPH_SCALE = 0.025f;
	private static final float BASE_3D_SCALE = 2.5f;
	private static final long MS_PER_CHAR_TYPE = 35L;
	private static final long MS_PER_CHAR_DELETE = 18L;
	private static final double TYPE_WINDOW_SHARE = 0.45;
	private static final double DELETE_WINDOW_SHARE = 0.3;
	private static final double INTRO_WINDOW_SHARE = 0.35;
	private static final double OUTRO_WINDOW_SHARE = 0.3;
	private static final long MAX_INTRO_MS = 600L;
	private static final long MAX_OUTRO_MS = 500L;
	private static final long INSTRUMENTAL_MIN_MS = 14000L;
	private static final double INSTRUMENTAL_HOLD_SHARE = 0.5;
	private static final long INSTRUMENTAL_HOLD_MAX_MS = 8000L;
	private static final long COUNTDOWN_FADE_MS = 400L;
	private static final int COUNTDOWN_DOTS = 5;
	private static final float DIM_FACTOR = 0.45f;
	private static final float PREVIEW_ALPHA = 0.45f;
	private static final double SIDE_ANGLE_JITTER = 0.65;
	private static final double HEIGHT_JITTER = 0.6;
	private static final double FOLLOW_START_SPEED = 7.0;
	private static final double FOLLOW_FULL_SPEED = 20.0;
	private static final double MAX_SPEED_LEAD = 40.0;
	private static final double TICKS_PER_SECOND = 20.0;

	private static final Random RANDOM = new Random();
	private static Vec3 worldAnchor = null;
	private static String anchorTrackKey = "";
	private static double anchorConfiguredDistance = -1.0;
	private static double anchorPlacedDistance = -1.0;
	private static double anchorHeightSetting = Double.NaN;
	private static long lastSeenLineTimestamp = Long.MIN_VALUE;
	private static boolean lastSideRight = false;
	private static Vec3 lastCameraPos = null;

	private Lyrics3DRenderer() {
	}

	public static void render(LevelRenderContext context) {
		LyricsConfig config = LyricsConfig.getInstance();
		if (!config.showLyrics) {
			return;
		}
		TrackInfo track = MediaManager.getInstance().getCurrentTrack();
		if (track == null || !track.isValidTrack()) {
			return;
		}
		if (config.autoHide && !track.isPlaying()) {
			return;
		}
		Lyrics lyrics = LyricsService.getInstance().lyrics();
		if (lyrics.isEmpty()) {
			return;
		}
		long positionMs = track.getCurrentEstimatedPositionMs(config.timeOffsetMs);
		LyricLine currentLine = lyrics.lineAt(positionMs);
		if (currentLine == null || currentLine.text().isBlank()) {
			return;
		}
		Font textRenderer = Minecraft.getInstance().font;
		if (textRenderer == null) {
			return;
		}
		SubmitNodeCollector queue = context.submitNodeCollector();
		PoseStack matrices = context.poseStack();
		CameraRenderState state = context.levelState() != null ? context.levelState().cameraRenderState : null;
		if (queue == null || matrices == null || state == null
				|| !state.initialized || state.pos == null || state.orientation == null) {
			return;
		}
		CameraView camera = new CameraView(state.pos, state.orientation);
		TextSink sink = (poses, x, y, text, shadow, mode, color, background, outline) ->
				queue.submitText(poses, x, y, text, shadow, mode, FULL_BRIGHT, color, background, outline);

		double configuredDistance = Math.max(1.0, config.textDistance3D);
		double distance = configuredDistance + speedLead(config);
		String trackKey = track.getTrackKey();
		long lineTimestamp = currentLine.startMs();
		boolean lineChanged = lineTimestamp != lastSeenLineTimestamp;
		lastSeenLineTimestamp = lineTimestamp;

		boolean placedThisFrame = needsNewAnchor(config, configuredDistance, trackKey, lineChanged);
		if (placedThisFrame) {
			placeAnchor(camera, config, distance);
			anchorTrackKey = trackKey;
			anchorConfiguredDistance = configuredDistance;
			anchorPlacedDistance = distance;
			anchorHeightSetting = config.textHeightOffset3D;
		} else if (lastCameraPos != null && config.anchorFollow3D > 0.0) {
			double follow = Math.min(1.0, config.anchorFollow3D) * followStrength();
			if (follow > 0.0) {
				worldAnchor = worldAnchor.add(camera.pos().subtract(lastCameraPos).scale(follow));
			}
		}
		lastCameraPos = camera.pos();

		Style style = buildStyle(config);
		String lineText = simplify(currentLine.text());
		if (config.lyricsUppercase) {
			lineText = lineText.toUpperCase();
		}
		int wrapWidth = clamp(config.lyricsWrapWidth, 40, 400);
		List<String> rows = wrapRows(textRenderer, style, lineText, wrapWidth);
		int totalChars = 0;
		for (String row : rows) {
			totalChars += row.length();
		}
		if (totalChars == 0) {
			return;
		}
		long window = Math.max(1L, currentLine.durationMs());
		long elapsed = Math.max(0L, Math.min(window, positionMs - lineTimestamp));
		LyricLine nextLine = lyrics.lineAfter(positionMs);
		boolean instrumental = config.showInstrumentalCountdown && window >= INSTRUMENTAL_MIN_MS;
		long contentMs = instrumental
				? Math.min((long) (window * INSTRUMENTAL_HOLD_SHARE), INSTRUMENTAL_HOLD_MAX_MS)
				: window;
		int accent = AccentColor.resolve(config, track) & 0xFFFFFF;

		if (instrumental && elapsed > contentMs) {
			long span = Math.max(1L, window - contentMs);
			long into = elapsed - contentMs;
			float progress = clamp01((float) into / (float) span);
			float fade = Math.min(clamp01((float) into / COUNTDOWN_FADE_MS),
					clamp01((float) (span - into) / COUNTDOWN_FADE_MS));
			submitCountdown(sink, matrices, camera, config, textRenderer, style, progress,
					easeOut(fade), accent);
			return;
		}
		AnimState anim = animate(config, rows, totalChars, elapsed, contentMs);
		if (anim.visibleChars() <= 0 || anim.alpha() <= 0.02f) {
			return;
		}

		int readChars = -1;
		if (config.activeLineHighlight) {
			readChars = sungChars(track, snapshotOf(track), lineText, rows, positionMs);
			if (readChars < 0) {
				readChars = Math.round((float) totalChars * clamp01((float) elapsed / (float) contentMs));
			}
		}
		List<String> previewRows = null;
		if (config.showNextLinePreview && nextLine != null && !nextLine.text().isBlank()) {
			String previewText = simplify(nextLine.text());
			if (config.lyricsUppercase) {
				previewText = previewText.toUpperCase();
			}
			previewRows = wrapRows(textRenderer, style, previewText, wrapWidth);
		}
		submitRows(sink, matrices, camera, config, textRenderer, style, rows, anim, accent,
				readChars, previewRows);
	}

	/** Char-count of text sung so far, from Melodia's per-word timings; -1 when unavailable. */
	private static int sungChars(TrackInfo track, MelodiaSnapshot snapshot, String lineText,
			List<String> rows, long positionMs) {
		if (snapshot == null || snapshot.words().isEmpty()
				|| snapshot.line() == null || snapshot.line().isBlank()) {
			return -1;
		}
		// The status line must be the one on screen (Melodia updates it atomically with words).
		String statusLine = simplify(snapshot.line());
		if (!lineText.contains(statusLine) && !statusLine.contains(lineText)
				&& !statusLine.equalsIgnoreCase(lineText)) {
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
		for (int i = 0; i <= currentWord && i < words.size(); ++i) {
			sung += words.get(i).text().length() + 1;
		}
		if (currentWord >= 0 && currentWord < words.size() - 1) {
			long from = words.get(currentWord).tMs();
			long to = words.get(currentWord + 1).tMs();
			if (to > from) {
				double frac = clamp01((float) (positionMs - from) / (float) (to - from));
				sung += (int) Math.round(words.get(currentWord).text().length() * frac);
			}
		}
		return clamp(sung, 0, lineText.length());
	}

	private static MelodiaSnapshot snapshotOf(TrackInfo track) {
		return track == null ? null : track.snapshot();
	}

	private static void pushAnchor(PoseStack matrices, CameraView camera, LyricsConfig config,
			float scaleMul) {
		Vec3 offset = worldAnchor.subtract(camera.pos());
		float scale = GLYPH_SCALE * effectiveScale(config) * scaleMul;
		if (anchorPlacedDistance > 0.0) {
			scale *= (float) (anchorPlacedDistance / Math.max(1.0, config.textDistance3D));
		}
		matrices.pushPose();
		matrices.translate(offset.x, offset.y + 0.5, offset.z);
		matrices.rotate((Quaternionfc) camera.orientation());
		matrices.scale(scale, -scale, scale);
	}

	private static void submitRun(TextSink sink, PoseStack matrices, float x, float y,
			FormattedCharSequence text, boolean shadow, int color, int background, int outline) {
		sink.text(matrices, x, y, text, shadow, Font.DisplayMode.NORMAL, color, background, outline);
		sink.text(matrices, x, y, text, shadow, Font.DisplayMode.SEE_THROUGH, color, 0, 0);
	}

	private static FormattedCharSequence ordered(String text, Style style) {
		return Component.literal(text).setStyle(style).getVisualOrderText();
	}

	private static int dim(int rgb, float factor) {
		int red = Math.round((float) (rgb >> 16 & 0xFF) * factor);
		int green = Math.round((float) (rgb >> 8 & 0xFF) * factor);
		int blue = Math.round((float) (rgb & 0xFF) * factor);
		return red << 16 | green << 8 | blue;
	}

	private static void submitSplitRow(TextSink sink, PoseStack matrices, Font textRenderer,
			Style style, String text, int readChars, float y, boolean shadow, int litColor,
			int dimColor, int background, int outline) {
		FormattedCharSequence whole = ordered(text, style);
		float x = (float) (-textRenderer.width(whole)) / 2.0f;
		if (readChars < 0 || readChars >= text.length()) {
			submitRun(sink, matrices, x, y, whole, shadow, litColor, background, outline);
			return;
		}
		if (readChars > 0) {
			FormattedCharSequence lit = ordered(text.substring(0, readChars), style);
			submitRun(sink, matrices, x, y, lit, shadow, litColor, background, outline);
			x += (float) textRenderer.width(lit);
		}
		FormattedCharSequence rest = ordered(text.substring(readChars), style);
		submitRun(sink, matrices, x, y, rest, shadow, dimColor, background, outline);
	}

	private static void submitRows(TextSink sink, PoseStack matrices, CameraView camera,
			LyricsConfig config, Font textRenderer, Style style, List<String> rows, AnimState anim,
			int accentRgb, int readChars, List<String> previewRows) {
		int alpha = clamp(Math.round(anim.alpha() * 255.0f), 0, 255);
		int color = alpha << 24 | accentRgb;
		int dimColor = alpha << 24 | dim(accentRgb, DIM_FACTOR);
		int outline = config.lyricsGlowEffect ? alpha << 24 | config.lyricsOutlineColorHex & 0xFFFFFF : 0;
		int background = 0;
		boolean shadow = false;
		if (outline == 0) {
			background = config.lyricsBgAlpha > 0.01f
					? clamp(Math.round(config.lyricsBgAlpha * (float) alpha), 0, 255) << 24
					: 0;
			shadow = config.lyricsDropShadow;
		}
		pushAnchor(matrices, camera, config, anim.scaleMul());
		int consumed = 0;
		float y = anim.yOffset();
		for (String row : rows) {
			if (anim.visibleChars() <= consumed) {
				break;
			}
			int show = Math.min(row.length(), anim.visibleChars() - consumed);
			String revealed = row.substring(0, show);
			if (!revealed.isBlank()) {
				int readInRow = readChars < 0 ? -1 : clamp(readChars - consumed, 0, show);
				submitSplitRow(sink, matrices, textRenderer, style, revealed, readInRow, y,
						shadow, color, dimColor, background, outline);
			}
			consumed += row.length();
			y += LINE_HEIGHT;
		}
		if (previewRows != null) {
			int previewAlpha = clamp(Math.round((float) alpha * PREVIEW_ALPHA), 0, 255);
			int previewColor = previewAlpha << 24 | dim(accentRgb, DIM_FACTOR);
			int previewOutline = outline == 0 ? 0 : previewAlpha << 24 | config.lyricsOutlineColorHex & 0xFFFFFF;
			for (String row : previewRows) {
				if (!row.isBlank()) {
					submitSplitRow(sink, matrices, textRenderer, style, row, -1, y, shadow,
							previewColor, previewColor, 0, previewOutline);
				}
				y += LINE_HEIGHT;
			}
		}
		matrices.popPose();
	}

	private static void submitCountdown(TextSink sink, PoseStack matrices, CameraView camera,
			LyricsConfig config, Font textRenderer, Style style, float progress, float alpha,
			int accentRgb) {
		StringBuilder dots = new StringBuilder();
		for (int i = 0; i < COUNTDOWN_DOTS; ++i) {
			if (i > 0) {
				dots.append(' ');
			}
			dots.append('\u2022');
		}
		int filled = clamp(Math.round(COUNTDOWN_DOTS * clamp01(progress)), 0, COUNTDOWN_DOTS);
		int readChars = Math.min(dots.length(), filled * 2);
		int opacity = clamp(Math.round(alpha * 255.0f), 0, 255);
		if (opacity <= 2) {
			return;
		}
		int color = opacity << 24 | accentRgb;
		int dimColor = opacity << 24 | dim(accentRgb, DIM_FACTOR);
		int outline = config.lyricsGlowEffect ? opacity << 24 | config.lyricsOutlineColorHex & 0xFFFFFF : 0;
		pushAnchor(matrices, camera, config, 1.0f);
		submitSplitRow(sink, matrices, textRenderer, style, dots.toString(), readChars, 0.0f,
				outline == 0 && config.lyricsDropShadow, color, dimColor, 0, outline);
		matrices.popPose();
	}

	private static AnimState animate(LyricsConfig config, List<String> rows, int totalChars,
			long elapsed, long window) {
		String style = config.lyricsAnimation != null ? config.lyricsAnimation.toUpperCase() : "DROP";
		if ("TYPEWRITER".equals(style)) {
			return new AnimState(typedChars(totalChars, elapsed, window), 1.0f, 0.0f, 1.0f);
		}
		long introMs = Math.min(MAX_INTRO_MS, (long) (window * INTRO_WINDOW_SHARE));
		long outroMs = Math.min(MAX_OUTRO_MS, (long) (window * OUTRO_WINDOW_SHARE));
		float intro = introMs > 0L ? easeOut(clamp01((float) elapsed / (float) introMs)) : 1.0f;
		float outro = outroMs > 0L
				? easeIn(clamp01((float) (elapsed - (window - outroMs)) / (float) outroMs))
				: 0.0f;
		float alpha = intro * (1.0f - outro);
		return switch (style) {
			case "RISE" -> new AnimState(totalChars, alpha,
					12.0f * (1.0f - intro) - 12.0f * outro, 1.0f);
			case "WORDS" -> new AnimState(revealedWordChars(rows, intro), alpha, 0.0f,
					1.0f + 0.15f * (1.0f - intro));
			case "TYPEWRITER" -> new AnimState(typedChars(totalChars, elapsed, window), 1.0f, 0.0f, 1.0f);
			default -> new AnimState(totalChars, alpha, -12.0f * (1.0f - intro) + 12.0f * outro, 1.0f);
		};
	}

	private static int typedChars(int totalChars, long elapsed, long window) {
		long typeMs = Math.min(totalChars * MS_PER_CHAR_TYPE, (long) (window * TYPE_WINDOW_SHARE));
		if (typeMs > 0L && elapsed < typeMs) {
			return clamp((int) (totalChars * elapsed / typeMs), 0, totalChars);
		}
		long deleteMs = Math.min(totalChars * MS_PER_CHAR_DELETE, (long) (window * DELETE_WINDOW_SHARE));
		if (deleteMs > 0L && elapsed >= window - deleteMs) {
			long remaining = window - elapsed;
			return clamp((int) Math.ceil(totalChars * (double) remaining / deleteMs), 0, totalChars);
		}
		return totalChars;
	}

	private static int revealedWordChars(List<String> rows, float progress) {
		int totalWords = 0;
		for (String row : rows) {
			totalWords += row.split(" ").length;
		}
		int wanted = Math.max(1, (int) Math.ceil(totalWords * clamp01(progress)));
		int chars = 0;
		int seen = 0;
		for (String row : rows) {
			for (String word : row.split(" ")) {
				if (seen >= wanted) {
					return chars;
				}
				chars += word.length() + 1;
				++seen;
			}
		}
		return chars;
	}

	private static float easeOut(float t) {
		float inv = 1.0f - t;
		return 1.0f - inv * inv * inv;
	}

	private static float easeIn(float t) {
		return t * t * t;
	}

	private static float clamp01(float value) {
		return Math.max(0.0f, Math.min(1.0f, value));
	}

	public static float effectiveScale(LyricsConfig config) {
		return effectiveScale(config.lyricsFontScale);
	}

	public static float effectiveScale(float fontScale) {
		return BASE_3D_SCALE * (fontScale > 0.1f ? fontScale : 1.0f);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static Style buildStyle(LyricsConfig config) {
		Style style = Style.EMPTY.withBold(config.boldActiveLyric);
		String font = config.lyricsFont != null ? config.lyricsFont.toUpperCase() : "DEFAULT";
		String id = switch (font) {
			case "UNIFORM" -> "uniform";
			case "ALT" -> "alt";
			case "ILLAGER" -> "illageralt";
			default -> null;
		};
		return id == null ? style
				: style.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("minecraft", id)));
	}

	private static boolean needsNewAnchor(LyricsConfig config, double configuredDistance,
			String trackKey, boolean lineChanged) {
		if (worldAnchor == null) {
			return true;
		}
		if (lineChanged) {
			return true;
		}
		if (!trackKey.equals(anchorTrackKey)) {
			return true;
		}
		return anchorConfiguredDistance != configuredDistance
				|| anchorHeightSetting != config.textHeightOffset3D;
	}

	private static double speedLead(LyricsConfig config) {
		if (config.speedLead3D <= 0.0) {
			return 0.0;
		}
		return Math.min(MAX_SPEED_LEAD, horizontalSpeed() * config.speedLead3D);
	}

	private static double followStrength() {
		double speed = horizontalSpeed();
		if (speed <= FOLLOW_START_SPEED) {
			return 0.0;
		}
		return Math.min(1.0, (speed - FOLLOW_START_SPEED) / (FOLLOW_FULL_SPEED - FOLLOW_START_SPEED));
	}

	private static double horizontalSpeed() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return 0.0;
		}
		Vec3 velocity = client.player.getDeltaMovement();
		return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) * TICKS_PER_SECOND;
	}

	private static void placeAnchor(CameraView camera, LyricsConfig config, double distance) {
		Vector3f forward = camera.orientation().transform(new Vector3f(0.0f, 0.0f, -1.0f));
		double forwardX = forward.x();
		double forwardZ = (double) forward.z();
		double horizontal = Math.sqrt(forwardX * forwardX + forwardZ * forwardZ);
		if (horizontal < 1.0E-4) {
			forwardX = 0.0;
			forwardZ = -1.0;
			horizontal = 1.0;
		}
		forwardX /= horizontal;
		forwardZ /= horizontal;
		double baseYaw = Math.atan2(-forwardX, forwardZ);
		lastSideRight = !lastSideRight;
		double spread = Math.max(0.0, config.sideSpread3D);
		double magnitude = spread * (SIDE_ANGLE_JITTER + RANDOM.nextDouble() * (1.0 - SIDE_ANGLE_JITTER));
		if (config.keepOnScreen3D) {
			magnitude = Math.min(magnitude, maxOnScreenAngle(config, distance));
		}
		double yaw = baseYaw + Math.toRadians(lastSideRight ? magnitude : -magnitude);
		double jitter = (RANDOM.nextDouble() * 2.0 - 1.0) * HEIGHT_JITTER;
		worldAnchor = camera.pos().add(-Math.sin(yaw) * distance,
				config.textHeightOffset3D + jitter, Math.cos(yaw) * distance);
	}

	private static double maxOnScreenAngle(LyricsConfig config, double distance) {
		Minecraft client = Minecraft.getInstance();
		if (client.getWindow() == null || client.options == null) {
			return Double.MAX_VALUE;
		}
		double height = client.getWindow().getHeight();
		if (height <= 0.0) {
			return Double.MAX_VALUE;
		}
		double aspect = (double) client.getWindow().getWidth() / height;
		double verticalHalf = Math.toRadians(((Integer) client.options.fov().get()).intValue() / 2.0);
		double horizontalHalf = Math.toDegrees(Math.atan(Math.tan(verticalHalf) * aspect));
		int wrapWidth = clamp(config.lyricsWrapWidth, 40, 400);
		double halfWidthBlocks = wrapWidth / 2.0 * (double) GLYPH_SCALE * (double) effectiveScale(config);
		double textHalfAngle = Math.toDegrees(Math.atan(halfWidthBlocks / Math.max(1.0, distance)));
		return Math.max(0.0, horizontalHalf * 0.9 - textHalfAngle);
	}

	private static String simplify(String text) {
		String cleaned = text.replaceAll("\\([^)]*\\)", " ");
		cleaned = cleaned.replaceAll("\\(.*$", " ");
		cleaned = cleaned.replaceAll("\\s+", " ").trim();
		return cleaned.isEmpty() ? text.trim() : cleaned;
	}

	private static int widthOf(Font textRenderer, Style style, String text) {
		return textRenderer.width(Component.literal(text).setStyle(style).getVisualOrderText());
	}

	private static List<String> wrapRows(Font textRenderer, Style style, String text, int maxWidth) {
		List<String> rows = new ArrayList<>();
		if (text.isEmpty() || widthOf(textRenderer, style, text) <= maxWidth) {
			rows.add(text);
			return rows;
		}
		StringBuilder row = new StringBuilder();
		for (String word : text.split(" ")) {
			if (word.isEmpty()) {
				continue;
			}
			if (row.length() == 0) {
				row.append(word);
				continue;
			}
			String candidate = row + " " + word;
			if (widthOf(textRenderer, style, candidate) > maxWidth) {
				rows.add(row.toString());
				row.setLength(0);
				row.append(word);
				continue;
			}
			row.setLength(0);
			row.append(candidate);
		}
		if (row.length() > 0) {
			rows.add(row.toString());
		}
		return rows;
	}

	private record CameraView(Vec3 pos, Quaternionf orientation) {
	}

	@FunctionalInterface
	private interface TextSink {
		void text(PoseStack poses, float x, float y, FormattedCharSequence text, boolean shadow,
				Font.DisplayMode mode, int color, int background, int outline);
	}

	private record AnimState(int visibleChars, float alpha, float yOffset, float scaleMul) {
	}
}
