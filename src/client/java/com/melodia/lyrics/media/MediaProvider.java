package com.melodia.lyrics.media;

/** A source of "what is playing right now". */
public interface MediaProvider {
	void start();

	TrackInfo poll();

	void stop();
}
