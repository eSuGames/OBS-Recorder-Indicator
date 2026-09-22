package com.obsindicator.config;

/**
 * Mutable runtime configuration.
 * Position is screen-center relative: (0,0) is the center of the GUI.
 * +X = right, +Y = down. Default places the indicator near the top-left.
 */
public final class ModConfig {
	/** Center-relative X. Default ≈ top-left of a typical GUI. */
	public int positionX = DEFAULT_POS_X;
	/** Center-relative Y. Default ≈ top-left of a typical GUI. */
	public int positionY = DEFAULT_POS_Y;

	public boolean enabled = true;
	public float scale = 1.0f;
	public boolean showText = true;
	public boolean showCircle = true;
	public boolean showBackground = true;
	public boolean showShadow = true;
	public String recordingText = "REC";
	public String pausedText = "PAUSED";
	public String recordingColor = "#E53935";
	public String pausedColor = "#FB8C00";
	public float circleRadius = 5.0f;
	public boolean showTimecode = false;

	public String obsHost = "127.0.0.1";
	public int obsPort = 4455;
	public String obsPassword = "";
	public boolean obsUseTls = false;
	public int reconnectIntervalSeconds = 5;
	public int connectTimeoutMs = 4000;

	/** Default near top-left of a ~640x360 GUI. */
	public static final int DEFAULT_POS_X = -280;
	public static final int DEFAULT_POS_Y = -160;

	public int recordingColorArgb() {
		return parseColor(recordingColor, 0xFFE53935);
	}

	public int pausedColorArgb() {
		return parseColor(pausedColor, 0xFFFB8C00);
	}

	public void resetPosition() {
		positionX = DEFAULT_POS_X;
		positionY = DEFAULT_POS_Y;
	}

	/** Left edge of the indicator: screen-center origin + positionX. */
	public int originX(int screenWidth) {
		return screenWidth / 2 + positionX;
	}

	/** Vertical center of the indicator: screen-center origin + positionY. */
	public int originY(int screenHeight, int contentHeight) {
		return screenHeight / 2 + positionY - contentHeight / 2;
	}

	public static int parseColor(String hex, int fallback) {
		if (hex == null || hex.isBlank()) {
			return fallback;
		}
		String s = hex.trim();
		if (s.startsWith("#")) {
			s = s.substring(1);
		}
		try {
			if (s.length() == 6) {
				return 0xFF000000 | Integer.parseInt(s, 16);
			}
			if (s.length() == 8) {
				return (int) Long.parseLong(s, 16);
			}
		} catch (NumberFormatException ignored) {
		}
		return fallback;
	}

	public ModConfig copy() {
		ModConfig c = new ModConfig();
		c.positionX = positionX;
		c.positionY = positionY;
		c.enabled = enabled;
		c.scale = scale;
		c.showText = showText;
		c.showCircle = showCircle;
		c.showBackground = showBackground;
		c.showShadow = showShadow;
		c.recordingText = recordingText;
		c.pausedText = pausedText;
		c.recordingColor = recordingColor;
		c.pausedColor = pausedColor;
		c.circleRadius = circleRadius;
		c.showTimecode = showTimecode;
		c.obsHost = obsHost;
		c.obsPort = obsPort;
		c.obsPassword = obsPassword;
		c.obsUseTls = obsUseTls;
		c.reconnectIntervalSeconds = reconnectIntervalSeconds;
		c.connectTimeoutMs = connectTimeoutMs;
		return c;
	}

	public void applyFrom(ModConfig other) {
		if (other == null) {
			return;
		}
		positionX = other.positionX;
		positionY = other.positionY;
		enabled = other.enabled;
		scale = other.scale;
		showText = other.showText;
		showCircle = other.showCircle;
		showBackground = other.showBackground;
		showShadow = other.showShadow;
		recordingText = other.recordingText;
		pausedText = other.pausedText;
		recordingColor = other.recordingColor;
		pausedColor = other.pausedColor;
		circleRadius = other.circleRadius;
		showTimecode = other.showTimecode;
		obsHost = other.obsHost;
		obsPort = other.obsPort;
		obsPassword = other.obsPassword;
		obsUseTls = other.obsUseTls;
		reconnectIntervalSeconds = other.reconnectIntervalSeconds;
		connectTimeoutMs = other.connectTimeoutMs;
	}
}
