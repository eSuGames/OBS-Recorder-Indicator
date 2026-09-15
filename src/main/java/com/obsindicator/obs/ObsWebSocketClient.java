package com.obsindicator.obs;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.obsindicator.RecordState;
import com.obsindicator.config.ConfigManager;
import com.obsindicator.config.ModConfig;
import com.obsindicator.event.ObsRecordingEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Non-blocking OBS WebSocket 5.x client.
 * Network I/O runs on a dedicated daemon scheduler; Minecraft's main thread is never blocked.
 */
public final class ObsWebSocketClient implements WebSocket.Listener {
	private static final Logger LOGGER = LoggerFactory.getLogger("obs_rec_indicator");
	private static final Gson GSON = new Gson();
	private static final int EVENT_SUBSCRIPTION_OUTPUTS = 1 << 6;
	private static final long AUTH_FAILED_CLOSE_CODE = 4009L;

	private final ScheduledExecutorService executor;
	private final HttpClient httpClient;
	private final AtomicReference<WebSocket> socketRef = new AtomicReference<>();
	private final AtomicReference<RecordState> recordState = new AtomicReference<>(RecordState.IDLE);
	private final AtomicBoolean connected = new AtomicBoolean(false);
	private final AtomicBoolean identified = new AtomicBoolean(false);
	private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
	private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
	private final StringBuilder textBuffer = new StringBuilder(512);
	private final AtomicReference<String> lastError = new AtomicReference<>("");
	private final AtomicBoolean connectFailureLogged = new AtomicBoolean(false);

	private volatile ScheduledFuture<?> reconnectFuture;

