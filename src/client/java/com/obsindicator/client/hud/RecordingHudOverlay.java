package com.obsindicator.client.hud;

import com.obsindicator.RecordState;
import com.obsindicator.client.ObsRecIndicatorClient;
import com.obsindicator.config.ConfigManager;
import com.obsindicator.config.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Always draws the recording indicator via Fabric HUD.
 * Never references OneConfig types (would crash when OneConfig is absent).
 */
public final class RecordingHudOverlay {
	private RecordingHudOverlay() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(ObsRecIndicatorClient.MOD_ID, "recording_indicator"),
			RecordingHudOverlay::render
		);
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		ModConfig config = ConfigManager.get();
		if (!config.enabled) {
			return;
		}

		var client = ObsRecIndicatorClient.obsClient();
		RecordState state = client == null ? RecordState.IDLE : client.currentRecordState();
		if (!state.isIndicatorVisible()) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.getWindow() == null) {
			return;
		}

		int screenWidth = minecraft.getWindow().getGuiScaledWidth();
		int screenHeight = minecraft.getWindow().getGuiScaledHeight();
		int color = state == RecordState.PAUSED ? config.pausedColorArgb() : config.recordingColorArgb();
		String label = state == RecordState.PAUSED ? config.pausedText : config.recordingText;
		if (label == null) {
			label = "";
		}
		drawIndicator(graphics, minecraft, config, screenWidth, screenHeight, color, label, 0, 0);
	}

	public static void drawIndicator(
		GuiGraphicsExtractor graphics,
		Minecraft minecraft,
		ModConfig config,
		int screenWidth,
		int screenHeight,
		int color,
		String label,
		int translateX,
		int translateY
	) {
		float scale = config.scale;
		boolean showCircle = config.showCircle;
		boolean showText = config.showText && label != null && !label.isEmpty();

		int fontH = minecraft.font.lineHeight;
		int fontW = showText ? minecraft.font.width(label) : 0;
		// Text follows the same scale as the circle so the pair stays proportional.
		int textWidth = showText ? Math.max(1, Math.round(fontW * scale)) : 0;
		int textHeight = showText ? Math.max(1, Math.round(fontH * scale)) : 0;
		int radius = showCircle ? Math.max(1, Math.round(config.circleRadius * scale)) : 0;
		// Tighter gap at large scales so the circle and text stay close.
		int gap = showText && showCircle ? Math.max(2, Math.round(1.5f * scale)) : 0;
		int padding = Math.max(2, Math.round(3 * scale));

		int circleBlock = showCircle ? radius * 2 + (textWidth > 0 ? gap : 0) : 0;
		int contentWidth = circleBlock + textWidth;
		int contentHeight = Math.max(showCircle ? radius * 2 : 0, showText ? textHeight : 0);
		if (contentWidth <= 0 || contentHeight <= 0) {
			return;
		}

		int originX = config.originX(screenWidth) + translateX;
		int originY = config.originY(screenHeight, contentHeight) + translateY;

		if (config.showBackground) {
			graphics.fill(originX - padding, originY - padding,
				originX + contentWidth + padding, originY + contentHeight + padding, 0x66000000);
		}

		int textX = originX;
		int midY = originY + contentHeight / 2;
		if (showCircle) {
			fillCircle(graphics, originX + radius, midY, radius, color);
			textX = originX + radius * 2 + gap;
		}

		if (showText) {
			int textColor = showCircle ? 0xFFFFFFFF : color;
			// One compensation only: place origin on the circle center, scale once,
			// then offset in unscaled font pixels (-3 ≈ optical center of capitals).
			// Do NOT also multiply fontH * scale in the translate — that stacks with pose.scale.
			var pose = graphics.pose();
			pose.pushMatrix();
			// -0.5f: half-step down from the previous -4 (i.e. effective -3.5)
			pose.translate(textX, midY + 0.5f);
			if (scale != 1.0f) {
				pose.scale(scale, scale);
			}
			graphics.text(minecraft.font, label, 0, -4, textColor, config.showShadow);
			pose.popMatrix();
		}
	}

	/**
	 * Anti-aliased filled circle. Edge pixels get partial alpha so the rim looks smooth
	 * instead of stair-stepped at small radii.
	 */
	private static void fillCircle(GuiGraphicsExtractor graphics, int cx, int cy, int radius, int argb) {
		if (radius <= 0) {
			return;
		}
		int baseA = (argb >>> 24) & 0xFF;
		int rgb = argb & 0x00FFFFFF;
		float r = radius;
		int r1 = radius + 1;
		for (int dy = -r1; dy <= r1; dy++) {
			for (int dx = -r1; dx <= r1; dx++) {
				// Distance from pixel center to circle center.
				float dist = (float) Math.sqrt(dx * dx + dy * dy);
				// 1.0 inside, 0 outside; ~1px soft edge.
				float coverage = r - dist + 0.5f;
				if (coverage <= 0f) {
					continue;
				}
				if (coverage > 1f) {
					coverage = 1f;
				}
				int a = (int) (baseA * coverage + 0.5f);
				if (a <= 0) {
					continue;
				}
				graphics.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, (a << 24) | rgb);
			}
		}
	}
}
