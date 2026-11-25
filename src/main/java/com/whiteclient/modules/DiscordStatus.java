package com.whiteclient.modules;

import com.google.gson.JsonObject;
import com.whiteclient.WhiteClient;
import com.whiteclient.discord.DiscordIpc;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.setting.ActionSetting;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.InfoSetting;
import com.whiteclient.setting.TextSetting;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.Level;

public class DiscordStatus extends Module {
	public static final String APP_ID = "1574503180992249856";

	public final InfoSetting statusInfo = add(new InfoSetting("Status", () -> this.ipc == null ? "Off" : this.ipc.status()));
	public final ActionSetting reconnect = add(new ActionSetting("Discord", () -> "Reconnect", this::restart));
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
	private boolean wasFocused;
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
			ipc.stop();
			ipc = null;
		}
	}

	private void restart() {
		if (ipc != null) {
			ipc.stop();
			ipc = null;
		}
		tick = 0;
		if (isEnabled()) {
			ensure();
		}
	}

	private void ensure() {
		if (ipc == null) {
			ipc = new DiscordIpc(APP_ID);
			ipc.start();
		}
	}

	@Override
	public void onTick(Minecraft mc) {
		ensure();
		boolean focused = mc.isWindowActive();
		if (focused && !wasFocused) {
			ipc.bump();
		}
		wasFocused = focused;
		boolean inWorld = mc.level != null;
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
		}
	}

	private JsonObject build(Minecraft mc) {
		JsonObject act = new JsonObject();
		String details;
		String state = null;
		ClientLevel world = mc.level;
		if (world == null) {
			details = "In the main menu";
		} else {
			ServerData info = mc.getCurrentServer();
			if (mc.isLocalServer() || mc.hasSingleplayerServer()) {
				details = "Playing singleplayer";
			} else if (info != null && info.isRealm()) {
				details = "Playing on Realms";
			} else if (info != null && showServer.isOn() && info.ip != null && !info.ip.isBlank()) {
				details = "Playing on " + info.ip.trim();
			} else {
				details = "Playing multiplayer";
			}
			if (showWorld.isOn()) {
				if (world.dimension() == Level.NETHER) {
					state = "In the Nether";
				} else if (world.dimension() == Level.END) {
					state = "In the End";
				} else if (world.dimension() == Level.OVERWORLD) {
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
			String name = mc.getUser().getName();
			UUID id = mc.getUser().getProfileId();
			String who = id != null ? id.toString().replace("-", "") : name;
			assets.addProperty("small_image", "https://mc-heads.net/avatar/" + who + "/64");
			assets.addProperty("small_text", fit(name));
		}
		if (assets.size() > 0) {
			act.add("assets", assets);
		}
		return act;
	}

	private static String fit(String s) {
		if (s.length() > 128) {
			s = s.substring(0, 125) + "...";
		}
		return s.length() < 2 ? s + "  " : s;
	}
}
