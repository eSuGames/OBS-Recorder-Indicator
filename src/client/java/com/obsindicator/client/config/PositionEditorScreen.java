package com.obsindicator.client.config;

import com.obsindicator.client.hud.RecordingHudOverlay;
import com.obsindicator.config.ConfigManager;
import com.obsindicator.config.ModConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Position / size editor. Full-window preview, center = (0,0).
 * Opened from the built-in config and from OneConfig's "Position editor" button.
 */
public final class PositionEditorScreen extends Screen {
	private final Screen parent;
	private final ModConfig draft;
	private boolean dragging;

	private EditBox posXBox;
	private EditBox posYBox;
	private Button scaleButton;

	public PositionEditorScreen(Screen parent) {
		super(Component.translatable("obs_rec_indicator.gui.position_editor"));
		this.parent = parent;
		this.draft = ConfigManager.get().copy();
	}

	private static Component tr(String key) {
		return Component.translatable(key);
	}

	private Component scaleLabel() {
		return Component.translatable("obs_rec_indicator.gui.scale")
			.append(String.format(": %.2fx", draft.scale));
	}

	private void adjustScale(float delta) {
		float next = draft.scale + delta;
		if (next < 0.25f) {
			next = 0.25f;
		}
		if (next > 4.0f) {
			next = 4.0f;
		}
		draft.scale = next;
		if (scaleButton != null) {
			scaleButton.setMessage(scaleLabel());
		}
	}

	@Override
	protected void init() {
		dragging = false;
		int w = 100;
		int y = this.height - 80;
		int left = this.width / 2 - w - 5;

		posXBox = new EditBox(this.font, left, y, w, 20, tr("obs_rec_indicator.gui.x_offset"));
		posXBox.setValue(String.valueOf(draft.positionX));
		posXBox.setMaxLength(6);
		posXBox.setResponder(v -> {
			try {
				draft.positionX = Integer.parseInt(v.trim());
			} catch (NumberFormatException ignored) {
			}
		});
		this.addRenderableWidget(posXBox);

		posYBox = new EditBox(this.font, left + w + 10, y, w, 20, tr("obs_rec_indicator.gui.y_offset"));
		posYBox.setValue(String.valueOf(draft.positionY));
		posYBox.setMaxLength(6);
		posYBox.setResponder(v -> {
			try {
				draft.positionY = Integer.parseInt(v.trim());
			} catch (NumberFormatException ignored) {
			}
		});
		this.addRenderableWidget(posYBox);
		y += 24;

		// [ -0.5 ] [ -0.25 ] [ 1.00x ] [ +0.25 ] [ +0.5 ]
		int stepW = 48;
		int labelW = 72;
		int pad = 4;
		int rowW = stepW * 4 + labelW + pad * 4;
		int rowLeft = this.width / 2 - rowW / 2;
		int x = rowLeft;

		this.addRenderableWidget(Button.builder(Component.literal("-0.5"), b -> {
			adjustScale(-0.5f);
		}).bounds(x, y, stepW, 20).build());
		x += stepW + pad;

		this.addRenderableWidget(Button.builder(Component.literal("-0.25"), b -> {
			adjustScale(-0.25f);
		}).bounds(x, y, stepW, 20).build());
		x += stepW + pad;

		scaleButton = Button.builder(scaleLabel(), b -> {
			draft.scale = 1.0f;
			if (scaleButton != null) {
				scaleButton.setMessage(scaleLabel());
			}
		}).bounds(x, y, labelW, 20).build();
		this.addRenderableWidget(scaleButton);
		x += labelW + pad;

		this.addRenderableWidget(Button.builder(Component.literal("+0.25"), b -> {
			adjustScale(0.25f);
		}).bounds(x, y, stepW, 20).build());
		x += stepW + pad;

		this.addRenderableWidget(Button.builder(Component.literal("+0.5"), b -> {
			adjustScale(0.5f);
		}).bounds(x, y, stepW, 20).build());
		y += 24;

		this.addRenderableWidget(Button.builder(tr("obs_rec_indicator.gui.reset_position"), b -> {
			draft.resetPosition();
			if (posXBox != null) {
				posXBox.setValue(String.valueOf(draft.positionX));
			}
			if (posYBox != null) {
				posYBox.setValue(String.valueOf(draft.positionY));
			}
		}).bounds(this.width / 2 - w - 5, y, w, 20).build());

		this.addRenderableWidget(Button.builder(tr("obs_rec_indicator.gui.save"), b -> {
			ConfigManager.get().positionX = draft.positionX;
			ConfigManager.get().positionY = draft.positionY;
			ConfigManager.get().scale = draft.scale;
			ConfigManager.save();
			// Reflective sync — do not reference OneConfig types here (absent at runtime).
			try {
				Class<?> clazz = Class.forName("com.obsindicator.client.config.ObsIndicatorOneConfig");
				Object one = clazz.getMethod("instance").invoke(null);
				if (one != null) {
					clazz.getField("positionX").setInt(one, draft.positionX);
					clazz.getField("positionY").setInt(one, draft.positionY);
					clazz.getField("scale").setFloat(one, draft.scale);
				}
			} catch (Throwable ignored) {
			}
			this.onClose();
		}).bounds(this.width / 2 + 5, y, w, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		if (dragging) {
			updateFromMouse(mouseX, mouseY);
		}

		RecordingHudOverlay.drawIndicator(
			graphics,
			this.minecraft,
			draft,
			this.width,
			this.height,
			draft.recordingColorArgb(),
			draft.recordingText == null ? "REC" : draft.recordingText,
			0,
			0
		);

		graphics.text(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 12, 0xFFFFFFFF, true);
		graphics.text(this.font,
			Component.literal("X=" + draft.positionX + "  Y=" + draft.positionY),
			this.width / 2 - 40, this.height - 96, 0xFFCCCCCC, false);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && event.y() < this.height - 114) {
			dragging = true;
			updateFromMouse(event.x(), event.y());
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging) {
			updateFromMouse(event.x(), event.y());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	private void updateFromMouse(double mouseX, double mouseY) {
		draft.positionX = (int) Math.round(mouseX - this.width / 2.0);
		draft.positionY = (int) Math.round(mouseY - this.height / 2.0);
		if (posXBox != null) {
			posXBox.setValue(String.valueOf(draft.positionX));
		}
		if (posYBox != null) {
			posYBox.setValue(String.valueOf(draft.positionY));
		}
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(this.parent);
		}
	}
}
