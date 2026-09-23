package com.obsindicator.client.config;

import com.obsindicator.client.McReflect;
import com.obsindicator.client.ObsRecIndicatorClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
		if (OneConfigSupport.isPresent() && tryOpenOneConfigModPage()) {
			return;
		}
		try {
			Class<?> screenCl = Class.forName("com.obsindicator.client.config.BuiltinConfigScreen");
			Object parent = McReflect.currentScreen();
			Object screen = screenCl.getConstructor(Class.forName("net.minecraft.client.gui.screen.Screen"))
				.newInstance(parent);
			// fall back to any GuiScreen-typed ctor
			McReflect.openScreen(screen);
		} catch (Throwable t) {
			LOGGER.debug("builtin config screen unavailable: {}", t.toString());
			ObsRecIndicatorClient.feedback("Config UI unavailable (Ornithe remap). Use /obsindicator toggle|status|position");
		}
	}

	public static void openPositionEditor() {
		com.obsindicator.config.ConfigManager.get().resetPosition();
		com.obsindicator.config.ConfigManager.save();
		ObsRecIndicatorClient.feedback("Position reset");
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
