package com.obsindicator.client.config;

import com.obsindicator.config.ConfigManager;
import com.obsindicator.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Button;
import org.polyfrost.oneconfig.api.config.v1.annotations.Number;
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;
import org.polyfrost.oneconfig.api.config.v1.annotations.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Optional OneConfig 1.8.9 config (compileOnly). */
public final class ObsIndicatorOneConfig extends Config {
	private static final Logger LOGGER = LoggerFactory.getLogger("obs_rec_indicator");

	@Switch(title = "Indicator enabled", category = "Display")
	public boolean enabled = true;

	@Switch(title = "Text only (no circle)", category = "Display")
	public boolean textOnly = false;

	@Switch(title = "Show text", category = "Display")
	public boolean showText = true;

	@Switch(title = "Background", category = "Display")
	public boolean showBackground = true;

	@Switch(title = "Text shadow", category = "Display")
	public boolean showShadow = true;

	@Text(title = "Recording text", category = "Text")
	public String recordingText = "REC";

	@Text(title = "Paused text", category = "Text")
	public String pausedText = "PAUSED";

	@Text(title = "Recording color (hex)", category = "Text", placeholder = "#E53935")
	public String recordingColor = "#E53935";

	@Number(title = "X", min = -800, max = 800, category = "Position")
	public int positionX = ModConfig.DEFAULT_POS_X;

	@Number(title = "Y", min = -800, max = 800, category = "Position")
	public int positionY = ModConfig.DEFAULT_POS_Y;

	@Slider(title = "Indicator size", min = 0.25f, max = 4.0f, step = 0.05f, category = "Position")
	public float scale = 1.0f;

	@Button(title = "Position editor", text = "Open", category = "Position")
	public void openPositionEditor() {
		applyToRuntime();
		net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
		if (client != null) {
			client.setScreen(new PositionEditorScreen(client.currentScreen));
		}
	}

	@Text(title = "OBS host", category = "OBS")
	public String obsHost = "127.0.0.1";

	@Number(title = "OBS port", min = 1, max = 65535, category = "OBS")
	public int obsPort = 4455;

	@Text(title = "OBS password", category = "OBS")
	public String obsPassword = "";

	@Switch(title = "Use TLS (wss)", category = "OBS")
	public boolean obsUseTls = false;

	@Number(title = "Reconnect interval (s)", min = 1, max = 60, category = "OBS")
	public int reconnectIntervalSeconds = 5;

	@Button(title = "Reconnect OBS", text = "Reconnect", category = "OBS")
	public void reconnectObs() {
		applyToRuntime();
		var obs = com.obsindicator.client.ObsRecIndicatorClient.obsClient();
		if (obs != null) {
			obs.applyConnectionSettingsChanged();
		}
	}

	private static ObsIndicatorOneConfig instance;
	private int lastHash = 0;
	private static boolean pendingReloadFromRuntime;

	public ObsIndicatorOneConfig() {
		super("obs-rec-indicator", "OBS Rec Indicator", Category.UTILITY);
		loadFromRuntime();
	}

	public static void registerIfPresent() {
		if (!FabricLoader.getInstance().isModLoaded("oneconfig")) {
			return;
		}
		if (instance == null) {
			instance = new ObsIndicatorOneConfig();
			try {
				org.polyfrost.oneconfig.api.config.v1.ConfigManager.submitForInitialization(instance);
			} catch (Throwable t) {
				LOGGER.debug("submitForInitialization: {}", t.toString());
			}
			LOGGER.info("OneConfig config registered for OBS Rec Indicator");
			pendingReloadFromRuntime = true;
		}
	}

	public static ObsIndicatorOneConfig instance() {
		return instance;
	}

	public static void syncFromOneConfigIfChanged() {
		ObsIndicatorOneConfig cfg = instance;
		if (cfg == null) {
			return;
		}
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
		int h = Boolean.hashCode(enabled);
		h = 31 * h + Boolean.hashCode(textOnly);
		h = 31 * h + Boolean.hashCode(showText);
		h = 31 * h + Boolean.hashCode(showBackground);
		h = 31 * h + Boolean.hashCode(showShadow);
		h = 31 * h + Float.floatToIntBits(scale);
		h = 31 * h + (recordingText == null ? 0 : recordingText.hashCode());
		h = 31 * h + (pausedText == null ? 0 : pausedText.hashCode());
		h = 31 * h + (recordingColor == null ? 0 : recordingColor.hashCode());
		h = 31 * h + positionX;
		h = 31 * h + positionY;
		h = 31 * h + (obsHost == null ? 0 : obsHost.hashCode());
		h = 31 * h + obsPort;
		h = 31 * h + (obsPassword == null ? 0 : obsPassword.hashCode());
		h = 31 * h + Boolean.hashCode(obsUseTls);
		h = 31 * h + reconnectIntervalSeconds;
		return h;
	}

	public void loadFromRuntime() {
		ModConfig src = ConfigManager.get();
		enabled = src.enabled;
		textOnly = !src.showCircle;
		showText = src.showText;
		showBackground = src.showBackground;
		showShadow = src.showShadow;
		scale = src.scale;
		recordingText = src.recordingText;
		pausedText = src.pausedText;
		recordingColor = src.recordingColor;
		positionX = src.positionX;
		positionY = src.positionY;
		obsHost = src.obsHost;
		obsPort = src.obsPort;
		obsPassword = src.obsPassword;
		obsUseTls = src.obsUseTls;
		reconnectIntervalSeconds = src.reconnectIntervalSeconds;
		lastHash = valueHash();
	}

	public void applyToRuntime() {
		ModConfig dst = ConfigManager.get();
		dst.enabled = enabled;
		dst.showCircle = !textOnly;
		dst.showText = showText;
		dst.showBackground = showBackground;
		dst.showShadow = showShadow;
		dst.scale = scale;
		dst.recordingText = recordingText == null || recordingText.isBlank() ? "REC" : recordingText;
		dst.pausedText = pausedText == null || pausedText.isBlank() ? "PAUSED" : pausedText;
		dst.recordingColor = recordingColor;
		dst.positionX = positionX;
		dst.positionY = positionY;
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

