package com.heavychop.client;

import java.lang.reflect.Method;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.fml.ModList;

/**
 * Plays Punchy's own first-person attack animation (its arms, its item placement) via reflection,
 * so there is no compile-time dependency on Punchy. Falls back to a vanilla swing if Punchy is absent
 * or its internals change.
 */
public final class PunchyBridge {
    private static boolean checked = false;
    private static Method recordAttack = null;

    public static void playSwing(LocalPlayer player) {
        if (!checked) {
            checked = true;
            try {
                if (ModList.get().isLoaded("punchy")) {
                    Class<?> c = Class.forName("punchy.client.state.AttackActionTracker");
                    recordAttack = c.getMethod("recordAttack", InteractionHand.class);
                }
            } catch (Throwable t) {
                recordAttack = null;
            }
        }
        if (recordAttack != null) {
            try {
                recordAttack.invoke(null, InteractionHand.MAIN_HAND);
                return;
            } catch (Throwable t) {
                recordAttack = null; // do not retry every swing
            }
        }
        player.swing(InteractionHand.MAIN_HAND);
    }

    private PunchyBridge() {}
}
