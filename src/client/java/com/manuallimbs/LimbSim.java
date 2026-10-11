package com.manuallimbs;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The whole manual-limb simulation: two legs that have to be swung and planted to move the body,
 * and two arms that are aimed with the mouse and have to physically touch things to hit or use them.
 */
public final class LimbSim {
	public static final LimbSim INSTANCE = new LimbSim();

	/** Hip-to-foot and shoulder-to-hand length of the player model, in blocks. */
	public static final float LEG_LEN = 0.7F;
	public static final float ARM_LEN = 0.7F;
	/** How far an arm stretches to touch something. Longer than the model so the ground is reachable. */
	public static final float ARM_REACH = 1.6F;
	public static final float LEG_MAX = 1.0F;

	private static final float SWING_RATE = 0.3F;
	private static final float PUSH_RATE = 0.24F;
	/** Ground covered per unit of leg sweep. Above 1 so that a good rhythm is about as fast as vanilla walking. */
	private static final float STRIDE_GAIN = 1.6F;
	private static final float SIN_MAX = Mth.sin(LEG_MAX);
	private static final float ARM_SENSITIVITY = 2.0F;
	private static final float PUNCH_MIN_SPEED = 0.2F;
	private static final float PUNCH_FULL_SPEED = 0.7F;
	private static final int PUNCH_COOLDOWN = 4;
	private static final int ONE_LEG_LIMIT = 35;
	private static final int FALL_TICKS = 50;

	public static final class Leg {
		public float angle;
		public float prevAngle;
		public boolean lifted;
		public int liftedTicks;
		boolean pushWasDown;
		int pushPressedAt = -100;

		public float angle(final float partialTicks) {
			return Mth.lerp(partialTicks, this.prevAngle, this.angle);
		}
	}

	public static final class Arm {
		/** +1 for the right arm, -1 for the left. */
		public final int side;
		/** Degrees away from straight ahead, positive is outward. */
		public float yaw;
		/** Degrees up from hanging straight down: 90 points forward, 180 straight up. */
		public float raise;
		public boolean held;
		public @Nullable HitResult contact;
		public float length = ARM_LEN;
		public float prevLength = ARM_LEN;
		public float speed;
		float tickYaw;
		float tickRaise;
		int cooldown;
		int lastActive;

		Arm(final int side) {
			this.side = side;
		}

		public float length(final float partialTicks) {
			return Mth.lerp(partialTicks, this.prevLength, this.length);
		}
	}

	/** What the vanilla attack/use code should do this tick. */
	public record Actions(boolean attack, boolean use) {
		static final Actions NONE = new Actions(false, false);
	}

	public final Leg leftLeg = new Leg();
	public final Leg rightLeg = new Leg();
	public final Arm leftArm = new Arm(-1);
	public final Arm rightArm = new Arm(1);

	public boolean enabled = true;
	public boolean balance = true;
	public boolean hud = true;

	public boolean fallen;
	public int fallTicks;
	/** Why the last push did nothing, shown on the HUD. */
	public String hint = "";

	/** Lets the automated client test press the limb keys without a real keyboard. */
	public boolean testLeftFwd, testLeftBack, testRightFwd, testRightBack, testGrip, testReverse;

	private Vec3 pushVelocity = Vec3.ZERO;
	private int tick;
	private int lastSimTick = -1;
	private boolean jumpQueued;
	/** Direction of the last stomp jump while still in the air from it, 0 otherwise. */
	private int jumpDir;
	private int mineBudget;
	private boolean gripWasDown;
	private @Nullable Arm focus;

	private LimbSim() {
	}

	public boolean isActive() {
		LocalPlayer player = Minecraft.getInstance().player;
		return this.enabled
			&& player != null
			&& player.isAlive()
			&& !player.isSpectator()
			&& !player.getAbilities().flying
			&& !player.isPassenger()
			&& !player.isFallFlying()
			&& !player.isSleeping();
	}

	/** True when the player's own body should be drawn even though the camera is first person. */
	public boolean showBodyInFirstPerson() {
		Minecraft mc = Minecraft.getInstance();
		return this.isActive() && mc.options.getCameraType().isFirstPerson() && mc.getCameraEntity() == mc.player;
	}

