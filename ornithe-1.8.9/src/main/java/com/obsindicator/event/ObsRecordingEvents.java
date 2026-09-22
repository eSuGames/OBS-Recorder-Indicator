package com.obsindicator.event;

import com.obsindicator.RecordState;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Recording lifecycle events. No fabric-api required.
 */
public final class ObsRecordingEvents {
	public interface Listener {
		void onRecordingStarted();
		void onRecordingStopped();
		void onRecordingPaused();
		void onRecordingResumed();
		void onObsConnected();
		void onObsDisconnected();
		void onRecordStateChanged(RecordState previous, RecordState current);
	}

	private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

	public static void register(Listener listener) {
		if (listener != null) {
			LISTENERS.add(listener);
		}
	}

	public static void fireRecordingStarted() {
		for (Listener l : LISTENERS) {
			try {
				l.onRecordingStarted();
			} catch (Throwable ignored) {
			}
		}
	}

	public static void fireRecordingStopped() {
		for (Listener l : LISTENERS) {
			try {
				l.onRecordingStopped();
			} catch (Throwable ignored) {
			}
		}
	}

	public static void fireRecordingPaused() {
		for (Listener l : LISTENERS) {
			try {
				l.onRecordingPaused();
			} catch (Throwable ignored) {
			}
		}
	}

	public static void fireRecordingResumed() {
		for (Listener l : LISTENERS) {
			try {
				l.onRecordingResumed();
			} catch (Throwable ignored) {
			}
		}
	}

	public static void fireObsConnected() {
		for (Listener l : LISTENERS) {
			try {
				l.onObsConnected();
			} catch (Throwable ignored) {
			}
		}
	}

	public static void fireObsDisconnected() {
		for (Listener l : LISTENERS) {
			try {
				l.onObsDisconnected();
			} catch (Throwable ignored) {
			}
		}
	}

	public static void fireRecordStateChanged(RecordState previous, RecordState current) {
		for (Listener l : LISTENERS) {
			try {
				l.onRecordStateChanged(previous, current);
			} catch (Throwable ignored) {
			}
		}
	}

	private ObsRecordingEvents() {
	}
}
