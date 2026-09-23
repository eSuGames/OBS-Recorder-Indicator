package com.obsindicator.client;

import com.obsindicator.client.config.ConfigScreenOpener;
import com.obsindicator.client.config.OneConfigSupport;
import com.obsindicator.config.ConfigManager;

/** Chat / client-command handlers for 1.8.9. */
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
		if (!ChatCommands.isCommand(message)) {
			return false;
		}
		String raw = ChatCommands.normalize(message).trim();
		String[] parts = raw.split("\\s+");
		String sub = parts.length > 1 ? parts[1].toLowerCase() : "";

		switch (sub) {
			case "", "config" -> ConfigScreenOpener.open();
			case "toggle" -> runToggle();
			case "status" -> runStatus();
			case "reconnect" -> runReconnect();
			case "textonly" -> runTextOnly();
			case "position" -> {
				if (parts.length >= 4) {
					try {
						runPosition(Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
					} catch (NumberFormatException e) {
						ObsRecIndicatorClient.feedback("Usage: /obsindicator position <x> <y> | reset");
					}
				} else if (parts.length == 3 && "reset".equalsIgnoreCase(parts[2])) {
					runPositionReset();
				} else {
					runPosition(null, null);
				}
			}
			default -> ObsRecIndicatorClient.feedback("Usage: /obsindicator [config|toggle|status|reconnect|textonly|position]");
		}
		return true;
	}

	public static int runToggle() {
		boolean enabled = !ConfigManager.get().enabled;
		ConfigManager.get().enabled = enabled;
		ConfigManager.save();
		ObsRecIndicatorClient.feedback("OBS indicator: " + (enabled ? "ON" : "OFF"));
		return 1;
	}

	public static int runStatus() {
		var obs = ObsRecIndicatorClient.obsClient();
		if (obs == null) {
			ObsRecIndicatorClient.feedback("OBS client not started");
			return 1;
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
		return 1;
	}

	public static int runReconnect() {
		var obs = ObsRecIndicatorClient.obsClient();
		if (obs != null) {
			obs.applyConnectionSettingsChanged();
		}
		ObsRecIndicatorClient.feedback("Reconnecting to OBS...");
		return 1;
	}

	public static int runTextOnly() {
		boolean textOnly = !ConfigManager.get().showCircle;
		ConfigManager.get().showCircle = !textOnly;
		ConfigManager.save();
		ObsRecIndicatorClient.feedback("Text-only indicator: " + (textOnly ? "ON" : "OFF"));
		return 1;
	}

	public static int runPosition(Integer x, Integer y) {
		if (x == null || y == null) {
			ObsRecIndicatorClient.feedback("Position X=" + ConfigManager.get().positionX
				+ " Y=" + ConfigManager.get().positionY
				+ "  (set via OneConfig X/Y or /obsindicator position <x> <y>)");
			return 1;
		}
		ConfigManager.get().positionX = x;
		ConfigManager.get().positionY = y;
		ConfigManager.save();
		ObsRecIndicatorClient.feedback("Position set to " + x + ", " + y);
		return 1;
	}

	public static int runPositionReset() {
		ConfigManager.get().resetPosition();
		ConfigManager.save();
		ObsRecIndicatorClient.feedback("Position reset");
		return 1;
	}
}
