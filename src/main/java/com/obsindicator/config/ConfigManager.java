package com.obsindicator.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Loads/saves {@code config/obs_rec_indicator.json}.
 * Thread-safe enough for the connection thread to read connection settings.
 */
public final class ConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("obs_rec_indicator");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private static final ModConfig CONFIG = new ModConfig();
	private static final CopyOnWriteArrayList<Runnable> SAVE_LISTENERS = new CopyOnWriteArrayList<>();
	private static Path configPath;

	private ConfigManager() {
	}

	public static Path configPath() {
		if (configPath == null) {
			configPath = FabricLoader.getInstance().getConfigDir().resolve("obs_rec_indicator.json");
		}
		return configPath;
	}

	public static ModConfig get() {
		return CONFIG;
	}

	public static void load() {
		Path path = configPath();
		if (!Files.exists(path)) {
			save();
			return;
		}
		try (Reader reader = Files.newBufferedReader(path)) {
			ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
			if (loaded != null) {
				CONFIG.applyFrom(loaded);
				normalize();
			}
		} catch (Exception e) {
			LOGGER.error("Failed to load config, keeping defaults", e);
		}
	}

	public static void save() {
		normalize();
		Path path = configPath();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(CONFIG, writer);
			}
		} catch (IOException e) {
			LOGGER.error("Failed to save config", e);
		}
		for (Runnable listener : SAVE_LISTENERS) {
			try {
				listener.run();
			} catch (Exception e) {
				LOGGER.warn("Config save listener failed", e);
			}
		}
	}

	public static void addSaveListener(Runnable listener) {
		if (listener != null) {
			SAVE_LISTENERS.add(listener);
		}
	}

	private static void normalize() {
		if (CONFIG.scale < 0.25f) {
			CONFIG.scale = 0.25f;
		}
		if (CONFIG.scale > 4.0f) {
			CONFIG.scale = 4.0f;
		}
		if (CONFIG.circleRadius < 1.0f) {
			CONFIG.circleRadius = 1.0f;
		}
		if (CONFIG.circleRadius > 24.0f) {
			CONFIG.circleRadius = 24.0f;
		}
		if (CONFIG.obsPort < 1 || CONFIG.obsPort > 65535) {
			CONFIG.obsPort = 4455;
		}
		if (CONFIG.reconnectIntervalSeconds < 1) {
			CONFIG.reconnectIntervalSeconds = 1;
		}
		if (CONFIG.reconnectIntervalSeconds > 300) {
			CONFIG.reconnectIntervalSeconds = 300;
		}
		if (CONFIG.obsHost == null || CONFIG.obsHost.isBlank()) {
			CONFIG.obsHost = "127.0.0.1";
		}
		if (CONFIG.recordingText == null) {
			CONFIG.recordingText = "REC";
		}
		if (CONFIG.pausedText == null) {
			CONFIG.pausedText = "PAUSED";
		}
		// Clamp center-relative position to a sane range.
		if (CONFIG.positionX < -2000) {
			CONFIG.positionX = -2000;
		}
		if (CONFIG.positionX > 2000) {
			CONFIG.positionX = 2000;
		}
		if (CONFIG.positionY < -2000) {
			CONFIG.positionY = -2000;
		}
		if (CONFIG.positionY > 2000) {
			CONFIG.positionY = 2000;
		}
	}
}
