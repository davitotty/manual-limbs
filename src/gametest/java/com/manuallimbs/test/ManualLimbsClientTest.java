package com.manuallimbs.test;

import com.manuallimbs.LimbSim;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Drives the limbs in a real client and checks that the body, blocks and mobs react. */
public class ManualLimbsClientTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("MLTEST");
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(final ClientGameTestContext context) {
		LimbSim sim = LimbSim.INSTANCE;

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			server.runCommand("execute as @a at @s run tp @s ~ 200 ~ 0 0");
			server.runCommand("execute at @a run fill ~-6 ~-1 ~-6 ~6 ~-1 ~40 minecraft:smooth_stone");
			server.runCommand("gamemode survival @a");
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			world.getConnection().waitForChunksRender();
			context.waitTicks(20);
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));

			// --- vanilla walk key must do nothing
			Vec3 start = pos(context);
			context.getInput().holdKey(options -> options.keyUp);
			context.waitTicks(20);
			context.getInput().releaseKey(options -> options.keyUp);
			context.waitTicks(5);
			Vec3 afterW = pos(context);
			// W is also the right leg's swing key, so the leg lifts but the body must stay put.
			check("holding W alone does not walk", afterW.distanceTo(start) < 0.05, "moved " + afterW.distanceTo(start));
			sim.reset();
			context.waitTicks(5);

			// --- alternate the legs to walk
			start = pos(context);

			for (int step = 0; step < 6; step++) {
				boolean leftSwings = step % 2 == 0;
				sim.testLeftFwd = leftSwings;
				sim.testRightBack = leftSwings;
				sim.testRightFwd = !leftSwings;
				sim.testLeftBack = !leftSwings;
				context.waitTicks(8);
				if (step == 2) {
					context.takeScreenshot("walk_third_person");
				}
			}

			releaseLegs(sim);
			context.waitTicks(10);
			Vec3 walked = pos(context);
			LOGGER.info("walked dz={} dx={} in 48 ticks, fallen={}", walked.z - start.z, walked.x - start.x, sim.fallen);
			check("alternating legs walks forward", walked.z - start.z > 3.0, "dz=" + (walked.z - start.z));
			check("walking did not make the player fall", !sim.fallen, "");

			// --- stomping both legs is a jump
			sim.reset();
			context.waitTicks(5);
			double groundY = pos(context).y;
			sim.testLeftBack = true;
			sim.testRightBack = true;
			double peak = groundY;

			for (int i = 0; i < 8; i++) {
				context.waitTick();
				peak = Math.max(peak, pos(context).y);
			}

			releaseLegs(sim);
			check("both legs down jumps", peak - groundY > 0.5, "peak=" + (peak - groundY));
			context.waitTicks(20);

			// --- lifting both legs falls over
			sim.reset();
			sim.testLeftFwd = true;
			sim.testRightFwd = true;
			context.waitTicks(4);
			releaseLegs(sim);
			context.waitTicks(4);
			boolean lying = context.computeOnClient(mc -> mc.player.getPose() == Pose.SWIMMING);
			check("lifting both legs falls over", sim.fallen && lying, "fallen=" + sim.fallen + " lying=" + lying);
			context.takeScreenshot("fallen");
			context.waitTicks(70);
			check("gets back up", !sim.fallen, "");

			// --- arms: real mouse input moves the held arm instead of the camera
			sim.reset();
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
			context.waitTicks(10);
			float yawBefore = context.computeOnClient(mc -> mc.player.getYRot());
			context.getInput().holdMouse(3);
			context.waitTicks(2);
			context.getInput().moveCursor(0.0, -150.0);
			context.waitTicks(3);
			float yawAfter = context.computeOnClient(mc -> mc.player.getYRot());
			LOGGER.info("right arm after mouse: raise={} yaw={} held={}", sim.rightArm.raise, sim.rightArm.yaw, sim.rightArm.held);
			check("mouse raises the held right arm", sim.rightArm.raise > 20.0F, "raise=" + sim.rightArm.raise);
			check("camera stays still while an arm is held", Math.abs(yawAfter - yawBefore) < 0.01F, "");
			context.getInput().releaseMouse(3);
			context.waitTicks(2);

			// --- poses, third and first person
			sim.rightArm.raise = 90.0F;
			sim.rightArm.yaw = 0.0F;
			sim.leftArm.raise = 90.0F;
			sim.leftArm.yaw = 60.0F;
			sim.leftLeg.angle = 0.7F;
			sim.rightLeg.angle = -0.7F;
			context.waitTicks(3);
			context.takeScreenshot("pose_third_person");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(3);
			context.takeScreenshot("pose_first_person");
			context.getInput().lookAt(0.0F, 60.0F);
			context.waitTicks(3);
			context.takeScreenshot("pose_first_person_look_down");
			context.getInput().lookAt(0.0F, 0.0F);
			sim.reset();

			// --- mining: swing the right hand against a dirt block in front of the chest
			BlockPos feet = context.computeOnClient(mc -> mc.player.blockPosition());
			BlockPos dirt = feet.offset(0, 1, 1);
			setBlock(server, dirt, "minecraft:dirt");
			context.waitTicks(5);
			sim.rightArm.raise = 90.0F;
			context.waitTicks(3);
			check("right hand touches the block in front", sim.rightArm.contact != null, "");
			context.takeScreenshot("touching_dirt_first_person");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(2);
			context.takeScreenshot("touching_dirt_third_person");
			context.waitTicks(30);
			check("resting a hand on a block does not break it", !isAir(context, dirt), "");
			boolean broken = false;

			for (int i = 0; i < 60 && !broken; i++) {
				sim.rightArm.raise = i % 2 == 0 ? 105.0F : 80.0F;
				context.waitTicks(3);
				broken = isAir(context, dirt);
				if (i == 2) {
					context.takeScreenshot("mining");
				}
			}

			check("swinging the hand breaks the block", broken, "");
			sim.reset();
			context.waitTicks(5);

			// --- chest: touch it and grip to open
			BlockPos chest = feet.offset(0, 0, 1);
			setBlock(server, chest, "minecraft:chest");
			context.waitTicks(5);
			sim.rightArm.raise = 50.0F;
			context.waitTicks(4);
			check("right hand touches the chest", sim.rightArm.contact != null, "");
			sim.testGrip = true;
			context.waitTicks(3);
			sim.testGrip = false;
			context.waitTicks(10);
			boolean chestOpen = context.computeOnClient(mc -> mc.gui.screen() instanceof AbstractContainerScreen);
			check("gripping the chest opens it", chestOpen, "");
			context.takeScreenshot("chest_open");
			context.runOnClient(mc -> mc.player.closeContainer());
			context.waitTicks(10);

			// --- crafting table
			setBlock(server, chest, "minecraft:crafting_table");
			context.waitTicks(5);
			sim.testGrip = true;
			context.waitTicks(3);
			sim.testGrip = false;
			context.waitTicks(10);
			boolean tableOpen = context.computeOnClient(mc -> mc.gui.screen() instanceof AbstractContainerScreen);
			check("gripping the crafting table opens it", tableOpen, "");
			context.runOnClient(mc -> mc.player.closeContainer());
			context.waitTicks(10);
			setBlock(server, chest, "minecraft:air");

			// --- punching a mob
			sim.reset();
			server.runCommand("execute at @a run summon minecraft:pig ~ ~ ~1 {NoAI:1b,Tags:[\"mltest\"]}");
			context.waitTicks(10);
			sim.rightArm.raise = 60.0F;
			context.waitTicks(3);
			check("right hand touches the pig", sim.rightArm.contact != null, "contact=" + sim.rightArm.contact);

			for (int i = 0; i < 8; i++) {
				sim.rightArm.raise = i % 2 == 0 ? 80.0F : 55.0F;
				context.waitTicks(3);
			}

			context.takeScreenshot("punch_pig");
			context.waitTicks(10);
			float pigHealth = server.computeOnServer(s -> {
				for (var level : s.getAllLevels()) {
					for (var entity : level.getAllEntities()) {
						if (entity.entityTags().contains("mltest") && entity instanceof net.minecraft.world.entity.LivingEntity living) {
							return living.getHealth();
						}
					}
				}

				return -1.0F;
			});
			LOGGER.info("pig health after punches: {}", pigHealth);
			check("punching hurts the pig", pigHealth < 10.0F, "health=" + pigHealth);
			sim.reset();
		}

		if (!this.failures.isEmpty()) {
			throw new AssertionError("Manual Limbs checks failed: " + this.failures);
		}

		LOGGER.info("ALL CHECKS PASSED");
	}

	private void check(final String name, final boolean ok, final String detail) {
		LOGGER.info("{} {} {}", ok ? "PASS" : "FAIL", name, detail);
		if (!ok) {
			this.failures.add(name + " (" + detail + ")");
		}
	}

	private static void releaseLegs(final LimbSim sim) {
		sim.testLeftFwd = sim.testLeftBack = sim.testRightFwd = sim.testRightBack = false;
	}

	private static Vec3 pos(final ClientGameTestContext context) {
		return context.computeOnClient(mc -> mc.player.position());
	}

	private static boolean isAir(final ClientGameTestContext context, final BlockPos pos) {
		return context.computeOnClient(mc -> mc.level.getBlockState(pos).isAir());
	}

	private static void setBlock(final TestServerContext server, final BlockPos pos, final String block) {
		server.runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + block);
	}
}
