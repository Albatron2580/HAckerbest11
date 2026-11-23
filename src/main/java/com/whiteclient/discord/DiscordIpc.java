package com.whiteclient.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whiteclient.WhiteClient;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Talks to the Discord desktop app over its local IPC pipe (the same way every game shows
 * "Playing ..."), so no extra library is needed. Nothing leaves your PC except to the Discord app.
 * Runs on its own background thread and quietly retries when Discord is closed.
 */
public final class DiscordIpc {
	private static final int OP_HANDSHAKE = 0, OP_FRAME = 1, OP_CLOSE = 2, OP_PING = 3, OP_PONG = 4;

	private final String clientId;
	private volatile boolean running;
	private Thread thread;

	private volatile JsonObject wanted;
	private volatile boolean dirty = true;
	private volatile boolean ready;
	private JsonObject lastSent;

	private Pipe pipe;

	public DiscordIpc(String clientId) {
		this.clientId = clientId;
	}

	public boolean connected() {
		return ready;
	}

	public String clientId() {
		return clientId;
	}

	public synchronized void start() {
		if (running) {
			return;
		}
		running = true;
		thread = new Thread(this::loop, "WHITECLIENT Discord");
		thread.setDaemon(true);
		thread.start();
	}

	public synchronized void stop() {
		running = false;
		if (thread != null) {
			thread.interrupt();
			thread = null;
		}
	}

	/** The activity to show; {@code null} clears it. Sent when it changes (Discord allows ~5 updates per 20 s). */
	public void setActivity(JsonObject activity) {
		JsonObject cur = wanted;
		if (cur == null ? activity == null : cur.equals(activity)) {
			return;
		}
		wanted = activity;
		dirty = true;
	}

	/** A short status line for the menu. */
	public String status() {
		return status;
	}

	private volatile String status = "Starting...";
	private int errors;

	// ------------------------------------------------------------------ loop
	// Every message is "send, then read Discord's answer" on this one thread. That is the only way that
	// works reliably with the Windows named pipe (a waiting read there blocks writes from other threads).

	private void loop() {
		long nextTry = 0, lastSend = 0;
		boolean loggedMissing = false;
		while (running) {
			try {
				long now = System.currentTimeMillis();
				if (pipe == null) {
					if (now < nextTry) {
						Thread.sleep(500);
						continue;
					}
					pipe = Pipe.open();
					if (pipe == null) {
						status = "Discord app not found - open the Discord desktop app";
						if (!loggedMissing) {
							WhiteClient.LOGGER.info("[WHITECLIENT] Discord app not found, Discord status will connect when it is open");
							loggedMissing = true;
						}
						nextTry = now + 10000;
						continue;
					}
					WhiteClient.LOGGER.info("[WHITECLIENT] Discord pipe found ({}), saying hello", pipe);
					status = "Connecting to Discord...";
					ready = false;
					lastSent = null;
					JsonObject hs = new JsonObject();
					hs.addProperty("v", 1);
					hs.addProperty("client_id", clientId);
					send(OP_HANDSHAKE, hs);
					// wait for READY
					for (int i = 0; i < 4 && !ready; i++) {
						handle(readFrame());
					}
					if (!ready) {
						throw new IOException("Discord did not say READY");
					}
					dirty = true;
					lastSend = 0;
				}
				// send when something changed, and refresh every minute (also notices a restarted Discord)
				boolean refresh = now - lastSend > 60000;
				if ((dirty && now - lastSend > 4000) || refresh) {
					JsonObject act = wanted;
					if (act != null && errors >= 1) {
						act = withoutLinks(act);
					}
					if (act != null && errors >= 2) {
						act = act.deepCopy();
						act.remove("assets");
					}
					dirty = false;
					lastSend = now;
					lastSent = act;
					String nonce = sendActivity(act);
					// read until Discord answers this message
					for (int i = 0; i < 6; i++) {
						Frame f = readFrame();
						if (handle(f) && nonce.equals(nonceOf(f))) {
							break;
						}
					}
				}
				Thread.sleep(250);
			} catch (InterruptedException e) {
				break;
			} catch (Throwable t) {
				String why = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
				WhiteClient.LOGGER.info("[WHITECLIENT] Discord connection problem: {}", why);
				status = "Lost Discord (" + why + "), trying again...";
				closePipe();
				nextTry = System.currentTimeMillis() + 10000;
			}
		}
		// clear the status when the module is turned off / the game closes
		try {
			if (pipe != null && ready) {
				sendActivity(null);
			}
		} catch (Throwable ignored) {
		}
		closePipe();
	}

	private void closePipe() {
		ready = false;
		dirty = true;
		if (pipe != null) {
			try {
				pipe.close();
			} catch (Throwable ignored) {
			}
			pipe = null;
		}
	}

	private String sendActivity(JsonObject activity) throws IOException {
		JsonObject args = new JsonObject();
		args.addProperty("pid", ProcessHandle.current().pid());
		if (activity != null) {
			args.add("activity", activity);
		}
		JsonObject msg = new JsonObject();
		String nonce = UUID.randomUUID().toString();
		msg.addProperty("cmd", "SET_ACTIVITY");
		msg.add("args", args);
		msg.addProperty("nonce", nonce);
		send(OP_FRAME, msg);
		return nonce;
	}

	private static JsonObject withoutLinks(JsonObject activity) {
		JsonObject copy = activity.deepCopy();
		if (copy.has("assets")) {
			JsonObject assets = copy.getAsJsonObject("assets");
			for (String key : new String[]{"large_image", "small_image"}) {
				if (assets.has(key) && assets.get(key).getAsString().startsWith("http")) {
					assets.remove(key);
					assets.remove(key.replace("image", "text"));
				}
			}
		}
		return copy;
	}

