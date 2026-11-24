package com.whiteclient.modules;

import com.whiteclient.WhiteClient;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.mixin.PlayerLikeEntityAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NameLogo extends Module {
	public static final String GLYPH = "";

	public final BoolSetting ownTag = add(new BoolSetting("Show Your Nametag In F5", true));
	public final BoolSetting tab = add(new BoolSetting("Logo In Tab List", true));
	public final BoolSetting others = add(new BoolSetting("Logo On Other WhiteClient Users", true));
	public final BoolSetting share = add(new BoolSetting("Let Others See You Use WhiteClient", true));

	public static final int FLAG = 0x80;
	private static final Set<UUID> USERS = ConcurrentHashMap.newKeySet();
	private int scan;
	private boolean lastShare = true;

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

	public static boolean broadcasting() {
		NameLogo n = get();
		return n != null && n.isEnabled() && n.share.isOn();
	}

	private static void resend() {
		try {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc != null && mc.player != null && mc.getNetworkHandler() != null) {
				mc.options.sendClientSettings();
			}
		} catch (Throwable ignored) {
		}
	}

	@Override
	protected void onEnable() {
		resend();
	}

	@Override
	protected void onDisable() {
		resend();
	}

	public static SyncedClientOptions withFlag(SyncedClientOptions o) throws ReflectiveOperationException {
		int parts = o.playerModelParts();
		if ((parts & FLAG) != 0) {
			return o;
		}
		RecordComponent[] rc = SyncedClientOptions.class.getRecordComponents();
		Object[] vals = new Object[rc.length];
		Class<?>[] types = new Class<?>[rc.length];
		for (int i = 0; i < rc.length; i++) {
			types[i] = rc[i].getType();
			rc[i].getAccessor().setAccessible(true);
			vals[i] = rc[i].getAccessor().invoke(o);
		}
		int target = -1;
		if (rc.length > 4 && types[4] == int.class && (Integer) vals[4] == parts) {
			target = 4;
		} else {
			for (int i = 0; i < rc.length; i++) {
				if (types[i] == int.class && (Integer) vals[i] == parts) {
					if (target >= 0) {
						return o;
					}
					target = i;
				}
			}
		}
		if (target < 0) {
			return o;
		}
		vals[target] = parts | FLAG;
		Constructor<SyncedClientOptions> c = SyncedClientOptions.class.getDeclaredConstructor(types);
		c.setAccessible(true);
		return c.newInstance(vals);
	}

	public static boolean isUser(Entity e) {
		if (!(e instanceof PlayerEntity p) || isSelf(e)) {
			return false;
		}
		try {
			return (p.getDataTracker().get(PlayerLikeEntityAccessor.whiteclient$modelParts()) & FLAG) != 0;
		} catch (Throwable t) {
			return false;
		}
	}

	public static boolean isKnownUser(UUID id) {
		return id != null && USERS.contains(id);
	}

	public static boolean ownFlagSynced() {
		MinecraftClient mc = MinecraftClient.getInstance();
		try {
			return mc.player != null && (mc.player.getDataTracker().get(PlayerLikeEntityAccessor.whiteclient$modelParts()) & FLAG) != 0;
		} catch (Throwable t) {
			return false;
		}
	}

	@Override
	public void onTick(MinecraftClient mc) {
		if (mc.world == null) {
			USERS.clear();
			return;
		}
		if (share.isOn() != lastShare) {
			lastShare = share.isOn();
			resend();
		}
		if (scan-- > 0) {
			return;
		}
		scan = 10;
		for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
			if (p == mc.player) {
				continue;
			}
			if (isUser(p)) {
				USERS.add(p.getUuid());
			} else {
				USERS.remove(p.getUuid());
			}
		}
	}

	public static boolean showOthers() {
		NameLogo n = get();
		return n != null && n.isEnabled() && n.others.isOn();
	}

	public static Text decorate(Text name) {
		if (name == null || name.getString().startsWith(GLYPH)) {
			return name;
		}
		MutableText logo = Text.literal(GLYPH).styled(s -> s.withColor(0xFFFFFF).withBold(false).withItalic(false));
		return Text.empty().append(logo).append(Text.literal(" ")).append(name);
	}
}
