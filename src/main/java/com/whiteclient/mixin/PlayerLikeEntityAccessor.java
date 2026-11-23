package com.whiteclient.mixin;

import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.entity.data.TrackedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to the synced skin-layer byte (bit 0x80 is unused by Minecraft; WHITECLIENT uses it as its "I use WhiteClient" flag). */
@Mixin(PlayerLikeEntity.class)
public interface PlayerLikeEntityAccessor {
	@Accessor("PLAYER_MODE_CUSTOMIZATION_ID")
	static TrackedData<Byte> whiteclient$modelParts() {
		throw new AssertionError();
	}
}
