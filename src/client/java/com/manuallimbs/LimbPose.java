package com.manuallimbs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Poses the player's own model from the limb simulation instead of the walk cycle. */
public final class LimbPose {
	private static final float DEG = (float) (Math.PI / 180.0);

	private LimbPose() {
	}

	public static void apply(final HumanoidModel<?> model, final HumanoidRenderState state) {
		Minecraft mc = Minecraft.getInstance();
		LimbSim sim = LimbSim.INSTANCE;
		if (mc.player == null || !(state instanceof AvatarRenderState avatar) || avatar.id != mc.player.getId() || !sim.isActive()) {
			return;
		}

		if (Boolean.TRUE.equals(state.getData(ManualLimbsClient.OWN_BODY)) && sim.showBodyInFirstPerson()) {
			// The camera sits inside the head.
			model.head.xScale = model.head.yScale = model.head.zScale = 0.0F;
		}

		if (sim.fallen) {
			return;
		}

		float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		leg(model.leftLeg, sim.leftLeg, partialTicks);
		leg(model.rightLeg, sim.rightLeg, partialTicks);
		arm(model.leftArm, sim.leftArm, partialTicks);
		arm(model.rightArm, sim.rightArm, partialTicks);
	}

	private static void leg(final ModelPart part, final LimbSim.Leg leg, final float partialTicks) {
		part.xRot = -leg.angle(partialTicks);
		if (leg.lifted) {
			part.y -= 1.5F;
		}
	}

	private static void arm(final ModelPart part, final LimbSim.Arm arm, final float partialTicks) {
		part.xRot = -arm.raise * DEG;
		part.yRot = -arm.side * arm.yaw * DEG;
		part.zRot = 0.0F;
		part.yScale = arm.length(partialTicks) / LimbSim.ARM_LEN;
	}
}
