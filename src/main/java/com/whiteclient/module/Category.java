package com.whiteclient.module;

public enum Category {
	VISUALS("Visuals", "Cosmetics on your player"),
	WORLD("World", "Sky, particles, 3D text"),
	HUD("HUD", "On-screen overlays"),
	MENU("Menu", "Menus & backgrounds");

	public final String title;
	public final String subtitle;

	Category(String title, String subtitle) {
		this.title = title;
		this.subtitle = subtitle;
	}
}
