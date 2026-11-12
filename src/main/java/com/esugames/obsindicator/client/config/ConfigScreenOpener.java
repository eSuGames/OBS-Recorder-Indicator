package com.esugames.obsindicator.client.config;

import com.esugames.obsindicator.client.ObsRecIndicatorClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opens the mod's settings page in OneConfig's GUI.
 *
 * <p>1.8.9 has no built-in config screen of its own: OneConfig is required here (see
 * fabric.mod.json), so this only routes to OneConfig's mod page. Everything else — the indicator's
 * font, shadow, background and position — is edited in OneConfig's HUD editor.</p>
 */
public final class ConfigScreenOpener {
	private static final Logger LOGGER = LoggerFactory.getLogger("obs_rec_indicator");
	private static final String MOD_CONFIG_ID = "obs_rec_indicator";

	private ConfigScreenOpener() {
	}

	public static boolean isOneConfigLoaded() {
		return OneConfigSupport.isPresent();
	}

	public static void open() {
		OneConfigSupport.registerIfPresent();
		OneConfigSupport.registerHudIfPresent();
		if (OneConfigSupport.isPresent() && tryOpenOneConfigModPage()) {
			return;
		}
		LOGGER.debug("OneConfig config UI unavailable");
		ObsRecIndicatorClient.feedback(
			"OneConfig is required on 1.8.9. Install it, or use /obsindicator toggle|status|position");
	}

	private static boolean tryOpenOneConfigModPage() {
		try {
			Class<?> routeClass = Class.forName(
				"org.polyfrost.oneconfig.internal.ui.navigation.graph.ModConfigRoute");
			var ctor = routeClass.getConstructor(String.class, String.class);
			for (String category : new String[]{"Utility", "UTILITY", "utility", "Other", "OTHER", "QOL"}) {
				try {
					Object route = ctor.newInstance(MOD_CONFIG_ID, category);
					if (openOneConfig(route)) {
						return true;
					}
				} catch (Throwable ignored) {
				}
			}
			return openOneConfig(null);
		} catch (Throwable t) {
			LOGGER.debug("ModConfigRoute open failed: {}", t.toString());
			return openOneConfig(null);
		}
	}

	private static boolean openOneConfig(Object route) {
		try {
			Class<?> ui = Class.forName("org.polyfrost.oneconfig.api.ui.v1.OneConfigUI");
			if (route != null) {
				ui.getMethod("open", Object.class).invoke(null, route);
				return true;
			}
			ui.getMethod("open").invoke(null);
			return true;
		} catch (Throwable t) {
			LOGGER.debug("OneConfigUI.open failed: {}", t.toString());
			return false;
		}
	}
}
