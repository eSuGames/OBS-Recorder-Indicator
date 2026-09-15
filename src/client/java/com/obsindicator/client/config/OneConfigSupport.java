package com.obsindicator.client.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Isolates OneConfig class-loading. Config UI only — no HUD registration.
 */
public final class OneConfigSupport {
	private static final Logger LOGGER = LoggerFactory.getLogger("obs_rec_indicator");
	private static final String ONECONFIG_MOD_ID = "oneconfig";

	private static Boolean present;
	private static boolean configRegistered;

	private OneConfigSupport() {
	}

	public static boolean isPresent() {
		if (present == null) {
			try {
				present = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(ONECONFIG_MOD_ID);
			} catch (Throwable t) {
				present = false;
			}
		}
		return present;
	}

	public static void registerIfPresent() {
		if (!isPresent() || configRegistered) {
			return;
		}
		try {
			Class<?> clazz = Class.forName("com.obsindicator.client.config.ObsIndicatorOneConfig");
			clazz.getMethod("registerIfPresent").invoke(null);
			configRegistered = true;
		} catch (Throwable t) {
			LOGGER.debug("OneConfig config register skipped: {}", t.toString());
		}
	}

	public static void syncFromOneConfigIfChanged() {
		if (!isPresent() || !configRegistered) {
			return;
		}
		try {
			Class<?> clazz = Class.forName("com.obsindicator.client.config.ObsIndicatorOneConfig");
			clazz.getMethod("syncFromOneConfigIfChanged").invoke(null);
		} catch (Throwable ignored) {
		}
	}
}
