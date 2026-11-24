package com.whiteclient.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tiny helper that runs on its own after you close Minecraft: it waits for the game to exit, removes the old
 * WHITECLIENT jar and puts the new one in your mods folder. Only plain Java, no Minecraft classes.
 *
 * <p>Arguments: game pid, old jar, downloaded new jar, where the new jar goes.
 */
public final class Swap {
	private Swap() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 4) {
			log("wrong arguments");
			return;
		}
		long pid = Long.parseLong(args[0]);
		Path oldJar = Path.of(args[1]);
		Path staged = Path.of(args[2]);
		Path target = Path.of(args[3]);
		log("waiting for the game (pid " + pid + ") to close");

		Optional<ProcessHandle> game = ProcessHandle.of(pid);
		if (game.isPresent()) {
			try {
				game.get().onExit().get(15, TimeUnit.MINUTES);
			} catch (TimeoutException e) {
				log("game still running after 15 minutes, trying again next time");
				return;
			}
		}
		if (!Files.isRegularFile(staged)) {
			log("new jar missing: " + staged);
			return;
		}
		// Windows can keep the old jar locked for a moment after the game closes
		boolean removed = false;
		for (int i = 0; i < 120 && !removed; i++) {
			try {
				Files.deleteIfExists(oldJar);
				removed = true;
			} catch (IOException e) {
				Thread.sleep(500);
			}
		}
		if (!removed) {
			log("could not remove " + oldJar + " (still in use), trying again next time");
			return;
		}
		Path tmp = target.resolveSibling(target.getFileName() + ".part");
		Files.copy(staged, tmp, StandardCopyOption.REPLACE_EXISTING);
		Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		log("updated: " + oldJar.getFileName() + " -> " + target.getFileName());
	}

	private static void log(String msg) {
		System.out.println("[" + LocalDateTime.now().withNano(0) + "] " + msg);
	}
}
