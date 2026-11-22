package com.whiteclient.gui;

import com.whiteclient.WhiteClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

/** Smooth Poppins font for the WHITECLIENT menus (falls back to the Minecraft font for missing glyphs). */
public final class UiFont {
	public static final Identifier UI = Identifier.of(WhiteClient.MOD_ID, "ui");
	public static final Identifier TITLE = Identifier.of(WhiteClient.MOD_ID, "title");
	private static final Style UI_STYLE = Style.EMPTY.withFont(new StyleSpriteSource.Font(UI));
	private static final Style TITLE_STYLE = Style.EMPTY.withFont(new StyleSpriteSource.Font(TITLE));

	private UiFont() {
	}

	public static Text ui(String s) {
		return Text.literal(s).setStyle(UI_STYLE);
	}

	public static Text title(String s) {
		return Text.literal(s).setStyle(TITLE_STYLE);
	}

	public static int width(TextRenderer tr, String s) {
		return tr.getWidth(ui(s));
	}

	public static void draw(DrawContext ctx, TextRenderer tr, String s, float x, float y, int color) {
		Matrix3x2fStack m = ctx.getMatrices();
		m.pushMatrix();
		m.translate(x, y);
		ctx.drawText(tr, ui(s), 0, 0, color, false);
		m.popMatrix();
	}

	public static void drawCentered(DrawContext ctx, TextRenderer tr, String s, float cx, float y, int color) {
		draw(ctx, tr, s, cx - width(tr, s) / 2f, y, color);
	}

	public static void drawScaled(DrawContext ctx, TextRenderer tr, String s, float x, float y, float scale, int color) {
		Matrix3x2fStack m = ctx.getMatrices();
		m.pushMatrix();
		m.translate(x, y);
		m.scale(scale, scale);
		ctx.drawText(tr, ui(s), 0, 0, color, false);
		m.popMatrix();
	}
}
