package com.manuallimbs.mixin;

import com.manuallimbs.LimbSim;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Shadow
	private boolean startAttack() {
		throw new AssertionError();
	}

	@Shadow
	private void startUseItem() {
		throw new AssertionError();
	}

	@Inject(method = "handleKeybinds", at = @At("HEAD"))
	private void manuallimbs$tickLimbs(final CallbackInfo ci) {
		LimbSim.Actions actions = LimbSim.INSTANCE.tick((Minecraft)(Object)this);
		if (actions.attack()) {
			this.startAttack();
		}

		if (actions.use()) {
			this.startUseItem();
		}
	}

	@Inject(method = "pick", at = @At("TAIL"))
	private void manuallimbs$handIsTheCrosshair(final float partialTicks, final CallbackInfo ci) {
		LimbSim.INSTANCE.applyHitResult((Minecraft)(Object)this);
	}
}
