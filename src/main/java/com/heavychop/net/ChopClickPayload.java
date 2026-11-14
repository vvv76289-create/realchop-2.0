package com.heavychop.net;

import com.heavychop.HeavyChop;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: one LMB click on a log. */
public record ChopClickPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<ChopClickPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HeavyChop.MODID, "chop_click"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChopClickPayload> CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, ChopClickPayload::pos, ChopClickPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
