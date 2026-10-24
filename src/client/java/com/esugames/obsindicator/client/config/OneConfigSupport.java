package com.esugames.obsindicator.client.config;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Isolates OneConfig class-loading.
 *
 * <p>Everything that touches a OneConfig type lives in classes that are only ever loaded through
 * this class, so the mod keeps working — unchanged — when OneConfig is not installed.</p>
 */
public final class OneConfigSupport {
	private static final Logger LOGGER = LoggerFactory.getLogger("obs_rec_indicator");
	/** The OneConfig loader mod. */
	private static final String ONECONFIG_MOD_ID = "oneconfig";
	/** The platform module, which is what actually ships the API classes. */
	private static final String ONECONFIG_PLATFORM_MOD_ID = "oneconfigv1";

	private static Boolean present;
	private static boolean configRegistered;
	private static boolean hudRegistered;

	private OneConfigSupport() {
	}

	public static boolean isPresent() {
		if (present == null) {
			try {
				FabricLoader loader = FabricLoader.getInstance();
				present = loader.isModLoaded(ONECONFIG_MOD_ID)
					|| loader.isModLoaded(ONECONFIG_PLATFORM_MOD_ID);
			} catch (Throwable t) {
				present = false;
			}
		}
		return present;
	}

	/** Registers the mod's page in OneConfig's standard config GUI. */
	public static void registerIfPresent() {
		if (!isPresent() || configRegistered) {
			return;
		}
		try {
			Class<?> clazz = Class.forName("com.esugames.obsindicator.client.config.ObsIndicatorOneConfig");
			clazz.getMethod("registerIfPresent").invoke(null);
			configRegistered = true;
		} catch (Throwable t) {
			LOGGER.debug("OneConfig config register skipped: {}", t.toString());
		}
	}

	/**
	 * Registers the recording indicator as a OneConfig HUD.
	 *
	 * <p>That is what moves the position onto OneConfig's own UI: the indicator can be dragged in
	 * the OneConfig HUD editor and its Background / Shadow / Circle / Text switches live in the same
	 * settings page.</p>
	 */
	public static void registerHudIfPresent() {
		if (!isPresent() || hudRegistered) {
			return;
		}
		try {
			Class<?> clazz = Class.forName("com.esugames.obsindicator.client.config.RecordingIndicatorHud");
			Object result = clazz.getMethod("register").invoke(null);
			hudRegistered = Boolean.TRUE.equals(result);
		} catch (Throwable t) {
			LOGGER.debug("OneConfig HUD register skipped: {}", t.toString());
		}
	}

	/**
	 * Whether OneConfig draws the indicator itself.
	 *
	 * <p>When this is {@code false} the mod falls back to its own Fabric HUD, exactly like it does
	 * without OneConfig. Reached directly, without touching a OneConfig type, so the fallback path
	 * stays byte-for-byte the pre-OneConfig behaviour.</p>
	 *
	 * <p>Stays {@code true} once the HUD is registered, even when no instance is left: deleting
	 * every indicator from OneConfig must keep it deleted instead of handing drawing back to the
	 * fallback HUD.</p>
	 */
	public static boolean isHudRegistered() {
		return hudRegistered;
	}

	public static void syncFromOneConfigIfChanged() {
		if (!isPresent() || !configRegistered) {
			return;
		}
		try {
			Class<?> clazz = Class.forName("com.esugames.obsindicator.client.config.ObsIndicatorOneConfig");
			clazz.getMethod("syncFromOneConfigIfChanged").invoke(null);
		} catch (Throwable ignored) {
		}
	}
}
