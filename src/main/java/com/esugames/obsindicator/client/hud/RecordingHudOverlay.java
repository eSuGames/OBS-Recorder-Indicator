package com.esugames.obsindicator.client.hud;

import com.esugames.obsindicator.RecordState;
import com.esugames.obsindicator.client.ObsRecIndicatorClient;
import com.esugames.obsindicator.client.config.OneConfigSupport;
import com.esugames.obsindicator.config.ConfigManager;
import com.esugames.obsindicator.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.Window;
import org.lwjgl.opengl.GL11;

/**
 * Draws the recording indicator via the vanilla HUD (Minecraft 1.8.9).
 * Never references OneConfig types directly (the class must load without them).
 *
 * <p>When OneConfig is installed the indicator is registered as a OneConfig HUD instead, so
 * OneConfig draws (and positions) it from its own HUD editor and this pass stays out of the way.
 * Everything in here is still used unchanged when the OneConfig HUD cannot draw.</p>
 */
public final class RecordingHudOverlay {

	private RecordingHudOverlay() {
	}

	public static void init() {
	}

	public static void render() {
		// OneConfig owns rendering when its HUD is registered.
		if (OneConfigSupport.isHudRegistered()) {
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
		if (minecraft == null) {
			return;
		}

		int screenWidth = guiScaledWidth(minecraft);
		int screenHeight = guiScaledHeight(minecraft);
		int color = state == RecordState.PAUSED ? config.pausedColorArgb() : config.recordingColorArgb();
		String label = state == RecordState.PAUSED ? config.pausedText : config.recordingText;
		if (label == null) {
			label = "";
		}
		drawIndicator(minecraft, config, screenWidth, screenHeight, color, label);
	}

	/**
	 * Measured geometry of the indicator at a given scale.
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
		boolean showText = config.showText && label != null && !label.isEmpty();

		TextRenderer font = minecraft.textRenderer;
		int fontH = font.fontHeight;
		int fontW = showText ? font.getWidth(label) : 0;
		// Text follows the same scale as the circle so the pair stays proportional.
		int textWidth = showText ? Math.max(1, Math.round(fontW * scale)) : 0;
		int textHeight = showText ? Math.max(1, Math.round(fontH * scale)) : 0;
		int radius = config.showCircle ? Math.max(1, Math.round(config.circleRadius * scale)) : 0;
		// Tighter gap at large scales so the circle and text stay close.
		int gap = showText && config.showCircle ? Math.max(2, Math.round(1.5f * scale)) : 0;
		int padding = Math.max(2, Math.round(3 * scale));

		int circleBlock = config.showCircle ? radius * 2 + (textWidth > 0 ? gap : 0) : 0;
		int contentWidth = circleBlock + textWidth;
		int contentHeight = Math.max(config.showCircle ? radius * 2 : 0, showText ? textHeight : 0);
		return new Metrics(scale, config.showCircle, showText, textWidth, textHeight, radius, gap, padding,
			contentWidth, contentHeight);
	}

	public static void drawIndicator(
		Minecraft minecraft,
		ModConfig config,
		int screenWidth,
		int screenHeight,
		int color,
		String label
	) {
		Metrics metrics = metrics(minecraft, config, label, config.scale);
		if (metrics.contentWidth() <= 0 || metrics.contentHeight() <= 0) {
			return;
		}

		int originX = config.originX(screenWidth);
		int midY = config.originY(screenHeight, metrics.contentHeight()) + metrics.contentHeight() / 2;

		if (config.showBackground) {
			GuiElement.fill(
				originX - metrics.padding(), midY - metrics.contentHeight() / 2 - metrics.padding(),
				originX + metrics.contentWidth() + metrics.padding(),
				midY + metrics.contentHeight() / 2 + metrics.padding(),
				0x66000000);
		}

		int textX = originX;
		if (metrics.showCircle()) {
			fillCircle(originX + metrics.radius(), midY, metrics.radius(), color);
			textX = originX + metrics.radius() * 2 + metrics.gap();
		}

		if (metrics.showText()) {
			int textColor = metrics.showCircle() ? 0xFFFFFFFF : color;
			// One compensation only: place origin on the circle center, scale once,
			// then offset in unscaled font pixels (-4 ≈ optical center of capitals).
			GL11.glPushMatrix();
			GL11.glTranslatef(textX, midY + 0.5f, 0.0f);
			if (metrics.scale() != 1.0f) {
				GL11.glScalef(metrics.scale(), metrics.scale(), 1.0f);
			}
			minecraft.textRenderer.draw(label, 0, -4, textColor, config.showShadow);
			GL11.glPopMatrix();
		}
	}

	/**
	 * Anti-aliased filled circle. Edge pixels get partial alpha so the rim looks smooth
	 * instead of stair-stepped at small radii.
	 */
	private static void fillCircle(int cx, int cy, int radius, int argb) {
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
				GuiElement.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, (a << 24) | rgb);
			}
		}
	}

	/** GUI-scaled screen width; 1.8.9 measures the GUI scale through {@link Window}. */
	public static int guiScaledWidth(Minecraft minecraft) {
		try {
			return (int) new Window(minecraft).getScaledWidth();
		} catch (Throwable t) {
			return 640;
		}
	}

	/** GUI-scaled screen height; 1.8.9 measures the GUI scale through {@link Window}. */
	public static int guiScaledHeight(Minecraft minecraft) {
		try {
			return (int) new Window(minecraft).getScaledHeight();
		} catch (Throwable t) {
			return 360;
		}
	}
}
