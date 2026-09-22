package com.obsindicator.client.config;

import com.obsindicator.client.hud.RecordingHudOverlay;
import com.obsindicator.config.ConfigManager;
import com.obsindicator.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;

/** Position / size editor. Center = (0,0). */
public class PositionEditorScreen extends Screen {
	private static final int B_RESET = 1;
	private static final int B_SAVE = 2;
	private static final int B_M05 = 3;
	private static final int B_M025 = 4;
	private static final int B_RESET_SCALE = 5;
	private static final int B_P025 = 6;
	private static final int B_P05 = 7;

	private final Screen parent;
	private final ModConfig draft;
	private boolean dragging;

	private TextFieldWidget posXBox;
	private TextFieldWidget posYBox;
	private ButtonWidget scaleButton;

	public PositionEditorScreen(Screen parent) {
		this.parent = parent;
		this.draft = ConfigManager.get().copy();
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
			scaleButton.message = String.format("Size: %.2fx", draft.scale);
		}
	}

	@Override
	public void init() {
		dragging = false;
		buttons.clear();
		int w = 100;
		int y = this.height - 80;
		int left = this.width / 2 - w - 5;

		posXBox = new TextFieldWidget(10, this.textRenderer, left, y, w, 20);
		posXBox.setText(String.valueOf(draft.positionX));
		posYBox = new TextFieldWidget(11, this.textRenderer, left + w + 10, y, w, 20);
		posYBox.setText(String.valueOf(draft.positionY));
		y += 24;

		int stepW = 48;
		int labelW = 72;
		int pad = 4;
		int rowW = stepW * 4 + labelW + pad * 4;
		int rowLeft = this.width / 2 - rowW / 2;
		int x = rowLeft;

		buttons.add(new ButtonWidget(B_M05, x, y, stepW, 20, "-0.5"));
		x += stepW + pad;
		buttons.add(new ButtonWidget(B_M025, x, y, stepW, 20, "-0.25"));
		x += stepW + pad;
		scaleButton = new ButtonWidget(B_RESET_SCALE, x, y, labelW, 20, String.format("Size: %.2fx", draft.scale));
		buttons.add(scaleButton);
		x += labelW + pad;
		buttons.add(new ButtonWidget(B_P025, x, y, stepW, 20, "+0.25"));
		x += stepW + pad;
		buttons.add(new ButtonWidget(B_P05, x, y, stepW, 20, "+0.5"));
		y += 24;

		buttons.add(new ButtonWidget(B_RESET, this.width / 2 - w - 5, y, w, 20, "Reset"));
		buttons.add(new ButtonWidget(B_SAVE, this.width / 2 + 5, y, w, 20, "Save"));
	}

	protected void buttonClicked(ButtonWidget button) {
		switch (button.id) {
			case B_M05 -> adjustScale(-0.5f);
			case B_M025 -> adjustScale(-0.25f);
			case B_RESET_SCALE -> {
				draft.scale = 1.0f;
				button.message = String.format("Size: %.2fx", draft.scale);
			}
			case B_P025 -> adjustScale(0.25f);
			case B_P05 -> adjustScale(0.5f);
			case B_RESET -> {
				draft.resetPosition();
				syncPos();
			}
			case B_SAVE -> {
				try {
					draft.positionX = Integer.parseInt(posXBox.getText().trim());
				} catch (Exception ignored) {
				}
				try {
					draft.positionY = Integer.parseInt(posYBox.getText().trim());
				} catch (Exception ignored) {
				}
				ConfigManager.get().positionX = draft.positionX;
				ConfigManager.get().positionY = draft.positionY;
				ConfigManager.get().scale = draft.scale;
				ConfigManager.save();
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
			}
			default -> {
			}
		}
	}

	private void syncPos() {
		if (posXBox != null) {
			posXBox.setText(String.valueOf(draft.positionX));
		}
		if (posYBox != null) {
			posYBox.setText(String.valueOf(draft.positionY));
		}
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		this.renderBackground();
		if (dragging) {
			updateFromMouse(mouseX, mouseY);
		}
		RecordingHudOverlay.drawIndicator(
			this.client, draft, this.width, this.height,
			draft.recordingColorArgb(),
			draft.recordingText == null ? "REC" : draft.recordingText,
			0, 0
		);
		this.drawCenteredString(this.textRenderer, "Position Editor", this.width / 2, 12, 0xFFFFFFFF);
		this.textRenderer.draw("X=" + draft.positionX + "  Y=" + draft.positionY, this.width / 2 - 40, this.height - 96, 0xFFCCCCCC);
		for (ButtonWidget b : buttons) {
			b.render(this.client, mouseX, mouseY);
		}
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		if (button == 0 && mouseY < this.height - 114) {
			dragging = true;
			updateFromMouse(mouseX, mouseY);
			return;
		}
		super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	protected void mouseDragged(int mouseX, int mouseY, int button, long timeSinceLastClick) {
		if (dragging) {
			updateFromMouse(mouseX, mouseY);
			return;
		}
		super.mouseDragged(mouseX, mouseY, button, timeSinceLastClick);
	}

	@Override
	protected void mouseReleased(int mouseX, int mouseY, int button) {
		dragging = false;
		super.mouseReleased(mouseX, mouseY, button);
	}

	private void updateFromMouse(int mouseX, int mouseY) {
		draft.positionX = (int) Math.round(mouseX - this.width / 2.0);
		draft.positionY = (int) Math.round(mouseY - this.height / 2.0);
		syncPos();
	}

	public void onClose() {
		if (this.client != null) {
			this.client.setScreen(this.parent);
		}
	}
}




