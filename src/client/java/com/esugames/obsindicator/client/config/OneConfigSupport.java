package com.esugames.obsindicator.client.config;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
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
	private static boolean hudDrawingFailed;
	private static java.lang.reflect.Method refreshEditorLabels;
	private static boolean refreshEditorLabelsFailed;
	/**
	 * One of the tab titles the HUD editor caches. Both the language instance and this title are
	 * watched, so the change is noticed however Minecraft reloads its language files.
	 */
	private static final String WATCHED_TITLE = "obs_rec_indicator.oneconfig.hud.category";
	/** Language resource set the HUD editor's tab titles were last built from. */
	private static Object lastLanguage;
	/** Tab title of {@link #WATCHED_TITLE} in that language. */
	private static String lastTitle;

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
		return hudRegistered && !hudDrawingFailed;
	}

	/**
	 * Marks OneConfig's HUD drawing as unusable.
	 *
	 * <p>Called by the HUD when it cannot attach its drawing node. The indicator then falls back to
	 * the mod's own Fabric HUD — the position comes from the mod's config again, but the indicator
	 * stays visible instead of silently disappearing.</p>
	 */
	public static void markHudDrawingFailed() {
		hudDrawingFailed = true;
	}

	/**
	 * Rebuilds the HUD editor's settings panel after Minecraft's language changed.
	 *
	 * <p>OneConfig translates the tab titles of a HUD's settings page once, when that page is built,
	 * and keeps them for as long as it stays composed — so switching the language in-game used to
	 * leave the tabs ("Display"/"Text") in the old language until the game was restarted. The panel
	 * is thrown away and built again here instead, which is what makes the new language show up
	 * without a restart.</p>
	 *
	 * <p>Called once per client tick and costs one reference comparison until the language actually
	 * changes.</p>
	 */
	public static void tickHudEditorLanguage() {
		if (!isPresent() || !hudRegistered || refreshEditorLabelsFailed) {
			return;
		}
		try {
			Object language = Language.getInstance();
			String title = I18n.get(WATCHED_TITLE);
			if (language == lastLanguage && title.equals(lastTitle)) {
				return;
			}
			// The first tick also rebuilds: OneConfig may have warmed the settings panel up
			// before Minecraft had its language files, which would leave raw keys on the tabs.
			lastLanguage = language;
			lastTitle = title;
			if (refreshEditorLabels == null) {
				Class<?> clazz = Class.forName(
					"com.esugames.obsindicator.client.config.RecordingIndicatorHud");
				refreshEditorLabels = clazz.getMethod("refreshEditorLabels");
			}
			refreshEditorLabels.invoke(null);
		} catch (Throwable t) {
			refreshEditorLabelsFailed = true;
			LOGGER.debug("HUD editor tab titles are not refreshed on language changes: {}", t.toString());
		}
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
