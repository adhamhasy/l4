package com.melodia.lyrics.gui;

import com.melodia.lyrics.config.LyricsConfig;
import com.melodia.lyrics.render.AccentColor;
import com.melodia.lyrics.render.Lyrics3DRenderer;

import dev.isxander.yacl3.api.Binding;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.CyclingListControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.LongSliderControllerBuilder;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ConfigScreenBuilder {
	private static final String[] ACCENT_COLORS = buildAccentList();
	private static final String[] ANIMATIONS = new String[] {"TYPEWRITER", "DROP", "RISE", "WORDS"};
	private static final String[] FONTS = new String[] {"DEFAULT", "UNIFORM", "ALT", "ILLAGER"};
	private static final String[] HUD_ANCHORS = new String[] {"above_hotbar", "top", "bottom", "top_left", "top_right"};

	private static String[] buildAccentList() {
		List<String> names = new ArrayList<>();
		names.add("ALBUM");
		names.addAll(Arrays.asList(AccentColor.PALETTE));
		names.add("RAINBOW");
		names.add("CUSTOM");
		return names.toArray(new String[0]);
	}

	private ConfigScreenBuilder() {
	}

	public static Screen create(Screen parent) {
		LyricsConfig config = LyricsConfig.getInstance();
		return YetAnotherConfigLib.createBuilder()
				.title(Component.literal("Melodia Lyrics"))
				.category(widgetCategory(config))
				.category(lyricsCategory(config))
				.category(hudCategory(config))
				.save(LyricsConfig::save)
				.build()
				.generateScreen(parent);
	}

	private static ConfigCategory widgetCategory(LyricsConfig config) {
		return ConfigCategory.createBuilder()
				.name(Component.literal("Widget"))
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Widget"))
						.option(Option.createBuilder()
								.name(Component.literal("Show Player Widget"))
								.description(OptionDescription.of(Component.literal(
										"The track card with title, artist and progress bar.")))
								.binding(Binding.generic(false, () -> config.showPlayerWidget,
										value -> config.showPlayerWidget = value))
								.controller(option -> BooleanControllerBuilder.create(option).coloured(true).onOffFormatter())
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("Position"))
								.description(OptionDescription.of(Component.literal(
										"Which corner of the screen the widget sits in.")))
								.binding(Binding.generic(LyricsConfig.WidgetPosition.TOP_LEFT,
										() -> config.playerWidgetPosition,
										value -> config.playerWidgetPosition = value))
								.controller(option -> CyclingListControllerBuilder.create(option)
										.values((Object[]) LyricsConfig.WidgetPosition.values())
										.valueFormatter(value -> Component.literal(prettify(value.name()))))
								.build())
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Size & Opacity"))
						.option(Option.createBuilder()
								.name(Component.literal("Widget Scale"))
								.binding(Binding.generic(1.0f, () -> config.playerWidgetScale,
										value -> config.playerWidgetScale = value))
								.controller(option -> ((FloatSliderControllerBuilder) ((FloatSliderControllerBuilder)
										FloatSliderControllerBuilder.create(option)
												.range(0.6f, 1.6f))
										.step(0.1f))
										.valueFormatter(value -> Component.literal(String.format("%.1fx", value))))
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("Widget Opacity"))
								.binding(Binding.generic(0.95f, () -> config.playerWidgetAlpha,
										value -> config.playerWidgetAlpha = value))
								.controller(option -> ((FloatSliderControllerBuilder) ((FloatSliderControllerBuilder)
										FloatSliderControllerBuilder.create(option)
												.range(0.2f, 1.0f))
										.step(0.05f))
										.valueFormatter(value -> Component.literal(
												Math.round(value * 100.0f) + "%")))
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("Hide While Paused"))
								.description(OptionDescription.of(Component.literal(
										"Hides the widget and the lyrics whenever playback is paused.")))
								.binding(Binding.generic(false, () -> config.autoHide,
										value -> config.autoHide = value))
								.controller(option -> BooleanControllerBuilder.create(option).coloured(true).onOffFormatter())
								.build())
						.build())
				.build();
	}

	private static ConfigCategory lyricsCategory(LyricsConfig config) {
		return ConfigCategory.createBuilder()
				.name(Component.literal("Lyrics"))
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Display"))
						.option(Option.createBuilder()
								.name(Component.literal("Show Lyrics"))
								.description(OptionDescription.of(Component.literal(
										"Master switch. Off hides lyrics in both 3D and HUD mode.")))
								.binding(Binding.generic(true, () -> config.showLyrics,
										value -> config.showLyrics = value))
								.controller(option -> BooleanControllerBuilder.create(option).coloured(true).onOffFormatter())
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("Accent Colour"))
								.description(OptionDescription.of(Component.literal(
										"ALBUM picks Melodia's album colour. CUSTOM uses the colour below.")))
								.binding(Binding.generic("ICE", () -> config.accentColor,
										value -> config.accentColor = value))
								.controller(option -> CyclingListControllerBuilder.create(option)
										.values((Object[]) ACCENT_COLORS)
										.valueFormatter(value -> accentLabel(config, value)))
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("Custom Colour"))
								.description(OptionDescription.of(Component.literal(
										"Used only while Accent Colour is set to CUSTOM.")))
								.binding(Binding.generic(new Color(13500410), () -> new Color(config.lyricsColorHex & 0xFFFFFF),
										value -> config.lyricsColorHex = value.getRGB() & 0xFFFFFF))
								.controller(option -> dev.isxander.yacl3.api.controller.ColorControllerBuilder.create(option)
										.allowAlpha(false))
								.build())
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.literal("3D World"))
						.option(Option.createBuilder()
								.name(Component.literal("Animation"))
								.description(OptionDescription.of(Component.literal(
										"TYPEWRITER types in and backspaces away. DROP falls in and keeps falling on the way out. RISE does the reverse. WORDS reveals a word at a time with a small pop.")))
								.binding(Binding.generic("TYPEWRITER", () -> config.lyricsAnimation,
										value -> config.lyricsAnimation = value))
								.controller(option -> CyclingListControllerBuilder.create(option)
										.values((Object[]) ANIMATIONS)
										.valueFormatter(value -> Component.literal(prettify(value))))
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("Distance"))
								.description(OptionDescription.of(Component.literal(
										"How far ahead the text is placed. Closer also means bigger on screen.")))
								.binding(Binding.generic(15.0, () -> config.textDistance3D,
										value -> config.textDistance3D = value))
								.controller(option -> ((DoubleSliderControllerBuilder) ((DoubleSliderControllerBuilder)
										DoubleSliderControllerBuilder.create(option).range(3.0, 24.0))
										.step(1.0))
										.valueFormatter(value -> Component.literal(String.format("%.0f blocks", value))))
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("Speed Lead"))
								.description(OptionDescription.of(Component.literal(
										"Places the text further ahead the faster you move.")))
								.binding(Binding.generic(0.5, () -> config.speedLead3D,
										value -> config.speedLead3D = value))
								.controller(option -> ((DoubleSliderControllerBuilder) ((DoubleSliderControllerBuilder)
										DoubleSliderControllerBuilder.create(option).range(0.0, 2.0))
										.step(0.1))
										.valueFormatter(value -> Component.literal(
												value <= 0.0 ? "Off" : String.format("%.1f s ahead", value))))
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("Follow You"))
								.description(OptionDescription.of(Component.literal(
										"How much a bar travels with you while moving fast.")))
								.binding(Binding.generic(1.0, () -> config.anchorFollow3D,
										value -> config.anchorFollow3D = value))
								.controller(option -> ((DoubleSliderControllerBuilder) ((DoubleSliderControllerBuilder)
										DoubleSliderControllerBuilder.create(option).range(0.0, 1.0))
										.step(0.1))
										.valueFormatter(value -> Component.literal(
												value <= 0.0 ? "Fixed in world" : Math.round(value * 100.0) + "%")))
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("Side Spread"))
								.description(OptionDescription.of(Component.literal(
										"How far left or right of where you were looking a bar appears.")))
								.binding(Binding.generic(50.0, () -> config.sideSpread3D,
										value -> config.sideSpread3D = value))
								.controller(option -> ((DoubleSliderControllerBuilder) ((DoubleSliderControllerBuilder)
										DoubleSliderControllerBuilder.create(option).range(10.0, 80.0))
										.step(5.0))
										.valueFormatter(value -> Component.literal(String.format("%.0f\u00b0", value))))
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("Keep On Screen"))
								.description(OptionDescription.of(Component.literal(
										"Limits how far to the side a bar can be placed so the whole block stays inside your view.")))
								.binding(Binding.generic(false, () -> config.keepOnScreen3D,
										value -> config.keepOnScreen3D = value))
								.controller(option -> BooleanControllerBuilder.create(option).coloured(true).onOffFormatter())
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("Height"))
								.description(OptionDescription.of(Component.literal(
										"Vertical offset from eye level.")))
								.binding(Binding.generic(2.5, () -> config.textHeightOffset3D,
										value -> config.textHeightOffset3D = value))
								.controller(option -> ((DoubleSliderControllerBuilder) ((DoubleSliderControllerBuilder)
										DoubleSliderControllerBuilder.create(option).range(-2.0, 5.0))
										.step(0.5))
										.valueFormatter(value -> Component.literal(String.format("%.1f", value))))
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("Text Size"))
								.description(OptionDescription.of(Component.literal(
										"Multiplier on top of the base 3D text size.")))
								.binding(Binding.generic(1.25f, () -> config.lyricsFontScale,
										value -> config.lyricsFontScale = value))
								.controller(option -> ((FloatSliderControllerBuilder) ((FloatSliderControllerBuilder)
										FloatSliderControllerBuilder.create(option).range(0.5f, 2.0f))
										.step(0.25f))
										.valueFormatter(value -> Component.literal(String.format("%.2fx",
												Lyrics3DRenderer.effectiveScale(value)))))
								.build())
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Text Style"))
						.option(Option.createBuilder()
								.name(Component.literal("Line Width"))
								.description(OptionDescription.of(Component.literal(
										"How wide a row may get before the text wraps.")))
								.binding(Binding.generic(110, () -> config.lyricsWrapWidth,
										value -> config.lyricsWrapWidth = value))
								.controller(option -> ((IntegerSliderControllerBuilder) ((IntegerSliderControllerBuilder)
										IntegerSliderControllerBuilder.create(option).range(60, 280))
										.step(10))
										.valueFormatter(value -> Component.literal(value + " px")))
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("Font"))
								.description(OptionDescription.of(Component.literal(
										"Minecraft's built-in fonts.")))
								.binding(Binding.generic("DEFAULT", () -> config.lyricsFont,
										value -> config.lyricsFont = value))
								.controller(option -> CyclingListControllerBuilder.create(option)
										.values((Object[]) FONTS)
										.valueFormatter(value -> Component.literal(prettify(value))))
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("Bold"))
								.binding(Binding.generic(false, () -> config.boldActiveLyric,
										value -> config.boldActiveLyric = value))
								.controller(option -> BooleanControllerBuilder.create(option).coloured(true).onOffFormatter())
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("Uppercase"))
								.binding(Binding.generic(false, () -> config.lyricsUppercase,
										value -> config.lyricsUppercase = value))
								.controller(option -> BooleanControllerBuilder.create(option).coloured(true).onOffFormatter())
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("Outline"))
								.description(OptionDescription.of(Component.literal(
										"Draws a hard edge around every glyph.")))
								.binding(Binding.generic(false, () -> config.lyricsGlowEffect,
										value -> config.lyricsGlowEffect = value))
								.controller(option -> BooleanControllerBuilder.create(option).coloured(true).onOffFormatter())
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("Outline Colour"))
								.binding(Binding.generic(new Color(0xFFFFFF),
										() -> new Color(config.lyricsOutlineColorHex & 0xFFFFFF),
										value -> config.lyricsOutlineColorHex = value.getRGB() & 0xFFFFFF))
								.controller(option -> dev.isxander.yacl3.api.controller.ColorControllerBuilder.create(option)
										.allowAlpha(false))
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("Drop Shadow"))
								.description(OptionDescription.of(Component.literal(
										"Ignored while Outline is on.")))
								.binding(Binding.generic(true, () -> config.lyricsDropShadow,
										value -> config.lyricsDropShadow = value))
								.controller(option -> BooleanControllerBuilder.create(option).coloured(true).onOffFormatter())
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("Background"))
								.description(OptionDescription.of(Component.literal(
										"Panel behind the text. Ignored while Outline is on.")))
								.binding(Binding.generic(0.0f, () -> config.lyricsBgAlpha,
										value -> config.lyricsBgAlpha = value))
								.controller(option -> ((FloatSliderControllerBuilder) ((FloatSliderControllerBuilder)
										FloatSliderControllerBuilder.create(option).range(0.0f, 1.0f))
										.step(0.05f))
										.valueFormatter(value -> Component.literal(
												value <= 0.01f ? "Off" : Math.round(value * 100.0f) + "%")))
								.build())
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Extras"))
						.option(Option.createBuilder()
								.name(Component.literal("Karaoke Highlight"))
								.description(OptionDescription.of(Component.literal(
										"Fills the line in as it is sung. Word-timed when Melodia reports word timings.")))
								.binding(Binding.generic(false, () -> config.activeLineHighlight,
										value -> config.activeLineHighlight = value))
								.controller(option -> BooleanControllerBuilder.create(option).coloured(true).onOffFormatter())
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("Instrumental Countdown"))
								.description(OptionDescription.of(Component.literal(
										"Dots fill up during a long gap before the next line.")))
								.binding(Binding.generic(false, () -> config.showInstrumentalCountdown,
										value -> config.showInstrumentalCountdown = value))
								.controller(option -> BooleanControllerBuilder.create(option).coloured(true).onOffFormatter())
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("Next Line Preview"))
								.description(OptionDescription.of(Component.literal(
										"Shows the upcoming line underneath, dimmed.")))
								.binding(Binding.generic(false, () -> config.showNextLinePreview,
										value -> config.showNextLinePreview = value))
								.controller(option -> BooleanControllerBuilder.create(option).coloured(true).onOffFormatter())
								.build())
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Timing"))
						.option(Option.createBuilder()
								.name(Component.literal("Sync Offset"))
								.description(OptionDescription.of(Component.literal(
										"Nudge the lyrics earlier or later.")))
								.binding(Binding.generic(120L, () -> config.timeOffsetMs,
										value -> config.timeOffsetMs = value))
								.controller(option -> ((LongSliderControllerBuilder) ((LongSliderControllerBuilder)
										LongSliderControllerBuilder.create(option).range(-1000L, 1000L))
										.step(50L))
										.valueFormatter(value -> Component.literal(value + " ms")))
								.build())
						.build())
				.build();
	}

	private static ConfigCategory hudCategory(LyricsConfig config) {
		return ConfigCategory.createBuilder()
				.name(Component.literal("HUD (optional)"))
				.group(OptionGroup.createBuilder()
						.name(Component.literal("HUD"))
						.option(Option.createBuilder()
								.name(Component.literal("Show HUD Overlay"))
								.description(OptionDescription.of(Component.literal(
										"Flat karaoke overlay pinned to the HUD (off by default).")))
								.binding(Binding.generic(false, () -> config.showHud,
										value -> config.showHud = value))
								.controller(option -> BooleanControllerBuilder.create(option).coloured(true).onOffFormatter())
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("HUD Anchor"))
								.binding(Binding.generic("above_hotbar", () -> config.hudAnchor,
										value -> config.hudAnchor = value))
								.controller(option -> CyclingListControllerBuilder.create(option)
										.values((Object[]) HUD_ANCHORS)
										.valueFormatter(value -> Component.literal(prettify(value))))
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("HUD Scale"))
								.binding(Binding.generic(1.0f, () -> config.hudScale,
										value -> config.hudScale = value))
								.controller(option -> ((FloatSliderControllerBuilder) ((FloatSliderControllerBuilder)
										FloatSliderControllerBuilder.create(option).range(0.4f, 3.0f))
										.step(0.1f))
										.valueFormatter(value -> Component.literal(String.format("%.1fx", value))))
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("HUD Max Width"))
								.binding(Binding.generic(560, () -> config.hudMaxWidth,
										value -> config.hudMaxWidth = value))
								.controller(option -> ((IntegerSliderControllerBuilder) ((IntegerSliderControllerBuilder)
										IntegerSliderControllerBuilder.create(option).range(100, 2000))
										.step(20))
										.valueFormatter(value -> Component.literal(value + " px")))
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("HUD Background"))
								.binding(Binding.generic(true, () -> config.hudBackground,
										value -> config.hudBackground = value))
								.controller(option -> BooleanControllerBuilder.create(option).coloured(true).onOffFormatter())
								.build())
						.option(Option.createBuilder()
								.name(Component.literal("HUD Background Opacity"))
								.binding(Binding.generic(110, () -> config.hudBackgroundOpacity,
										value -> config.hudBackgroundOpacity = value))
								.controller(option -> ((IntegerSliderControllerBuilder) ((IntegerSliderControllerBuilder)
										IntegerSliderControllerBuilder.create(option).range(0, 255))
										.step(5))
										.valueFormatter(value -> Component.literal(value + " / 255")))
								.build())
						.build())
				.build();
	}

	private static MutableComponent accentLabel(LyricsConfig config, Object value) {
		String v = String.valueOf(value);
		String label = prettify(v);
		if ("RAINBOW".equals(v)) {
			MutableComponent out = Component.empty();
			for (int i = 0; i < label.length(); ++i) {
				int rgb = AccentColor.paletteColor(AccentColor.PALETTE[i % AccentColor.PALETTE.length]);
				out.append(Component.literal(String.valueOf(label.charAt(i)))
						.withStyle(style -> style.withColor(rgb)));
			}
			return out;
		}
		int rgb = switch (v) {
			case "ALBUM" -> 0xFFFFFF;
			case "CUSTOM" -> config.lyricsColorHex & 0xFFFFFF;
			default -> AccentColor.paletteColor(v);
		};
		return Component.literal(label).withStyle(style -> style.withColor(rgb));
	}

	private static String prettify(String constant) {
		String[] words = constant.toLowerCase().split("_");
		StringBuilder out = new StringBuilder();
		for (String word : words) {
			if (word.isEmpty()) {
				continue;
			}
			if (out.length() > 0) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return out.toString();
	}
}
