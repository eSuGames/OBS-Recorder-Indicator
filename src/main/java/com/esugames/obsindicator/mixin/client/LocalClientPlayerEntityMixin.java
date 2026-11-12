package com.esugames.obsindicator.mixin.client;

import com.esugames.obsindicator.client.ClientBootstrap;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chat hook for the {@code /obsindicator} command (Minecraft 1.8.9 has no client command API).
 *
 * <p>Messages starting with {@code /obsindicator} are handled by the mod and never reach the
 * server.</p>
 */
@Mixin(LocalClientPlayerEntity.class)
public abstract class LocalClientPlayerEntityMixin {

	@Inject(method = "sendChat(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
	private void obsrec$onChat(String message, CallbackInfo ci) {
		try {
			if (ClientBootstrap.handleChat(message)) {
				ci.cancel();
			}
		} catch (Throwable ignored) {
			// A command error must never break chat.
		}
	}
}
