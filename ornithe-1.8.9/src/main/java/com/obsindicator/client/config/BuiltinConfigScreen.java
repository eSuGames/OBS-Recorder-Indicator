package com.obsindicator.client.config;

import com.obsindicator.client.hud.RecordingHudOverlay;
import com.obsindicator.config.ConfigManager;
import com.obsindicator.config.ModConfig;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;

import java.util.ArrayList;
import java.util.List;

/** Built-in config screen for 1.8.9. Full window is a drag preview (center = 0,0). */
public class BuiltinConfigScreen extends Screen {
	private static final int B_ENABLED = 1;
	private static final int B_TEXT_ONLY = 2;
	private static final int B_BG = 3;
	private static final int B_SHADOW = 4;
	private static final int B_SHOW_TEXT = 5;
	private static final int B_SCALE = 6;
	private static final int B_RESET_POS = 7;
	private static final int B_SAVE = 8;
	private static final int B_CANCEL = 9;

	private final Screen parent;
	private final ModConfig draft;
	private final List<int[]> labelPos = new ArrayList<>();
	private final List<String> labelText = new ArrayList<>();

	private TextFieldWidget recordingTextBox;
	private TextFieldWidget pausedTextBox;
	private TextFieldWidget posXBox;
	private TextFieldWidget posYBox;
	private TextFieldWidget hostBox;
	private TextFieldWidget passwordBox;
	private TextFieldWidget colorBox;

	private boolean dragging;
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;

	public BuiltinConfigScreen(Screen parent) {
		this.parent = parent;
		this.draft = ConfigManager.get().copy();
	}

	private void label(String text, int x, int y) {
		labelPos.add(new int[]{x, y});
		labelText.add(text);
	}

	private String onOff(String name, boolean value) {
		return name + ": " + (value ? "ON" : "OFF");
	}

	@Override
	public void init() {
		labelPos.clear();
		labelText.clear();
		dragging = false;
		buttons.clear();

		panelW = 320;
		panelX = (this.width - panelW) / 2;
		panelY = 24;
		int left = panelX + 10;
		int y = panelY + 8;
		int w = 140;
		int gap = 10;
		int lh = 10;
		int right = left + w + gap;

		label("Display", left, y);
		y += lh;
		buttons.add(new ButtonWidget(B_ENABLED, left, y, w, 20, onOff("Indicator", draft.enabled)));
		buttons.add(new ButtonWidget(B_TEXT_ONLY, right, y, w, 20, onOff("Text only", !draft.showCircle)));
		y += 24;
		buttons.add(new ButtonWidget(B_BG, left, y, w, 20, onOff("Background", draft.showBackground)));
		buttons.add(new ButtonWidget(B_SHADOW, right, y, w, 20, onOff("Shadow", draft.showShadow)));
		y += 24;
		buttons.add(new ButtonWidget(B_SHOW_TEXT, left, y, w, 20, onOff("Show text", draft.showText)));
		buttons.add(new ButtonWidget(B_SCALE, right, y, w, 20, String.format("Size: %.2fx", draft.scale)));
		y += 28;

		label("Text", left, y);
		y += lh;
		label("Recording text", left, y);
		label("Paused text", right, y);
		y += lh;
		recordingTextBox = new TextFieldWidget(10, this.textRenderer, left, y, w, 20);
		recordingTextBox.setText(draft.recordingText);
		pausedTextBox = new TextFieldWidget(11, this.textRenderer, right, y, w, 20);
		pausedTextBox.setText(draft.pausedText);
		y += 24;

		label("Recording color (hex)", left, y);
		y += lh;
		colorBox = new TextFieldWidget(12, this.textRenderer, left, y, w, 20);
		colorBox.setText(draft.recordingColor);
		y += 28;

		label("Position (center = 0,0)", left, y);
		y += lh;
		label("X", left, y);
		label("Y", right, y);
		y += lh;
		posXBox = new TextFieldWidget(13, this.textRenderer, left, y, w, 20);
		posXBox.setText(String.valueOf(draft.positionX));
		posYBox = new TextFieldWidget(14, this.textRenderer, right, y, w, 20);
		posYBox.setText(String.valueOf(draft.positionY));
		y += 24;
		buttons.add(new ButtonWidget(B_RESET_POS, left, y, w, 20, "Reset position"));
		y += 28;

		label("OBS", left, y);
		y += lh;
		label("Host", left, y);
		label("Password", right, y);
		y += lh;
		hostBox = new TextFieldWidget(15, this.textRenderer, left, y, w, 20);
		hostBox.setText(draft.obsHost);
		passwordBox = new TextFieldWidget(16, this.textRenderer, right, y, w, 20);
		passwordBox.setText(draft.obsPassword);
		y += 28;

		buttons.add(new ButtonWidget(B_SAVE, left, y, w, 20, "Save"));
		buttons.add(new ButtonWidget(B_CANCEL, right, y, w, 20, "Cancel"));

		panelH = y + 20 + 12 - panelY;
	}

