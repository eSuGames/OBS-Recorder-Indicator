package com.esugames.obsindicator.client.config;

import com.esugames.obsindicator.RecordState;
import com.esugames.obsindicator.client.ObsRecIndicatorClient;
import com.esugames.obsindicator.client.hud.RecordingHudOverlay;
import com.esugames.obsindicator.config.ConfigManager;
import com.esugames.obsindicator.config.ModConfig;
import kotlin.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;
import org.polyfrost.oneconfig.api.hud.v1.Hud;
import org.polyfrost.oneconfig.api.hud.v1.HudManager;
import org.polyfrost.oneconfig.api.hud.v1.LegacyHud;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The recording indicator as a OneConfig HUD.
 *
 * <p>This is what makes the position available from OneConfig itself: OneConfig draws the HUD,
 * its HUD editor is where the indicator is dragged around, and the HUD settings page stores the
 * position and the size (Scale). The switches declared here are collected into the HUD's config
 * tree, so Background / Shadow / Circle / Text appear in that same settings page right below
 * Position and Scale.</p>
 *
 * <p>Only loaded when OneConfig is installed (see {@link OneConfigSupport}). Without OneConfig the
 * Fabric HUD in {@link RecordingHudOverlay} is used exactly as before.</p>
 *
 * <p>The HUD renders at its natural size and OneConfig applies the user's Scale on top of it, so
 * the indicator is never scaled twice. The switches are mirrored into {@link ModConfig} so the
 * rest of the mod (and the built-in screen, which stays available as a fallback) sees the same
 * values.</p>
 *
 * <p>Several instances may exist at once: OneConfig duplicates the HUD (its "duplicate" action) and
 * each copy keeps its own position, scale and visibility. Deleting is per instance as well —
 * OneConfig removes the instance whose delete button was pressed together with its saved config
 * and leaves the other copies alone. The display switches are deliberately shared through
 * {@link ModConfig}, so every copy of the indicator looks the same.</p>
 */
public final class RecordingIndicatorHud extends LegacyHud {
	private static final Logger LOGGER = LoggerFactory.getLogger("obs_rec_indicator");

	/** Tree id becomes {@code huds/<id>-obs_rec_indicator}. */
	public static final String HUD_ID = "obs_rec_indicator";
	/** Mod id the HUD is listed under in OneConfig's HUD library. */
	private static final String CONFIG_ID = "obs_rec_indicator";
	/** Library icon: the mod's own icon, which OneConfig loads straight from the jar. */
	private static final String ICON = "/assets/obs_rec_indicator/icon.png";
	/** How far each additional copy of the HUD is offset from the previous one. */
	private static final float COPY_CASCADE_PX = 16.0f;

	private static boolean registered;

	@Switch(
		title = "obs_rec_indicator.oneconfig.hud.background",
		description = "obs_rec_indicator.oneconfig.hud.background.desc",
		category = "obs_rec_indicator.oneconfig.hud.category"
	)
	public boolean drawBackground = true;

	@Switch(
		title = "obs_rec_indicator.oneconfig.hud.shadow",
		description = "obs_rec_indicator.oneconfig.hud.shadow.desc",
		category = "obs_rec_indicator.oneconfig.hud.category"
	)
	public boolean drawShadow = true;

	@Switch(
		title = "obs_rec_indicator.oneconfig.hud.circle",
		description = "obs_rec_indicator.oneconfig.hud.circle.desc",
		category = "obs_rec_indicator.oneconfig.hud.category"
	)
	public boolean drawCircle = true;

	@Switch(
		title = "obs_rec_indicator.oneconfig.hud.text",
		description = "obs_rec_indicator.oneconfig.hud.text.desc",
		category = "obs_rec_indicator.oneconfig.hud.category"
	)
	public boolean drawText = true;

	/** Values the switches had when the runtime config was last read/written. */
	private boolean lastBackground;
	private boolean lastShadow;
	private boolean lastCircle;
	private boolean lastText;

	public RecordingIndicatorHud() {
		super(HUD_ID, "OBS Rec Indicator", Hud.Category.getINFO());
		readFromRuntime();
	}

