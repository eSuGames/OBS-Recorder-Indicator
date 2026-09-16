package com.obsindicator.client;

import com.obsindicator.client.config.ConfigScreenOpener;
import com.obsindicator.client.config.OneConfigSupport;
import com.obsindicator.client.hud.RecordingHudOverlay;
import com.obsindicator.config.ConfigManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Commands: config / status / toggle / reconnect always. textonly / position only without OneConfig.
 */
public final class ClientBootstrap {
	private static KeyMapping.Category category;
	private static KeyMapping openConfigKey;
	private static KeyMapping toggleIndicatorKey;

	private ClientBootstrap() {
	}

	public static void init() {
		RecordingHudOverlay.register();

		category = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(ObsRecIndicatorClient.MOD_ID, "main")
		);

		int unbound = InputConstants.UNKNOWN.getValue();
		openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.obs_rec_indicator.open_config",
			InputConstants.Type.KEYBOARD,
			unbound,
			category
		));
		toggleIndicatorKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.obs_rec_indicator.toggle_indicator",
			InputConstants.Type.KEYBOARD,
			unbound,
			category
		));

		ClientTickEvents.END_CLIENT_TICK.register(ClientBootstrap::onEndTick);
		registerCommands();
	}

	private static void toggleEnabled() {
		boolean enabled = !ConfigManager.get().enabled;
		ConfigManager.get().enabled = enabled;
		ConfigManager.save();
	}

	private static void onEndTick(Minecraft client) {
		if (client == null) {
			return;
		}
		while (openConfigKey != null && openConfigKey.consumeClick()) {
			ConfigScreenOpener.open();
		}
		while (toggleIndicatorKey != null && toggleIndicatorKey.consumeClick()) {
			toggleEnabled();
		}
	}

	private static void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			var root = ClientCommands.literal("obsindicator")
				.then(ClientCommands.literal("config")
					.executes(ctx -> {
						ConfigScreenOpener.open();
						return 1;
					}))
				.then(ClientCommands.literal("toggle")
					.executes(ctx -> {
						toggleEnabled();
						ctx.getSource().sendFeedback(Component.translatable(
							"obs_rec_indicator.cmd.toggle", onOff(ConfigManager.get().enabled)
						));
						return 1;
					}))
				.then(ClientCommands.literal("status")
					.executes(ctx -> {
						var obs = ObsRecIndicatorClient.obsClient();
						if (obs == null) {
							ctx.getSource().sendFeedback(Component.translatable("obs_rec_indicator.cmd.not_started"));
							return 1;
						}
						ctx.getSource().sendFeedback(Component.literal(
							"endpoint=" + obs.endpoint()
								+ " connected=" + obs.isConnected()
								+ " recordState=" + obs.currentRecordState()
								+ " enabled=" + ConfigManager.get().enabled
								+ " textOnly=" + (!ConfigManager.get().showCircle)
								+ " pos=" + ConfigManager.get().positionX + "," + ConfigManager.get().positionY
						));
						if (!obs.lastError().isEmpty()) {
							ctx.getSource().sendFeedback(Component.literal("lastError: " + obs.lastError()));
						}
						return 1;
					}))
				.then(ClientCommands.literal("reconnect")
					.executes(ctx -> {
						var obs = ObsRecIndicatorClient.obsClient();
						if (obs != null) {
							obs.applyConnectionSettingsChanged();
						}
						ctx.getSource().sendFeedback(Component.translatable("obs_rec_indicator.cmd.reconnect"));
						return 1;
					}));

			// Extra settings commands only when OneConfig is NOT present.
			if (!OneConfigSupport.isPresent()) {
				root = root
					.then(ClientCommands.literal("textonly")
						.executes(ctx -> {
							boolean textOnly = !ConfigManager.get().showCircle;
							ConfigManager.get().showCircle = textOnly;
							ConfigManager.save();
							ctx.getSource().sendFeedback(Component.translatable(
								"obs_rec_indicator.cmd.textonly", onOff(!textOnly)
							));
							return 1;
						}))
					.then(ClientCommands.literal("position")
						.executes(ctx -> {
							ctx.getSource().sendFeedback(Component.translatable(
								"obs_rec_indicator.cmd.offset_show",
								ConfigManager.get().positionX,
								ConfigManager.get().positionY
							));
							return 1;
						})
						.then(ClientCommands.literal("reset")
							.executes(ctx -> {
								ConfigManager.get().resetPosition();
								ConfigManager.save();
								ctx.getSource().sendFeedback(Component.translatable("obs_rec_indicator.cmd.offset_reset"));
								return 1;
							}))
						.then(ClientCommands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-800, 800))
							.then(ClientCommands.argument("y", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-800, 800))
								.executes(ctx -> {
									int x = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "x");
									int y = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "y");
									ConfigManager.get().positionX = x;
									ConfigManager.get().positionY = y;
									ConfigManager.save();
									ctx.getSource().sendFeedback(Component.translatable("obs_rec_indicator.cmd.offset_set", x, y));
									return 1;
								}))));
			}

			dispatcher.register(root);
		});
	}

	private static Component onOff(boolean value) {
		return Component.translatable(value ? "obs_rec_indicator.gui.on" : "obs_rec_indicator.gui.off");
	}
}
