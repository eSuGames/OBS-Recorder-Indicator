package com.obsindicator.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /obsindicator command helpers (no aliases). */
public final class ChatCommands {
	public static final String ROOT = "/obsindicator";
	private static final String[] SUBS = {
		"config", "toggle", "status", "reconnect", "textonly", "position",
	};

	private ChatCommands() {
	}

	public static List<String> complete(String input) {
		List<String> out = new ArrayList<>();
		if (input == null || !input.startsWith("/")) {
			return out;
		}
		String lower = input.toLowerCase(Locale.ROOT);
		if (!ROOT.startsWith(lower) && !lower.startsWith(ROOT)) {
			return out;
		}
		if (!lower.startsWith(ROOT)) {
			if (ROOT.startsWith(lower)) {
				out.add(ROOT);
			}
			return out;
		}
		int sp = input.indexOf(' ');
		if (sp < 0) {
			for (String s : SUBS) {
				String cand = ROOT + " " + s;
				if (cand.toLowerCase(Locale.ROOT).startsWith(lower)) {
					out.add(cand);
				}
			}
			return out;
		}
		String head = input.substring(0, sp + 1);
		String tail = input.substring(sp + 1).toLowerCase(Locale.ROOT);
		for (String s : SUBS) {
			if (s.startsWith(tail)) {
				out.add(head + s);
			}
		}
		if (input.toLowerCase(Locale.ROOT).startsWith(ROOT + " position")) {
			String rest = input.substring((ROOT + " position").length()).trim();
			if (rest.isEmpty() || "reset".startsWith(rest.toLowerCase(Locale.ROOT))) {
				out.add(ROOT + " position reset");
			}
		}
		return out;
	}

	public static boolean isCommand(String message) {
		if (message == null) {
			return false;
		}
		String t = message.trim().toLowerCase(Locale.ROOT);
		return t.equals(ROOT) || t.startsWith(ROOT + " ");
	}

	public static String normalize(String message) {
		return message == null ? "" : message.trim();
	}
}
