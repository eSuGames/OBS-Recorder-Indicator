package com.obsindicator.mixin.client;

import com.obsindicator.client.ClientBootstrap;
import com.obsindicator.client.hud.RecordingHudOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * HUD + chat command (Ornithe Calamus gen2 names).
 * No GUI-screen mixins — those break other mods' menu backgrounds.
 */
public final class ClientMixins {
	private ClientMixins() {
	}

	/** GuiIngame — renderGameOverlay(F)V */
	@Pseudo
	@Mixin(targets = "net.minecraft.unmapped.C_97866173", remap = false)
	public static class InGameHudMixin {
		@Inject(method = "m_94668477", at = @At("TAIL"), remap = false)
		private void obsrec$afterRender(float partialTicks, CallbackInfo ci) {
			try {
				RecordingHudOverlay.render();
			} catch (Throwable ignored) {
			}
		}
	}

	/** EntityPlayerSP — sendChatMessage (both String overloads) */
	@Pseudo
	@Mixin(targets = "net.minecraft.unmapped.C_57778754", remap = false)
	public static class ClientPlayerEntityMixin {
		@Inject(method = "m_83442988", at = @At("HEAD"), cancellable = true, remap = false)
		private void obsrec$onChatE(String message, CallbackInfo ci) {
			if (ClientBootstrap.handleChat(message)) {
				ci.cancel();
			}
		}

		@Inject(method = "m_17089821", at = @At("HEAD"), cancellable = true, remap = false)
		private void obsrec$onChatF(String message, CallbackInfo ci) {
			if (ClientBootstrap.handleChat(message)) {
				ci.cancel();
			}
		}
	}
}
