package com.manuallimbs.mixin;

import com.manuallimbs.LimbSim;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
	@Inject(method = "getDesiredPose", at = @At("HEAD"), cancellable = true)
	private void manuallimbs$lieDownWhenFallen(final CallbackInfoReturnable<Pose> cir) {
		if ((Object)this instanceof LocalPlayer && LimbSim.INSTANCE.fallen && LimbSim.INSTANCE.isActive()) {
			cir.setReturnValue(Pose.SWIMMING);
		}
	}
}
