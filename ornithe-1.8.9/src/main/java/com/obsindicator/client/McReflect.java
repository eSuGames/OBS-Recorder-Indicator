package com.obsindicator.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Ornithe Calamus gen2 runtime bridge (hardcoded official→intermediary names).
 * Works without Flap remapping of this jar.
 */
public final class McReflect {
	public static final String MC = "net.minecraft.client.Minecraft";
	public static final String FONT = "net.minecraft.unmapped.C_23014920";
	public static final String GUI = "net.minecraft.unmapped.C_29058632";

	private static final String[] GET_INSTANCE = {"m_20213497", "func_71410_x", "getInstance"};
	private static final String[] FONT_FIELDS = {"f_21497584", "f_77337277", "k", "l", "fontRenderer", "textRenderer"};
	private static final String[] FONT_HEIGHT = {"f_30706543", "a", "fontHeight", "field_78268_c"};
	private static final String[] STR_WIDTH = {"m_09235706", "getStringWidth", "func_78256_a", "a"};
	private static final String[] DRAW = {"m_72964882", "m_27673420", "drawString", "func_78258_a", "a"};
	private static final String[] DRAW_SHADOW = {"m_81783033", "m_52334671", "m_11445413", "m_83960853", "drawStringWithShadow", "func_78261_a", "b"};
	private static final String[] FILL = {"m_57734177", "drawRect", "func_73734_a", "a"};
	private static final String[] PLAYER = {"f_28396371", "thePlayer", "player", "field_71439_g", "h"};
	private static final String[] WIDTH = {"f_31800787", "displayWidth", "field_71443_c", "d"};
	private static final String[] HEIGHT = {"f_74192110", "displayHeight", "field_71440_d", "e"};

	private static Class<?> mcClass;
	private static Method getInstance;
	private static boolean resolved;

	private McReflect() {
	}

