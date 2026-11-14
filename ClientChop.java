package com.heavychop.client;

import com.heavychop.ChopRules;
import com.heavychop.HeavyChop;
import com.heavychop.net.ChopAnimPayload;
import com.heavychop.net.ChopClickPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = HeavyChop.MODID, value = Dist.CLIENT)
public final class ClientChop {
    private static long lastSendTick = -100;
    public static boolean stuck = false;

    /** Replaces the vanilla attack click with a single chop click when aiming at a log with an axe. */
    @SubscribeEvent
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        if (!ChopRules.isChoppable(p, state)) return;

        e.setCanceled(true);
        e.setSwingHand(false);
        long t = mc.level.getGameTime();
        if (t - lastSendTick < 2) return; // tiny anti-spam, the server decides everything else
        lastSendTick = t;
        if (com.heavychop.ChopConfig.DEBUG.get()) HeavyChop.LOGGER.info("[HeavyChop] client click sent {}", hit.getBlockPos());
        PacketDistributor.sendToServer(new ChopClickPayload(hit.getBlockPos()));
    }

    /**
     * While aiming at a log with an axe, release the virtual "attack held" state so vanilla does not keep
     * mining/swinging every tick. The click itself is still delivered once (see onAttack).
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.screen != null) return;
        if (!mc.options.keyAttack.isDown()) return;
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && ChopRules.isChoppable(p, mc.level.getBlockState(hit.getBlockPos()))) {
            mc.options.keyAttack.setDown(false);
        }
    }

    /** Called when the server says what animation/state to show. Uses Punchy's own axe animation (see PunchyBridge). */
    public static void onAnim(ChopAnimPayload p) {
        LocalPlayer pl = Minecraft.getInstance().player;
        if (pl == null) return;
        switch (p.kind()) {
            case ChopAnimPayload.SWING, ChopAnimPayload.PULL -> PunchyBridge.playSwing(pl);
            case ChopAnimPayload.STUCK -> {
                stuck = true;
                pl.displayClientMessage(Component.translatable("message.heavychop.stuck"), true);
            }
            default -> {}
        }
        if (p.kind() == ChopAnimPayload.PULL || p.kind() == ChopAnimPayload.RESET) stuck = false;
    }

    private ClientChop() {}
}
