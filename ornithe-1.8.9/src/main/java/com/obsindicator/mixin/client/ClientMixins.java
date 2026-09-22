package com.obsindicator.mixin.client;

import com.obsindicator.client.ClientBootstrap;
import com.obsindicator.client.hud.RecordingHudOverlay;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * HUD + /obsindicator chat command without fabric-api.
 */
public final class ClientMixins {
	private ClientMixins() {
	}

	@Mixin(InGameHud.class)
	public static class InGameHudMixin {
		@Inject(method = "render", at = @At("TAIL"))
		private void obsrec$afterRender(CallbackInfo ci) {
			try {
				RecordingHudOverlay.render();
			} catch (Throwable ignored) {
			}
		}
	}

	@Mixin(ClientPlayerEntity.class)
	public static class ClientPlayerEntityMixin {
		@Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
		private void obsrec$onChat(String message, CallbackInfo ci) {
			try {
				if (ClientBootstrap.handleChat(message)) {
					ci.cancel();
				}
			} catch (Throwable ignored) {
			}
		}
	}
}
