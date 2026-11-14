package com.heavychop.net;

import com.heavychop.HeavyChop;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> nearby clients: play chopping effects (chips, dust, camera kick for the chopper). */
public record ChopFxPayload(BlockPos pos, int kind, int stateId, int dir, int actor) implements CustomPacketPayload {
    public static final int HIT = 0;
    public static final int MISS = 1;
    public static final int STUCK = 2;
    public static final int FINAL = 3;
    public static final int PULL = 4;

    public static final Type<ChopFxPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HeavyChop.MODID, "chop_fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChopFxPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ChopFxPayload::pos,
            ByteBufCodecs.VAR_INT, ChopFxPayload::kind,
            ByteBufCodecs.VAR_INT, ChopFxPayload::stateId,
            ByteBufCodecs.VAR_INT, ChopFxPayload::dir,
            ByteBufCodecs.VAR_INT, ChopFxPayload::actor,
            ChopFxPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
