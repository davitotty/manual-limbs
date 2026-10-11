package com.manuallimbs;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/** Side-view diagram of the legs plus the control hints. */
public final class LimbHud {
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GREY = 0xFFAAAAAA;
	private static final int GREEN = 0xFF55FF55;
	private static final int YELLOW = 0xFFFFFF55;
	private static final int RED = 0xFFFF5555;

	private LimbHud() {
	}

	public static void extract(final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		LimbSim sim = LimbSim.INSTANCE;
		if (!sim.hud || mc.player == null || mc.gui.hud.isHidden() || !sim.isActive()) {
			return;
		}

		Font font = mc.font;
		int x = 6;
		int y = graphics.guiHeight() - 122;
		graphics.fill(x - 2, y - 2, x + 150, y + 116, 0x90000000);
		float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
		// Stick figure seen from the player's left: forward is to the right.
		int hipX = x + 30;
		int hipY = y + 22;
		line(graphics, hipX, hipY, hipX, hipY - 16, WHITE);
		graphics.fill(hipX - 3, hipY - 23, hipX + 4, hipY - 16, WHITE);
		leg(graphics, hipX, hipY, sim.leftLeg, partialTicks);
		leg(graphics, hipX, hipY, sim.rightLeg, partialTicks);
		graphics.fill(x, hipY + 28, x + 62, hipY + 29, GREY);
		graphics.text(font, "front >", x + 66, hipY + 20, GREY, false);
		int row = y + 54;
		if (sim.fallen) {
			graphics.text(font, "YOU FELL OVER", x, row, RED, true);
		} else {
			graphics.text(font, legLine("L leg", sim.leftLeg, LimbKeys.LEFT_LEG_FWD, LimbKeys.LEFT_LEG_BACK), x, row, legColor(sim.leftLeg), true);
			graphics.text(font, legLine("R leg", sim.rightLeg, LimbKeys.RIGHT_LEG_FWD, LimbKeys.RIGHT_LEG_BACK), x, row + 10, legColor(sim.rightLeg), true);
		}

		graphics.text(font, armLine("L arm", sim.leftArm, LimbKeys.LEFT_ARM), x, row + 22, sim.leftArm.contact != null ? GREEN : GREY, true);
		graphics.text(font, armLine("R arm", sim.rightArm, LimbKeys.RIGHT_ARM), x, row + 32, sim.rightArm.contact != null ? GREEN : GREY, true);
		graphics.text(font, LimbKeys.name(LimbKeys.GRIP) + ": grab / use", x, row + 42, GREY, true);
		if (!sim.hint.isEmpty()) {
			graphics.text(font, sim.hint, x, row + 52, YELLOW, true);
		}
	}

	private static String legLine(final String name, final LimbSim.Leg leg, final KeyMapping fwd, final KeyMapping back) {
		return name + " [" + LimbKeys.name(fwd) + "/" + LimbKeys.name(back) + "] " + (leg.lifted ? "in air" : "planted");
	}

	private static String armLine(final String name, final LimbSim.Arm arm, final KeyMapping key) {
		return name + " [" + LimbKeys.name(key) + "] " + (arm.contact != null ? "touching" : arm.held ? "moving" : "");
	}

	private static int legColor(final LimbSim.Leg leg) {
		return leg.lifted ? YELLOW : WHITE;
	}

	private static void leg(final GuiGraphicsExtractor graphics, final int hipX, final int hipY, final LimbSim.Leg leg, final float partialTicks) {
		float angle = leg.angle(partialTicks);
		float length = leg.lifted ? 21.0F : 26.0F;
		int footX = hipX + Math.round(Mth.sin(angle) * length);
		int footY = hipY + Math.round(Mth.cos(angle) * length);
		line(graphics, hipX, hipY, footX, footY, legColor(leg));
	}

	private static void line(final GuiGraphicsExtractor graphics, final int x0, final int y0, final int x1, final int y1, final int color) {
		int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));

		for (int i = 0; i <= steps; i++) {
			float t = steps == 0 ? 0.0F : (float)i / steps;
			int px = Math.round(Mth.lerp(t, x0, x1));
			int py = Math.round(Mth.lerp(t, y0, y1));
			graphics.fill(px, py, px + 2, py + 2, color);
		}
	}
}
