package com.whiteclient.module;

import com.whiteclient.render.WorldContext;
import com.whiteclient.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
	private final String name;
	private final String description;
	private final Category category;
	private final List<Setting<?>> settings = new ArrayList<>();
	private boolean enabled;

	protected Module(String name, String description, Category category, boolean enabledByDefault) {
		this.name = name;
		this.description = description;
		this.category = category;
		this.enabled = enabledByDefault;
	}

	protected <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public Category getCategory() {
		return category;
	}

	public List<Setting<?>> getSettings() {
		return Collections.unmodifiableList(settings);
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}
		this.enabled = enabled;
		if (enabled) {
			onEnable();
		} else {
			onDisable();
		}
	}

	/** Called after a module is toggled from the menus (used for notifications). */
	public static java.util.function.Consumer<Module> toggleListener;

	public void toggle() {
		setEnabled(!enabled);
		if (toggleListener != null) {
			try {
				toggleListener.accept(this);
			} catch (Throwable ignored) {
			}
		}
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	/** Called every client tick (20/s) while enabled. */
	public void onTick(MinecraftClient client) {
	}

	/** Called every frame while enabled, after the world's main pass. Coordinates are camera-relative. */
	public void renderWorld(WorldContext ctx) {
	}

	/** Called every frame while enabled to draw on the HUD. */
	public void renderHud(DrawContext context, float tickDelta) {
	}
}
