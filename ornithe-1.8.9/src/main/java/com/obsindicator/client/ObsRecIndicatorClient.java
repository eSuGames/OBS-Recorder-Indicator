package com.obsindicator.client;

import com.obsindicator.client.config.OneConfigSupport;
import com.obsindicator.client.hud.RecordingHudOverlay;
import com.obsindicator.config.ConfigManager;
import com.obsindicator.obs.ObsWebSocketClient;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ObsRecIndicatorClient implements ClientModInitializer {
	public static final String MOD_ID = "obs_rec_indicator";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static ObsWebSocketClient obsClient;

	@Override
	public void onInitializeClient() {
		System.out.println("[obs_rec_indicator] init start");
		try {
			ConfigManager.load();
			obsClient = new ObsWebSocketClient();
			obsClient.start();

			final String[] lastConn = {connectionKey(ConfigManager.get())};
			ConfigManager.addSaveListener(() -> {
				String key = connectionKey(ConfigManager.get());
				if (key.equals(lastConn[0])) {
					return;
				}
				lastConn[0] = key;
				if (obsClient != null) {
					obsClient.applyConnectionSettingsChanged();
				}
			});

			RecordingHudOverlay.init();
			ClientBootstrap.init();
			OneConfigSupport.registerIfPresent();

			LOGGER.info("OBS Rec Indicator initialized (OBS {}:{}) mcReflect={}",
				ConfigManager.get().obsHost, ConfigManager.get().obsPort, McReflect.available());
			System.out.println("[obs_rec_indicator] init done mcReflect=" + McReflect.available());
		} catch (Throwable t) {
			System.out.println("[obs_rec_indicator] init failed: " + t);
			t.printStackTrace();
			throw t instanceof RuntimeException re ? re : new RuntimeException(t);
		}
	}

	private static String connectionKey(com.obsindicator.config.ModConfig c) {
		return c.obsHost + "|" + c.obsPort + "|" + c.obsPassword + "|" + c.obsUseTls
			+ "|" + c.reconnectIntervalSeconds + "|" + c.connectTimeoutMs;
	}

	public static ObsWebSocketClient obsClient() {
		return obsClient;
	}

	public static void feedback(String msg) {
		try {
			McReflect.sendFeedback(msg);
		} catch (Throwable ignored) {
		}
		LOGGER.info("[feedback] {}", msg);
	}
}
