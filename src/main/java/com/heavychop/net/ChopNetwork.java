package com.heavychop.net;

import com.heavychop.client.ChopFx;
import com.heavychop.client.ClientChop;
import com.heavychop.server.ChopManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ChopNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(ChopClickPayload.TYPE, ChopClickPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) ChopManager.handleClick(sp, payload.pos());
                }));
        r.playToClient(ChopAnimPayload.TYPE, ChopAnimPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientChop.onAnim(payload)));
        r.playToClient(ChopFxPayload.TYPE, ChopFxPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ChopFx.play(payload)));
    }

    private ChopNetwork() {}
}
