package com.whiteclient.modules;

import com.whiteclient.gui.Render2D;
import com.whiteclient.hud.Hud;
import com.whiteclient.hud.HudLayout;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ModeSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class ArmorHUD extends Module {
	public final ModeSetting position = add(new ModeSetting("Position", "Next To Hotbar", "Next To Hotbar", "Top Left", "Top Right", "Bottom Left", "Bottom Right", "Left", "Right"));
	public final ModeSetting durability = add(new ModeSetting("Durability", "Percent", "Percent", "Number", "Bar Only"));
	public final BoolSetting heldItem = add(new BoolSetting("Held Item", true));

	public ArmorHUD() {
		super("Armor HUD", "Armour and item durability", Category.HUD, true);
	}

	@Override
	public void renderHud(GuiGraphics ctx, float tickDelta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		List<ItemStack> items = new ArrayList<>();
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack s = mc.player.getItemBySlot(slot);
			if (!s.isEmpty()) {
				items.add(s);
			}
		}
		if (heldItem.isOn() && !mc.player.getMainHandItem().isEmpty() && mc.player.getMainHandItem().isDamageableItem()) {
			items.add(mc.player.getMainHandItem());
		}
		if (items.isEmpty()) {
			return;
		}
		Font tr = mc.font;
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
			x = ctx.guiWidth() / 2f + 91 + 6;
			y = ctx.guiHeight() - h - 1;
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
			ctx.renderItem(s, (int) ix, (int) iy);
			if (s.isDamageableItem()) {
				float frac = 1f - s.getDamageValue() / (float) Math.max(1, s.getMaxDamage());
				int col = 0xFF000000 | Colors.mix(0xFF5C6E, 0x7CFF9A, frac);
				if (hotbar || durability.is("Bar Only")) {
					Render2D.roundRect(ctx, ix + 1, iy + 17, 14, 2, 1, 0xAA000000);
					Render2D.roundRect(ctx, ix + 1, iy + 17, Math.max(1.5f, 14 * frac), 2, 1, col);
				} else {
					String t = durability.is("Number") ? String.valueOf(s.getMaxDamage() - s.getDamageValue()) : Math.round(frac * 100) + "%";
					Render2D.text(ctx, tr, t, ix + 20, iy + 4, col);
				}
			} else if (s.getCount() > 1) {
				Render2D.text(ctx, tr, "x" + s.getCount(), ix + 20, iy + 4, Hud.TEXT);
			}
		}
	}
}
