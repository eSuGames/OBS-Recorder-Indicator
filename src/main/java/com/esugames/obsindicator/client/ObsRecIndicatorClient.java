package com.esugames.obsindicator.client;

import com.esugames.obsindicator.client.config.OneConfigSupport;
import com.esugames.obsindicator.client.hud.RecordingHudOverlay;
import com.esugames.obsindicator.config.ConfigManager;
import com.esugames.obsindicator.config.ModConfig;
import com.esugames.obsindicator.obs.ObsWebSocketClient;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.text.LiteralText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-only bootstrap. Never references OneConfig types.
 */
public final class ObsRecIndicatorClient implements ClientModInitializer {
	public static final String MOD_ID = "obs_rec_indicator";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	/** OneConfig bookkeeping is cheap, but there is no reason to do it more than ~10x per second. */
	private static final long ONECONFIG_SYNC_INTERVAL_MS = 100L;

	private static ObsWebSocketClient obsClient;
	private static long lastOneConfigSyncMs;

	@Override
	public void onInitializeClient() {
		ConfigManager.load();
		obsClient = new ObsWebSocketClient();
		obsClient.start();

		// Reconnect OBS only when connection settings actually change.
		final String[] lastConn = {connectionKey(ConfigManager.get())};
		ConfigManager.addSaveListener(() -> {
			String key = connectionKey(ConfigManager.get());
			if (key.equals(lastConn[0])) {
				return;
			}
			lastConn[0] = key;
			ObsWebSocketClient client = obsClient;
			if (client != null) {
				client.applyConnectionSettingsChanged();
			}
		});

		RecordingHudOverlay.init();

		if (OneConfigSupport.isPresent()) {
			OneConfigSupport.registerIfPresent();
			OneConfigSupport.registerHudIfPresent();
		}

		LOGGER.info("OBS Recorder Indicator initialized (OBS {}:{})",
			ConfigManager.get().obsHost, ConfigManager.get().obsPort);
	}

	/**
	 * Per-frame hook from the vanilla HUD mixin: keeps OneConfig's registration and config sync
	 * running without a fabric-api tick event on 1.8.9.
	 */
	public static void onFrame() {
		if (!OneConfigSupport.isPresent()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastOneConfigSyncMs < ONECONFIG_SYNC_INTERVAL_MS) {
			return;
		}
		lastOneConfigSyncMs = now;
		OneConfigSupport.registerIfPresent();
		OneConfigSupport.registerHudIfPresent();
		OneConfigSupport.syncFromOneConfigIfChanged();
	}

	private static String connectionKey(ModConfig c) {
		return c.obsHost + "|" + c.obsPort + "|" + c.obsPassword + "|" + c.obsUseTls
			+ "|" + c.reconnectIntervalSeconds + "|" + c.connectTimeoutMs;
	}

	public static ObsWebSocketClient obsClient() {
		return obsClient;
	}

	/** Prints a status line to the in-game chat. */
	public static void feedback(String msg) {
		try {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft != null && minecraft.gui != null && minecraft.gui.getChat() != null) {
				minecraft.gui.getChat().addMessage(new LiteralText(msg));
				return;
			}
		} catch (Throwable ignored) {
		}
		LOGGER.info("[feedback] {}", msg);
	}
}
