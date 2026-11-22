package com.whiteclient.module;

public enum Category {
	COSMETICS("Cosmetics", "Halo, wings, pets & trails"),
	PVP("PvP", "Effects around your target"),
	WORLD("World", "Sky, weather & particles"),
	MUSIC("Music", "Spotify, lyrics & 3D text"),
	HUD("HUD", "On-screen overlays"),
	MENU("Menu", "Menus & backgrounds"),
	CLIENT("Client", "Theme & wardrobe");

	public final String title;
	public final String subtitle;

	Category(String title, String subtitle) {
		this.title = title;
		this.subtitle = subtitle;
	}

	/** Categories shown as cards / in the sidebar. */
	public boolean listed() {
		return this != CLIENT;
	}
}
