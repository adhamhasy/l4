package com.melodia.lyrics.media;

import java.util.ArrayList;
import java.util.List;

/** Fan-out over every known media provider. */
public final class MediaManager {
	private static MediaManager instance;
	private final List<MediaProvider> providers = new ArrayList<>();
	private volatile TrackInfo currentTrack;
	private TrackInfo lastPublished;

	private MediaManager() {
		providers.add(new MelodiaStatusProvider());
	}

	public static synchronized MediaManager getInstance() {
		if (instance == null) {
			instance = new MediaManager();
		}
		return instance;
	}

	public void start() {
		for (MediaProvider provider : providers) {
			try {
				provider.start();
			} catch (Exception failed) {
				com.melodia.lyrics.MelodiaLyricsClient.LOGGER.warn("[MelodiaLyrics] {} failed to start",
						provider.getClass().getSimpleName(), failed);
			}
		}
	}

	public void tick() {
		TrackInfo found = null;
		for (MediaProvider provider : providers) {
			try {
				TrackInfo polled = provider.poll();
				if (polled == null || !polled.isValidTrack()) {
					continue;
				}
				found = polled;
				break;
			} catch (Exception exception) {
			}
		}
		// The HUD draws off this reference; publish a new object only when something changed
		// so the renderer is not handed a new object every tick for no reason.
		if (lastPublished == null || !lastPublished.equals(found)) {
			lastPublished = found;
		}
		currentTrack = found;
	}

	public TrackInfo getCurrentTrack() {
		return currentTrack;
	}

	public void stop() {
		for (MediaProvider provider : providers) {
			try {
				provider.stop();
			} catch (Exception ignored) {
			}
		}
	}
}