	public boolean consumeJump() {
		boolean jump = this.jumpQueued;
		this.jumpQueued = false;
		return jump;
	}

	public void reset() {
		for (Leg leg : new Leg[] {this.leftLeg, this.rightLeg}) {
			leg.angle = leg.prevAngle = 0.0F;
			leg.lifted = false;
			leg.liftedTicks = 0;
		}

		for (Arm arm : new Arm[] {this.leftArm, this.rightArm}) {
			arm.yaw = arm.tickYaw = 0.0F;
			arm.raise = arm.tickRaise = 0.0F;
			arm.contact = null;
			arm.length = arm.prevLength = ARM_LEN;
		}

		this.fallen = false;
		this.fallTicks = 0;
		this.pushVelocity = Vec3.ZERO;
		this.mineBudget = 0;
		this.focus = null;
		this.hint = "";
	}

	/**
	 * Mouse movement, already scaled like a camera turn. Returns true when an arm took it, so the
	 * camera must stay still.
	 */
	public boolean onMouse(final double dx, final double dy) {
		if (!this.isActive() || this.fallen) {
			return false;
		}

		boolean consumed = false;

		for (Arm arm : new Arm[] {this.leftArm, this.rightArm}) {
			if (arm.held) {
				arm.yaw = Mth.clamp(arm.yaw + arm.side * (float)dx * 0.15F * ARM_SENSITIVITY, -70.0F, 150.0F);
				arm.raise = Mth.clamp(arm.raise - (float)dy * 0.15F * ARM_SENSITIVITY, 0.0F, 180.0F);
				consumed = true;
			}
		}

		return consumed;
	}

	/** Runs once per client tick while no screen is open, just before vanilla handles its keybinds. */
	public Actions tick(final Minecraft mc) {
		LocalPlayer player = mc.player;
		this.tick++;
		if (player == null || !this.isActive()) {
			this.pushVelocity = Vec3.ZERO;
			this.mineBudget = 0;
			if (this.fallen) {
				this.fallen = false;
			}

			return Actions.NONE;
		}

		if (this.lastSimTick != this.tick - 1) {
			this.pushVelocity = Vec3.ZERO;
		}

		this.lastSimTick = this.tick;
		this.silenceVanillaKeys(mc);
		this.tickLegs(player);
		return this.tickArms(mc, player);
	}

	/** Vanilla bindings that share a key with a limb must not also fire. */
	private void silenceVanillaKeys(final Minecraft mc) {
		for (KeyMapping mapping : mc.options.keyMappings) {
			if (mapping != mc.options.keyAttack && mapping != mc.options.keyUse && LimbKeys.sharesKeyWithLimb(mapping)) {
				while (mapping.consumeClick()) {
				}
			}
		}

		while (mc.options.keyAttack.consumeClick()) {
		}

		while (mc.options.keyUse.consumeClick()) {
		}
	}

	// ---------------------------------------------------------------- legs

