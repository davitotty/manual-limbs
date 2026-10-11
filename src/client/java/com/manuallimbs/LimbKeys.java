package com.manuallimbs;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class LimbKeys {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("manuallimbs", "limbs"));

	public static final KeyMapping LEFT_LEG_FWD = key("left_leg_forward", InputConstants.KEY_Q);
	public static final KeyMapping LEFT_LEG_BACK = key("left_leg_back", InputConstants.KEY_A);
	public static final KeyMapping RIGHT_LEG_FWD = key("right_leg_forward", InputConstants.KEY_W);
	public static final KeyMapping RIGHT_LEG_BACK = key("right_leg_back", InputConstants.KEY_S);
	public static final KeyMapping REVERSE = key("reverse", InputConstants.KEY_LCONTROL);
	public static final KeyMapping LEFT_ARM = mouse("left_arm", InputConstants.MOUSE_BUTTON_LEFT);
	public static final KeyMapping RIGHT_ARM = mouse("right_arm", InputConstants.MOUSE_BUTTON_RIGHT);
	public static final KeyMapping GRIP = key("grip", InputConstants.KEY_SPACE);
	public static final KeyMapping TOGGLE = key("toggle", InputConstants.KEY_M);
	public static final KeyMapping TOGGLE_BALANCE = key("toggle_balance", InputConstants.KEY_B);
	public static final KeyMapping TOGGLE_HUD = key("toggle_hud", InputConstants.KEY_H);

	private static final KeyMapping[] LIMBS = {LEFT_LEG_FWD, LEFT_LEG_BACK, RIGHT_LEG_FWD, RIGHT_LEG_BACK, REVERSE, LEFT_ARM, RIGHT_ARM, GRIP};
	private static final KeyMapping[] ALL = {
		LEFT_LEG_FWD, LEFT_LEG_BACK, RIGHT_LEG_FWD, RIGHT_LEG_BACK, REVERSE, LEFT_ARM, RIGHT_ARM, GRIP, TOGGLE, TOGGLE_BALANCE, TOGGLE_HUD
	};

	private LimbKeys() {
	}

	private static KeyMapping key(final String name, final int keysym) {
		return new KeyMapping("key.manuallimbs." + name, InputConstants.Type.KEYBOARD, keysym, CATEGORY);
	}

	private static KeyMapping mouse(final String name, final int button) {
		return new KeyMapping("key.manuallimbs." + name, InputConstants.Type.MOUSE, button, CATEGORY);
	}

	public static void register() {
		for (KeyMapping mapping : ALL) {
			KeyMappingHelper.registerKeyMapping(mapping);
		}
	}

	public static boolean sharesKeyWithLimb(final KeyMapping other) {
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(other);

		for (KeyMapping limb : LIMBS) {
			if (limb == other) {
				return false;
			}
		}

		for (KeyMapping limb : LIMBS) {
			if (!limb.isUnbound() && KeyMappingHelper.getBoundKeyOf(limb).equals(key)) {
				return true;
			}
		}

		return false;
	}

	public static String name(final KeyMapping mapping) {
		return mapping.getTranslatedKeyMessage().getString();
	}
}
