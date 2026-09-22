package com.obsindicator.client;

import com.obsindicator.client.config.ConfigScreenOpener;
import com.obsindicator.client.config.OneConfigSupport;
import com.obsindicator.client.hud.RecordingHudOverlay;
import com.obsindicator.config.ConfigManager;
import com.obsindicator.obs.ObsWebSocketClient;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.LiteralText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ObsRecIndicatorClient implements ClientModInitializer {
	public static final String MOD_ID = "obs_rec_indicator";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static ObsWebSocketClient obsClient;

	@Override
	public void onInitializeClient() {
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

		LOGGER.info("OBS Rec Indicator initialized (OBS {}:{})",
			ConfigManager.get().obsHost, ConfigManager.get().obsPort);
	}

	private static String connectionKey(com.obsindicator.config.ModConfig c) {
		return c.obsHost + "|" + c.obsPort + "|" + c.obsPassword + "|" + c.obsUseTls
			+ "|" + c.reconnectIntervalSeconds + "|" + c.connectTimeoutMs;
	}

	public static ObsWebSocketClient obsClient() {
		return obsClient;
	}

	public static void feedback(String msg) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(new LiteralText(msg));
		}
	}
}
