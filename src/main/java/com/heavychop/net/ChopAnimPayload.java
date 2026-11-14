package com.heavychop.net;

import com.heavychop.HeavyChop;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: tells the client which animation/state to show. */
public record ChopAnimPayload(int kind, int duration, int contact) implements CustomPacketPayload {
    public static final int SWING = 0;
    public static final int STUCK = 1;
    public static final int PULL = 2;
    public static final int RESET = 3;

    public static final Type<ChopAnimPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HeavyChop.MODID, "chop_anim"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChopAnimPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChopAnimPayload::kind,
            ByteBufCodecs.VAR_INT, ChopAnimPayload::duration,
            ByteBufCodecs.VAR_INT, ChopAnimPayload::contact,
            ChopAnimPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
