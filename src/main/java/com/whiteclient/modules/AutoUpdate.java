package com.whiteclient.modules;

import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.setting.ActionSetting;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.InfoSetting;
import com.whiteclient.update.AutoUpdater;
import net.minecraft.client.MinecraftClient;

public class AutoUpdate extends Module {
	public final InfoSetting statusInfo = add(new InfoSetting("Status", AutoUpdater::status));
	public final ActionSetting checkNow = add(new ActionSetting("Updates", () -> "Check Now", () -> AutoUpdater.checkAsync(this.installAuto.isOn(), true)));
	public final BoolSetting installAuto = add(new BoolSetting("Install Automatically", true));

	private long nextCheck = System.currentTimeMillis() + 10_000;
	private long lastCheck;
	private Object connection;
	private long joinedAt;

	public AutoUpdate() {
		super("Auto Update", "Keeps WHITECLIENT up to date by itself", Category.MENU, true);
	}

	@Override
	public void onTick(MinecraftClient mc) {
		long now = System.currentTimeMillis();
		boolean onMainMenu = mc.currentScreen instanceof net.minecraft.client.gui.screen.TitleScreen
				|| mc.currentScreen instanceof com.whiteclient.gui.WhiteTitleScreen;
		if (now >= nextCheck || (onMainMenu && now - lastCheck > 3 * 60 * 1000)) {
			nextCheck = now + 20L * 60 * 1000;
			lastCheck = now;
			AutoUpdater.checkAsync(installAuto.isOn(), false);
		}
		Object conn = mc.getNetworkHandler();
		if (conn != connection) {
			connection = conn;
			joinedAt = conn != null ? now : 0;
		}
		if (joinedAt != 0 && now - joinedAt > 2500 && mc.player != null) {
			joinedAt = 0;
			AutoUpdater.chatNotice(mc);
		}
	}
}
