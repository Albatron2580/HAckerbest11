package com.whiteclient.modules;

import com.whiteclient.WhiteClient;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.CapeTextures;
import com.whiteclient.setting.ActionSetting;
import com.whiteclient.setting.ModeSetting;
import com.whiteclient.setting.TextSetting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

public class Capes extends Module {
	public final ModeSetting design = add(new ModeSetting("Cape", "White", CapeTextures.DESIGNS));
	public final TextSetting customFile = add(new TextSetting("Custom File", "", 64).visibleWhen(() -> design.is("Custom")));
	public final ActionSetting openFolder = add(new ActionSetting("Cape Folder", () -> "Open", () -> {
		try {
			Util.getOperatingSystem().open(CapeTextures.folder().toFile());
		} catch (Throwable ignored) {
		}
	}));
	public final ActionSetting reload = add(new ActionSetting("Reload Capes", () -> "Reload", CapeTextures::reload));

	public Capes() {
		super("Capes", "Free capes (only you see them)", Category.COSMETICS, true);
	}

	public static Capes get() {
		WhiteClient wc = WhiteClient.get();
		return wc == null ? null : wc.modules().get(Capes.class);
	}

	public Identifier texture() {
		if (design.is("Custom")) {
			String f = customFile.get().trim();
			if (f.isEmpty()) {
				var files = CapeTextures.customFiles();
				f = files.isEmpty() ? "" : files.get(0);
			}
			return CapeTextures.file(f);
		}
		return CapeTextures.design(design.get());
	}
}
