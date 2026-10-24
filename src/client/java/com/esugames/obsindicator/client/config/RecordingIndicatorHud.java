package com.esugames.obsindicator.client.config;

import com.esugames.obsindicator.RecordState;
import com.esugames.obsindicator.client.ObsRecIndicatorClient;
import com.esugames.obsindicator.client.hud.RecordingHudOverlay;
import com.esugames.obsindicator.config.ConfigManager;
import com.esugames.obsindicator.config.ModConfig;
import androidx.compose.runtime.Composer;
import kotlin.Pair;
import kotlin.Unit;
import kotlin.jvm.functions.Function5;
import net.minecraft.client.Minecraft;
import org.polyfrost.compose.layout.PolySize;
import org.polyfrost.compose.node.CanvasNode;
import org.polyfrost.compose.node.McTextNode;
import org.polyfrost.compose.render.PolyColor;
import org.polyfrost.compose.render.RenderContext;
import org.polyfrost.oneconfig.api.config.v1.Node;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.annotations.Color;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;
import org.polyfrost.oneconfig.api.config.v1.annotations.Text;
import org.polyfrost.oneconfig.api.hud.v1.Hud;
import org.polyfrost.oneconfig.api.hud.v1.HudManager;
import org.polyfrost.oneconfig.api.hud.v1.HudResize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * The recording indicator as a OneConfig HUD.
 *
 * <p>This is what makes the position available from OneConfig itself: OneConfig draws the HUD,
 * its HUD editor is where the indicator is dragged around, and the HUD settings page stores the
 * position and the size (Scale). The options declared here are collected into the HUD's config
 * tree, so they appear in that same settings page as one flat list — the drawing switches first,
 * the text options below them, with no tab row.</p>
 *
 * <p>It is a regular OneConfig HUD, not a {@code LegacyHud}. OneConfig can only place and drag a
 * legacy HUD while a world is loaded, because a legacy HUD has no size of its own until its
 * in-game renderer has run. A regular HUD is measured by OneConfig itself, which is what makes the
 * indicator appear <em>and</em> stay draggable in the HUD editor when the editor is opened from the
 * main menu — the same way every other OneConfig HUD mod behaves.</p>
 *
 * <p>The content is a single {@link CanvasNode}: OneConfig lays that node out like any other HUD
 * content, so the HUD's size, its box, the drag target and the measured bounds all come from
 * OneConfig, while the actual pixels are still drawn by this mod with the very same drawing code
 * the Fabric HUD uses. The node is attached once to the HUD's compose root
 * ({@link #ensureNode()}) and {@link #update()} keeps its size in step with the label, so no Kotlin
 * or compose source is needed for it.</p>
 *
 * <p>Every option is per instance: OneConfig stores the values in the HUD's own config tree
 * ({@code huds/<id>-obs_rec_indicator}), so two indicators on screen can look different. The
 * runtime config only provides the defaults for a newly created copy and is kept up to date from
 * the first (primary) copy, so removing OneConfig later keeps the last look.</p>
 *
 * <p>The background and the text shadow are OneConfig's own HUD options, so this mod declares no
 * switches for them: OneConfig's Background and Appearance sections edit {@code showBackground},
 * {@code bgColor}, {@code bgRadius} and {@code showShadow} directly, and drawing follows them. The
 * shadow is the vanilla glyph shadow — the same one every other OneConfig HUD with the Minecraft
 * font draws — because OneConfig's own texture-font path forwards its shadow switch to exactly
 * that.</p>
 *
 * <p>Only loaded when OneConfig is installed (see {@link OneConfigSupport}). Without OneConfig the
 * Fabric HUD in {@link RecordingHudOverlay} is used exactly as before.</p>
 *
 * <p>Several instances may exist at once: OneConfig duplicates the HUD (its "duplicate" action) and
 * each copy keeps its own position, scale, visibility and look. Deleting is per instance as well —
 * OneConfig removes the instance whose delete button was pressed together with its saved config
 * and leaves the other copies alone.</p>
 */
public final class RecordingIndicatorHud extends Hud {
	private static final Logger LOGGER = LoggerFactory.getLogger("obs_rec_indicator");

	/** Tree id becomes {@code huds/<id>-obs_rec_indicator}. */
	public static final String HUD_ID = "obs_rec_indicator";
	/** Mod id the HUD is listed under in OneConfig's HUD library. */
	private static final String CONFIG_ID = "obs_rec_indicator";
	/** Library icon: the mod's own icon, which OneConfig loads straight from the jar. */
	private static final String ICON = "/assets/obs_rec_indicator/icon.png";
	/** How far each additional copy of the HUD is offset from the previous one. */
	private static final float COPY_CASCADE_PX = 16.0f;

	/** The HUD's title, also the name shown in OneConfig's HUD library. */
	private static final String TITLE = "OBS Recorder Indicator";

	/**
	 * The one category every option below belongs to.
	 *
	 * <p>OneConfig shows a tab row only when an HUD has two or more categories, so keeping them all
	 * in one leaves a single flat list. That is what this HUD wants: the mod only contributes the
	 * five options below — the background and the text shadow are OneConfig's own HUD options and
	 * are edited in OneConfig's Background and Appearance sections.</p>
	 *
	 * <p>The value is a translation key, OneConfig translates it when it exists. It is only used
	 * as the group's name, never shown as a heading.</p>
	 */
	public static final String CATEGORY = "obs_rec_indicator.oneconfig.hud.category";

	/** Vanilla line height of the Minecraft bitmap font, in unscaled pixels. */
	private static final float FONT_LINE_HEIGHT = 9.0f;

	/**
	 * Background of the indicator, matching what the Fabric HUD draws. It is also OneConfig's own
	 * background option, so the editor's Background section starts at the mod's look.
	 */
	private static final int DEFAULT_BACKGROUND_ARGB = 0x66000000;

	/**
	 * Ids of the options this mod contributes to the HUD's config tree.
	 *
	 * <p>Everything else in that tree is OneConfig's own bookkeeping (position, scale, padding,
	 * visibility, merge settings, …). Those are filtered out of the HUD editor's settings list by
	 * {@link #addToSerialized(Tree)} so the editor only shows the options above — without that
	 * they end up in an extra "General" group next to the mod's own options.</p>
	 */
	private static final Set<String> OWN_PROPERTIES = Set.of(
		"drawCircle",
		"drawText",
		"recordingText",
		"pausedText",
		"recordingColor"
	);

	private static boolean registered;
	private static Method bumpUiEpoch;
	private static boolean bumpUiEpochFailed;
	/** Set once drawing turned out to be impossible, so it is not retried on every frame. */
	private static boolean drawingFailed;
	private static boolean drawErrorLogged;

	// Every option shares one category, so the editor shows them as one flat list: the two
	// drawing switches first, then the text options, with no tab row and no headings.
	@Switch(title = "obs_rec_indicator.oneconfig.hud.circle", category = CATEGORY)
	public boolean drawCircle = true;

	@Switch(title = "obs_rec_indicator.oneconfig.hud.text", category = CATEGORY)
	public boolean drawText = true;

	@Text(title = "obs_rec_indicator.oneconfig.hud.text.recording", category = CATEGORY)
	public String recordingText = "REC";

	@Text(title = "obs_rec_indicator.oneconfig.hud.text.paused", category = CATEGORY)
	public String pausedText = "PAUSED";

	@Color(
		title = "obs_rec_indicator.oneconfig.hud.text.color",
		description = "obs_rec_indicator.oneconfig.hud.text.color.desc",
		category = CATEGORY,
		alpha = false
	)
	public PolyColor recordingColor = new PolyColor(0xFFE53935);

	/**
	 * The drawing node attached to this HUD's compose root.
	 *
	 * <p>Per instance, never shared: OneConfig clones the HUD for every extra copy, and
	 * {@link #clone()} drops these so each copy builds its own.</p>
	 */
	private transient CanvasNode canvasNode;
	private transient McTextNode textNode;

	/** Values the runtime config was last updated from/to, so only real edits are written back. */
	private boolean lastCircle;
	private boolean lastText;
	private String lastRecordingText = "";
	private String lastPausedText = "";
	private int lastColor;

	public RecordingIndicatorHud() {
		super(HUD_ID, TITLE, Hud.Category.getINFO());
		// The background is OneConfig's own option, so it starts at the colour and shape the mod
		// itself used to draw with.
		setBgColor(DEFAULT_BACKGROUND_ARGB);
		setBgRadius(0.0f);
		seedFromRuntime();
		rememberValues();
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
			LOGGER.info("Registered OBS Recorder Indicator as a OneConfig HUD");
		} catch (Throwable t) {
			LOGGER.warn("OneConfig HUD registration failed: {}", t.toString());
			return false;
		}
		return true;
	}

	/**
	 * Rebuilds the HUD editor's settings panel for every live copy of this HUD.
	 *
	 * <p>OneConfig resolves the tab titles once, when the panel is first built, and keeps them for
	 * as long as that panel stays composed. Changing Minecraft's language therefore used to leave
	 * the tabs in the old language until the game was restarted. Bumping OneConfig's per-HUD UI
	 * counter throws the panel away so it is built again, this time from the current language.</p>
	 *
	 * <p>Called once per client tick by {@link OneConfigSupport} when the language changed.
	 * OneConfig keeps that counter private, so it is reached reflectively; if that ever fails the
	 * tabs simply keep their old behaviour and everything else still works.</p>
	 */
	public static void refreshEditorLabels() {
		if (bumpUiEpochFailed) {
			return;
		}
		try {
			if (bumpUiEpoch == null) {
				Class<?> holder = Class.forName(
					"org.polyfrost.oneconfig.internal.ui.hud.HudSettingsResetKt");
				Method method = holder.getDeclaredMethod("bumpHudUiEpoch", Hud.class);
				method.setAccessible(true);
				bumpUiEpoch = method;
			}
			for (Hud hud : HudManager.INSTANCE.getActiveInstances()) {
				if (hud instanceof RecordingIndicatorHud) {
					bumpUiEpoch.invoke(null, hud);
				}
			}
		} catch (Throwable t) {
			bumpUiEpochFailed = true;
			LOGGER.debug("HUD editor labels are not refreshed on language changes: {}", t.toString());
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
	 * <p>Only the primary copy writes its look back into {@link ModConfig}: the runtime config is a
	 * single shared record, and letting every copy write to it would make them fight over it.</p>
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

	/**
	 * The HUD has nothing for compose to emit: the indicator is drawn by {@link #canvasNode}.
	 *
	 * <p>OneConfig still asks every HUD for a content tree, and an empty one is exactly right here
	 * — the node is attached to the resulting root in {@link #ensureNode()}.</p>
	 */
	@Override
	public void Content(Composer composer, int changed) {
	}

	@Override
	public void setup() {
		super.setup();
		// Re-measure immediately when an option is changed instead of waiting for the next frame.
		updateWhenChanged("drawCircle");
		updateWhenChanged("drawText");
		updateWhenChanged("recordingText");
		updateWhenChanged("pausedText");
		updateWhenChanged("recordingColor");
	}

	/**
	 * Sizes the drawing node and keeps {@link ModConfig} in sync with the primary copy.
	 *
	 * <p>The HUD is measured by OneConfig from the node attached here, which is what gives it a box
	 * — and therefore a drag target — even while no world is loaded. The values themselves are
	 * never read back from {@link ModConfig}: each instance keeps its own saved options, so a copy
	 * added later starts from the runtime config and is independent after that.</p>
	 */
	@Override
	public boolean update() {
		CanvasNode node = ensureNode();
		if (node != null) {
			resizeNode(node);
		}

		if (!isPrimary(this)) {
			rememberValues();
			return true;
		}

		ModConfig config = ConfigManager.get();
		boolean changed = false;

		// OneConfig owns the background and the text shadow, so its values are the ones to keep.
		// They are mirrored into the runtime config so dropping OneConfig later keeps the look.
		boolean showBackground = getShowBackground();
		if (config.showBackground != showBackground) {
			config.showBackground = showBackground;
			changed = true;
		}

		boolean showShadow = getShowShadow();
		if (config.showShadow != showShadow) {
			config.showShadow = showShadow;
			changed = true;
		}

		if (drawCircle != lastCircle) {
			config.showCircle = drawCircle;
			changed = true;
		}
		lastCircle = drawCircle;

		if (drawText != lastText) {
			config.showText = drawText;
			changed = true;
		}
		lastText = drawText;

		String recording = textOrDefault(recordingText);
		if (!recording.equals(lastRecordingText)) {
			config.recordingText = recording;
			lastRecordingText = recording;
			changed = true;
		}

		String paused = textOrDefault(pausedText);
		if (!paused.equals(lastPausedText)) {
			config.pausedText = paused;
			lastPausedText = paused;
			changed = true;
		}

		int color = colorArgb();
		if (color != lastColor) {
			config.recordingColor = hex(color);
			lastColor = color;
			changed = true;
		}

		if (changed) {
			ConfigManager.save();
		}
		return true;
	}

	/**
	 * Attaches the drawing node to this HUD's compose root, once per instance.
	 *
	 * <p>OneConfig lays the root out itself, so the node's box is what OneConfig measures and drags.
	 * The node is added to the root directly rather than from a composable, because the mod is
	 * plain Java while OneConfig's HUD content API is compose-based; an empty {@link #Content} keeps
	 * the composition itself from ever touching the root's children.</p>
	 */
	private CanvasNode ensureNode() {
		if (canvasNode != null) {
			return canvasNode;
		}
		if (drawingFailed) {
			return null;
		}
		try {
			CanvasNode node = new CanvasNode();
			Function5<RenderContext, Float, Float, Float, Float, Unit> onDraw =
				(ctx, x, y, width, height) -> {
					draw(ctx, x, y, width, height);
					return Unit.INSTANCE;
				};
			node.setOnDraw(onDraw);
			textNode = new McTextNode();
			getRuntime().getRoot().getChildren().add(node);
			canvasNode = node;
			return node;
		} catch (Throwable t) {
			drawingFailed = true;
			markDrawingFailed();
			if (!drawErrorLogged) {
				drawErrorLogged = true;
				LOGGER.warn("OneConfig HUD drawing node unavailable, using the mod's own HUD: {}",
					t.toString());
			}
			return null;
		}
	}

	/**
	 * Tells {@link OneConfigSupport} to hand drawing back to the mod's own Fabric HUD.
	 *
	 * <p>Used only when OneConfig's HUD drawing cannot be driven from here, which would otherwise
	 * leave the indicator invisible instead of merely undraggable.</p>
	 */
	private static void markDrawingFailed() {
		try {
			OneConfigSupport.markHudDrawingFailed();
		} catch (Throwable ignored) {
			// Nothing else to do: the indicator is simply not drawn.
		}
	}

	/** Gives the node this frame's size so OneConfig measures the HUD around the indicator. */
	private void resizeNode(CanvasNode node) {
		RecordingHudOverlay.Metrics metrics = currentMetrics();
		float width = metrics == null ? 1.0f : metrics.contentWidth() + metrics.padding() * 2.0f;
		float height = metrics == null ? 1.0f : metrics.contentHeight() + metrics.padding() * 2.0f;
		node.getStyle().setWidth(new PolySize.Px(Math.max(1.0f, width)));
		node.getStyle().setHeight(new PolySize.Px(Math.max(1.0f, height)));
	}

	/**
	 * Paints the indicator into the HUD's box.
	 *
	 * @param x      left edge of the HUD box, which already includes the padding
	 * @param y      top edge of the HUD box
	 * @param width  width of the box
	 * @param height height of the box
	 */
	private void draw(RenderContext ctx, float x, float y, float width, float height) {
		if (drawingFailed) {
			return;
		}
		try {
			paint(ctx, x, y, width, height);
		} catch (Throwable t) {
			// A HUD that throws every frame would break OneConfig's whole HUD pass, so the
			// indicator stops drawing instead and the mod's own Fabric HUD takes over.
			drawingFailed = true;
			markDrawingFailed();
			if (!drawErrorLogged) {
				drawErrorLogged = true;
				LOGGER.warn("OneConfig HUD drawing stopped, using the mod's own HUD: {}", t.toString());
			}
		}
	}

	private void paint(RenderContext ctx, float x, float y, float width, float height) {
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

		String label = labelFor(state);
		RecordingHudOverlay.Metrics metrics = RecordingHudOverlay.metrics(
			minecraft, drawCircle, drawText, config.circleRadius, label, 1.0f);
		if (metrics.contentWidth() <= 0 || metrics.contentHeight() <= 0) {
			return;
		}

		int color = state == RecordState.PAUSED ? config.pausedColorArgb() : colorArgb();
		float padding = metrics.padding();

		if (getShowBackground()) {
			ctx.rect(x, y, width, height,
				new PolyColor(getBgColor(), getBgChroma(), getBgChromaSpeed()), getBgRadius());
		}

		if (metrics.showCircle()) {
			float radius = metrics.radius();
			float centerX = x + padding + radius;
			float centerY = y + padding + metrics.contentHeight() / 2.0f;
			// A rounded rect with the radius of half its width is a circle.
			ctx.rect(centerX - radius, centerY - radius, radius * 2.0f, radius * 2.0f,
				new PolyColor(color), radius);
		}

		if (!metrics.showText()) {
			return;
		}

		float textX = x + padding + (metrics.showCircle() ? metrics.radius() * 2.0f + metrics.gap() : 0.0f);
		float textY = y + padding + (metrics.contentHeight() - FONT_LINE_HEIGHT) / 2.0f + 0.5f;
		// The text is OneConfig's own text colour next to the circle and takes the indicator colour
		// when the circle is off, which is what the colour option's description promises.
		PolyColor textColor = metrics.showCircle()
			? new PolyColor(getTextColor(), getTextChroma(), getTextChromaSpeed())
			: new PolyColor(color);
		drawText(ctx, label, textX, textY, textColor);
	}

	/** Draws {@code label} with the Minecraft font at an absolute position. */
	private void drawText(RenderContext ctx, String label, float textX, float textY, PolyColor color) {
		McTextNode node = textNode;
		if (node == null) {
			return;
		}
		node.setText(label);
		node.setColor(color);
		// The vanilla glyph shadow, the same one every other OneConfig HUD with the Minecraft font
		// draws: OneConfig's texture-font path forwards its "Text Shadow" switch to exactly this.
		node.setShadow(getShowShadow());
		node.setScale(1.0f);
		node.setX(textX);
		node.setY(textY);
		node.render(ctx);
	}

	/**
	 * Hides OneConfig's own tree entries from the HUD editor's option list.
	 *
	 * <p>Only this mod's options belong there. OneConfig's bookkeeping nodes would otherwise show up
	 * as an extra "General" tab next to the mod's own tabs; the HUD editor skips anything carrying
	 * the {@code hudInternal} metadata.</p>
	 */
	@Override
	protected void addToSerialized(Tree tree) {
		super.addToSerialized(tree);
		try {
			for (var entry : tree.map.entrySet()) {
				Node node = entry.getValue();
				if (node != null && !OWN_PROPERTIES.contains(entry.getKey())) {
					node.addMetadata("hudInternal", Boolean.TRUE);
				}
			}
		} catch (Throwable t) {
			LOGGER.debug("Failed to hide OneConfig HUD internals from the editor: {}", t.toString());
		}
	}

	@Override
	public boolean showByDefault() {
		return true;
	}

	/** The indicator is a fixed-size HUD: OneConfig gets no corner handles for it. */
	@Override
	public HudResize getResizeAxes() {
		return HudResize.None;
	}

	/** This HUD paints its own background, so OneConfig must not add one of its own. */
	@Override
	public boolean hasBackground() {
		return false;
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
		float boxHeight = metrics == null ? 0.0f : metrics.contentHeight() + padding * 2.0f;

		// ModConfig stores the content origin; the HUD box starts one padding earlier.
		float x = screenWidth / 2.0f + config.positionX - padding;
		float y = screenHeight / 2.0f + config.positionY - boxHeight / 2.0f;

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
	public Hud clone() {
		Hud copy = super.clone();
		if (copy instanceof RecordingIndicatorHud indicator) {
			// Each copy draws through its own node; the root they hang off is per instance too.
			indicator.canvasNode = null;
			indicator.textNode = null;
		}
		return copy;
	}

	/** Recording colour of this instance, as an ARGB value. */
	public int colorArgb() {
		PolyColor color = recordingColor;
		return color == null ? 0xFFE53935 : color.getArgb();
	}

	/** Recording text of this instance, never {@code null}. */
	public String currentRecordingText() {
		return textOrDefault(recordingText);
	}

	/** Paused text of this instance, never {@code null}. */
	public String currentPausedText() {
		return textOrDefault(pausedText);
	}

	private void seedFromRuntime() {
		ModConfig config = ConfigManager.get();
		// Background and shadow are OneConfig's own options; a fresh copy starts at the values
		// this mod's own config holds so upgrading keeps the current look.
		setShowBackground(config.showBackground);
		setShowShadow(config.showShadow);
		drawCircle = config.showCircle;
		drawText = config.showText;
		recordingText = config.recordingText;
		pausedText = config.pausedText;
		recordingColor = new PolyColor(config.recordingColorArgb());
	}

	private void rememberValues() {
		lastCircle = drawCircle;
		lastText = drawText;
		lastRecordingText = textOrDefault(recordingText);
		lastPausedText = textOrDefault(pausedText);
		lastColor = colorArgb();
	}

	private static String textOrDefault(String value) {
		return value == null ? "" : value;
	}

	private static String hex(int argb) {
		return String.format("#%06X", argb & 0x00FFFFFF);
	}

	private RecordingHudOverlay.Metrics currentMetrics() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.font == null) {
			return null;
		}
		ModConfig config = ConfigManager.get();
		return RecordingHudOverlay.metrics(
			minecraft, drawCircle, drawText, config.circleRadius, labelFor(currentState()), 1.0f);
	}

	private static RecordState currentState() {
		var client = ObsRecIndicatorClient.obsClient();
		return client == null ? RecordState.IDLE : client.currentRecordState();
	}

	private String labelFor(RecordState state) {
		return state == RecordState.PAUSED ? currentPausedText() : currentRecordingText();
	}
}
