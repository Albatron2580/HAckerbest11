package com.whiteclient.modules;

import com.whiteclient.gui.Render2D;
import com.whiteclient.hud.Hud;
import com.whiteclient.hud.HudLayout;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ModeSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Your armour and held item with durability. */
public class ArmorHUD extends Module {
	public final ModeSetting position = add(new ModeSetting("Position", "Next To Hotbar", "Next To Hotbar", "Top Left", "Top Right", "Bottom Left", "Bottom Right", "Left", "Right"));
	public final ModeSetting durability = add(new ModeSetting("Durability", "Percent", "Percent", "Number", "Bar Only"));
	public final BoolSetting heldItem = add(new BoolSetting("Held Item", true));

	public ArmorHUD() {
		super("Armor HUD", "Armour and item durability", Category.HUD, false);
	}

	@Override
	public void renderHud(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) {
			return;
		}
		List<ItemStack> items = new ArrayList<>();
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack s = mc.player.getEquippedStack(slot);
			if (!s.isEmpty()) {
				items.add(s);
			}
		}
		if (heldItem.isOn() && !mc.player.getMainHandStack().isEmpty() && mc.player.getMainHandStack().isDamageable()) {
			items.add(mc.player.getMainHandStack());
		}
		if (items.isEmpty()) {
			return;
		}
		TextRenderer tr = mc.textRenderer;
		boolean hotbar = position.is("Next To Hotbar");
		float cell = durability.is("Bar Only") ? 20 : 32;
		float w, h;
		if (hotbar) {
			w = items.size() * 20 + 4;
			h = 24;
		} else {
			w = 22 + cell;
			h = items.size() * 20 + 4;
		}
		float x, y;
		if (hotbar) {
			x = ctx.getScaledWindowWidth() / 2f + 91 + 6;
			y = ctx.getScaledWindowHeight() - h - 1;
		} else {
			float[] p = HudLayout.place(position.get(), w, h);
			x = p[0];
			y = p[1];
		}
		Hud.panel(ctx, x, y, w, h, 0.9f);
		for (int i = 0; i < items.size(); i++) {
			ItemStack s = items.get(i);
			float ix = hotbar ? x + 2 + i * 20 + 2 : x + 4;
			float iy = hotbar ? y + 2 : y + 2 + i * 20 + 2;
			ctx.drawItem(s, (int) ix, (int) iy);
			if (s.isDamageable()) {
				float frac = 1f - s.getDamage() / (float) Math.max(1, s.getMaxDamage());
				int col = 0xFF000000 | Colors.mix(0xFF5C6E, 0x7CFF9A, frac);
				if (hotbar || durability.is("Bar Only")) {
					Render2D.roundRect(ctx, ix + 1, iy + 17, 14, 2, 1, 0xAA000000);
					Render2D.roundRect(ctx, ix + 1, iy + 17, Math.max(1.5f, 14 * frac), 2, 1, col);
				} else {
					String t = durability.is("Number") ? String.valueOf(s.getMaxDamage() - s.getDamage()) : Math.round(frac * 100) + "%";
					Render2D.text(ctx, tr, t, ix + 20, iy + 4, col);
				}
			} else if (s.getCount() > 1) {
				Render2D.text(ctx, tr, "x" + s.getCount(), ix + 20, iy + 4, Hud.TEXT);
			}
		}
	}
}
