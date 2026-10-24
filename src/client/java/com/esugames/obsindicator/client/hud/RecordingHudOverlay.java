package com.esugames.obsindicator.client.hud;

import com.esugames.obsindicator.RecordState;
import com.esugames.obsindicator.client.ObsRecIndicatorClient;
import com.esugames.obsindicator.client.config.OneConfigSupport;
import com.esugames.obsindicator.config.ConfigManager;
import com.esugames.obsindicator.config.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Draws the recording indicator via the Fabric HUD.
 * Never references OneConfig types (would crash when OneConfig is absent).
 *
 * <p>When OneConfig is installed the indicator is registered as a OneConfig HUD instead, so
 * OneConfig draws (and positions) it from its own HUD editor and this pass stays out of the way.
 * Everything in here is still used unchanged when OneConfig is not installed.</p>
 */
public final class RecordingHudOverlay {
	/** True while the built-in config screen draws its own draggable preview. */
	private static boolean screenPreviewActive;

	private RecordingHudOverlay() {
	}

	/**
	 * Hides the in-game indicator while one of the mod's own screens previews it, so there is only
	 * ever one indicator on screen and it is the one following the mouse.
	 */
	public static void setScreenPreviewActive(boolean active) {
		screenPreviewActive = active;
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(ObsRecIndicatorClient.MOD_ID, "recording_indicator"),
			RecordingHudOverlay::render
		);
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		// OneConfig owns rendering when its HUD is registered.
		if (OneConfigSupport.isHudRegistered()) {
			return;
		}
		// The built-in config screen draws its own preview.
		if (screenPreviewActive) {
			return;
		}

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

	/**
	 * Measured geometry of the indicator at a given scale. Shared by the Fabric HUD, the built-in
	 * screens and the OneConfig HUD so all of them agree on the indicator's size.
	 */
	public record Metrics(
		float scale,
		boolean showCircle,
		boolean showText,
		int textWidth,
		int textHeight,
		int radius,
		int gap,
		int padding,
		int contentWidth,
		int contentHeight
	) {
	}

	public static Metrics metrics(Minecraft minecraft, ModConfig config, String label, float scale) {
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
		return new Metrics(scale, showCircle, showText, textWidth, textHeight, radius, gap, padding,
			contentWidth, contentHeight);
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
		Metrics metrics = metrics(minecraft, config, label, config.scale);
		if (metrics.contentWidth() <= 0 || metrics.contentHeight() <= 0) {
			return;
		}

		int originX = config.originX(screenWidth) + translateX;
		int originY = config.originY(screenHeight, metrics.contentHeight()) + translateY;
		drawAt(graphics, minecraft, config, label, color, metrics, originX, originY);
	}

	/**
	 * Draws the indicator with its top-left content corner at {@code originX/originY}.
	 *
	 * @param metrics geometry from {@link #metrics(Minecraft, ModConfig, String, float)}
	 */
	public static void drawAt(
		GuiGraphicsExtractor graphics,
		Minecraft minecraft,
		ModConfig config,
		String label,
		int color,
		Metrics metrics,
		int originX,
		int originY
	) {
		if (metrics.contentWidth() <= 0 || metrics.contentHeight() <= 0) {
			return;
		}

		if (config.showBackground) {
			graphics.fill(originX - metrics.padding(), originY - metrics.padding(),
				originX + metrics.contentWidth() + metrics.padding(),
				originY + metrics.contentHeight() + metrics.padding(), 0x66000000);
		}

		int textX = originX;
		int midY = originY + metrics.contentHeight() / 2;
		if (metrics.showCircle()) {
			fillCircle(graphics, originX + metrics.radius(), midY, metrics.radius(), color);
			textX = originX + metrics.radius() * 2 + metrics.gap();
		}

		if (metrics.showText()) {
			int textColor = metrics.showCircle() ? 0xFFFFFFFF : color;
			// One compensation only: place origin on the circle center, scale once,
			// then offset in unscaled font pixels (-3 ≈ optical center of capitals).
			// Do NOT also multiply fontH * scale in the translate — that stacks with pose.scale.
			var pose = graphics.pose();
			pose.pushMatrix();
			// -0.5f: half-step down from the previous -4 (i.e. effective -3.5)
			pose.translate(textX, midY + 0.5f);
			if (metrics.scale() != 1.0f) {
				pose.scale(metrics.scale(), metrics.scale());
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
