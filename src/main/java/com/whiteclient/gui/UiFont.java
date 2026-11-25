package com.whiteclient.gui;

import com.whiteclient.WhiteClient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

public final class UiFont {
	public static final Identifier UI = Identifier.fromNamespaceAndPath(WhiteClient.MOD_ID, "ui");
	public static final Identifier TITLE = Identifier.fromNamespaceAndPath(WhiteClient.MOD_ID, "title");
	private static final Style UI_STYLE = Style.EMPTY.withFont(new FontDescription.Resource(UI));
	private static final Style TITLE_STYLE = Style.EMPTY.withFont(new FontDescription.Resource(TITLE));

	private UiFont() {
	}

	public static Component ui(String s) {
		return Component.literal(s).setStyle(UI_STYLE);
	}

	public static Component title(String s) {
		return Component.literal(s).setStyle(TITLE_STYLE);
	}

	public static int width(Font tr, String s) {
		return tr.width(ui(s));
	}

	public static void draw(GuiGraphics ctx, Font tr, String s, float x, float y, int color) {
		Matrix3x2fStack m = ctx.pose();
		m.pushMatrix();
		m.translate(x, y);
		ctx.drawString(tr, ui(s), 0, 0, color, false);
		m.popMatrix();
	}

	public static void drawCentered(GuiGraphics ctx, Font tr, String s, float cx, float y, int color) {
		draw(ctx, tr, s, cx - width(tr, s) / 2f, y, color);
	}

	public static void drawScaled(GuiGraphics ctx, Font tr, String s, float x, float y, float scale, int color) {
		Matrix3x2fStack m = ctx.pose();
		m.pushMatrix();
		m.translate(x, y);
		m.scale(scale, scale);
		ctx.drawString(tr, ui(s), 0, 0, color, false);
		m.popMatrix();
	}
}
