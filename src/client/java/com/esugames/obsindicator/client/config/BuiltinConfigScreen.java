package com.esugames.obsindicator.client.config;

import com.esugames.obsindicator.client.hud.RecordingHudOverlay;
import com.esugames.obsindicator.config.ConfigManager;
import com.esugames.obsindicator.config.ModConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Built-in config screen, used when OneConfig is not installed.
 *
 * <p>The window is a position preview: drag the indicator itself to move it. Only the indicator
 * starts a drag, so clicking anywhere else — the panel background or an empty area of the screen —
 * does nothing and can never move the indicator by accident. Widgets keep their own clicks.</p>
 *
 * <p>Origin (0,0) is the screen center. +X = right, +Y = down. While this screen is open the normal
 * in-game indicator is hidden, so the preview is the only indicator on screen and follows the
 * mouse exactly.</p>
 *
 * <p>Edits are kept when the screen is closed with Escape, exactly as if Save had been pressed.
 * Cancel is the only way out that discards them.</p>
 */
public final class BuiltinConfigScreen extends Screen {
	private static final Logger LOGGER = LoggerFactory.getLogger("obs_rec_indicator");

	/**
	 * Primary (left) mouse button.
	 *
	 * <p>1.21 and older numbered mouse buttons from zero, but 26.3 numbers them from one:
	 * {@code MouseHandler.onButton} treats {@code button() == 1} as the left button (2 = middle,
	 * 3 = right) and {@code AbstractWidget#isValidClickButton} only accepts 1. Comparing against 0
	 * — as this screen used to — never matches a real click, so dragging could never start.</p>
	 */
	private static final int PRIMARY_BUTTON = 1;
	/** Zero-based numbering used by older versions; unused in 26.3. */
	private static final int LEGACY_PRIMARY_BUTTON = 0;
	/** Extra pixels around the previewed indicator that still start a drag. */
	private static final int GRAB_MARGIN = 4;

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
	/** Set by Cancel so closing does not save the edits. */
	private boolean discarding;
	/** Set once the screen is on its way out, so the save above happens exactly once. */
	private boolean closed;
	/** Set once so a support log line shows whether clicks actually reach this screen. */
	private boolean toldAboutDrag;
	/** Cursor position minus the indicator origin at the moment the drag started. */
	private double dragOffsetX;
	private double dragOffsetY;

	private int panelX;
	private int panelY;
	private int panelW;

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

		// Centered settings panel. Everything that is not a widget is a drag zone.
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

		// Save and Escape both go through onClose(), which writes the edits out; Cancel is the
		// only button that throws them away.
		this.addRenderableWidget(Button.builder(tr("obs_rec_indicator.gui.save"), b -> {
			discarding = false;
			this.onClose();
		}).bounds(left, y, w, 20).build());

