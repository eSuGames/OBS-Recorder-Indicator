package com.esugames.obsindicator.client.config;

import com.esugames.obsindicator.config.ConfigManager;
import com.esugames.obsindicator.config.ModConfig;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Button;
import org.polyfrost.oneconfig.api.config.v1.annotations.Number;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;
import org.polyfrost.oneconfig.api.config.v1.annotations.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional OneConfig config with annotated options.
 *
 * <p>Only the OBS connection lives here: it is one connection for the whole mod, so it is shared
 * rather than per indicator. Everything that describes the indicator itself (background, shadow,
 * circle, text, colours, position) belongs to the OneConfig HUD
 * ({@link RecordingIndicatorHud}) and is edited per indicator in OneConfig's HUD editor.</p>
 */
public final class ObsIndicatorOneConfig extends Config {
	private static final Logger LOGGER = LoggerFactory.getLogger("obs_rec_indicator");

	/** OBS tab. The value is a translation key, OneConfig translates it when it exists. */
	private static final String CATEGORY_OBS = "obs_rec_indicator.oneconfig.obs.category";

	@Text(title = "obs_rec_indicator.oneconfig.obs.host", category = CATEGORY_OBS)
	public String obsHost = "127.0.0.1";

	@Number(title = "obs_rec_indicator.oneconfig.obs.port", min = 1, max = 65535, category = CATEGORY_OBS)
	public int obsPort = 4455;

	@Text(title = "obs_rec_indicator.oneconfig.obs.password", category = CATEGORY_OBS)
	public String obsPassword = "";

	@Switch(title = "obs_rec_indicator.oneconfig.obs.tls", category = CATEGORY_OBS)
	public boolean obsUseTls = false;

	@Number(
		title = "obs_rec_indicator.oneconfig.obs.reconnect_interval",
		min = 1,
		max = 60,
		category = CATEGORY_OBS
	)
	public int reconnectIntervalSeconds = 5;

	@Button(
		title = "obs_rec_indicator.oneconfig.obs.reconnect",
		text = "obs_rec_indicator.oneconfig.obs.reconnect.text",
		description = "obs_rec_indicator.oneconfig.obs.reconnect.desc",
		category = CATEGORY_OBS
	)
	public void reconnectObs() {
		applyToRuntime();
		var obs = com.esugames.obsindicator.client.ObsRecIndicatorClient.obsClient();
		if (obs != null) {
			obs.applyConnectionSettingsChanged();
		}
	}

	private static ObsIndicatorOneConfig instance;
	private int lastHash = 0;
	private static boolean pendingReloadFromRuntime;

	public ObsIndicatorOneConfig() {
		super("obs_rec_indicator", "OBS Recorder Indicator", Category.UTILITY);
		loadFromRuntime();
	}

	public static void registerIfPresent() {
		if (!OneConfigSupport.isPresent()) {
			return;
		}
		if (instance == null) {
			instance = new ObsIndicatorOneConfig();
			try {
				org.polyfrost.oneconfig.api.config.v1.ConfigManager.submitForInitialization(instance);
			} catch (Throwable t) {
				LOGGER.debug("submitForInitialization: {}", t.toString());
			}
			LOGGER.info("OneConfig config registered for OBS Recorder Indicator");
			pendingReloadFromRuntime = true;
		}
	}

	public static ObsIndicatorOneConfig instance() {
		return instance;
	}

	/** Re-reads the values from {@link ModConfig}. */
	public static void reloadFromRuntime() {
		ObsIndicatorOneConfig cfg = instance;
		if (cfg != null) {
			cfg.loadFromRuntime();
		}
	}

	public static void syncFromOneConfigIfChanged() {
		ObsIndicatorOneConfig cfg = instance;
		if (cfg == null) {
			return;
		}
		// After OneConfig may have loaded its own file, re-apply our JSON as source of truth once.
		if (pendingReloadFromRuntime) {
			pendingReloadFromRuntime = false;
			cfg.loadFromRuntime();
			return;
		}
		int hash = cfg.valueHash();
		if (hash == cfg.lastHash) {
			return;
		}
		cfg.lastHash = hash;
		cfg.applyToRuntime();
	}

	private int valueHash() {
		int h = obsHost == null ? 0 : obsHost.hashCode();
		h = 31 * h + obsPort;
		h = 31 * h + (obsPassword == null ? 0 : obsPassword.hashCode());
		h = 31 * h + Boolean.hashCode(obsUseTls);
		h = 31 * h + reconnectIntervalSeconds;
		return h;
	}

	public void loadFromRuntime() {
		ModConfig src = ConfigManager.get();
		obsHost = src.obsHost;
		obsPort = src.obsPort;
		obsPassword = src.obsPassword;
		obsUseTls = src.obsUseTls;
		reconnectIntervalSeconds = src.reconnectIntervalSeconds;
		lastHash = valueHash();
	}

	public void applyToRuntime() {
		ModConfig dst = ConfigManager.get();
		dst.obsHost = obsHost;
		dst.obsPort = obsPort;
		dst.obsPassword = obsPassword == null ? "" : obsPassword;
		dst.obsUseTls = obsUseTls;
		dst.reconnectIntervalSeconds = reconnectIntervalSeconds;
		ConfigManager.save();
	}

	@Override
	public void save() {
		super.save();
		applyToRuntime();
	}
}
