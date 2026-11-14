package com.heavychop.client;

import com.heavychop.ChopConfig;
import com.heavychop.HeavyChop;
import com.heavychop.net.ChopFxPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Client-only visuals. Everything is spawned once per hit, nothing runs every tick. */
@EventBusSubscriber(modid = HeavyChop.MODID, value = Dist.CLIENT)
public final class ChopFx {
    private static final long SHAKE_MS = 240;
    private static long shakeStart = 0;
    private static float shakeAmp = 0F;
    private static float shakeDir = 1F;

    public static void play(ChopFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        BlockPos pos = p.pos();
        BlockState state = Block.stateById(p.stateId());
        if (state.isAir()) state = level.getBlockState(pos);
        if (state.isAir()) return;

        double amt = ChopConfig.FX_PARTICLES.get();
        Direction out = Direction.from3DDataValue(p.dir());
        boolean mine = mc.player != null && mc.player.getId() == p.actor();

        switch (p.kind()) {
            case ChopFxPayload.HIT -> {
                chips(mc, level, pos, state, out, scaled(9, amt), 0.20);
                if (mine) shake(0.35F);
            }
            case ChopFxPayload.FINAL -> {
                chips(mc, level, pos, state, out, scaled(24, amt), 0.30);
                puffs(level, pos, out, scaled(5, amt));
                if (ChopConfig.FX_LEAVES.get()) leaves(mc, level, pos, amt);
                if (mine) shake(0.9F);
            }
            case ChopFxPayload.MISS -> {
                sparks(level, pos, out, scaled(6, amt));
                chips(mc, level, pos, state, out, scaled(3, amt), 0.26);
                if (mine) shake(0.2F);
            }
            case ChopFxPayload.STUCK -> {
                chips(mc, level, pos, state, out, scaled(7, amt), 0.16);
                puffs(level, pos, out, scaled(3, amt));
                if (mine) shake(0.7F);
            }
            case ChopFxPayload.PULL -> {
                chips(mc, level, pos, state, out, scaled(6, amt), 0.18);
                puffs(level, pos, out, scaled(2, amt));
                if (mine) shake(0.45F);
            }
            default -> {}
        }
    }

    private static int scaled(int base, double amt) {
        return (int) Math.round(base * amt);
    }

    /** Small pieces of the log that fly out of the cut and fall with gravity. */
    private static void chips(Minecraft mc, ClientLevel level, BlockPos pos, BlockState state,
                              Direction out, int count, double speed) {
        if (count <= 0) return;
        RandomSource r = level.random;
        BlockParticleOption opt = new BlockParticleOption(ParticleTypes.BLOCK, state);
        for (int i = 0; i < count; i++) {
            double side = (r.nextDouble() - 0.5) * 0.8;
            double x = pos.getX() + 0.5 + out.getStepX() * 0.5 + out.getStepZ() * side;
            double z = pos.getZ() + 0.5 + out.getStepZ() * 0.5 + out.getStepX() * side;
            double y = pos.getY() + 0.2 + r.nextDouble() * 0.6;
            double sp = speed * (0.55 + r.nextDouble() * 0.9);
            double vx = out.getStepX() * sp + (r.nextDouble() - 0.5) * speed;
            double vz = out.getStepZ() * sp + (r.nextDouble() - 0.5) * speed;
            double vy = speed * (0.35 + r.nextDouble() * 0.9);
            Particle part = mc.particleEngine.createParticle(opt, x, y, z, vx, vy, vz);
            if (part != null) {
                part.setParticleSpeed(vx, vy, vz);
                part.scale(0.55F + r.nextFloat() * 0.5F);
                part.setLifetime(16 + r.nextInt(20));
            }
        }
    }

    private static void puffs(ClientLevel level, BlockPos pos, Direction out, int count) {
        RandomSource r = level.random;
        for (int i = 0; i < count; i++) {
            double x = pos.getX() + 0.5 + out.getStepX() * 0.55 + (r.nextDouble() - 0.5) * 0.6;
            double y = pos.getY() + 0.3 + r.nextDouble() * 0.5;
            double z = pos.getZ() + 0.5 + out.getStepZ() * 0.55 + (r.nextDouble() - 0.5) * 0.6;
            level.addParticle(ParticleTypes.POOF, x, y, z,
                    out.getStepX() * 0.03, 0.02, out.getStepZ() * 0.03);
        }
    }

    private static void sparks(ClientLevel level, BlockPos pos, Direction out, int count) {
        RandomSource r = level.random;
        for (int i = 0; i < count; i++) {
            double x = pos.getX() + 0.5 + out.getStepX() * 0.55 + (r.nextDouble() - 0.5) * 0.5;
            double y = pos.getY() + 0.3 + r.nextDouble() * 0.5;
            double z = pos.getZ() + 0.5 + out.getStepZ() * 0.55 + (r.nextDouble() - 0.5) * 0.5;
            level.addParticle(ParticleTypes.CRIT, x, y, z,
                    out.getStepX() * 0.35 + (r.nextDouble() - 0.5) * 0.2,
                    0.1 + r.nextDouble() * 0.2,
                    out.getStepZ() * 0.35 + (r.nextDouble() - 0.5) * 0.2);
        }
    }

    /** Leaves near the top of the tree shake loose when the trunk is cut. */
    private static void leaves(Minecraft mc, ClientLevel level, BlockPos pos, double amt) {
        RandomSource r = level.random;
        int limit = scaled(24, amt);
        int spawned = 0;
        for (int i = 0; i < 40 && spawned < limit; i++) {
            BlockPos q = pos.offset(r.nextInt(9) - 4, 1 + r.nextInt(10), r.nextInt(9) - 4);
            BlockState ls = level.getBlockState(q);
            if (!ls.is(BlockTags.LEAVES)) continue;
            BlockParticleOption opt = new BlockParticleOption(ParticleTypes.BLOCK, ls);
            for (int k = 0; k < 2; k++) {
                Particle part = mc.particleEngine.createParticle(opt,
                        q.getX() + r.nextDouble(), q.getY() + r.nextDouble() * 0.5, q.getZ() + r.nextDouble(),
                        0, 0, 0);
                if (part != null) {
                    part.setParticleSpeed((r.nextDouble() - 0.5) * 0.06, -0.02 - r.nextDouble() * 0.03,
                            (r.nextDouble() - 0.5) * 0.06);
                    part.scale(0.8F + r.nextFloat() * 0.5F);
                    part.setLifetime(30 + r.nextInt(30));
                    spawned++;
                }
            }
        }
    }

    // ---------- camera kick (only for the player who is chopping) ----------
    private static void shake(float amp) {
        float scale = ChopConfig.FX_CAMERA_SHAKE.get().floatValue();
        if (scale <= 0F) return;
        shakeAmp = amp * scale;
        shakeStart = Util.getMillis();
        shakeDir = Math.random() < 0.5 ? -1F : 1F;
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles e) {
        if (shakeAmp <= 0F) return;
        float t = (Util.getMillis() - shakeStart) / (float) SHAKE_MS;
        if (t >= 1F) {
            shakeAmp = 0F;
            return;
        }
        float decay = (1F - t) * (1F - t);
        e.setPitch(e.getPitch() + shakeAmp * decay * (float) Math.cos(t * 9.0));
        e.setRoll(e.getRoll() + shakeDir * shakeAmp * 0.45F * decay * (float) Math.sin(t * 22.0));
        e.setYaw(e.getYaw() + shakeDir * shakeAmp * 0.25F * decay * (float) Math.sin(t * 17.0 + 1.0));
    }

    private ChopFx() {}
}