	public static synchronized boolean available() {
		if (!resolved) {
			resolved = true;
			try {
				mcClass = Class.forName(MC);
				for (String n : GET_INSTANCE) {
					try {
						Method m = mcClass.getDeclaredMethod(n);
						if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == mcClass) {
							m.setAccessible(true);
							getInstance = m;
							break;
						}
					} catch (NoSuchMethodException ignored) {
					}
				}
			} catch (Throwable ignored) {
			}
		}
		return getInstance != null;
	}

	public static Object minecraft() {
		if (!available()) {
			return null;
		}
		try {
			return getInstance.invoke(null);
		} catch (Throwable t) {
			return null;
		}
	}

	public static Object player() {
		return fieldByNameAny(minecraft(), PLAYER);
	}

	public static Object currentScreen() {
		return fieldByNameAny(minecraft(), new String[]{"f_26407825", "currentScreen", "field_71462_r", "m"});
	}

	public static Object font() {
		Object mc = minecraft();
		if (mc == null) {
			return null;
		}
		for (String n : FONT_FIELDS) {
			Object v = getFieldValue(mc, n);
			if (v != null && isFont(v)) {
				return v;
			}
		}
		return null;
	}

	private static boolean isFont(Object v) {
		try {
			v.getClass().getDeclaredMethod(STR_WIDTH[0], String.class);
			return true;
		} catch (Throwable ignored) {
		}
		return v.getClass().getName().contains("C_23014920")
			|| v.getClass().getName().contains("FontRenderer");
	}

	public static int fontHeight(Object font) {
		if (font == null) {
			return 9;
		}
		for (String n : FONT_HEIGHT) {
			Integer v = getIntField(font, n);
			if (v != null && v > 0 && v < 64) {
				return v;
			}
		}
		return 9;
	}

	public static int stringWidth(Object font, String text) {
		if (font == null || text == null) {
			return text == null ? 0 : text.length() * 6;
		}
		for (String n : STR_WIDTH) {
			try {
				Method m = font.getClass().getDeclaredMethod(n, String.class);
				m.setAccessible(true);
				return ((Number) m.invoke(font, text)).intValue();
			} catch (Throwable ignored) {
			}
		}
		return text.length() * 6;
	}

	public static void drawString(Object font, String text, float x, float y, int color, boolean shadow) {
		if (font == null || text == null) {
			return;
		}
		int xi = Math.round(x);
		int yi = Math.round(y);
		String[] names = shadow ? DRAW_SHADOW : DRAW;
		for (String n : names) {
			// (String, III)I  or (String, III)V
			try {
				Method m = findMethod(font.getClass(), n, String.class, int.class, int.class, int.class);
				if (m != null) {
					m.setAccessible(true);
					m.invoke(font, text, xi, yi, color);
					return;
				}
			} catch (Throwable ignored) {
			}
			// (String, IIII)I / (String, IIIIZ)I with shadow flag
			try {
				Method m = findMethod(font.getClass(), n, String.class, int.class, int.class, int.class, boolean.class);
				if (m != null) {
					m.setAccessible(true);
					m.invoke(font, text, xi, yi, color, shadow);
					return;
				}
			} catch (Throwable ignored) {
			}
			// (String, FFI)I
			try {
				Method m = findMethod(font.getClass(), n, String.class, float.class, float.class, int.class);
				if (m != null) {
					m.setAccessible(true);
					m.invoke(font, text, x, y, color);
					return;
				}
			} catch (Throwable ignored) {
			}
			// (String, FFIZ)I
			try {
				Method m = findMethod(font.getClass(), n, String.class, float.class, float.class, int.class, boolean.class);
				if (m != null) {
					m.setAccessible(true);
					m.invoke(font, text, x, y, color, shadow);
					return;
				}
			} catch (Throwable ignored) {
			}
		}
	}

	public static void fill(int x1, int y1, int x2, int y2, int argb) {
		// Prefer static drawRect on Gui
		try {
			Class<?> gui = Class.forName(GUI);
			for (String n : FILL) {
				try {
					Method m = gui.getDeclaredMethod(n, int.class, int.class, int.class, int.class, int.class);
					m.setAccessible(true);
					if (Modifier.isStatic(m.getModifiers())) {
						m.invoke(null, x1, y1, x2, y2, argb);
						return;
					}
				} catch (NoSuchMethodException ignored) {
				}
			}
		} catch (Throwable ignored) {
		}
		// Fallback: any static (IIIII)V on GuiIngame hierarchy
		Object guiObj = fieldByNameAny(minecraft(), new String[]{"f_28235293", "ingameGUI", "inGameHud", "q"});
		for (Class<?> c = guiObj != null ? guiObj.getClass() : null; c != null; c = c.getSuperclass()) {
			for (Method m : c.getDeclaredMethods()) {
				Class<?>[] p = m.getParameterTypes();
				if (p.length == 5 && p[0] == int.class && p[4] == int.class) {
					try {
						m.setAccessible(true);
						if (Modifier.isStatic(m.getModifiers())) {
							m.invoke(null, x1, y1, x2, y2, argb);
						} else {
							m.invoke(guiObj, x1, y1, x2, y2, argb);
						}
						return;
					} catch (Throwable ignored) {
					}
				}
			}
		}
	}

	public static int scaledWidth() {
		Integer sr = scaledSize(true);
		if (sr != null && sr > 0) {
			return sr;
		}
		Object mc = minecraft();
		int w = intOf(getFieldValue(mc, WIDTH[0]), 0);
		if (w <= 0) {
			w = intOf(fieldByNameAny(mc, WIDTH), 0);
		}
		return w > 0 ? Math.max(320, w / 2) : 640;
	}

	public static int scaledHeight() {
		Integer sr = scaledSize(false);
		if (sr != null && sr > 0) {
			return sr;
		}
		Object mc = minecraft();
		int h = intOf(getFieldValue(mc, HEIGHT[0]), 0);
		if (h <= 0) {
			h = intOf(fieldByNameAny(mc, HEIGHT), 0);
		}
		return h > 0 ? Math.max(240, h / 2) : 360;
	}

	private static Integer scaledSize(boolean width) {
		// try constructing ScaledResolution
		Object mc = minecraft();
		if (mc == null) {
			return null;
		}
		String[] classes = {
			"net.minecraft.client.gui.ScaledResolution",
			"net.minecraft.unmapped.C_80745364",
		};
		for (String cn : classes) {
			try {
				Class<?> cl = Class.forName(cn);
				Object sr = cl.getConstructor(mc.getClass()).newInstance(mc);
				for (String n : (width
					? new String[]{"getScaledWidth", "m_85145191", "a"}
					: new String[]{"getScaledHeight", "b", "c"})) {
					try {
						Method m = cl.getMethod(n);
						m.setAccessible(true);
						Object v = m.invoke(sr);
						int i = intOf(v, 0);
						if (i > 0) {
							return i;
						}
					} catch (Throwable ignored) {
					}
				}
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	public static void sendFeedback(String msg) {
		// Print via in-game chat GUI (local). Never player.sendChatMessage — that
		// would make the client "say" the line on the server.
		try {
			Object mc = minecraft();
			Object ingame = fieldByNameAny(mc, new String[]{"f_28235293", "ingameGUI", "inGameHud", "q"});
			Object chatGui = null;
			if (ingame != null) {
				try {
					Method m = ingame.getClass().getMethod("m_20387035");
					m.setAccessible(true);
					chatGui = m.invoke(ingame);
				} catch (Throwable ignored) {
				}
			}
			if (chatGui != null) {
				// OneConfig Component.nullToEmpty → printChatMessage
				try {
					Class<?> compCl = Class.forName("org.polyfrost.oneconfig.internal.legacy.chat.Component");
					Object comp = compCl.getMethod("nullToEmpty", String.class).invoke(null, msg);
					for (Method m : chatGui.getClass().getMethods()) {
						Class<?>[] p = m.getParameterTypes();
						if (p.length == 1 && p[0].isInstance(comp)) {
							m.setAccessible(true);
							m.invoke(chatGui, comp);
							return;
						}
					}
				} catch (Throwable ignored) {
				}
				// ChatComponentText(String) if present
				Object component = newChatText(msg);
				if (component != null) {
					for (Method m : chatGui.getClass().getMethods()) {
						Class<?>[] p = m.getParameterTypes();
						if (p.length == 1 && p[0].isInstance(component)) {
							m.setAccessible(true);
							m.invoke(chatGui, component);
							return;
						}
					}
				}
			}
		} catch (Throwable ignored) {
		}
	}

	private static Object newChatText(String msg) {
		for (String name : new String[]{
			"net.minecraft.util.ChatComponentText",
			"net.minecraft.text.LiteralText",
			"net.minecraft.unmapped.C_95106612",
		}) {
			try {
				return Class.forName(name).getConstructor(String.class).newInstance(msg);
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	/** @return true if displayGuiScreen was invoked */
	public static boolean openScreen(Object screen) {
		Object mc = minecraft();
		if (mc == null) {
			return false;
		}
		if (screen != null) {
			// Known: displayGuiScreen(GuiChat) = m_52715402
			for (String n : new String[]{"m_52715402", "m_85741928", "displayGuiScreen", "func_147108_a"}) {
				for (Method m : mc.getClass().getMethods()) {
					if (!m.getName().equals(n)) {
						continue;
					}
					Class<?>[] p = m.getParameterTypes();
					if (p.length == 1 && m.getReturnType() == void.class && p[0].isInstance(screen)) {
						try {
							m.setAccessible(true);
							m.invoke(mc, screen);
							return true;
						} catch (Throwable ignored) {
						}
					}
				}
			}
		}
		for (Method m : mc.getClass().getMethods()) {
			Class<?>[] p = m.getParameterTypes();
			if (p.length != 1 || m.getReturnType() != void.class) {
				continue;
			}
			boolean match = screen == null
				? p[0].getName().contains("C_") || p[0].getName().contains("GuiScreen") || p[0].getName().contains("Screen")
				: p[0].isInstance(screen);
			if (!match) {
				continue;
			}
			try {
				m.setAccessible(true);
				m.invoke(mc, screen);
				return true;
			} catch (Throwable ignored) {
			}
		}
		return false;
	}

	// ---- helpers ----

	private static Method findMethod(Class<?> cl, String name, Class<?>... params) {
		for (Class<?> c = cl; c != null; c = c.getSuperclass()) {
			try {
				return c.getDeclaredMethod(name, params);
			} catch (NoSuchMethodException ignored) {
			}
		}
		return null;
	}

	private static Object getFieldValue(Object target, String name) {
		if (target == null) {
			return null;
		}
		for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
			try {
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				return f.get(target);
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	private static Integer getIntField(Object target, String name) {
		Object v = getFieldValue(target, name);
		return v instanceof Integer i ? i : null;
	}

	private static Object fieldByNameAny(Object target, String[] names) {
		for (String n : names) {
			Object v = getFieldValue(target, n);
			if (v != null) {
				return v;
			}
		}
		return null;
	}

	private static int intOf(Object v, int def) {
		return v instanceof Integer i ? i : def;
	}

	public static int displayWidth() {
		return intOf(getFieldValue(minecraft(), WIDTH[0]), 0);
	}

	public static int displayHeight() {
		return intOf(getFieldValue(minecraft(), HEIGHT[0]), 0);
	}
}
