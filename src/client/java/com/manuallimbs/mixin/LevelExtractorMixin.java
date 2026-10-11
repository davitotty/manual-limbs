package com.manuallimbs.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.manuallimbs.LimbSim;
import com.manuallimbs.ManualLimbsClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
	@Redirect(method = "extractVisibleEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z"))
	private boolean manuallimbs$drawOwnBody(final Camera camera) {
		return camera.isDetached() || LimbSim.INSTANCE.showBodyInFirstPerson();
	}

	@ModifyExpressionValue(
		method = "extractVisibleEntities",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"
		)
	)
	private EntityRenderState manuallimbs$markOwnBody(final EntityRenderState state) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && state instanceof AvatarRenderState avatar && avatar.id == mc.player.getId()) {
			state.setData(ManualLimbsClient.OWN_BODY, Boolean.TRUE);
		}

		return state;
	}
}
