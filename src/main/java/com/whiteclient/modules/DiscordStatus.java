package com.whiteclient.modules;

import com.google.gson.JsonObject;
import com.whiteclient.WhiteClient;
import com.whiteclient.discord.DiscordIpc;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.TextSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;

import java.util.UUID;

/** Shows "Playing WhiteClient" with the logo, where you are and how long you have been playing on your Discord profile. */
public class DiscordStatus extends Module {
	/** The WhiteClient app on Discord: its name is what Discord shows after "Playing". */
	public static final String APP_ID = "1574503180992249856";

	public final BoolSetting showServer = add(new BoolSetting("Show Server Name", true));
	public final BoolSetting showWorld = add(new BoolSetting("Show World / Dimension", true));
	public final BoolSetting showHead = add(new BoolSetting("Show Your Skin Head", true));
	public final BoolSetting showTime = add(new BoolSetting("Show Play Time", true));
	public final TextSetting custom = add(new TextSetting("Custom Text", "", 100));
	public final TextSetting logoKey = add(new TextSetting("Logo Image Name", "logo", 64).visibleWhen(() -> false));

	private DiscordIpc ipc;
	private final long gameStart = System.currentTimeMillis() / 1000L;
	private long worldStart;
	private boolean wasInWorld;
	private int tick;

	public DiscordStatus() {
		super("Discord Status", "Shows Playing WhiteClient on your Discord profile", Category.MENU, true);
	}

	public boolean connected() {
		return ipc != null && ipc.connected();
	}

	@Override
	protected void onEnable() {
		tick = 0;
		ensure();
	}

	@Override
	protected void onDisable() {
		if (ipc != null) {
			ipc.stop(); // clears the status in Discord
			ipc = null;
		}
	}

	private void ensure() {
		if (ipc == null) {
			ipc = new DiscordIpc(APP_ID);
			ipc.start();
		}
	}

	@Override
	public void onTick(MinecraftClient mc) {
		ensure();
		boolean inWorld = mc.world != null;
		if (inWorld && !wasInWorld) {
			worldStart = System.currentTimeMillis() / 1000L;
			tick = 0;
		}
		if (inWorld != wasInWorld) {
			tick = 0;
		}
		wasInWorld = inWorld;
		if (tick-- > 0) {
			return;
		}
		tick = 40;
		try {
			ipc.setActivity(build(mc));
		} catch (Throwable t) {
			// never break the game for a status
		}
	}

	private JsonObject build(MinecraftClient mc) {
		JsonObject act = new JsonObject();
		String details;
		String state = null;
		ClientWorld world = mc.world;
		if (world == null) {
			details = "In the main menu";
		} else {
			ServerInfo info = mc.getCurrentServerEntry();
			if (mc.isInSingleplayer() || mc.isIntegratedServerRunning()) {
				details = "Playing singleplayer";
			} else if (info != null && info.isRealm()) {
				details = "Playing on Realms";
			} else if (info != null && showServer.isOn() && info.address != null && !info.address.isBlank()) {
				details = "Playing on " + info.address.trim();
			} else {
				details = "Playing multiplayer";
			}
			if (showWorld.isOn()) {
				if (world.getRegistryKey() == World.NETHER) {
					state = "In the Nether";
				} else if (world.getRegistryKey() == World.END) {
					state = "In the End";
				} else if (world.getRegistryKey() == World.OVERWORLD) {
					state = "In the Overworld";
				}
			}
		}
		String own = custom.get().trim();
		if (!own.isEmpty()) {
			state = own;
		}
		act.addProperty("details", fit(details));
		if (state != null) {
			act.addProperty("state", fit(state));
		}
		if (showTime.isOn()) {
			JsonObject ts = new JsonObject();
			ts.addProperty("start", world != null && worldStart > 0 ? worldStart : gameStart);
			act.add("timestamps", ts);
		}
		JsonObject assets = new JsonObject();
		String logo = logoKey.get().trim();
		if (!logo.isEmpty()) {
			assets.addProperty("large_image", logo);
			assets.addProperty("large_text", "WHITECLIENT " + WhiteClient.VERSION + " · visuals client");
		}
		if (showHead.isOn()) {
			String name = mc.getSession().getUsername();
			UUID id = mc.getSession().getUuidOrNull();
			String who = id != null ? id.toString().replace("-", "") : name;
			assets.addProperty("small_image", "https://mc-heads.net/avatar/" + who + "/64");
			assets.addProperty("small_text", fit(name));
		}
		if (assets.size() > 0) {
			act.add("assets", assets);
		}
		return act;
	}

	/** Discord wants 2..128 characters. */
	private static String fit(String s) {
		if (s.length() > 128) {
			s = s.substring(0, 125) + "...";
		}
		return s.length() < 2 ? s + "  " : s;
	}
}
