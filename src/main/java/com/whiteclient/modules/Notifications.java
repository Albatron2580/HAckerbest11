package com.whiteclient.modules;

import com.whiteclient.gui.Render2D;
import com.whiteclient.hud.Hud;
import com.whiteclient.hud.HudLayout;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.setting.ModeSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class Notifications extends Module {
	public final ModeSetting position = add(new ModeSetting("Position", "Bottom Right", "Bottom Right", "Top Right", "Bottom Left", "Top Left"));

	private static final List<Object[]> TOASTS = new ArrayList<>();

	public Notifications() {
		super("Notifications", "Toasts when you toggle a module", Category.HUD, true);
		Module.toggleListener = m -> {
			if (m == this) {
				return;
			}
			synchronized (TOASTS) {
				TOASTS.removeIf(t -> t[0].equals(m.getName()));
				TOASTS.add(new Object[]{m.getName(), m.isEnabled(), System.currentTimeMillis()});
				while (TOASTS.size() > 4) {
					TOASTS.remove(0);
				}
			}
		};
	}

	@Override
	public void renderHud(GuiGraphics ctx, float tickDelta) {
		Minecraft mc = Minecraft.getInstance();
		Font tr = mc.font;
		long now = System.currentTimeMillis();
		List<Object[]> list;
		synchronized (TOASTS) {
			TOASTS.removeIf(t -> now - (long) t[2] > 2600);
			list = new ArrayList<>(TOASTS);
		}
		boolean right = position.get().endsWith("Right");
		for (int i = list.size() - 1; i >= 0; i--) {
			Object[] t = list.get(i);
			String name = (String) t[0];
			boolean on = (boolean) t[1];
			float age = (now - (long) t[2]) / 1000f;
			float in = Math.min(1f, age / 0.25f), out = Math.min(1f, Math.max(0f, (2.6f - age) / 0.3f));
			float a = Math.min(in, out);
			float ease = 1f - (1f - in) * (1f - in);
			String sub = on ? "Enabled" : "Disabled";
			float w = Math.max(tr.width(name), tr.width(sub) * 0.8f) + 44, h = 26;
			float[] p = HudLayout.place(position.get(), w, h);
			float slide = (1f - ease) * 30 * (right ? 1 : -1);
			var m = ctx.pose();
			m.pushMatrix();
			m.translate(slide, 0);
			Hud.panel(ctx, p[0], p[1], w, h, a);
			float px = p[0] + 8, py = p[1] + 8;
			Render2D.roundRect(ctx, px, py, 18, 10, 5, on ? Colors.argb(0xFFFFFF, a) : Colors.argb(0x3A3A44, a));
			Render2D.circle(ctx, on ? px + 13 : px + 5, py + 5, 3.4f, on ? Colors.argb(0x15151B, a) : Colors.argb(0xA8A8B4, a));
			Render2D.text(ctx, tr, name, p[0] + 32, p[1] + 5, Hud.alpha(Hud.TEXT, a));
			Render2D.textScaled(ctx, tr, sub, p[0] + 32, p[1] + 15, 0.8f, Hud.alpha(on ? 0xFF7CFF9A : Hud.SUB, a));
			float life = Math.max(0f, 1f - age / 2.6f);
			Render2D.roundRect(ctx, p[0] + 4, p[1] + h - 2.5f, (w - 8) * life, 1.2f, 0.6f, Colors.argb(0xFFFFFF, 0.35f * a));
			m.popMatrix();
		}
	}
}
