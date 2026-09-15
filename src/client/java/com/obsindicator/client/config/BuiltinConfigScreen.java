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

import java.util.ArrayList;
import java.util.List;

/**
 * Built-in config screen. The full window is a position preview: drag the indicator.
 * Origin (0,0) is the screen center. +X = right, +Y = down.
 */
public final class BuiltinConfigScreen extends Screen {
	private final Screen parent;
	private final ModConfig draft;
	private final List<int[]> labelPos = new ArrayList<>();
	private final List<Component> labelText = new ArrayList<>();

	private EditBox recordingTextBox;
	private EditBox pausedTextBox;
	private EditBox posXBox;
	private EditBox posYBox;
	private EditBox hostBox;
	private EditBox passwordBox;
	private EditBox colorBox;

	private boolean dragging;
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;

	public BuiltinConfigScreen(Screen parent) {
		super(Component.translatable("obs_rec_indicator.gui.title"));
		this.parent = parent;
		this.draft = ConfigManager.get().copy();
	}

	private static Component tr(String key) {
		return Component.translatable(key);
	}

	private static Component onOff(String key, boolean value) {
		return Component.translatable(key).append(": ")
			.append(Component.translatable(value ? "obs_rec_indicator.gui.on" : "obs_rec_indicator.gui.off"));
	}

	private void label(Component text, int x, int y) {
		labelPos.add(new int[]{x, y});
		labelText.add(text);
	}

	@Override
	protected void init() {
		labelPos.clear();
		labelText.clear();
		dragging = false;

		// Centered settings panel. Rest of the screen is the position drag zone.
		panelW = 320;
		panelX = (this.width - panelW) / 2;
		panelY = 24;
		int left = panelX + 10;
		int y = panelY + 8;
		int w = 140;
		int gap = 10;
		int lh = 10;
		int right = left + w + gap;

		label(tr("obs_rec_indicator.gui.section_display"), left, y);
		y += lh;

		this.addRenderableWidget(Button.builder(onOff("obs_rec_indicator.gui.enabled", draft.enabled), b -> {
			draft.enabled = !draft.enabled;
			b.setMessage(onOff("obs_rec_indicator.gui.enabled", draft.enabled));
		}).bounds(left, y, w, 20).build());

		this.addRenderableWidget(Button.builder(
			onOff("obs_rec_indicator.gui.text_only", !draft.showCircle), b -> {
				draft.showCircle = !draft.showCircle;
				b.setMessage(onOff("obs_rec_indicator.gui.text_only", !draft.showCircle));
			}).bounds(right, y, w, 20).build());
		y += 24;

		this.addRenderableWidget(Button.builder(onOff("obs_rec_indicator.gui.background", draft.showBackground), b -> {
			draft.showBackground = !draft.showBackground;
			b.setMessage(onOff("obs_rec_indicator.gui.background", draft.showBackground));
		}).bounds(left, y, w, 20).build());

		this.addRenderableWidget(Button.builder(onOff("obs_rec_indicator.gui.shadow", draft.showShadow), b -> {
			draft.showShadow = !draft.showShadow;
			b.setMessage(onOff("obs_rec_indicator.gui.shadow", draft.showShadow));
		}).bounds(right, y, w, 20).build());
		y += 24;

		this.addRenderableWidget(Button.builder(onOff("obs_rec_indicator.gui.show_text", draft.showText), b -> {
			draft.showText = !draft.showText;
			b.setMessage(onOff("obs_rec_indicator.gui.show_text", draft.showText));
		}).bounds(left, y, w, 20).build());

		this.addRenderableWidget(Button.builder(
			Component.translatable("obs_rec_indicator.gui.scale").append(String.format(": %.2fx", draft.scale)), b -> {
				float next = draft.scale + 0.25f;
				if (next > 4.0f) {
					next = 0.25f;
				}
				draft.scale = next;
				b.setMessage(Component.translatable("obs_rec_indicator.gui.scale")
					.append(String.format(": %.2fx", draft.scale)));
			}).bounds(right, y, w, 20).build());
		y += 28;

		label(tr("obs_rec_indicator.gui.section_text"), left, y);
		y += lh;

		recordingTextBox = new EditBox(this.font, left, y, w, 20, tr("obs_rec_indicator.gui.recording_text"));
		recordingTextBox.setValue(draft.recordingText);
		recordingTextBox.setMaxLength(32);
		this.addRenderableWidget(recordingTextBox);

		pausedTextBox = new EditBox(this.font, right, y, w, 20, tr("obs_rec_indicator.gui.paused_text"));
		pausedTextBox.setValue(draft.pausedText);
		pausedTextBox.setMaxLength(32);
		this.addRenderableWidget(pausedTextBox);
		y += 24;

		colorBox = new EditBox(this.font, left, y, w, 20, tr("obs_rec_indicator.gui.recording_color"));
		colorBox.setValue(draft.recordingColor);
		colorBox.setMaxLength(9);
		this.addRenderableWidget(colorBox);
		y += 28;

		label(tr("obs_rec_indicator.gui.section_position"), left, y);
		y += lh;

		label(tr("obs_rec_indicator.gui.x_offset"), left, y);
		label(tr("obs_rec_indicator.gui.y_offset"), right, y);
		y += lh;

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

		posYBox = new EditBox(this.font, right, y, w, 20, tr("obs_rec_indicator.gui.y_offset"));
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

		this.addRenderableWidget(Button.builder(tr("obs_rec_indicator.gui.reset_position"), b -> {
			draft.resetPosition();
			syncPosBoxes();
		}).bounds(left, y, w, 20).build());
		y += 28;

		label(tr("obs_rec_indicator.gui.section_obs"), left, y);
		y += lh;

		hostBox = new EditBox(this.font, left, y, w, 20, tr("obs_rec_indicator.gui.host"));
		hostBox.setValue(draft.obsHost);
		hostBox.setMaxLength(128);
		this.addRenderableWidget(hostBox);

		passwordBox = new EditBox(this.font, right, y, w, 20, tr("obs_rec_indicator.gui.password"));
		passwordBox.setValue(draft.obsPassword);
		passwordBox.setMaxLength(128);
		this.addRenderableWidget(passwordBox);
		y += 28;

		this.addRenderableWidget(Button.builder(tr("obs_rec_indicator.gui.save"), b -> {
			pullFields();
			ConfigManager.get().applyFrom(draft);
			ConfigManager.save();
			this.onClose();
		}).bounds(left, y, w, 20).build());

		this.addRenderableWidget(Button.builder(tr("obs_rec_indicator.gui.cancel"), b -> this.onClose())
			.bounds(right, y, w, 20).build());

		panelH = y + 20 + 12 - panelY;
	}

