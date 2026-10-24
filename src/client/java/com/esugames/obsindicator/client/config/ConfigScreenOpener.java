package com.esugames.obsindicator.client.config;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opens OneConfig's mod page or the built-in config screen.
 *
 * <p>The built-in screen is the only editor when OneConfig is not installed, and it stays as the
 * fallback when OneConfig is installed but its page cannot be opened.</p>
 */
public final class ConfigScreenOpener {
	private static final Logger LOGGER = LoggerFactory.getLogger("obs_rec_indicator");
	private static final String MOD_CONFIG_ID = "obs_rec_indicator";

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
			OneConfigSupport.registerHudIfPresent();
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
