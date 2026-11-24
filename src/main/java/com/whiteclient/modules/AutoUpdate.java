package com.whiteclient.modules;

import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.setting.ActionSetting;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.InfoSetting;
import com.whiteclient.update.AutoUpdater;
import net.minecraft.client.MinecraftClient;

/** Checks for new WHITECLIENT versions, downloads them in the background and installs them when you close the game. */
public class AutoUpdate extends Module {
	public final InfoSetting statusInfo = add(new InfoSetting("Status", AutoUpdater::status));
	public final ActionSetting checkNow = add(new ActionSetting("Updates", () -> "Check Now", () -> AutoUpdater.checkAsync(this.installAuto.isOn(), true)));
	public final BoolSetting installAuto = add(new BoolSetting("Install Automatically", true));

	private long nextCheck = System.currentTimeMillis() + 15_000;

	public AutoUpdate() {
		super("Auto Update", "Keeps WHITECLIENT up to date by itself", Category.MENU, true);
	}

	@Override
	public void onTick(MinecraftClient mc) {
		long now = System.currentTimeMillis();
		if (now >= nextCheck) {
			nextCheck = now + 6L * 60 * 60 * 1000; // and again every 6 hours while you play
			AutoUpdater.checkAsync(installAuto.isOn(), false);
		}
	}
}
