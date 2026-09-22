package com.obsindicator.client.hud;

import com.obsindicator.RecordState;
import com.obsindicator.client.ObsRecIndicatorClient;
import com.obsindicator.config.ConfigManager;
import com.obsindicator.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;

/** HUD drawing for 1.8.9. Center origin (0,0). Left edge of indicator = positionX. */
public final class RecordingHudOverlay {
	private RecordingHudOverlay() {
	}

	public static void init() {
	}

	public static void render() {
		ModConfig config = ConfigManager.get();
		if (!config.enabled) {
			return;
		}
		var client = ObsRecIndicatorClient.obsClient();
		RecordState state = client == null ? RecordState.IDLE : client.currentRecordState();
		if (!state.isIndicatorVisible()) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null) {
			return;
		}
		int color = state == RecordState.PAUSED ? config.pausedColorArgb() : config.recordingColorArgb();
		String label = state == RecordState.PAUSED ? config.pausedText : config.recordingText;
		if (label == null) {
			label = "";
		}
		drawIndicator(mc, config, mc.width, mc.height, color, label, 0, 0);
	}

	public static void drawIndicator(
		MinecraftClient mc,
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

		TextRenderer font = mc.textRenderer;
		int fontH = font.fontHeight;
		int fontW = showText ? font.getStringWidth(label) : 0;
		int textWidth = showText ? Math.max(1, Math.round(fontW * scale)) : 0;
		int textHeight = showText ? Math.max(1, Math.round(fontH * scale)) : 0;
		int radius = showCircle ? Math.max(1, Math.round(config.circleRadius * scale)) : 0;
		int gap = showText && showCircle ? Math.max(2, Math.round(1.5f * scale)) : 0;
		int padding = Math.max(2, Math.round(3 * scale));

		int circleBlock = showCircle ? radius * 2 + (textWidth > 0 ? gap : 0) : 0;
		int contentWidth = circleBlock + textWidth;
		int contentHeight = Math.max(showCircle ? radius * 2 : 0, showText ? textHeight : 0);
		if (contentWidth <= 0 || contentHeight <= 0) {
			return;
		}

		int originX = config.originX(screenWidth) + translateX;
		int midY = config.originY(screenHeight, contentHeight) + translateY;

		if (config.showBackground) {
			DrawableHelper.fill(
				originX - padding, midY - contentHeight / 2 - padding,
				originX + contentWidth + padding, midY + contentHeight / 2 + padding,
				0x66000000
			);
		}

		int textX = originX;
		if (showCircle) {
			fillCircle(originX + radius, midY, radius, color);
			textX = originX + radius * 2 + gap;
		}

		if (showText) {
			int textColor = showCircle ? 0xFFFFFFFF : color;
			int textY = midY - fontH / 2 - 1;
			if (config.showShadow) {
				font.drawWithShadow(label, textX, textY, textColor);
			} else {
				font.draw(label, textX, textY, textColor);
			}
		}
	}

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
				float dist = (float) Math.sqrt(dx * dx + dy * dy);
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
				DrawableHelper.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, (a << 24) | rgb);
			}
		}
	}
}
