package com.obsindicator.client;

import com.obsindicator.client.config.OneConfigSupport;
import com.obsindicator.config.ConfigManager;
import com.obsindicator.config.ModConfig;
import com.obsindicator.obs.ObsWebSocketClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-only bootstrap. Never references OneConfig types.
 */
public final class ObsRecIndicatorClient implements ClientModInitializer {
	public static final String MOD_ID = "obs_rec_indicator";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static ObsWebSocketClient obsClient;

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

		ClientBootstrap.init();

		if (OneConfigSupport.isPresent()) {
			OneConfigSupport.registerIfPresent();
		}

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (OneConfigSupport.isPresent()) {
				OneConfigSupport.registerIfPresent();
				OneConfigSupport.syncFromOneConfigIfChanged();
			}
		});

		LOGGER.info("OBS Rec Indicator initialized (OBS {}:{})",
			ConfigManager.get().obsHost, ConfigManager.get().obsPort);
	}

	private static String connectionKey(ModConfig c) {
		return c.obsHost + "|" + c.obsPort + "|" + c.obsPassword + "|" + c.obsUseTls
			+ "|" + c.reconnectIntervalSeconds + "|" + c.connectTimeoutMs;
	}

	public static ObsWebSocketClient obsClient() {
		return obsClient;
	}
}
