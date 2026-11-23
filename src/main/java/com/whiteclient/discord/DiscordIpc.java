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
	/** Discord refused an image link: send activities without links from then on. */
	private volatile boolean noLinks;
	private JsonObject lastSent;

	private Pipe pipe;
	private byte[] acc = new byte[4096];
	private int accLen;

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

	// ------------------------------------------------------------------ loop

	private void loop() {
		long nextTry = 0, connectedAt = 0, lastSend = 0;
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
						if (!loggedMissing) {
							WhiteClient.LOGGER.info("[WHITECLIENT] Discord app not found, Discord status will connect when it is open");
							loggedMissing = true;
						}
						nextTry = now + 15000;
						continue;
					}
					accLen = 0;
					ready = false;
					lastSent = null;
					connectedAt = now;
					JsonObject hs = new JsonObject();
					hs.addProperty("v", 1);
					hs.addProperty("client_id", clientId);
					send(OP_HANDSHAKE, hs);
				}
				pump();
				if (!ready && now - connectedAt > 10000) {
					throw new IOException("no answer from Discord");
				}
				if (ready && dirty && now - lastSend > 4000) {
					JsonObject act = wanted;
					if (act != null && noLinks) {
						act = withoutLinks(act);
					}
					sendActivity(act);
					lastSent = act;
					dirty = false;
					lastSend = now;
				}
				Thread.sleep(250);
			} catch (InterruptedException e) {
				break;
			} catch (Throwable t) {
				if (ready) {
					WhiteClient.LOGGER.info("[WHITECLIENT] Discord disconnected ({})", t.getMessage());
				}
				closePipe();
				nextTry = System.currentTimeMillis() + 15000;
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

	private void sendActivity(JsonObject activity) throws IOException {
		JsonObject args = new JsonObject();
		args.addProperty("pid", ProcessHandle.current().pid());
		if (activity != null) {
			args.add("activity", activity);
		}
		JsonObject msg = new JsonObject();
		msg.addProperty("cmd", "SET_ACTIVITY");
		msg.add("args", args);
		msg.addProperty("nonce", UUID.randomUUID().toString());
		send(OP_FRAME, msg);
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

	private void send(int op, JsonObject json) throws IOException {
		byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
		ByteBuffer b = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
		b.putInt(op).putInt(body.length).put(body);
		pipe.write(b.array());
	}

	private void pump() throws IOException {
		byte[] in = pipe.readAvailable();
		if (in.length > 0) {
			if (accLen + in.length > acc.length) {
				byte[] n = new byte[Math.max(acc.length * 2, accLen + in.length)];
				System.arraycopy(acc, 0, n, 0, accLen);
				acc = n;
			}
			System.arraycopy(in, 0, acc, accLen, in.length);
			accLen += in.length;
		}
		while (accLen >= 8) {
			ByteBuffer h = ByteBuffer.wrap(acc, 0, 8).order(ByteOrder.LITTLE_ENDIAN);
			int op = h.getInt(), len = h.getInt();
			if (len < 0 || len > 1 << 20) {
				throw new IOException("bad frame");
			}
			if (accLen < 8 + len) {
				break;
			}
			String body = new String(acc, 8, len, StandardCharsets.UTF_8);
			System.arraycopy(acc, 8 + len, acc, 0, accLen - 8 - len);
			accLen -= 8 + len;
			handle(op, body);
		}
	}

	private void handle(int op, String body) throws IOException {
		switch (op) {
			case OP_PING -> {
				byte[] b = body.getBytes(StandardCharsets.UTF_8);
				ByteBuffer f = ByteBuffer.allocate(8 + b.length).order(ByteOrder.LITTLE_ENDIAN);
				f.putInt(OP_PONG).putInt(b.length).put(b);
				pipe.write(f.array());
			}
			case OP_CLOSE -> {
				WhiteClient.LOGGER.warn("[WHITECLIENT] Discord closed the connection: {}", body);
				throw new IOException("closed by Discord");
			}
			case OP_FRAME -> {
				JsonObject msg;
				try {
					msg = JsonParser.parseString(body).getAsJsonObject();
				} catch (Throwable t) {
					return;
				}
				String evt = msg.has("evt") && !msg.get("evt").isJsonNull() ? msg.get("evt").getAsString() : "";
				if (evt.equals("READY")) {
					ready = true;
					dirty = true;
					WhiteClient.LOGGER.info("[WHITECLIENT] Discord status connected");
				} else if (evt.equals("ERROR")) {
					WhiteClient.LOGGER.warn("[WHITECLIENT] Discord did not accept the status: {}", body);
					if (!noLinks && lastSent != null && lastSent.toString().contains("\"http")) {
						noLinks = true; // try again without the skin-head picture link
						dirty = true;
					}
				}
			}
			default -> {
			}
		}
	}

	// ------------------------------------------------------------------ pipes

	private interface Pipe {
		byte[] readAvailable() throws IOException;

		void write(byte[] data) throws IOException;

		void close() throws IOException;

		static Pipe open() {
			boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
			for (int i = 0; i < 10; i++) {
				if (windows) {
					try {
						return new WindowsPipe(new RandomAccessFile("\\\\?\\pipe\\discord-ipc-" + i, "rw"));
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
							ch.configureBlocking(false);
							return new UnixPipe(ch);
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

	private record WindowsPipe(RandomAccessFile file) implements Pipe {
		@Override
		public byte[] readAvailable() throws IOException {
			long n = file.length(); // bytes waiting in the pipe
			if (n <= 0) {
				return new byte[0];
			}
			byte[] b = new byte[(int) Math.min(n, 1 << 20)];
			file.readFully(b);
			return b;
		}

		@Override
		public void write(byte[] data) throws IOException {
			file.write(data);
		}

		@Override
		public void close() throws IOException {
			file.close();
		}
	}

	private record UnixPipe(SocketChannel channel) implements Pipe {
		@Override
		public byte[] readAvailable() throws IOException {
			ByteBuffer buf = ByteBuffer.allocate(8192);
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			while (true) {
				buf.clear();
				int r = channel.read(buf);
				if (r < 0) {
					throw new IOException("Discord closed the pipe");
				}
				if (r == 0) {
					break;
				}
				out.write(buf.array(), 0, r);
			}
			return out.toByteArray();
		}

		@Override
		public void write(byte[] data) throws IOException {
			ByteBuffer b = ByteBuffer.wrap(data);
			long until = System.currentTimeMillis() + 2000;
			while (b.hasRemaining()) {
				if (channel.write(b) == 0) {
					if (System.currentTimeMillis() > until) {
						throw new IOException("Discord is not reading");
					}
					Thread.onSpinWait();
				}
			}
		}

		@Override
		public void close() throws IOException {
			channel.close();
		}
	}
}
