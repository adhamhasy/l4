package com.melodia.lyrics.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

/** lrclib.net lookup, mirroring the original's exact/search strategy and title cleaning. */
public final class LrclibClient {
	private static final String EXACT_URL = "https://lrclib.net/api/get";
	private static final String SEARCH_URL = "https://lrclib.net/api/search";
	private static final String USER_AGENT = "MelodiaLyrics/0.2 (Minecraft mod; Melodia bridge)";
	private static final Duration TIMEOUT = Duration.ofSeconds(8L);
	private static final Pattern BRACKETED = Pattern.compile("[(\\[{][^)\\]}]*[)\\]}]");
	private static final int FEATURING_FLAGS = Pattern.CASE_INSENSITIVE;
	private static final Pattern FEATURING = Pattern.compile("\\s+(?:feat\\.?|ft\\.?|featuring|with)\\s+.*$", FEATURING_FLAGS);
	private static final Pattern NOISE_WORDS = Pattern.compile("\\b(?:official|lyrics?|lyric video|music video|audio|visualizer|hd|4k|remastered|explicit|mv)\\b", FEATURING_FLAGS);
	private static final Pattern CHANNEL_SUFFIX = Pattern.compile("\\s*-\\s*(?:topic|vevo)\\s*$", FEATURING_FLAGS);
	private static final Pattern WHITESPACE = Pattern.compile("\\s{2,}");
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(TIMEOUT)
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	private LrclibClient() {
	}

	public static String cleanTitle(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String cleaned = CHANNEL_SUFFIX.matcher(raw).replaceAll("");
		cleaned = BRACKETED.matcher(cleaned).replaceAll(" ");
		cleaned = FEATURING.matcher(cleaned).replaceAll("");
		cleaned = NOISE_WORDS.matcher(cleaned).replaceAll(" ");
		cleaned = cleaned.replace('_', ' ');
		cleaned = WHITESPACE.matcher(cleaned).replaceAll(" ").trim();
		return cleaned.isEmpty() ? raw.trim() : cleaned;
	}

	public static String fetchSynced(String artist, String title, String album, long durationMs) {
		String cleanArtist = cleanTitle(artist);
		String cleanTrack = cleanTitle(title);
		if (cleanTrack.isEmpty()) {
			return null;
		}
		String exact = exactLookup(cleanArtist, cleanTrack, album, durationMs);
		return exact != null ? exact : searchLookup(cleanArtist, cleanTrack, durationMs);
	}

	private static String exactLookup(String artist, String title, String album, long durationMs) {
		StringBuilder query = new StringBuilder(EXACT_URL)
				.append("?track_name=").append(encode(title))
				.append("&artist_name=").append(encode(artist));
		if (album != null && !album.isBlank()) {
			query.append("&album_name=").append(encode(album));
		}
		if (durationMs > 0L) {
			query.append("&duration=").append(Math.round(durationMs / 1000.0));
		}
		JsonElement body = get(query.toString());
		return body != null && body.isJsonObject() ? syncedOf(body.getAsJsonObject()) : null;
	}

	private static String searchLookup(String artist, String title, long durationMs) {
		String url = SEARCH_URL + "?track_name=" + encode(title) + "&artist_name=" + encode(artist);
		JsonElement body = get(url);
		if (body == null || !body.isJsonArray()) {
			return null;
		}
		JsonArray results = body.getAsJsonArray();
		String best = null;
		long bestGap = Long.MAX_VALUE;
		for (JsonElement element : results) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject candidate = element.getAsJsonObject();
			String synced = syncedOf(candidate);
			if (synced == null) {
				continue;
			}
			long gap = 0L;
			if (durationMs > 0L && candidate.has("duration") && !candidate.get("duration").isJsonNull()) {
				gap = Math.abs(Math.round(candidate.get("duration").getAsDouble() * 1000.0) - durationMs);
			}
			if (gap >= bestGap) {
				continue;
			}
			bestGap = gap;
			best = synced;
		}
		return best;
	}

	private static String syncedOf(JsonObject object) {
		if (!object.has("syncedLyrics") || object.get("syncedLyrics").isJsonNull()) {
			return null;
		}
		String synced = object.get("syncedLyrics").getAsString();
		return synced == null || synced.isBlank() ? null : synced;
	}

	private static JsonElement get(String url) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.header("User-Agent", USER_AGENT)
					.header("Accept", "application/json")
					.timeout(TIMEOUT)
					.GET()
					.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() != 200) {
				return null;
			}
			return JsonParser.parseString(response.body());
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			return null;
		} catch (Exception failed) {
			return null;
		}
	}

	private static String encode(String value) {
		return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
	}
}
