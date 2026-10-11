package com.manuallimbs.mixin;

import com.manuallimbs.LimbSim;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void manuallimbs$mouseMovesArms(final LocalPlayer player, final double xo, final double yo) {
		if (!LimbSim.INSTANCE.onMouse(xo, yo)) {
			player.turn(xo, yo);
		}
	}
}
