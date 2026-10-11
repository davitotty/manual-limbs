package com.manuallimbs;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class ManualLimbsClient implements ClientModInitializer {
	/** Set on the render state of the player's own body when it is drawn in the world (not in a menu). */
	public static final RenderStateDataKey<Boolean> OWN_BODY = RenderStateDataKey.create(() -> "manuallimbs:own_body");

	@Override
	public void onInitializeClient() {
		LimbKeys.register();
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("manuallimbs", "limbs"), LimbHud::extract);
		ClientTickEvents.END_CLIENT_TICK.register(ManualLimbsClient::endTick);
	}

	private static void endTick(final Minecraft mc) {
		LimbSim sim = LimbSim.INSTANCE;
		LocalPlayer player = mc.player;
		if (player == null) {
			sim.reset();
			return;
		}

		while (LimbKeys.TOGGLE.consumeClick()) {
			sim.enabled = !sim.enabled;
			sim.reset();
			say(mc, "Manual limbs " + (sim.enabled ? "ON" : "OFF - normal controls"));
		}

		while (LimbKeys.TOGGLE_BALANCE.consumeClick()) {
			sim.balance = !sim.balance;
			say(mc, "Falling over " + (sim.balance ? "ON" : "OFF"));
		}

		while (LimbKeys.TOGGLE_HUD.consumeClick()) {
			sim.hud = !sim.hud;
		}

		if (sim.isActive()) {
			// The legs push along the look direction, so the body has to face it too.
			player.yBodyRot = player.getYRot();
			player.yBodyRotO = player.yRotO;
		}
	}

	private static void say(final Minecraft mc, final String message) {
		mc.gui.hud.setOverlayMessage(Component.literal(message), false);
	}
}
