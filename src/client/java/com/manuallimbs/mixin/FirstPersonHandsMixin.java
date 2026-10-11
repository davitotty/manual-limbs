package com.manuallimbs.mixin;

import com.manuallimbs.LimbSim;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void manuallimbs$realArmsInstead(final CallbackInfo ci) {
		if (LimbSim.INSTANCE.showBodyInFirstPerson()) {
			ci.cancel();
		}
	}
}