	private void syncPosBoxes() {
		if (posXBox != null) {
			posXBox.setValue(String.valueOf(draft.positionX));
		}
		if (posYBox != null) {
			posYBox.setValue(String.valueOf(draft.positionY));
		}
	}

	private void pullFields() {
		if (recordingTextBox != null) {
			draft.recordingText = recordingTextBox.getValue();
		}
		if (pausedTextBox != null) {
			draft.pausedText = pausedTextBox.getValue();
		}
		if (colorBox != null) {
			draft.recordingColor = colorBox.getValue();
		}
		if (hostBox != null) {
			draft.obsHost = hostBox.getValue();
		}
		if (passwordBox != null) {
			draft.obsPassword = passwordBox.getValue();
		}
		if (posXBox != null) {
			try {
				draft.positionX = Integer.parseInt(posXBox.getValue().trim());
			} catch (NumberFormatException ignored) {
			}
		}
		if (posYBox != null) {
			try {
				draft.positionY = Integer.parseInt(posYBox.getValue().trim());
			} catch (NumberFormatException ignored) {
			}
		}
	}

	private boolean hitsPanel(double x, double y) {
		return x >= panelX && x <= panelX + panelW && y >= panelY && y <= panelY + panelH;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		if (dragging) {
			updatePositionFromMouse(mouseX, mouseY);
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

		graphics.text(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, panelY - 14, 0xFFFFFFFF, true);
		for (int i = 0; i < labelPos.size() && i < labelText.size(); i++) {
			int[] pos = labelPos.get(i);
			graphics.text(this.font, labelText.get(i), pos[0], pos[1], 0xFFCCCCCC, false);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && !hitsPanel(event.x(), event.y())) {
			dragging = true;
			updatePositionFromMouse(event.x(), event.y());
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging) {
			updatePositionFromMouse(event.x(), event.y());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	/** Mouse on the full screen → center-relative X/Y (0,0 = screen center). */
	private void updatePositionFromMouse(double mouseX, double mouseY) {
		int x = (int) Math.round(mouseX - this.width / 2.0);
		int y = (int) Math.round(mouseY - this.height / 2.0);
		draft.positionX = x;
		draft.positionY = y;
		syncPosBoxes();
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(this.parent);
		}
	}
}