	protected void buttonClicked(ButtonWidget button) {
		switch (button.id) {
			case B_ENABLED -> {
				draft.enabled = !draft.enabled;
				button.message = onOff("Indicator", draft.enabled);
			}
			case B_TEXT_ONLY -> {
				draft.showCircle = !draft.showCircle;
				button.message = onOff("Text only", !draft.showCircle);
			}
			case B_BG -> {
				draft.showBackground = !draft.showBackground;
				button.message = onOff("Background", draft.showBackground);
			}
			case B_SHADOW -> {
				draft.showShadow = !draft.showShadow;
				button.message = onOff("Shadow", draft.showShadow);
			}
			case B_SHOW_TEXT -> {
				draft.showText = !draft.showText;
				button.message = onOff("Show text", draft.showText);
			}
			case B_SCALE -> {
				float next = draft.scale + 0.25f;
				if (next > 4.0f) {
					next = 0.25f;
				}
				draft.scale = next;
				button.message = String.format("Size: %.2fx", draft.scale);
			}
			case B_RESET_POS -> {
				draft.resetPosition();
				syncPos();
			}
			case B_SAVE -> {
				pullFields();
				ConfigManager.get().applyFrom(draft);
				ConfigManager.save();
				this.onClose();
			}
			case B_CANCEL -> this.onClose();
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

	private void pullFields() {
		if (recordingTextBox != null) {
			draft.recordingText = recordingTextBox.getText();
		}
		if (pausedTextBox != null) {
			draft.pausedText = pausedTextBox.getText();
		}
		if (colorBox != null) {
			draft.recordingColor = colorBox.getText();
		}
		if (hostBox != null) {
			draft.obsHost = hostBox.getText();
		}
		if (passwordBox != null) {
			draft.obsPassword = passwordBox.getText();
		}
		try {
			if (posXBox != null) {
				draft.positionX = Integer.parseInt(posXBox.getText().trim());
			}
		} catch (NumberFormatException ignored) {
		}
		try {
			if (posYBox != null) {
				draft.positionY = Integer.parseInt(posYBox.getText().trim());
			}
		} catch (NumberFormatException ignored) {
		}
	}

	private boolean hitsPanel(int x, int y) {
		return x >= panelX && x <= panelX + panelW && y >= panelY && y <= panelY + panelH;
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		this.renderBackground();
		if (dragging) {
			updateFromMouse(mouseX, mouseY);
		}

		RecordingHudOverlay.drawIndicator(
			this.client, draft,
			this.width, this.height,
			draft.recordingColorArgb(),
			draft.recordingText == null ? "REC" : draft.recordingText,
			0, 0
		);

		this.drawCenteredString(this.textRenderer, "OBS Rec Indicator", this.width / 2, 8, 0xFFFFFFFF);
		for (int i = 0; i < labelPos.size() && i < labelText.size(); i++) {
			int[] pos = labelPos.get(i);
			this.textRenderer.draw(labelText.get(i), pos[0], pos[1], 0xFFCCCCCC);
		}
		for (ButtonWidget b : buttons) {
			b.render(this.client, mouseX, mouseY);
		}
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		if (button == 0 && !hitsPanel(mouseX, mouseY)) {
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