	// ------------------------------------------------------------------ framing

	private record Frame(int op, String body, JsonObject json) {
	}

	private void send(int op, JsonObject json) throws IOException {
		byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
		ByteBuffer b = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
		b.putInt(op).putInt(body.length).put(body);
		pipe.write(b.array());
	}

	private Frame readFrame() throws IOException {
		byte[] head = new byte[8];
		pipe.readFully(head);
		ByteBuffer h = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
		int op = h.getInt(), len = h.getInt();
		if (len < 0 || len > 1 << 20) {
			throw new IOException("bad message from Discord");
		}
		byte[] body = new byte[len];
		pipe.readFully(body);
		String text = new String(body, StandardCharsets.UTF_8);
		JsonObject json = null;
		try {
			json = JsonParser.parseString(text).getAsJsonObject();
		} catch (Throwable ignored) {
		}
		return new Frame(op, text, json);
	}

	private static String nonceOf(Frame f) {
		return f.json() != null && f.json().has("nonce") && !f.json().get("nonce").isJsonNull() ? f.json().get("nonce").getAsString() : "";
	}

	/** Handles one message; returns true for normal answers. */
	private boolean handle(Frame f) throws IOException {
		switch (f.op()) {
			case OP_PING -> {
				byte[] b = f.body().getBytes(StandardCharsets.UTF_8);
				ByteBuffer out = ByteBuffer.allocate(8 + b.length).order(ByteOrder.LITTLE_ENDIAN);
				out.putInt(OP_PONG).putInt(b.length).put(b);
				pipe.write(out.array());
				return false;
			}
			case OP_CLOSE -> {
				WhiteClient.LOGGER.warn("[WHITECLIENT] Discord closed the connection: {}", f.body());
				String msg = f.json() != null && f.json().has("message") ? f.json().get("message").getAsString() : "closed";
				throw new IOException("Discord: " + msg);
			}
			case OP_FRAME -> {
				JsonObject msg = f.json();
				if (msg == null) {
					return false;
				}
				String evt = msg.has("evt") && !msg.get("evt").isJsonNull() ? msg.get("evt").getAsString() : "";
				if (evt.equals("READY")) {
					ready = true;
					status = "Connected - showing Playing WhiteClient";
					WhiteClient.LOGGER.info("[WHITECLIENT] Discord status connected");
				} else if (evt.equals("ERROR")) {
					WhiteClient.LOGGER.warn("[WHITECLIENT] Discord did not accept the status: {}", f.body());
					String m = msg.has("data") && msg.getAsJsonObject("data").has("message")
							? msg.getAsJsonObject("data").get("message").getAsString() : "error";
					status = "Discord said: " + m;
					if (errors < 2) {
						errors++; // try again with fewer pictures
						dirty = true;
					}
				} else if (ready && msg.has("cmd") && "SET_ACTIVITY".equals(msg.get("cmd").getAsString())) {
					status = "Connected - showing Playing WhiteClient";
				}
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	// ------------------------------------------------------------------ pipes

	private interface Pipe {
		void readFully(byte[] into) throws IOException;

		void write(byte[] data) throws IOException;

		void close() throws IOException;

		static Pipe open() {
			boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
			for (int i = 0; i < 10; i++) {
				if (windows) {
					try {
						return new WindowsPipe(new RandomAccessFile("\\\\?\\pipe\\discord-ipc-" + i, "rw"), i);
					} catch (Throwable ignored) {
					}
				} else {
					for (Path dir : unixDirs()) {
						Path p = dir.resolve("discord-ipc-" + i);
						if (!Files.exists(p)) {
							continue;
						}
						try {
							SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
							ch.connect(UnixDomainSocketAddress.of(p));
							return new UnixPipe(ch, p.toString());
						} catch (Throwable ignored) {
						}
					}
				}
			}
			return null;
		}

		private static List<Path> unixDirs() {
			List<Path> out = new ArrayList<>();
			for (String env : new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
				String v = System.getenv(env);
				if (v != null && !v.isBlank()) {
					Path base = Path.of(v);
					out.add(base);
					out.add(base.resolve("app/com.discordapp.Discord")); // Flatpak
					out.add(base.resolve("snap.discord")); // Snap
					out.add(base.resolve(".flatpak/dev.vencord.Vesktop/xdg-run"));
				}
			}
			out.add(Path.of("/tmp"));
			return out;
		}
	}

	private record WindowsPipe(RandomAccessFile file, int index) implements Pipe {
		@Override
		public void readFully(byte[] into) throws IOException {
			file.readFully(into);
		}

		@Override
		public void write(byte[] data) throws IOException {
			file.write(data);
		}

		@Override
		public void close() throws IOException {
			file.close();
		}

		@Override
		public String toString() {
			return "discord-ipc-" + index;
		}
	}

	private record UnixPipe(SocketChannel channel, String path) implements Pipe {
		@Override
		public void readFully(byte[] into) throws IOException {
			ByteBuffer b = ByteBuffer.wrap(into);
			while (b.hasRemaining()) {
				if (channel.read(b) < 0) {
					throw new IOException("Discord closed the pipe");
				}
			}
		}

		@Override
		public void write(byte[] data) throws IOException {
			ByteBuffer b = ByteBuffer.wrap(data);
			while (b.hasRemaining()) {
				channel.write(b);
			}
		}

		@Override
		public void close() throws IOException {
			channel.close();
		}

		@Override
		public String toString() {
			return path;
		}
	}
}