	/**
	 * Registers the HUD with OneConfig.
	 *
	 * @return {@code true} once the HUD is registered
	 */
	public static boolean register() {
		if (registered) {
			return true;
		}
		try {
			// The config id groups the HUD under this mod in OneConfig's HUD library, which is
			// also where a deleted indicator is added back from.
			HudManager.register(new RecordingIndicatorHud(), CONFIG_ID, ICON);
			registered = true;
			LOGGER.info("Registered OBS Rec Indicator as a OneConfig HUD");
			return true;
		} catch (Throwable t) {
			LOGGER.warn("OneConfig HUD registration failed: {}", t.toString());
			return false;
		}
	}

	/** Number of copies of this HUD that are currently on screen. */
	public static int liveInstanceCount() {
		int count = 0;
		try {
			for (Hud hud : HudManager.INSTANCE.getActiveInstances()) {
				if (hud instanceof RecordingIndicatorHud) {
					count++;
				}
			}
		} catch (Throwable t) {
			LOGGER.debug("OneConfig HUD instance count failed: {}", t.toString());
		}
		return count;
	}

	/**
	 * Whether {@code hud} is the first live instance of this HUD.
	 *
	 * <p>Only the primary copy writes its position and switches back into {@link ModConfig}: with
	 * several indicators on screen there is one shared config, and letting every copy write to it
	 * would make them fight over the saved position.</p>
	 */
	private static boolean isPrimary(Hud hud) {
		try {
			for (Hud other : HudManager.INSTANCE.getActiveInstances()) {
				if (other instanceof RecordingIndicatorHud) {
					return other == hud;
				}
			}
		} catch (Throwable t) {
			LOGGER.debug("OneConfig HUD primary check failed: {}", t.toString());
		}
		return true;
	}

	@Override
	public void setup() {
		super.setup();
		// ModConfig stays the source of truth; the collected switches are only a view of it.
		readFromRuntime();
		// Re-measure immediately when a switch is flipped instead of waiting for the next frame.
		updateWhenChanged("drawBackground");
		updateWhenChanged("drawShadow");
		updateWhenChanged("drawCircle");
		updateWhenChanged("drawText");
	}

	@Override
	public boolean update() {
		ModConfig config = ConfigManager.get();
		boolean changed = false;

		if (drawBackground != lastBackground) {
			config.showBackground = drawBackground;
			changed = true;
		} else if (config.showBackground != lastBackground) {
			drawBackground = config.showBackground;
		}
		lastBackground = drawBackground;

		if (drawShadow != lastShadow) {
			config.showShadow = drawShadow;
			changed = true;
		} else if (config.showShadow != lastShadow) {
			drawShadow = config.showShadow;
		}
		lastShadow = drawShadow;

		if (drawCircle != lastCircle) {
			config.showCircle = drawCircle;
			changed = true;
		} else if (config.showCircle != lastCircle) {
			drawCircle = config.showCircle;
		}
		lastCircle = drawCircle;

		if (drawText != lastText) {
			config.showText = drawText;
			changed = true;
		} else if (config.showText != lastText) {
			drawText = config.showText;
		}
		lastText = drawText;

		if (changed) {
			ConfigManager.save();
			// Keep the standard config page showing the same values.
			ObsIndicatorOneConfig.reloadFromRuntime();
		}
		return true;
	}

	@Override
	public float getWidth() {
		RecordingHudOverlay.Metrics metrics = currentMetrics();
		if (metrics == null || metrics.contentWidth() <= 0) {
			return 1.0f;
		}
		return metrics.contentWidth() + metrics.padding() * 2.0f;
	}

	@Override
	public float getHeight() {
		RecordingHudOverlay.Metrics metrics = currentMetrics();
		if (metrics == null || metrics.contentHeight() <= 0) {
			return 1.0f;
		}
		return metrics.contentHeight() + metrics.padding() * 2.0f;
	}

	@Override
	public boolean showByDefault() {
		return true;
	}

	/**
	 * Lets OneConfig show its delete button for this HUD.
	 *
	 * <p>Deleting is per instance: OneConfig drops the instance whose delete button was pressed
	 * (and only that instance's saved config) and leaves every other copy running. Once every copy
	 * is gone the indicator stays gone — it is not re-created on the next launch, and the Fabric
	 * fallback HUD stays off — until it is added again from OneConfig's HUD library.</p>
	 */
	@Override
	public boolean deletable() {
		return true;
	}

