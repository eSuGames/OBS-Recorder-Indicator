package com.obsindicator.client.command;

import com.obsindicator.client.ClientBootstrap;
import com.obsindicator.client.ObsRecIndicatorClient;
import com.obsindicator.client.config.ConfigScreenOpener;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Registers /obsindicator (and aliases) with OneConfig's client command
 * dispatcher via reflection so Tab completion works without this jar being remapped.
 */
public final class ObsIndicatorCommand {
	private ObsIndicatorCommand() {
	}

	public static void register() {
		try {
			Class<?> managerCl = Class.forName("org.polyfrost.oneconfig.api.commands.v1.CommandManager");
			Object manager = managerCl.getField("INSTANCE").get(null);
			Method literal = managerCl.getMethod("literal", String.class);
			Method registerNode = null;
			for (Method m : managerCl.getMethods()) {
				if (m.getName().equals("register") && m.getParameterCount() == 1
					&& m.getParameterTypes()[0].getName().contains("LiteralCommandNode")) {
					registerNode = m;
					break;
				}
			}

			Object root = literal.invoke(manager, "obsindicator");
			addHandlers(managerCl, manager, literal, root);
			Object node = root.getClass().getMethod("build").invoke(root);

			if (registerNode != null) {
				registerNode.invoke(manager, node);
			}

			// Hook dispatcher init so nodes are present even if register() is late.
			Class<?> eventCl = Class.forName("org.polyfrost.oneconfig.internal.legacy.command.ClientCommandRegistrationCallback");
			Object event = eventCl.getField("EVENT").get(null);
			Class<?> callbackCl = Class.forName("org.polyfrost.oneconfig.internal.legacy.command.ClientCommandRegistrationCallback");
			Object callback = Proxy.newProxyInstance(
				ObsIndicatorCommand.class.getClassLoader(),
				new Class<?>[]{callbackCl},
				(proxy, method, args) -> {
					if ("register".equals(method.getName()) && args != null && args.length >= 1 && args[0] != null) {
						try {
							Object dispatcher = args[0];
							Object rootN = dispatcher.getClass().getMethod("getRoot").invoke(dispatcher);
							rootN.getClass().getMethod("addChild", Class.forName("com.mojang.brigadier.tree.CommandNode"))
								.invoke(rootN, node);
						} catch (Throwable ignored) {
						}
					}
					return null;
				});
			event.getClass().getMethod("register", callbackCl).invoke(event, callback);

			ObsRecIndicatorClient.LOGGER.info("Registered /obsindicator client command");
		} catch (Throwable t) {
			ObsRecIndicatorClient.LOGGER.warn("Failed to register /obsindicator command: {}", t.toString());
		}
	}

	private static void addHandlers(Class<?> managerCl, Object manager, Method literal, Object rootBuilder) throws Exception {
		Method then = findMethod(rootBuilder.getClass(), "then",
			Class.forName("com.mojang.brigadier.builder.ArgumentBuilder"));
		Method executes = findMethod(rootBuilder.getClass(), "executes",
			Class.forName("com.mojang.brigadier.Command"));

		// root → open config
		if (executes != null) {
			executes.invoke(rootBuilder, command(() -> {
				ConfigScreenOpener.open();
				return 1;
			}));
		}

		String[] names = {"config", "toggle", "status", "reconnect", "textonly", "position"};
		for (String name : names) {
			Object lit = literal.invoke(manager, name);
			Method subExecutes = findMethod(lit.getClass(), "executes",
				Class.forName("com.mojang.brigadier.Command"));
			if (subExecutes != null) {
				subExecutes.invoke(lit, command(switch (name) {
					case "config" -> () -> {
						ConfigScreenOpener.open();
						return 1;
					};
					case "toggle" -> ClientBootstrap::runToggle;
					case "status" -> ClientBootstrap::runStatus;
					case "reconnect" -> ClientBootstrap::runReconnect;
					case "textonly" -> ClientBootstrap::runTextOnly;
					default -> () -> ClientBootstrap.runPosition(null, null);
				}));
			}
			if (name.equals("position") && then != null) {
				Object reset = literal.invoke(manager, "reset");
				Method resetExec = findMethod(reset.getClass(), "executes",
					Class.forName("com.mojang.brigadier.Command"));
				if (resetExec != null) {
					resetExec.invoke(reset, command(ClientBootstrap::runPositionReset));
				}
				then.invoke(lit, reset);
			}
			if (then != null) {
				then.invoke(rootBuilder, lit);
			}
		}
	}

	private static Object command(IntSupplier fn) throws Exception {
		Class<?> cmdCl = Class.forName("com.mojang.brigadier.Command");
		return Proxy.newProxyInstance(ObsIndicatorCommand.class.getClassLoader(), new Class<?>[]{cmdCl},
			(proxy, method, args) -> {
				if ("run".equals(method.getName())) {
					return fn.getAsInt();
				}
				return null;
			});
	}

	private static Method findMethod(Class<?> cl, String name, Class<?>... params) {
		for (Class<?> c = cl; c != null; c = c.getSuperclass()) {
			try {
				return c.getDeclaredMethod(name, params);
			} catch (NoSuchMethodException ignored) {
			}
			for (Method m : c.getMethods()) {
				if (m.getName().equals(name) && m.getParameterCount() == params.length) {
					boolean ok = true;
					Class<?>[] p = m.getParameterTypes();
					for (int i = 0; i < params.length; i++) {
						if (!p[i].isAssignableFrom(params[i]) && !params[i].isAssignableFrom(p[i])) {
							ok = false;
							break;
						}
					}
					if (ok) {
						return m;
					}
				}
			}
		}
		return null;
	}

	@FunctionalInterface
	private interface IntSupplier {
		int getAsInt();
	}
}
