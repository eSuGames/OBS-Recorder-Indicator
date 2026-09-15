package com.obsindicator.client.config;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opens OneConfig mod page or the built-in config / position editor.
 * No OneConfig HUD integration.
 */
public final class ConfigScreenOpener {
	private static final Logger LOGGER = LoggerFactory.getLogger("obs_rec_indicator");
	private static final String MOD_CONFIG_ID = "obs-rec-indicator";

	private ConfigScreenOpener() {
	}

	public static boolean isOneConfigLoaded() {
		return OneConfigSupport.isPresent();
	}

	/** /obsindicator config */
	public static void open() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) {
			return;
		}
		minecraft.execute(() -> {
			OneConfigSupport.registerIfPresent();
			if (OneConfigSupport.isPresent() && tryOpenOneConfigModPage()) {
				return;
			}
			try {
				minecraft.gui.setScreen(new BuiltinConfigScreen(minecraft.gui.screen()));
			} catch (Throwable t) {
				LOGGER.error("Failed to open builtin config screen", t);
			}
		});
	}

	/** Position-only editor (also used from OneConfig's Position editor button). */
	public static void openPositionEditor() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) {
			return;
		}
		minecraft.execute(() -> {
			try {
				minecraft.gui.setScreen(new PositionEditorScreen(minecraft.gui.screen()));
			} catch (Throwable t) {
				LOGGER.error("Failed to open position editor", t);
			}
		});
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