	public ObsWebSocketClient() {
		this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "obs-rec-indicator-ws");
			t.setDaemon(true);
			t.setPriority(Thread.NORM_PRIORITY - 1);
			return t;
		});
		this.httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(4))
			.build();
	}

	public RecordState currentRecordState() {
		return recordState.get();
	}

	public boolean isConnected() {
		return connected.get() && identified.get();
	}

	public String lastError() {
		String err = lastError.get();
		return err == null ? "" : err;
	}

	public String endpoint() {
		ModConfig config = ConfigManager.get();
		String scheme = config.obsUseTls ? "wss" : "ws";
		return scheme + "://" + config.obsHost + ":" + config.obsPort;
	}

	public void start() {
		executor.execute(this::connectNow);
	}

	public void stop() {
		shuttingDown.set(true);
		cancelReconnect();
		WebSocket ws = socketRef.getAndSet(null);
		if (ws != null) {
			try {
				ws.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
			} catch (Exception ignored) {
			}
		}
		setConnected(false);
		executor.shutdownNow();
	}

	public void applyConnectionSettingsChanged() {
		connectFailureLogged.set(false);
		lastError.set("");
		executor.execute(() -> {
			forceDisconnect();
			connectNow();
		});
	}

	private void forceDisconnect() {
		cancelReconnect();
		WebSocket ws = socketRef.getAndSet(null);
		if (ws != null) {
			try {
				ws.sendClose(WebSocket.NORMAL_CLOSURE, "reconfigure");
			} catch (Exception ignored) {
			}
		}
		setConnected(false);
		identified.set(false);
		updateRecordState(RecordState.IDLE, null, null);
	}

	private void connectNow() {
		if (shuttingDown.get()) {
			return;
		}
		if (connected.get()) {
			return;
		}
		ModConfig config = ConfigManager.get();
		URI uri = URI.create(endpoint());
		int timeoutMs = Math.max(500, config.connectTimeoutMs);

		try {
			httpClient.newWebSocketBuilder()
				.connectTimeout(Duration.ofMillis(timeoutMs))
				.buildAsync(uri, this)
				.whenComplete((ws, error) -> {
					if (error != null) {
						handleConnectFailure(error);
						scheduleReconnect();
					}
				});
		} catch (Exception e) {
			handleConnectFailure(e);
			scheduleReconnect();
		}
	}

	private void handleConnectFailure(Throwable error) {
		String message = describeConnectError(error);
		lastError.set(message);
		if (connectFailureLogged.compareAndSet(false, true)) {
			LOGGER.warn("OBS WebSocket connect failed ({}): {}", endpoint(), message);
			LOGGER.warn("Check: OBS is running, Tools > WebSocket Server Settings is enabled, host/port match.");
		} else {
			LOGGER.debug("OBS WebSocket connect failed: {}", message);
		}
	}

	private static String describeConnectError(Throwable error) {
		Throwable cause = error;
		while (cause != null) {
			if (cause instanceof ConnectException) {
				return "Connection refused (OBS WebSocket may be disabled or wrong port)";
			}
			String msg = cause.getMessage();
			if (msg != null) {
				String lower = msg.toLowerCase();
				if (lower.contains("connection refused")) {
					return "Connection refused (OBS WebSocket may be disabled or wrong port)";
				}
				if (lower.contains("timed out") || lower.contains("timeout")) {
					return "Connection timed out";
				}
				if (lower.contains("unknown host")) {
					return "Unknown host";
				}
			}
			cause = cause.getCause();
		}
		return error.toString();
	}

	private void scheduleReconnect() {
		if (shuttingDown.get() || reconnectScheduled.get()) {
			return;
		}
		if (!reconnectScheduled.compareAndSet(false, true)) {
			return;
		}
		int seconds = Math.max(1, ConfigManager.get().reconnectIntervalSeconds);
		reconnectFuture = executor.schedule(() -> {
			reconnectScheduled.set(false);
			if (!shuttingDown.get()) {
				connectNow();
			}
		}, seconds, TimeUnit.SECONDS);
	}

	private void cancelReconnect() {
		ScheduledFuture<?> future = reconnectFuture;
		if (future != null) {
			future.cancel(false);
			reconnectFuture = null;
		}
		reconnectScheduled.set(false);
	}

	private void setConnected(boolean value) {
		boolean was = connected.getAndSet(value);
		if (value) {
			identified.set(false);
		}
		if (!value && was) {
			identified.set(false);
			try {
				ObsRecordingEvents.OBS_DISCONNECTED.invoker().onObsDisconnected();
			} catch (Exception e) {
				LOGGER.warn("OBS_DISCONNECTED listener failed", e);
			}
		}
	}

	@Override
	public void onOpen(WebSocket webSocket) {
		socketRef.set(webSocket);
		setConnected(true);
		connectFailureLogged.set(false);
		lastError.set("");
		webSocket.request(1);
		LOGGER.info("Connected to OBS WebSocket at {}", endpoint());
	}

	@Override
	public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
		synchronized (textBuffer) {
			textBuffer.append(data);
			if (last) {
				String message = textBuffer.toString();
				textBuffer.setLength(0);
				try {
					handleMessage(message);
				} catch (Exception e) {
					LOGGER.warn("Failed to handle OBS message: {}", e.toString());
				}
			}
		}
		webSocket.request(1);
		return null;
	}

	@Override
	public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
		String reasonText = reason == null ? "" : reason;
		LOGGER.info("OBS WebSocket closed ({}): {}", statusCode, reasonText);
		socketRef.set(null);
		setConnected(false);
		updateRecordState(RecordState.IDLE, null, null);
		if (statusCode == AUTH_FAILED_CLOSE_CODE) {
			String err = "Authentication failed (close 4009). Set obsPassword in config, or disable auth in OBS WebSocket settings.";
			lastError.set(err);
			LOGGER.error(err);
		} else {
			lastError.set("Closed (" + statusCode + ")" + (reasonText.isEmpty() ? "" : ": " + reasonText));
		}
		scheduleReconnect();
		return null;
	}

	@Override
	public void onError(WebSocket webSocket, Throwable error) {
		socketRef.set(null);
		setConnected(false);
		handleConnectFailure(error);
		scheduleReconnect();
	}

	private void handleMessage(String raw) {
		JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
		int op = root.has("op") && !root.get("op").isJsonNull() ? root.get("op").getAsInt() : -1;
		JsonObject data = root.has("d") && root.get("d").isJsonObject() ? root.getAsJsonObject("d") : new JsonObject();

		switch (op) {
			case 0 -> handleHello(data);
			case 2 -> handleIdentified(data);
			case 5 -> handleEvent(data);
			case 7 -> {
				if (data.has("requestStatus") && data.get("requestStatus").isJsonObject()) {
					JsonObject status = data.getAsJsonObject("requestStatus");
					boolean ok = status.has("result") && status.get("result").getAsBoolean();
					if (!ok) {
						int code = status.has("code") ? status.get("code").getAsInt() : -1;
						LOGGER.debug("OBS request failed code={}", code);
					} else {
						String requestType = data.has("requestType") ? data.get("requestType").getAsString() : "";
						if ("GetRecordStatus".equals(requestType)) {
							applyRecordStatusResponse(data);
						}
					}
				}
			}
			default -> {
			}
		}
	}

	private void handleHello(JsonObject data) {
		ModConfig config = ConfigManager.get();
		String password = config.obsPassword;
		boolean authRequired = data.has("authentication") && data.get("authentication").isJsonObject();
		String auth = null;

		if (authRequired) {
			if (password == null || password.isEmpty()) {
				lastError.set("OBS requires authentication but obsPassword is empty. Copy the password from OBS WebSocket settings.");
				LOGGER.error(lastError.get());
			} else {
				JsonObject challengeObj = data.getAsJsonObject("authentication");
				String challenge = challengeObj.get("challenge").getAsString();
				String salt = challengeObj.get("salt").getAsString();
				auth = createAuthString(password, salt, challenge);
			}
		}

		JsonObject identify = new JsonObject();
		identify.addProperty("rpcVersion", 1);
		if (auth != null) {
			identify.addProperty("authentication", auth);
		}
		identify.addProperty("eventSubscriptions", EVENT_SUBSCRIPTION_OUTPUTS);

		JsonObject message = new JsonObject();
		message.addProperty("op", 1);
		message.add("d", identify);
		sendText(GSON.toJson(message));
	}

	private void handleIdentified(JsonObject data) {
		identified.set(true);
		lastError.set("");
		connectFailureLogged.set(false);
		LOGGER.info("Identified with OBS WebSocket (rpcVersion={})",
			data.has("negotiatedRpcVersion") ? data.get("negotiatedRpcVersion").getAsInt() : 1);
		try {
			ObsRecordingEvents.OBS_CONNECTED.invoker().onObsConnected();
		} catch (Exception e) {
			LOGGER.warn("OBS_CONNECTED listener failed", e);
		}
		requestRecordStatus();
	}

	private void handleEvent(JsonObject data) {
		String eventType = data.has("eventType") ? data.get("eventType").getAsString() : "";
		if (!"RecordStateChanged".equals(eventType)) {
			return;
		}
		JsonObject eventData = data.has("eventData") && data.get("eventData").isJsonObject()
			? data.getAsJsonObject("eventData")
			: new JsonObject();
		boolean active = eventData.has("outputActive") && eventData.get("outputActive").getAsBoolean();
		String outputState = eventData.has("outputState") ? eventData.get("outputState").getAsString() : null;
		String path = eventData.has("outputPath") && !eventData.get("outputPath").isJsonNull()
			? eventData.get("outputPath").getAsString()
			: null;
		RecordState next = RecordState.fromObsOutputState(outputState, active, false);
		updateRecordState(next, outputState, path);
	}

	private void requestRecordStatus() {
		JsonObject d = new JsonObject();
		d.addProperty("requestType", "GetRecordStatus");
		d.addProperty("requestId", UUID.randomUUID().toString());
		d.add("requestData", new JsonObject());
		JsonObject message = new JsonObject();
		message.addProperty("op", 6);
		message.add("d", d);
		sendText(GSON.toJson(message));
	}

	void applyRecordStatusResponse(JsonObject data) {
		if (!data.has("responseData") || !data.get("responseData").isJsonObject()) {
			return;
		}
		JsonObject responseData = data.getAsJsonObject("responseData");
		boolean active = responseData.has("outputActive") && responseData.get("outputActive").getAsBoolean();
		boolean paused = responseData.has("outputPaused") && responseData.get("outputPaused").getAsBoolean();
		RecordState next = paused ? RecordState.PAUSED : (active ? RecordState.RECORDING : RecordState.IDLE);
		updateRecordState(next, null, null);
	}

	private void updateRecordState(RecordState next, String outputState, String path) {
		RecordState previous = recordState.getAndSet(next);
		if (previous == next) {
			return;
		}
		try {
			ObsRecordingEvents.RECORD_STATE_CHANGED.invoker().onRecordStateChanged(previous, next);
			if (previous == RecordState.IDLE && next == RecordState.RECORDING) {
				ObsRecordingEvents.RECORDING_STARTED.invoker().onRecordingStarted();
			} else if (previous != RecordState.IDLE && next == RecordState.IDLE) {
				ObsRecordingEvents.RECORDING_STOPPED.invoker().onRecordingStopped();
			} else if (previous == RecordState.RECORDING && next == RecordState.PAUSED) {
				ObsRecordingEvents.RECORDING_PAUSED.invoker().onRecordingPaused();
			} else if (previous == RecordState.PAUSED && next == RecordState.RECORDING) {
				ObsRecordingEvents.RECORDING_RESUMED.invoker().onRecordingResumed();
			}
		} catch (Exception e) {
			LOGGER.warn("Recording event listener failed", e);
		}
		LOGGER.info("Record state {} -> {}", previous, next);
	}

	private void sendText(String text) {
		WebSocket ws = socketRef.get();
		if (ws == null) {
			return;
		}
		try {
			ws.sendText(text, true);
		} catch (Exception e) {
			LOGGER.debug("Failed to send OBS message: {}", e.toString());
		}
	}

	static String createAuthString(String password, String salt, String challenge) {
		try {
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
			byte[] secretHash = sha256.digest((password + salt).getBytes(StandardCharsets.UTF_8));
			String secret = Base64.getEncoder().encodeToString(secretHash);
			sha256.reset();
			byte[] authHash = sha256.digest((secret + challenge).getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(authHash);
		} catch (Exception e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}
}
