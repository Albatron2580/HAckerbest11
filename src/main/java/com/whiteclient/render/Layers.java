package com.whiteclient.render;

import net.minecraft.client.renderer.rendertype.RenderType;

public final class Layers {
	private Layers() {
	}

	public static String topology(RenderType layer) {
		//#if MC >= 26.2
		//$ return layer.primitiveTopology().name();
		//#else
		return layer.mode().name();
		//#endif
	}
}
