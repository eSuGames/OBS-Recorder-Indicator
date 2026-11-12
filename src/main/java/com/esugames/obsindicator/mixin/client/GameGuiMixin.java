package com.esugames.obsindicator.mixin.client;

import com.esugames.obsindicator.client.ObsRecIndicatorClient;
import com.esugames.obsindicator.client.hud.RecordingHudOverlay;
import net.minecraft.client.gui.GameGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla HUD hook (Minecraft 1.8.9 has no Fabric HUD render API).
 *
 * <p>Runs after the whole vanilla HUD so the indicator draws on top of it, and doubles as the
 * mod's per-frame hook for OneConfig bookkeeping.</p>
 */
@Mixin(GameGui.class)
public abstract class GameGuiMixin {

	@Inject(method = "render(F)V", at = @At("TAIL"))
	private void obsrec$afterRender(float partialTicks, CallbackInfo ci) {
		try {
			ObsRecIndicatorClient.onFrame();
			RecordingHudOverlay.render();
		} catch (Throwable ignored) {
			// A drawing error must never take the vanilla HUD down with it.
		}
	}
}
