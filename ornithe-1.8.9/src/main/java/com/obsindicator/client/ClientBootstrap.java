package com.obsindicator.client;

import com.obsindicator.client.config.ConfigScreenOpener;
import com.obsindicator.client.config.OneConfigSupport;
import com.obsindicator.config.ConfigManager;

/** Chat command handling for 1.8.9. Invoked from ClientPlayerEntityMixin. */
public final class ClientBootstrap {
	private ClientBootstrap() {
	}

	public static void init() {
	}

	/** @return true if the message was an /obsindicator command (handled). */
	public static boolean handleChat(String message) {
		if (message == null) {
			return false;
		}
		String raw = message.trim();
		if (!raw.startsWith("/obsindicator")) {
			return false;
		}
		String[] parts = raw.split("\\s+");
		String sub = parts.length > 1 ? parts[1].toLowerCase() : "";

		switch (sub) {
			case "", "config" -> ConfigScreenOpener.open();
			case "toggle" -> {
				boolean enabled = !ConfigManager.get().enabled;
				ConfigManager.get().enabled = enabled;
				ConfigManager.save();
				ObsRecIndicatorClient.feedback("OBS indicator: " + (enabled ? "ON" : "OFF"));
			}
			case "status" -> {
				var obs = ObsRecIndicatorClient.obsClient();
				if (obs == null) {
					ObsRecIndicatorClient.feedback("OBS client not started");
					return true;
				}
				ObsRecIndicatorClient.feedback("endpoint=" + obs.endpoint()
					+ " connected=" + obs.isConnected()
					+ " recordState=" + obs.currentRecordState()
					+ " enabled=" + ConfigManager.get().enabled
					+ " textOnly=" + (!ConfigManager.get().showCircle)
					+ " pos=" + ConfigManager.get().positionX + "," + ConfigManager.get().positionY);
				if (!obs.lastError().isEmpty()) {
					ObsRecIndicatorClient.feedback("lastError: " + obs.lastError());
				}
			}
			case "reconnect" -> {
				var obs = ObsRecIndicatorClient.obsClient();
				if (obs != null) {
					obs.applyConnectionSettingsChanged();
				}
				ObsRecIndicatorClient.feedback("Reconnecting to OBS...");
			}
			case "textonly" -> {
				if (OneConfigSupport.isPresent()) {
					return true;
				}
				boolean textOnly = !ConfigManager.get().showCircle;
				ConfigManager.get().showCircle = textOnly;
				ConfigManager.save();
				ObsRecIndicatorClient.feedback("Text-only indicator: " + (!textOnly ? "ON" : "OFF"));
			}
			case "position" -> {
				if (OneConfigSupport.isPresent()) {
					return true;
				}
				if (parts.length >= 4) {
					try {
						ConfigManager.get().positionX = Integer.parseInt(parts[2]);
						ConfigManager.get().positionY = Integer.parseInt(parts[3]);
						ConfigManager.save();
						ObsRecIndicatorClient.feedback("Position set to " + ConfigManager.get().positionX + ", " + ConfigManager.get().positionY);
					} catch (NumberFormatException e) {
						ObsRecIndicatorClient.feedback("Usage: /obsindicator position <x> <y>");
					}
				} else if (parts.length == 3 && "reset".equalsIgnoreCase(parts[2])) {
					ConfigManager.get().resetPosition();
					ConfigManager.save();
					ObsRecIndicatorClient.feedback("Position reset");
				} else {
					ObsRecIndicatorClient.feedback("Position X=" + ConfigManager.get().positionX + " Y=" + ConfigManager.get().positionY);
				}
			}
			default -> ObsRecIndicatorClient.feedback("Usage: /obsindicator [config|toggle|status|reconnect|textonly|position]");
		}
		return true;
	}
}