		this.addRenderableWidget(Button.builder(tr("obs_rec_indicator.gui.cancel"), b -> {
			discarding = true;
			this.onClose();
		}).bounds(right, y, w, 20).build());
	}

	@Override
	public void added() {
		super.added();
		// The preview below is the only indicator while this screen is open.
		RecordingHudOverlay.setScreenPreviewActive(true);
	}

	@Override
	public void removed() {
		RecordingHudOverlay.setScreenPreviewActive(false);
		dragging = false;
		super.removed();
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

	private RecordingHudOverlay.Metrics metrics() {
		if (this.minecraft == null || this.minecraft.font == null) {
			return null;
		}
		String label = draft.recordingText == null ? "" : draft.recordingText;
		return RecordingHudOverlay.metrics(this.minecraft, draft, label, draft.scale);
	}

	/** Top-left content corner of the previewed indicator, matching {@link ModConfig}'s origin. */
	private int previewOriginX() {
		return this.width / 2 + draft.positionX;
	}

	private int previewOriginY(RecordingHudOverlay.Metrics metrics) {
		int contentHeight = metrics == null ? 0 : metrics.contentHeight();
		return this.height / 2 + draft.positionY - contentHeight / 2;
	}

	/** Starts a drag without snapping the indicator to the cursor. */
	private void beginDrag(double mouseX, double mouseY) {
		RecordingHudOverlay.Metrics metrics = metrics();
		dragOffsetX = mouseX - previewOriginX();
		dragOffsetY = mouseY - previewOriginY(metrics);
		dragging = true;
		if (!toldAboutDrag) {
			toldAboutDrag = true;
			LOGGER.info("Built-in config: drag started at ({}, {})", mouseX, mouseY);
		}
	}

	private void dragTo(double mouseX, double mouseY) {
		RecordingHudOverlay.Metrics metrics = metrics();
		int originX = (int) Math.round(mouseX - dragOffsetX);
		int originY = (int) Math.round(mouseY - dragOffsetY);
		int contentHeight = metrics == null ? 0 : metrics.contentHeight();

		// Keep the indicator origin inside the screen so it can never be dragged out of reach.
		int x = clamp(originX - this.width / 2, -this.width / 2, this.width / 2);
		int y = clamp(originY - this.height / 2 + contentHeight / 2, -this.height / 2, this.height / 2);
		draft.positionX = x;
		draft.positionY = y;
		syncPosBoxes();
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		if (dragging) {
			dragTo(mouseX, mouseY);
		}

		RecordingHudOverlay.Metrics metrics = metrics();
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

		if (metrics != null && metrics.contentWidth() > 0) {
			int originX = previewOriginX();
			int originY = previewOriginY(metrics);
			int pad = metrics.padding();
			int x0 = originX - pad;
			int y0 = originY - pad;
			int x1 = originX + metrics.contentWidth() + pad;
			int y1 = originY + metrics.contentHeight() + pad;

			if (dragging) {
				// Dashed-looking outline so it is obvious what is being dragged.
				int color = 0xFFFFFFFF;
				graphics.fill(x0, y0, x1, y0 + 1, color);
				graphics.fill(x0, y1 - 1, x1, y1, color);
				graphics.fill(x0, y0, x0 + 1, y1, color);
				graphics.fill(x1 - 1, y0, x1, y1, color);
				graphics.text(this.font, Component.literal(draft.positionX + ", " + draft.positionY),
					x1 + 4, y0, color, true);
			}
		}

		graphics.text(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, panelY - 14, 0xFFFFFFFF, true);
		for (int i = 0; i < labelPos.size() && i < labelText.size(); i++) {
			int[] pos = labelPos.get(i);
			graphics.text(this.font, labelText.get(i), pos[0], pos[1], 0xFFCCCCCC, false);
		}

		Component hint = tr("obs_rec_indicator.gui.drag_hint");
		graphics.text(this.font, hint, this.width / 2 - this.font.width(hint) / 2,
			this.height - 12, 0xFFAAAAAA, false);
	}

	private static boolean isPrimaryButton(MouseButtonEvent event) {
		return event != null
			&& (event.button() == PRIMARY_BUTTON || event.button() == LEGACY_PRIMARY_BUTTON);
	}

	/**
	 * Whether the cursor is on the previewed indicator.
	 *
	 * <p>The hit box gets a small margin so the indicator stays easy to grab when it is only a few
	 * pixels across.</p>
	 */
	private boolean isOnIndicator(double mouseX, double mouseY) {
		RecordingHudOverlay.Metrics metrics = metrics();
		if (metrics == null || metrics.contentWidth() <= 0 || metrics.contentHeight() <= 0) {
			return false;
		}
		int padding = metrics.padding() + GRAB_MARGIN;
		int originX = previewOriginX();
		int originY = previewOriginY(metrics);
		return mouseX >= originX - padding
			&& mouseX < originX + metrics.contentWidth() + padding
			&& mouseY >= originY - padding
			&& mouseY < originY + metrics.contentHeight() + padding;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// Only the indicator itself starts a drag; widgets and the empty screen keep their clicks.
		if (isPrimaryButton(event)
			&& this.getChildAt(event.x(), event.y()).isEmpty()
			&& isOnIndicator(event.x(), event.y())) {
			beginDrag(event.x(), event.y());
			dragTo(event.x(), event.y());
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging) {
			dragTo(event.x(), event.y());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public void mouseMoved(double x, double y) {
		super.mouseMoved(x, y);
		// Some paths deliver movement without a drag callback; keep following the cursor anyway.
		if (dragging) {
			dragTo(x, y);
		}
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	/**
	 * Closes the screen, saving first.
	 *
	 * <p>Escape lands here, so closing with Escape behaves exactly like pressing Save: the
	 * widgets are read back and the config is written. Only Cancel skips the save, which is what
	 * that button promises.</p>
	 */
	@Override
	public void onClose() {
		if (closed) {
			return;
		}
		closed = true;
		if (!discarding) {
			applyAndSaveDraft();
		}
		dragging = false;
		RecordingHudOverlay.setScreenPreviewActive(false);
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(this.parent);
		}
	}

	/** Reads the widgets back into the draft config and writes it to disk. */
	private void applyAndSaveDraft() {
		pullFields();
		ConfigManager.get().applyFrom(draft);
		ConfigManager.save();
		LOGGER.info("Built-in config saved: position=({}, {}) scale={}",
			draft.positionX, draft.positionY, draft.scale);
	}
}
