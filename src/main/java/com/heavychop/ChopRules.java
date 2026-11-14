package com.heavychop;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public final class ChopRules {
    /** Anything that counts as an axe: the vanilla axe tag or any AxeItem (modded axes too). */
    public static boolean isAxe(ItemStack stack) {
        return stack.is(ItemTags.AXES) || stack.getItem() instanceof AxeItem;
    }

    /** Log + axe in main hand, survival-like mode. Used on both client and server. */
    public static boolean isChoppable(Player p, BlockState s) {
        return !p.isCreative() && !p.isSpectator()
                && s.is(BlockTags.LOGS)
                && isAxe(p.getMainHandItem());
    }

    private ChopRules() {}
}