	@Override
	public Pair<Float, Float> defaultPosition() {
		ModConfig config = ConfigManager.get();
		Minecraft minecraft = Minecraft.getInstance();
		float screenWidth = 640.0f;
		float screenHeight = 360.0f;
		if (minecraft != null && minecraft.getWindow() != null) {
			screenWidth = minecraft.getWindow().getGuiScaledWidth();
			screenHeight = minecraft.getWindow().getGuiScaledHeight();
		}

		RecordingHudOverlay.Metrics metrics = currentMetrics();
		float padding = metrics == null ? 3.0f : metrics.padding();

		// ModConfig stores the content origin; the HUD box starts one padding earlier.
		float x = screenWidth / 2.0f + config.positionX - padding;
		float y = screenHeight / 2.0f + config.positionY - getHeight() / 2.0f;

		// Duplicates (and "reset position") cascade instead of stacking exactly on top of the
		// copies that are already on screen, so a new instance is always visible right away.
		float offset = COPY_CASCADE_PX * liveInstanceCount();
		return new Pair<>(x + offset, y + offset);
	}

	@Override
	public void onEditorDragEnd() {
		// OneConfig already saved this copy's position in its own config tree. Only the primary
		// copy also mirrors it into ModConfig, otherwise copies would overwrite each other's
		// saved position.
		if (!isPrimary(this)) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.getWindow() == null) {
			return;
		}
		RecordingHudOverlay.Metrics metrics = currentMetrics();
		if (metrics == null) {
			return;
		}

		float scale = getEffectiveScale();
		float screenWidth = minecraft.getWindow().getGuiScaledWidth();
		float screenHeight = minecraft.getWindow().getGuiScaledHeight();

		// Translate the dropped HUD box back into ModConfig's screen-centre origin so the position
		// survives removing OneConfig later.
		float contentLeft = getX() + metrics.padding() * scale;
		float contentTop = getY() + metrics.padding() * scale;
		float contentHeight = metrics.contentHeight() * scale;

		ModConfig config = ConfigManager.get();
		config.positionX = Math.round(contentLeft - screenWidth / 2.0f);
		config.positionY = Math.round(contentTop + contentHeight / 2.0f - screenHeight / 2.0f);
		ConfigManager.save();
	}

	@Override
	public void render(GuiGraphicsExtractor graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.font == null) {
			return;
		}

		ModConfig config = ConfigManager.get();
		RecordState state = currentState();
		boolean editing = HudManager.INSTANCE.isEditorOpen();

		// Outside the editor nothing is drawn while not recording (or while the indicator is off).
		if (!editing && (!config.enabled || !state.isIndicatorVisible())) {
			return;
		}

		String label = labelFor(state, config);
		RecordingHudOverlay.Metrics metrics =
			RecordingHudOverlay.metrics(minecraft, config, label, 1.0f);
		if (metrics.contentWidth() <= 0 || metrics.contentHeight() <= 0) {
			return;
		}

		int color = state == RecordState.PAUSED ? config.pausedColorArgb() : config.recordingColorArgb();
		// OneConfig already translated the pose to the HUD position and applied the Scale.
		RecordingHudOverlay.drawAt(graphics, minecraft, config, label, color, metrics,
			metrics.padding(), metrics.padding());
	}

	private void readFromRuntime() {
		ModConfig config = ConfigManager.get();
		drawBackground = config.showBackground;
		drawShadow = config.showShadow;
		drawCircle = config.showCircle;
		drawText = config.showText;
		lastBackground = drawBackground;
		lastShadow = drawShadow;
		lastCircle = drawCircle;
		lastText = drawText;
	}

	private RecordingHudOverlay.Metrics currentMetrics() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.font == null) {
			return null;
		}
		ModConfig config = ConfigManager.get();
		return RecordingHudOverlay.metrics(minecraft, config, labelFor(currentState(), config), 1.0f);
	}

	private static RecordState currentState() {
		var client = ObsRecIndicatorClient.obsClient();
		return client == null ? RecordState.IDLE : client.currentRecordState();
	}

	private static String labelFor(RecordState state, ModConfig config) {
		String label = state == RecordState.PAUSED ? config.pausedText : config.recordingText;
		return label == null ? "" : label;
	}
}
