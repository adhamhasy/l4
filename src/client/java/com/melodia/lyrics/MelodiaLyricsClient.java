package com.melodia.lyrics;

import com.melodia.lyrics.config.LyricsConfig;
import com.melodia.lyrics.lyrics.LyricsService;
import com.melodia.lyrics.media.MediaManager;
import com.melodia.lyrics.media.MelodiaStatusProvider;
import com.melodia.lyrics.media.TrackInfo;
import com.melodia.lyrics.render.HudKaraokeRenderer;
import com.melodia.lyrics.render.Lyrics3DRenderer;
import com.melodia.lyrics.render.PlayerWidgetRenderer;

import net.fabricmc.api.ClientModInitializer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint for Melodia Lyrics — the Show the Lyrics experience (world-space lyric bars
 * plus a corner now-playing card) driven by Melodia's live status file instead of a Windows
 * media session.
 */
public final class MelodiaLyricsClient implements ClientModInitializer {
	public static final String MOD_ID = "melodia_lyrics";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final int POLL_INTERVAL_TICKS = 5;

	private int tickCounter;

	@Override
	public void onInitializeClient() {
		LyricsConfig.load();
		MediaManager.getInstance().start();
		MelodiaStatusProvider.invalidatePath();

		HudElementRegistry.addLast(id("player_widget"), (graphics, deltaTracker) -> {
			TrackInfo track = MediaManager.getInstance().getCurrentTrack();
			PlayerWidgetRenderer.render(graphics, track, LyricsConfig.getInstance());
		});
		HudElementRegistry.addLast(id("hud_karaoke"), new HudKaraokeRenderer());
		LevelRenderEvents.COLLECT_SUBMITS.register(Lyrics3DRenderer::render);
		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
		Commands.register();
		LOGGER.info("[MelodiaLyrics] ready (status: {})", MelodiaStatusProvider.resolveFile());
	}

	private void onClientTick(Minecraft client) {
		if (++tickCounter % POLL_INTERVAL_TICKS != 0) {
			return;
		}
		MediaManager manager = MediaManager.getInstance();
		manager.tick();
		LyricsService.getInstance().onTrack(manager.getCurrentTrack());
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
