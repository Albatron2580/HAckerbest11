package com.whiteclient.gui;

import com.whiteclient.WhiteClient;
import com.whiteclient.modules.MenuBackground;
import com.whiteclient.modules.ModsButton;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import net.minecraft.text.TranslatableTextContent;

import java.util.List;

/** Pause-menu "Mods" button and animated menu backgrounds. */
public final class MenuIntegration {
	private MenuIntegration() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register(MenuIntegration::afterInit);
	}

	private static void afterInit(MinecraftClient client, Screen screen, int width, int height) {
		WhiteClient wc = WhiteClient.get();
		if (wc == null) {
			return;
		}
		if (screen instanceof GameMenuScreen) {
			ModsButton mods = wc.modules().get(ModsButton.class);
			if (mods != null && mods.isEnabled()) {
				try {
					addModsButton(screen);
				} catch (Throwable t) {
					WhiteClient.LOGGER.error("[WHITECLIENT] could not add the Mods button", t);
				}
			}
		}
		if (screen instanceof GameMenuScreen || screen instanceof TitleScreen) {
			ScreenEvents.afterBackground(screen).register((s, context, mouseX, mouseY, delta) -> {
				MenuBackground bg = wc.modules().get(MenuBackground.class);
				if (bg == null || !bg.isEnabled()) {
					return;
				}
				if (s instanceof TitleScreen && bg.titleScreen.isOn()) {
					MenuBackgroundRenderer.draw(context, s.width, s.height, 1f);
				} else if (s instanceof GameMenuScreen && bg.pauseMenu.isOn() && hasButtons(s)) {
					MenuBackgroundRenderer.draw(context, s.width, s.height, bg.opacity.getFloat());
				}
			});
		}
	}

	private static boolean hasButtons(Screen screen) {
		return !Screens.getButtons(screen).isEmpty();
	}

	private static String keyOf(ClickableWidget widget) {
		Text message = widget.getMessage();
		if (message == null) {
			return "";
		}
		TextContent content = message.getContent();
		if (content instanceof TranslatableTextContent translatable) {
			return translatable.getKey();
		}
		return message.getString();
	}

	private static void addModsButton(Screen screen) {
		List<ClickableWidget> buttons = Screens.getButtons(screen);
		if (buttons.isEmpty()) {
			return; // F3+Esc pause without menu
		}
		ClickableWidget feedback = null;
		ClickableWidget bugs = null;
		ClickableWidget options = null;
		ClickableWidget back = null;
		boolean otherModsButton = false;
		for (ClickableWidget w : buttons) {
			String key = keyOf(w);
			String text = w.getMessage() == null ? "" : w.getMessage().getString();
			if (key.equals("menu.sendFeedback") || key.equals("menu.feedback")) {
				feedback = w;
			} else if (key.equals("menu.reportBugs") || key.equals("menu.server_links") || key.equals("menu.custom_options")) {
				bugs = w;
			} else if (key.equals("menu.options")) {
				options = w;
			} else if (key.equals("menu.returnToGame")) {
				back = w;
			} else if (key.equals("modmenu.title") || text.equalsIgnoreCase("Mods")) {
				otherModsButton = true;
			}
		}
		ModsButton settings = WhiteClient.get().modules().get(ModsButton.class);
		String label = settings.label.get().isBlank() ? "Mods" : settings.label.get();
		Screen parent = screen;

		if (feedback != null && !otherModsButton) {
			// vanilla layout: the feedback / bug report row becomes one wide button
			int x = feedback.getX();
			int y = feedback.getY();
			int h = feedback.getHeight();
			int right = feedback.getX() + feedback.getWidth();
			if (bugs != null && bugs.getY() == y) {
				right = Math.max(right, bugs.getX() + bugs.getWidth());
				buttons.remove(bugs);
			}
			buttons.remove(feedback);
			ButtonWidget ours = ButtonWidget.builder(Text.literal(label),
					b -> MinecraftClient.getInstance().setScreen(new ClickGuiScreen(parent)))
					.dimensions(x, y, right - x, h).build();
			ButtonStyle.markAccent(ours);
			buttons.add(ours);
			return;
		}

		// Another client/mod (NoRisk, ModMenu...) owns that row: open up a new full-width row above "Options..."
		ClickableWidget widthRef = back != null ? back : options;
		if (options != null && widthRef != null) {
			int insertY = options.getY();
			int h = options.getHeight();
			int step = h + 4;
			int half = step / 2;
			for (ClickableWidget w : buttons) {
				if (w.getY() >= insertY) {
					w.setY(w.getY() + (step - half));
				} else {
					w.setY(w.getY() - half);
				}
			}
			int x = widthRef.getX();
			int width = widthRef.getWidth();
			ButtonWidget ours = ButtonWidget.builder(Text.literal(otherModsButton ? "WHITECLIENT" : label),
					b -> MinecraftClient.getInstance().setScreen(new ClickGuiScreen(parent)))
					.dimensions(x, insertY - half, width, h).build();
			ButtonStyle.markAccent(ours);
			buttons.add(ours);
			return;
		}

		// Unknown layout - compact button in the corner.
		String text = otherModsButton ? "WHITECLIENT" : label;
		int w = MinecraftClient.getInstance().textRenderer.getWidth(text) + 16;
		ButtonWidget ours = ButtonWidget.builder(Text.literal(text),
				b -> MinecraftClient.getInstance().setScreen(new ClickGuiScreen(parent)))
				.dimensions(6, 6, w, 20).build();
		ButtonStyle.markAccent(ours);
		buttons.add(ours);
	}
}
