package com.esugames.obsindicator.client.config;

import com.esugames.obsindicator.RecordState;
import com.esugames.obsindicator.client.ObsRecIndicatorClient;
import com.esugames.obsindicator.config.ConfigManager;
import com.esugames.obsindicator.config.ModConfig;
import androidx.compose.runtime.Composer;
import kotlin.Pair;
import kotlin.Unit;
import kotlin.jvm.functions.Function5;
import net.minecraft.client.Minecraft;
import org.polyfrost.compose.layout.PolyAlign;
import org.polyfrost.compose.layout.PolySize;
import org.polyfrost.compose.mc.McFontQueue;
import org.polyfrost.compose.node.CanvasNode;
import org.polyfrost.compose.node.McTextNode;
import org.polyfrost.compose.render.PolyColor;
import org.polyfrost.compose.render.PolyFont;
import org.polyfrost.compose.render.RenderContext;
import org.polyfrost.oneconfig.api.config.v1.Node;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.annotations.Color;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;
import org.polyfrost.oneconfig.api.config.v1.annotations.Text;
import org.polyfrost.oneconfig.api.hud.v1.Font;
import org.polyfrost.oneconfig.api.hud.v1.Hud;
import org.polyfrost.oneconfig.api.hud.v1.HudManager;
import org.polyfrost.oneconfig.api.hud.v1.HudResize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * OneConfig, while the actual pixels are still drawn by this mod with the very same circle drawing
 * code the Fabric HUD uses. The node is attached once to the HUD's compose root
 * ({@link #ensureNode()}) and {@link #update()} keeps its size in step with the label, so no Kotlin
 * or compose source is needed for it.</p>
 *
 * <p>Every option is per instance: OneConfig stores the values in the HUD's own config tree
 * ({@code huds/<id>-obs_rec_indicator}), so two indicators on screen can look different. The
 * runtime config only provides the defaults for a newly created copy and is kept up to date from
 * the first (primary) copy, so removing OneConfig later keeps the last look.</p>
 *
 * <p><strong>OneConfig's own HUD customizations are all honored</strong> and painted here the same
 * way OneConfig's own text HUDs paint them:</p>
 * <ul>
 *   <li><em>Font</em> — the Minecraft bitmap font (with {@code §l}/{@code §o}/{@code §n} styling)
 *       or OneConfig's Poppins font, whose variant (thin/regular/medium/bold/black, italic) comes
 *       from the Bold/Italic/Weight options exactly like {@code getPoppinsFontName()} spells it;</li>
 *   <li><em>Text style</em> — scale, bold, italic, underline, weight and the case option
 *       (unchanged / upper / lower);</li>
 *   <li><em>Colours</em> — text colour with chroma and chroma speed;</li>
 *   <li><em>Shadow</em> — on/off, drop shadow or full outline ("Full Shadow"), its colour with
 *       chroma, and the drop shadow offsets. The Minecraft font's drop shadow is the vanilla glyph
 *       shadow (OneConfig's own texture-font path forwards its shadow switch to exactly that) and
 *       the full shadow is the outlined glyph path; Poppins draws the shadow with its own
 *       offset/outline rules;</li>
 *   <li><em>Background</em> — on/off, colour with chroma, corner radius;</li>
 *   <li><em>Padding</em> — OneConfig's per-side padding replaces the mod's fixed 3px box padding;</li>
 *   <li><em>Static size &amp; content alignment</em> — a fixed HUD box aligns the indicator inside
 *       it with OneConfig's alignment picker.</li>
 * </ul>
 *
 * <p>The text is laid out and drawn exactly like OneConfig's {@code TextHud}: the Poppins path
 * uses the same font size ({@code 8 * textScale}), baseline and underline maths, and the Minecraft
 * path goes through the same {@link McTextNode} with the same formatting codes, so the indicator
 * and a neighbouring OneConfig text HUD always agree.</p>
 *
 * <p>Only loaded when OneConfig is installed (see {@link OneConfigSupport}). Without OneConfig the
 * Fabric HUD in {@code RecordingHudOverlay} is used exactly as before.</p>
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
	 * five options below — fonts, text style, colours, the shadow and the background are OneConfig's
	 * own HUD options and are edited in OneConfig's Text and Background sections.</p>
	 *
	 * <p>The value is a translation key, OneConfig translates it when it exists. It is only used
	 * as the group's name, never shown as a heading.</p>
	 */
	public static final String CATEGORY = "obs_rec_indicator.oneconfig.hud.category";

	/**
	 * Background of the indicator, matching what the Fabric HUD draws. It is also OneConfig's own
	 * background option, so the editor's Background section starts at the mod's look.
	 */
	private static final int DEFAULT_BACKGROUND_ARGB = 0x66000000;

	/** OneConfig's Poppins text HUDs render at this size before the text scale is applied. */
	private static final float POPPINS_BASE_SIZE = 8.0f;

	/** Gap between the circle and the text when both are drawn, in unscaled pixels. */
	private static final float CIRCLE_TEXT_GAP = 2.0f;

	/**
	 * Ids of the options this mod contributes to the HUD's config tree.
	 *
	 * <p>Everything else in that tree is OneConfig's own bookkeeping (position, scale, padding,
	 * font, shadow, visibility, …). Those are filtered out of the HUD editor's settings list by
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
	/** {@code Hud.getFullShadow()} exists since OneConfig 1.5; older versions get a drop shadow only. */
	private static Boolean fullShadowSupported;

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

	/** Cached Poppins font, rebuilt whenever the name or the size changes. */
	private transient PolyFont poppinsFont;
	private transient String poppinsFontKey;

	/** Values the runtime config was last updated from/to, so only real edits are written back. */
	private boolean lastCircle;
	private boolean lastText;
	private String lastRecordingText = "";
	private String lastPausedText = "";
	private int lastColor;

	/**
	 * Hash of everything the indicator's pixels depend on that OneConfig cannot see by itself.
	 *
	 * <p>OneConfig caches the whole HUD pass and only draws it again when a Compose state read by
	 * the draw pass changed, when the layout/order changed, or when the editor is open. The
	 * recording state comes from OBS and most of the mod's display settings are plain fields, so
	 * changing them changes the indicator without touching any of those states. Opening the
	 * OneConfig GUI used to be what forced a redraw for exactly that reason.</p>
	 */
	private int lastVisualHash;

	public RecordingIndicatorHud() {
		super(HUD_ID, TITLE, Hud.Category.getINFO());
		// The background is OneConfig's own option, so it starts at the colour and shape the mod
		// itself used to draw with.
		setBgColor(DEFAULT_BACKGROUND_ARGB);
		setBgRadius(0.0f);
		// OneConfig's per-side padding replaces the mod's old fixed 3px box padding, so a HUD that
		// keeps its default padding looks exactly like it did before.
		setPadLeft(3.0f);
		setPadRight(3.0f);
		setPadTop(3.0f);
		setPadBottom(3.0f);
		seedFromRuntime();
		rememberValues();
		lastVisualHash = visualHash();
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
			try {
				resizeNode(node);
				// Chroma colours animate with the clock, so the cached HUD pass would freeze them.
				// While any chroma is on the node draws live instead, exactly like OneConfig's own
				// text nodes do for their chroma colours.
				node.setLive(chromaActive());
			} catch (Throwable t) {
				// One API call too new for the installed OneConfig: fall back to the mod's own HUD
				// instead of breaking OneConfig's update loop.
				drawingFailed = true;
				markDrawingFailed();
				if (!drawErrorLogged) {
					drawErrorLogged = true;
					LOGGER.warn("OneConfig HUD measurement failed, using the mod's own HUD: {}",
						t.toString());
				}
			}
		}

		// OneConfig keeps the last HUD pass cached and redraws it only when it notices a change
		// (Compose state, layout, editor). The recording state and the mod's own display options
		// are not Compose state, so tell OneConfig explicitly whenever the pixels would differ.
		// Without this the indicator stays at the state of the last forced redraw, which is why
		// opening the OneConfig GUI made it appear.
		int visualHash = visualHash();
		if (visualHash != lastVisualHash) {
			lastVisualHash = visualHash;
			HudManager.invalidate();
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
		float width;
		float height;
		if (isStaticValid()) {
			width = getStaticW();
			height = getStaticH();
		} else {
			Layout layout = layout();
			width = layout.contentWidth() + getPadLeft() + getPadRight();
			height = layout.contentHeight() + getPadTop() + getPadBottom();
		}
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
		ModConfig config = ConfigManager.get();
		RecordState state = currentState();
		boolean editing = HudManager.INSTANCE.isEditorOpen();

		// Outside the editor nothing is drawn while not recording (or while the indicator is off).
		if (!editing && (!config.enabled || !state.isIndicatorVisible())) {
			return;
		}

		Layout layout = layout();
		if (layout.contentWidth() <= 0 || layout.contentHeight() <= 0) {
			return;
		}

		// The background is OneConfig's own option and covers the whole padded box, exactly like
		// the background of OneConfig's own text HUDs.
		if (getShowBackground()) {
			ctx.rect(x, y, width, height,
				new PolyColor(getBgColor(), getBgChroma(), getBgChromaSpeed()), getBgRadius());
		}

		// The indicator sits inside the padded box; with a static size OneConfig's content
		// alignment decides where, without one the box hugs the indicator and this is (0, 0).
		float innerX = x + getPadLeft();
		float innerY = y + getPadTop();
		float innerWidth = width - getPadLeft() - getPadRight();
		float innerHeight = height - getPadTop() - getPadBottom();
		float contentX = innerX + alignOffsetX(innerWidth - layout.contentWidth());
		float contentY = innerY + alignOffsetY(innerHeight - layout.contentHeight());

		if (layout.showCircle()) {
			float radius = layout.radius();
			float centerX = contentX + radius;
			float centerY = contentY + layout.contentHeight() / 2.0f;
			// A rounded rect with the radius of half its width is a circle.
			int color = state == RecordState.PAUSED ? config.pausedColorArgb() : colorArgb();
			ctx.rect(centerX - radius, centerY - radius, radius * 2.0f, radius * 2.0f,
				new PolyColor(color), radius);
		}

		if (layout.showText()) {
			float textX = contentX + layout.circleBlockWidth();
			float textY = contentY + (layout.contentHeight() - layout.textHeight()) / 2.0f;
			// The text is OneConfig's own text colour next to the circle and takes the indicator
			// colour when the circle is off, which is what the colour option's description
			// promises. The paused indicator's colour has the same rule.
			int color = state == RecordState.PAUSED ? config.pausedColorArgb() : colorArgb();
			PolyColor textColor = layout.showCircle()
				? new PolyColor(getTextColor(), getTextChroma(), getTextChromaSpeed())
				: new PolyColor(color);
			drawLabel(ctx, layout, textX, textY, textColor);
		}
	}

	/**
	 * Draws the label with the font, the styling and the shadow OneConfig's HUD editor describes.
	 *
	 * <p>The Minecraft font path goes through {@link McTextNode} — the very node OneConfig's own
	 * {@code PolyMcText} builds — so formatting codes, the vanilla drop shadow and the outlined
	 * full shadow behave identically. The Poppins path repeats OneConfig's own baseline, shadow
	 * and underline maths, so both HUDs agree pixel for pixel.</p>
	 */
	private void drawLabel(RenderContext ctx, Layout layout, float textX, float textY, PolyColor textColor) {
		float textScale = textScale();
		PolyColor shadowColor = new PolyColor(getShadowColor(), getShadowChroma(), getShadowChromaSpeed());
		boolean shadow = getShowShadow();
		boolean full = shadow && fullShadow();

		if (isPoppins()) {
			PolyFont font = poppinsFont(POPPINS_BASE_SIZE * textScale);
			if (font != null) {
				drawPoppins(ctx, layout, font, textX, textY, textColor, shadow ? shadowColor : null,
					full, getShadowOffsetX(), getShadowOffsetY(), textScale);
				return;
			}
			// No skia font engine (older OneConfig): fall through to the Minecraft font.
		}

		McTextNode node = textNode;
		if (node == null) {
			return;
		}
		node.setText(mcStyledText(layout.label()));
		node.setColor(textColor);
		// The vanilla glyph shadow, the same one every other OneConfig HUD with the Minecraft font
		// draws: OneConfig's texture-font path forwards its "Text Shadow" switch to exactly this.
		// The full shadow replaces it with the outlined glyph path, as OneConfig's own text HUDs do.
		node.setShadow(shadow && !full);
		node.setOutlineColor(full ? shadowColor : null);
		node.setScale(textScale);
		node.setX(textX);
		node.setY(textY);
		node.render(ctx);
	}

	/**
	 * Poppins text drawing, a Java copy of OneConfig's own {@code PoppinsTextNode.draw}.
	 *
	 * <p>Multi-line labels are laid out line by line with the font's own line spacing, and the
	 * underline is drawn as a line at the font's underline position — skia fonts carry no
	 * underline of their own.</p>
	 */
	private void drawPoppins(
		RenderContext ctx,
		Layout layout,
		PolyFont font,
		float x,
		float y,
		PolyColor color,
		PolyColor shadowColor,
		boolean fullShadow,
		float shadowOffsetX,
		float shadowOffsetY,
		float outline
	) {
		PolyFont.Metrics metrics = font.getMetrics();
		float underlinePos = metrics.getUnderlinePosition();
		float underlineThick = metrics.getUnderlineThickness();
		float spacing = metrics.getSpacing();
		float ascent = metrics.getAscent();
		List<String> lines = splitLines(layout.label());
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			if (line.isEmpty()) {
				continue;
			}
			float baseline = y + i * spacing - ascent;
			float lineW = font.measureTextWidth(line);
			if (shadowColor != null && fullShadow) {
				ctx.textOutline(line, x, baseline, shadowColor, font, outline);
				if (getTextUnderline()) {
					ctx.line(x - outline, baseline + underlinePos, x + lineW + outline,
						baseline + underlinePos, shadowColor, underlineThick + outline * 2.0f);
				}
			} else if (shadowColor != null) {
				ctx.text(line, x + shadowOffsetX, baseline + shadowOffsetY, shadowColor, font);
				if (getTextUnderline()) {
					ctx.line(x + shadowOffsetX, baseline + shadowOffsetY + underlinePos,
						x + shadowOffsetX + lineW, baseline + shadowOffsetY + underlinePos,
						shadowColor, underlineThick);
				}
			}
			ctx.text(line, x, baseline, color, font);
			if (getTextUnderline()) {
				ctx.line(x, baseline + underlinePos, x + lineW, baseline + underlinePos, color,
					underlineThick);
			}
		}
	}

	/**
	 * Wraps the label in the formatting codes the Bold / Italic / Underline options ask for.
	 *
	 * <p>The same codes OneConfig's own Minecraft-font text HUD uses, so the bitmap font styling
	 * is exactly what the HUD editor's buttons promise.</p>
	 */
	private String mcStyledText(String label) {
		StringBuilder head = new StringBuilder();
		if (getTextBold()) {
			head.append("§l");
		}
		if (getTextItalic()) {
			head.append("§o");
		}
		if (getTextUnderline()) {
			head.append("§n");
		}
		String tail = head.length() > 0 ? "§r" : "";
		return head + label + tail;
	}

	/** Natural geometry of the indicator's content for the current options. */
	private Layout layout() {
		ModConfig config = ConfigManager.get();
		String label = labelFor(currentState());
		float textScale = textScale();
		boolean showText = drawText && !label.isEmpty();
		float radius = config.circleRadius;

		float textWidth = 0.0f;
		float textHeight = 0.0f;
		if (showText) {
			String measured = isPoppins() ? label : mcStyledText(label);
			List<String> lines = splitLines(measured);
			if (isPoppins()) {
				PolyFont font = poppinsFont(POPPINS_BASE_SIZE * textScale);
				if (font != null) {
					PolyFont.Metrics metrics = font.getMetrics();
					for (String line : lines) {
						textWidth = Math.max(textWidth, font.measureTextWidth(line));
					}
					textHeight = (lines.size() - 1) * metrics.getSpacing()
						+ (metrics.getDescent() - metrics.getAscent());
				} else {
					showText = false;
				}
			} else {
				textWidth = McFontQueue.INSTANCE.measureTextWidth(measured, textScale);
				textHeight = McFontQueue.INSTANCE.measureBlockHeight(lines.size(), textScale);
			}
		}

		float circleBlock = drawCircle
			? radius * 2.0f + (showText ? CIRCLE_TEXT_GAP : 0.0f)
			: 0.0f;
		float contentWidth = circleBlock + (showText ? textWidth : 0.0f);
		float contentHeight = Math.max(drawCircle ? radius * 2.0f : 0.0f, showText ? textHeight : 0.0f);
		return new Layout(drawCircle, showText, label, contentWidth, contentHeight,
			textWidth, textHeight, radius, circleBlock);
	}

	/** Whether this instance draws with OneConfig's Poppins font rather than the Minecraft font. */
	private boolean isPoppins() {
		return getFont() == Font.Poppins;
	}

	/**
	 * The Poppins font of this instance, or {@code null} when the skia font engine is missing.
	 *
	 * <p>OneConfig's own text HUDs render Poppins at {@code 8 * textScale} and pick the variant
	 * from the Bold / Italic / Weight options through {@code getPoppinsFontName()}; the font is
	 * cached because it caches its metrics per font instance.</p>
	 */
	private PolyFont poppinsFont(float size) {
		String key = getPoppinsFontName() + "@" + size;
		if (poppinsFont != null && key.equals(poppinsFontKey)) {
			return poppinsFont;
		}
		try {
			PolyFont font = new PolyFont(getPoppinsFontName(), size);
			// Touch the metrics so a missing font engine is noticed here and falls back to the
			// Minecraft font instead of failing on every frame.
			font.getMetrics();
			poppinsFont = font;
			poppinsFontKey = key;
			return font;
		} catch (Throwable t) {
			poppinsFont = null;
			poppinsFontKey = null;
			return null;
		}
	}

	/** The label with the case option applied: 1 = upper case, 2 = lower case. */
	private String casedLabel(String label) {
		int caseType = getCaseType();
		if (caseType == 1) {
			return label.toUpperCase(Locale.ROOT);
		}
		if (caseType == 2) {
			return label.toLowerCase(Locale.ROOT);
		}
		return label;
	}

	private static List<String> splitLines(String text) {
		List<String> lines = new ArrayList<>();
		int start = 0;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '\n') {
				lines.add(text.substring(start, i));
				start = i + 1;
			}
		}
		lines.add(text.substring(start));
		return lines;
	}

	private float textScale() {
		float scale = getTextScale();
		return scale > 0.0f ? scale : 1.0f;
	}

	/**
	 * Whether OneConfig's "Full Shadow" option is on.
	 *
	 * <p>Reached reflectively because {@code Hud.getFullShadow()} only exists since OneConfig 1.5;
	 * on older versions the indicator keeps the plain drop shadow instead of failing to draw.</p>
	 */
	private boolean fullShadow() {
		if (fullShadowSupported == null) {
			try {
				Hud.class.getMethod("getFullShadow");
				fullShadowSupported = true;
			} catch (Throwable t) {
				fullShadowSupported = false;
			}
		}
		if (!fullShadowSupported) {
			return false;
		}
		return getFullShadow();
	}

	/** Whether any colour of this HUD animates with the chroma cycle. */
	private boolean chromaActive() {
		return getBgChroma() || getTextChroma() || getShadowChroma();
	}

	/** Horizontal content offset for the alignment picker; {@code slack} is the spare box width. */
	private float alignOffsetX(float slack) {
		PolyAlign align = getAlignment();
		if (align == PolyAlign.Top || align == PolyAlign.Center || align == PolyAlign.Bottom) {
			return slack / 2.0f;
		}
		if (align == PolyAlign.TopRight || align == PolyAlign.Right || align == PolyAlign.BottomRight) {
			return slack;
		}
		return 0.0f;
	}

	/** Vertical content offset for the alignment picker; {@code slack} is the spare box height. */
	private float alignOffsetY(float slack) {
		PolyAlign align = getAlignment();
		if (align == PolyAlign.Left || align == PolyAlign.Center || align == PolyAlign.Right) {
			return slack / 2.0f;
		}
		if (align == PolyAlign.BottomLeft || align == PolyAlign.Bottom || align == PolyAlign.BottomRight) {
			return slack;
		}
		return 0.0f;
	}

	/** Whether the static size options give this HUD a fixed box. */
	private boolean isStaticValid() {
		return getStaticWidth() && getStaticW() > 0.0f && getStaticH() > 0.0f;
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

		float padding = getPadLeft();
		float boxHeight;
		try {
			boxHeight = layout().contentHeight() + getPadTop() + getPadBottom();
		} catch (Throwable t) {
			boxHeight = 13.0f;
		}

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
		float contentHeight;
		try {
			contentHeight = layout().contentHeight();
		} catch (Throwable t) {
			return;
		}

		float scale = getEffectiveScale();
		float screenWidth = minecraft.getWindow().getGuiScaledWidth();
		float screenHeight = minecraft.getWindow().getGuiScaledHeight();

		// Translate the dropped HUD box back into ModConfig's screen-centre origin so the position
		// survives removing OneConfig later.
		float contentLeft = getX() + getPadLeft() * scale;
		float contentTop = getY() + getPadTop() * scale;
		contentHeight = contentHeight * scale;

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
			indicator.poppinsFont = null;
			indicator.poppinsFontKey = null;
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

	/**
	 * Hash of every value {@link #paint} and the node measurement use that OneConfig does not
	 * observe as Compose state.
	 *
	 * <p>Compose-backed options (font, text style, background, colours, chroma, shadow) are read by
	 * the draw pass and are observed by OneConfig already; the mod's own plain fields and the
	 * recording state are included here so the indicator is redrawn when they change.</p>
	 */
	private int visualHash() {
		ModConfig config = ConfigManager.get();
		RecordState state = currentState();
		int h = state == null ? 0 : state.ordinal();
		h = 31 * h + (config.enabled ? 1 : 0);
		h = 31 * h + (drawCircle ? 1 : 0);
		h = 31 * h + (drawText ? 1 : 0);
		h = 31 * h + textOrDefault(recordingText).hashCode();
		h = 31 * h + textOrDefault(pausedText).hashCode();
		h = 31 * h + colorArgb();
		h = 31 * h + config.pausedColorArgb();
		h = 31 * h + Float.floatToIntBits(config.circleRadius);
		return h;
	}

	private static String textOrDefault(String value) {
		return value == null ? "" : value;
	}

	private static String hex(int argb) {
		return String.format("#%06X", argb & 0x00FFFFFF);
	}

	private static RecordState currentState() {
		var client = ObsRecIndicatorClient.obsClient();
		return client == null ? RecordState.IDLE : client.currentRecordState();
	}

	private String labelFor(RecordState state) {
		return casedLabel(state == RecordState.PAUSED ? currentPausedText() : currentRecordingText());
	}

	/** Natural content geometry of the indicator for the current options. */
	private record Layout(
		boolean showCircle,
		boolean showText,
		String label,
		float contentWidth,
		float contentHeight,
		float textWidth,
		float textHeight,
		float radius,
		float circleBlockWidth
	) {
	}
}