	private void tickLegs(final LocalPlayer player) {
		Leg[] legs = {this.leftLeg, this.rightLeg};
		boolean reverse = LimbKeys.REVERSE.isDown() || this.testReverse;
		boolean[] fwd = {LimbKeys.LEFT_LEG_FWD.isDown() || this.testLeftFwd, LimbKeys.RIGHT_LEG_FWD.isDown() || this.testRightFwd};
		boolean[] back = {LimbKeys.LEFT_LEG_BACK.isDown() || this.testLeftBack, LimbKeys.RIGHT_LEG_BACK.isDown() || this.testRightBack};
		for (Leg leg : legs) {
			leg.prevAngle = leg.angle;
		}

		if (player.horizontalCollision) {
			this.pushVelocity = Vec3.ZERO;
		}

		if (this.fallen) {
			this.tickFallen(player, fwd, back);
			return;
		}

		this.hint = "";
		boolean grounded = player.onGround() || player.onClimbable();
		float yaw = player.getYRot() * (float) (Math.PI / 180.0);
		Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		if (!grounded) {
			this.tickLegsOffGround(player, legs, fwd, back, forward);
			return;
		}

		this.jumpDir = 0;

		// A leg swings through the air while its swing key is held and plants the moment it is released.
		int dir = reverse ? -1 : 1;
		boolean[] pushing = new boolean[2];

		for (int i = 0; i < 2; i++) {
			Leg leg = legs[i];
			boolean swingKey = reverse ? back[i] : fwd[i];
			boolean pushKey = reverse ? fwd[i] : back[i];
			if (swingKey && !pushKey) {
				leg.lifted = true;
				leg.liftedTicks++;
				leg.angle = Mth.clamp(leg.angle + dir * SWING_RATE, -LEG_MAX, LEG_MAX);
			} else {
				leg.lifted = false;
				leg.liftedTicks = 0;
				pushing[i] = pushKey && !swingKey;
			}

			if (pushKey && !leg.pushWasDown) {
				leg.pushPressedAt = this.tick;
			}

			leg.pushWasDown = pushKey;
		}

		if (this.leftLeg.lifted && this.rightLeg.lifted) {
			if (this.balance) {
				this.fall(player, "Both feet off the ground!");
				return;
			}

			this.hint = "Both feet are in the air";
		} else if (this.balance && (this.leftLeg.liftedTicks > ONE_LEG_LIMIT || this.rightLeg.liftedTicks > ONE_LEG_LIMIT)) {
			this.fall(player, "Lost your balance on one leg!");
			return;
		}

		// Planted feet stay where they are, so pushing one back drags the hips forward over all of them.
		float speed = Mth.clamp(player.getSpeed() / 0.1F, 0.4F, 2.5F) * (player.isShiftKeyDown() ? 0.5F : 1.0F);
		float wanted = 0.0F;
		float available = Float.MAX_VALUE;
		boolean anyPush = false;

		for (int i = 0; i < 2; i++) {
			Leg leg = legs[i];
			if (!leg.lifted) {
				float s = Mth.sin(leg.angle) * dir;
				available = Math.min(available, s + SIN_MAX);
				if (pushing[i]) {
					anyPush = true;
					float target = Math.max(leg.angle * dir - PUSH_RATE * speed, -LEG_MAX);
					wanted = Math.max(wanted, s - Mth.sin(target));
				}
			}
		}

		float travel = anyPush ? Math.max(0.0F, Math.min(wanted, available)) : 0.0F;
		if (anyPush && travel < 0.002F) {
			this.hint = "Leg fully stretched - swing the other one";
		}

		if (travel > 0.0F) {
			for (Leg leg : legs) {
				if (!leg.lifted) {
					float s = Mth.clamp(Mth.sin(leg.angle) * dir - travel, -SIN_MAX, SIN_MAX);
					leg.angle = (float)Math.asin(s) * dir;
				}
			}
		}

		boolean bothPlanted = !this.leftLeg.lifted && !this.rightLeg.lifted;
		if (this.balance && bothPlanted) {
			float lean = (Mth.sin(this.leftLeg.angle) + Mth.sin(this.rightLeg.angle)) * 0.5F;
			if (Math.abs(lean) > 0.78F) {
				this.fall(player, lean > 0.0F ? "Both feet too far ahead - fell on your back!" : "Both feet too far behind - faceplant!");
				return;
			}
		}

		Vec3 step = forward.scale(travel * LEG_LEN * STRIDE_GAIN * dir);
		if (bothPlanted && pushing[0] && pushing[1] && Math.abs(this.leftLeg.pushPressedAt - this.rightLeg.pushPressedAt) <= 2
			&& this.tick - Math.max(this.leftLeg.pushPressedAt, this.rightLeg.pushPressedAt) == 0 && player.onGround()) {
			// Stomping both legs down at once is a jump.
			this.jumpQueued = true;
			this.jumpDir = dir;
			step = step.add(forward.scale(0.18 * dir));
		}

		player.addDeltaMovement(step.subtract(this.pushVelocity));
		float friction = player.level().getBlockState(player.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction() * 0.91F;
		this.pushVelocity = this.jumpQueued ? Vec3.ZERO : step.scale(friction);
	}

	private void tickLegsOffGround(final LocalPlayer player, final Leg[] legs, final boolean[] fwd, final boolean[] back, final Vec3 forward) {
		this.pushVelocity = Vec3.ZERO;
		boolean[] stomp = this.jumpDir < 0 ? fwd : back;
		if (this.jumpDir != 0 && stomp[0] && stomp[1]) {
			// Keeping the stomp held leans into the jump, so a jump started against a ledge still gets onto it.
			player.addDeltaMovement(forward.scale(0.045 * this.jumpDir));
		}

		float kicked = 0.0F;

		for (int i = 0; i < 2; i++) {
			Leg leg = legs[i];
			leg.lifted = true;
			leg.liftedTicks = 0;
			float before = leg.angle;
			if (fwd[i] != back[i]) {
				leg.angle = Mth.clamp(leg.angle + (fwd[i] ? SWING_RATE : -SWING_RATE), -LEG_MAX, LEG_MAX);
			}

			kicked += Math.abs(leg.angle - before);
		}

		if (player.isInWater() && kicked > 0.0F) {
			// Kicking in water swims toward where you look.
			Vec3 look = player.getLookAngle().add(0.0, 0.25, 0.0).normalize();
			player.addDeltaMovement(look.scale(kicked * 0.13));
		}
	}

	private void tickFallen(final LocalPlayer player, final boolean[] fwd, final boolean[] back) {
		this.pushVelocity = Vec3.ZERO;
		Leg[] legs = {this.leftLeg, this.rightLeg};
		this.fallTicks--;

		for (int i = 0; i < 2; i++) {
			// Flailing gets you up faster.
			boolean down = fwd[i] || back[i];
			if (down && !legs[i].pushWasDown) {
				this.fallTicks -= 4;
			}

			legs[i].pushWasDown = down;
			legs[i].angle *= 0.8F;
			legs[i].lifted = false;
			legs[i].liftedTicks = 0;
		}

		this.hint = "Mash the leg keys to get up";
		if (this.fallTicks <= 0) {
			this.fallen = false;
			this.hint = "";
			for (Leg leg : legs) {
				leg.angle = 0.0F;
			}
		}
	}

	private void fall(final LocalPlayer player, final String reason) {
		this.fallen = true;
		this.fallTicks = FALL_TICKS;
		this.mineBudget = 0;
		this.pushVelocity = Vec3.ZERO;
		for (Arm arm : new Arm[] {this.leftArm, this.rightArm}) {
			arm.contact = null;
		}

		Minecraft.getInstance().gui.hud.setOverlayMessage(Component.literal(reason), false);
	}

	// ---------------------------------------------------------------- arms

	private Actions tickArms(final Minecraft mc, final LocalPlayer player) {
		Arm[] arms = {this.rightArm, this.leftArm};
		this.leftArm.held = LimbKeys.LEFT_ARM.isDown();
		this.rightArm.held = LimbKeys.RIGHT_ARM.isDown();
		boolean grip = LimbKeys.GRIP.isDown() || this.testGrip;
		boolean attack = false;
		if (this.fallen) {
			this.mineBudget = 0;
			this.gripWasDown = grip;
			mc.options.keyAttack.setDown(false);
			mc.options.keyUse.setDown(false);
			this.applyHitResult(mc);
			return Actions.NONE;
		}

		for (Arm arm : arms) {
			Vec3 before = localDirection(arm.side, arm.tickYaw, arm.tickRaise);
			Vec3 now = localDirection(arm.side, arm.yaw, arm.raise);
			arm.speed = (float)Math.acos(Mth.clamp(before.dot(now), -1.0, 1.0));
			arm.tickYaw = arm.yaw;
			arm.tickRaise = arm.raise;
			if (arm.speed > 0.01F) {
				arm.lastActive = this.tick;
			}

			this.updateContact(player, arm);
			if (arm.cooldown > 0) {
				arm.cooldown--;
			}
		}

		// The arm that was moved last is the one whose touch counts for hitting and using.
		Arm best = null;

		for (Arm arm : arms) {
			if (arm.contact != null && (best == null || arm.lastActive > best.lastActive)) {
				best = arm;
			}
		}

		this.focus = best;

		for (Arm arm : arms) {
			if (arm.contact != null && arm.speed >= PUNCH_MIN_SPEED && arm.cooldown == 0 && !player.isUsingItem()) {
				// A hand moving fast enough when it meets something is a hit.
				float power = Mth.clamp((arm.speed - PUNCH_MIN_SPEED) / (PUNCH_FULL_SPEED - PUNCH_MIN_SPEED), 0.0F, 1.0F);
				arm.cooldown = PUNCH_COOLDOWN;
				this.focus = arm;
				attack = true;
				if (arm.contact.getType() == HitResult.Type.BLOCK) {
					this.mineBudget = Math.min(45, this.mineBudget + 10 + Math.round(16.0F * power));
				}
			}
		}

		if (this.focus == null || this.focus.contact == null || this.focus.contact.getType() != HitResult.Type.BLOCK) {
			this.mineBudget = 0;
		}

		// Each hit keeps the vanilla block-breaking going for a short while, so mining means repeated swings.
		mc.options.keyAttack.setDown(this.mineBudget > 0);
		if (this.mineBudget > 0) {
			this.mineBudget--;
		}

		boolean use = grip && !this.gripWasDown;
		this.gripWasDown = grip;
		mc.options.keyUse.setDown(grip);
		this.applyHitResult(mc);
		return new Actions(attack, use);
	}

	/** Replaces the crosshair target with whatever the active hand is touching. */
	public void applyHitResult(final Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || !this.isActive()) {
			return;
		}

		HitResult result = this.focus != null ? this.focus.contact : null;
		if (result == null) {
			Vec3 eye = player.getEyePosition();
			result = BlockHitResult.miss(eye, Direction.UP, BlockPos.containing(eye));
		}

		mc.hitResult = result;
		mc.crosshairPickEntity = result instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
	}

