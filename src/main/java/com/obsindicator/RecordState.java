package com.obsindicator;

/**
 * Recording output state mirrored from OBS WebSocket.
 */
public enum RecordState {
	/** Not recording / OBS not connected / unknown. */
	IDLE,
	RECORDING,
	PAUSED;

	public boolean isIndicatorVisible() {
		return this == RECORDING || this == PAUSED;
	}

	public static RecordState fromObsOutputState(String outputState, boolean outputActive, boolean outputPaused) {
		if (outputState != null) {
			return switch (outputState) {
				case "OBS_WEBSOCKET_OUTPUT_STARTED", "OBS_WEBSOCKET_OUTPUT_STARTING", "OBS_WEBSOCKET_OUTPUT_RECONNECTING", "OBS_WEBSOCKET_OUTPUT_RECONNECTED" ->
					RECORDING;
				case "OBS_WEBSOCKET_OUTPUT_PAUSED" -> PAUSED;
				case "OBS_WEBSOCKET_OUTPUT_STOPPED", "OBS_WEBSOCKET_OUTPUT_STOPPING", "OBS_WEBSOCKET_OUTPUT_UNKNOWN" -> IDLE;
				default -> outputPaused ? PAUSED : (outputActive ? RECORDING : IDLE);
			};
		}
		if (outputPaused) {
			return PAUSED;
		}
		return outputActive ? RECORDING : IDLE;
	}
}
