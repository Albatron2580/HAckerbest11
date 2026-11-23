package com.whiteclient.modules;

import com.whiteclient.WhiteClient;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.setting.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/** The WHITECLIENT planet logo in front of your name (above your head and in the Tab list). */
public class NameLogo extends Module {
	/** Private-use character drawn as the planet logo by WHITECLIENT's font (assets/minecraft/font/default.json). */
	public static final String GLYPH = "";

	public final BoolSetting ownTag = add(new BoolSetting("Show Your Nametag In F5", true));
	public final BoolSetting tab = add(new BoolSetting("Logo In Tab List", true));

	public NameLogo() {
		super("Name Logo", "WHITECLIENT logo in front of your name (head + Tab list)", Category.COSMETICS, true);
	}

	public static NameLogo get() {
		WhiteClient wc = WhiteClient.get();
		return wc == null ? null : wc.modules().get(NameLogo.class);
	}

	public static boolean active() {
		NameLogo n = get();
		return n != null && n.isEnabled();
	}

	public static boolean isSelf(Entity e) {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc != null && mc.player != null && e == mc.player;
	}

	public static Text decorate(Text name) {
		if (name == null || name.getString().startsWith(GLYPH)) {
			return name;
		}
		MutableText logo = Text.literal(GLYPH).styled(s -> s.withColor(0xFFFFFF).withBold(false).withItalic(false));
		return Text.empty().append(logo).append(Text.literal(" ")).append(name);
	}
}
