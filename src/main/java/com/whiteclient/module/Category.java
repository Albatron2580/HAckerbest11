package com.whiteclient.module;

public enum Category {
	COSMETICS("Cosmetics", "Halo, wings, capes, pets & trails"),
	PVP("PvP", "Target ESP, hit & kill effects"),
	WORLD("World", "Sky, weather, zoom & particles"),
	MUSIC("Music", "Spotify, lyrics & 3D text"),
	HUD("HUD", "Keystrokes, crosshair & info"),
	MENU("Menu", "Menus & backgrounds"),
	CLIENT("Client", "Theme & wardrobe");

	public final String title;
	public final String subtitle;

	Category(String title, String subtitle) {
		this.title = title;
		this.subtitle = subtitle;
	}

	public boolean listed() {
		return this != CLIENT;
	}
}
