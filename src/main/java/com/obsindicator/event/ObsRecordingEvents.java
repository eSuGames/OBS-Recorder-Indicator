package com.obsindicator.event;

import com.obsindicator.RecordState;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Public recording lifecycle events.
 * Other mods can listen to these to react to OBS recording start/stop/pause.
 */
public final class ObsRecordingEvents {
	public static final Event<RecordingStarted> RECORDING_STARTED = EventFactory.createArrayBacked(
		RecordingStarted.class,
		callbacks -> () -> {
			for (RecordingStarted callback : callbacks) {
				callback.onRecordingStarted();
			}
		}
	);

	public static final Event<RecordingStopped> RECORDING_STOPPED = EventFactory.createArrayBacked(
		RecordingStopped.class,
		callbacks -> () -> {
			for (RecordingStopped callback : callbacks) {
				callback.onRecordingStopped();
			}
		}
	);

	public static final Event<RecordingPaused> RECORDING_PAUSED = EventFactory.createArrayBacked(
		RecordingPaused.class,
		callbacks -> () -> {
			for (RecordingPaused callback : callbacks) {
				callback.onRecordingPaused();
			}
		}
	);

	public static final Event<RecordingResumed> RECORDING_RESUMED = EventFactory.createArrayBacked(
		RecordingResumed.class,
		callbacks -> () -> {
			for (RecordingResumed callback : callbacks) {
				callback.onRecordingResumed();
			}
		}
	);

	public static final Event<ObsConnected> OBS_CONNECTED = EventFactory.createArrayBacked(
		ObsConnected.class,
		callbacks -> () -> {
			for (ObsConnected callback : callbacks) {
				callback.onObsConnected();
			}
		}
	);

	public static final Event<ObsDisconnected> OBS_DISCONNECTED = EventFactory.createArrayBacked(
		ObsDisconnected.class,
		callbacks -> () -> {
			for (ObsDisconnected callback : callbacks) {
				callback.onObsDisconnected();
			}
		}
	);

	public static final Event<RecordStateChanged> RECORD_STATE_CHANGED = EventFactory.createArrayBacked(
		RecordStateChanged.class,
		callbacks -> (previous, current) -> {
			for (RecordStateChanged callback : callbacks) {
				callback.onRecordStateChanged(previous, current);
			}
		}
	);

	@FunctionalInterface
	public interface RecordingStarted {
		void onRecordingStarted();
	}

	@FunctionalInterface
	public interface RecordingStopped {
		void onRecordingStopped();
	}

	@FunctionalInterface
	public interface RecordingPaused {
		void onRecordingPaused();
	}

	@FunctionalInterface
	public interface RecordingResumed {
		void onRecordingResumed();
	}

	@FunctionalInterface
	public interface ObsConnected {
		void onObsConnected();
	}

	@FunctionalInterface
	public interface ObsDisconnected {
		void onObsDisconnected();
	}

	@FunctionalInterface
	public interface RecordStateChanged {
		void onRecordStateChanged(RecordState previous, RecordState current);
	}

	private ObsRecordingEvents() {
	}
}