	private void updateContact(final LocalPlayer player, final Arm arm) {
		Vec3 shoulder = shoulder(player, arm.side, 1.0F);
		Vec3 direction = worldDirection(player.getYRot(), localDirection(arm.side, arm.yaw, arm.raise));
		Vec3 end = shoulder.add(direction.scale(ARM_REACH));
		HitResult result = null;
		double distance = ARM_REACH;
		// A hanging arm is at rest and touches nothing, otherwise standing still would grab the floor.
		if (arm.raise > 8.0F) {
			BlockHitResult block = player.level().clip(new ClipContext(shoulder, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
			if (block.getType() == HitResult.Type.BLOCK) {
				result = block;
				distance = block.getLocation().distanceTo(shoulder);
				end = block.getLocation();
			}

			AABB area = new AABB(shoulder, end).inflate(1.0);
			EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, shoulder, end, area, LimbSim::canTouch, distance * distance);
			if (entity != null) {
				result = entity;
				distance = entity.getLocation().distanceTo(shoulder);
			}
		}

		arm.contact = result;
		arm.prevLength = arm.length;
		float target = result != null ? Mth.clamp((float)distance, 0.4F, ARM_REACH) : ARM_LEN;
		arm.length += (target - arm.length) * 0.6F;
	}

	private static boolean canTouch(final Entity entity) {
		return !entity.isSpectator() && entity.isPickable();
	}

	/** Arm direction in the body's own frame: x to the player's right, y up, z forward. */
	public static Vec3 localDirection(final int side, final float yawDegrees, final float raiseDegrees) {
		float yaw = yawDegrees * (float) (Math.PI / 180.0);
		float raise = raiseDegrees * (float) (Math.PI / 180.0);
		float out = Mth.sin(raise);
		return new Vec3(side * out * Mth.sin(yaw), -Mth.cos(raise), out * Mth.cos(yaw));
	}

	public static Vec3 worldDirection(final float bodyYawDegrees, final Vec3 local) {
		float yaw = bodyYawDegrees * (float) (Math.PI / 180.0);
		float sin = Mth.sin(yaw);
		float cos = Mth.cos(yaw);
		return new Vec3(-cos * local.x - sin * local.z, local.y, -sin * local.x + cos * local.z);
	}

	public static Vec3 shoulder(final LocalPlayer player, final int side, final float partialTicks) {
		float height = player.isCrouching() ? 1.1F : 1.29F;
		Vec3 right = worldDirection(player.getViewYRot(partialTicks), new Vec3(1.0, 0.0, 0.0));
		return player.getPosition(partialTicks).add(0.0, height, 0.0).add(right.scale(side * 0.3));
	}
}
