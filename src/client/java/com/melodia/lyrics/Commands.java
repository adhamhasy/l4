package com.melodia.lyrics;

import com.melodia.lyrics.config.LyricsConfig;
import com.melodia.lyrics.config.LyricsConfig.WidgetPosition;
import com.melodia.lyrics.media.MediaManager;
import com.melodia.lyrics.media.MelodiaStatusProvider;
import com.melodia.lyrics.media.TrackInfo;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Locale;

/** Client commands standing in for the config screen (the original uses a YACL screen). */
public final class Commands {
	private Commands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
				ClientCommands.literal("melodia")
						.executes(ctx -> {
							toggle(ctx, cfg -> cfg.showLyrics = !cfg.showLyrics, "lyrics");
							return 1;
						})
						.then(ClientCommands.literal("lyrics").executes(ctx -> {
							toggle(ctx, cfg -> cfg.showLyrics = !cfg.showLyrics, "lyrics");
							return 1;
						}))
						.then(ClientCommands.literal("widget").executes(ctx -> {
							toggle(ctx, cfg -> cfg.showPlayerWidget = !cfg.showPlayerWidget, "player widget");
							return 1;
						}))
						.then(ClientCommands.literal("widget")
								.then(ClientCommands.argument("corner", StringArgumentType.word()).executes(ctx -> {
									try {
										WidgetPosition pos = WidgetPosition.valueOf(
												StringArgumentType.getString(ctx, "corner").toUpperCase(Locale.ROOT));
										LyricsConfig.getInstance().playerWidgetPosition = pos;
										LyricsConfig.getInstance().save();
										feedback(ctx.getSource(), "widget corner = " + pos);
									} catch (IllegalArgumentException bad) {
										feedback(ctx.getSource(), "corners: " + cornerList());
									}
									return 1;
								})))
						.then(ClientCommands.literal("hud").executes(ctx -> {
							toggle(ctx, cfg -> cfg.showHud = !cfg.showHud, "HUD overlay");
							return 1;
						}))
						.then(ClientCommands.literal("accent")
								.then(ClientCommands.argument("mode", StringArgumentType.word()).executes(ctx -> {
									String mode = StringArgumentType.getString(ctx, "mode").toUpperCase(Locale.ROOT);
									LyricsConfig cfg = LyricsConfig.getInstance();
									boolean ok = mode.equals("ALBUM") || mode.equals("CUSTOM")
											|| mode.equals("RAINBOW")
											|| Arrays.asList(com.melodia.lyrics.render.AccentColor.PALETTE).contains(mode);
									if (ok) {
										cfg.accentColor = mode;
										cfg.save();
										feedback(ctx.getSource(), "accent = " + mode);
									} else {
										feedback(ctx.getSource(), "modes: ALBUM, RAINBOW, CUSTOM, "
												+ String.join(", ", com.melodia.lyrics.render.AccentColor.PALETTE));
									}
									return 1;
								})))
						.then(ClientCommands.literal("animation")
								.then(ClientCommands.argument("style", StringArgumentType.word()).executes(ctx -> {
									String style = StringArgumentType.getString(ctx, "style").toUpperCase(Locale.ROOT);
									if (Arrays.asList("TYPEWRITER", "DROP", "RISE", "WORDS").contains(style)) {
										LyricsConfig.getInstance().lyricsAnimation = style;
										LyricsConfig.getInstance().save();
										feedback(ctx.getSource(), "animation = " + style);
									} else {
										feedback(ctx.getSource(), "styles: TYPEWRITER, DROP, RISE, WORDS");
									}
									return 1;
								})))
						.then(ClientCommands.literal("offset")
								.then(ClientCommands.argument("ms", IntegerArgumentType.integer(-1000, 1000))
										.executes(ctx -> {
											long ms = IntegerArgumentType.getInteger(ctx, "ms");
											LyricsConfig.getInstance().timeOffsetMs = ms;
											LyricsConfig.getInstance().save();
											feedback(ctx.getSource(), "sync offset = " + ms + " ms");
											return 1;
										})))
						.then(ClientCommands.literal("status").executes(ctx -> {
							TrackInfo t = MediaManager.getInstance().getCurrentTrack();
							com.melodia.lyrics.lyrics.LyricsService service =
									com.melodia.lyrics.lyrics.LyricsService.getInstance();
							feedback(ctx.getSource(), t == null
									? "no track - is Melodia playing?"
									: (t.getArtist().isBlank() ? t.getTitle() : t.getArtist() + " - " + t.getTitle())
											+ " | lyrics loaded=" + !service.lyrics().isEmpty()
											+ (service.isLoading() ? " (fetching)" : service.usedBridge() ? " (bridge)" : "")
											+ " | file=" + MelodiaStatusProvider.resolveFile());
							return 1;
						}))
						.then(ClientCommands.literal("reload").executes(ctx -> {
							MelodiaStatusProvider.invalidatePath();
							LyricsConfig.reload();
							feedback(ctx.getSource(), "config reloaded");
							return 1;
						}))
		));
	}

	private interface ToggleBody {
		void run(LyricsConfig cfg);
	}

	private static void toggle(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx,
			ToggleBody body, String what) {
		body.run(LyricsConfig.getInstance());
		LyricsConfig.getInstance().save();
		boolean on = what.equals("lyrics") ? LyricsConfig.getInstance().showLyrics
				: what.equals("player widget") ? LyricsConfig.getInstance().showPlayerWidget
				: LyricsConfig.getInstance().showHud;
		feedback(ctx.getSource(), what + " " + (on ? "on" : "off"));
	}

	private static String cornerList() {
		StringBuilder b = new StringBuilder();
		for (WidgetPosition p : WidgetPosition.values()) {
			if (b.length() > 0) {
				b.append(", ");
			}
			b.append(p.name().toLowerCase(Locale.ROOT));
		}
		return b.toString();
	}

	private static void feedback(FabricClientCommandSource source, String message) {
		source.sendFeedback(Component.literal("[melodia] " + message));
	}
}
