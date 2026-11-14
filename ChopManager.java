package com.heavychop.server;

import com.heavychop.ChopConfig;
import com.heavychop.ChopRules;
import com.heavychop.HeavyChop;
import com.heavychop.net.ChopAnimPayload;
import com.heavychop.net.ChopFxPayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** All chopping rules live here and run on the server only. State exists only for players who are chopping. */
@EventBusSubscriber(modid = HeavyChop.MODID)
public final class ChopManager {

    private static final class ChopState {
        BlockPos pos;
        int hits, required;
        long swingEndTick, contactTick, cooldownUntil;
        boolean pending, pendingCounts, stuck;
        int awayChecks;
    }

    private static final Map<UUID, ChopState> STATES = new HashMap<>();

    // ---------- vanilla breaking is disabled for logs + axe, our swings replace it ----------
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed e) {
        if (ChopRules.isChoppable(e.getEntity(), e.getState())) e.setNewSpeed(0.0F);
    }

    // ---------- click from client ----------
    public static void handleClick(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        BlockState state = level.getBlockState(pos);
        boolean ok = ChopRules.isChoppable(player, state) && player.canInteractWithBlock(pos, 1.0);
        if (ChopConfig.DEBUG.get()) {
            HeavyChop.LOGGER.info("[HeavyChop] server click {} block={} axe={} accepted={}", pos,
                    state.getBlock().getDescriptionId(), player.getMainHandItem().getItem(), ok);
        }
        if (!ok) return;

        ChopState st = STATES.computeIfAbsent(player.getUUID(), id -> new ChopState());

        if (st.stuck) { // any click pulls the axe out
            st.stuck = false;
            st.pending = false;
            st.swingEndTick = 0;
            st.cooldownUntil = now + ChopConfig.PULL_TICKS.get();
            level.playSound(null, pos, SoundEvents.WOOD_STEP, SoundSource.BLOCKS, 1.0F, 0.5F);
            level.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.7F, 0.6F);
            send(player, ChopAnimPayload.PULL, ChopConfig.PULL_TICKS.get(), 0);
            fx(player, level, pos, state, ChopFxPayload.PULL);
            return;
        }
        if (now < st.cooldownUntil) return;

        if (st.pos != null && !st.pos.equals(pos)) resetProgress(player, st, level);
        if (st.pos == null) {
            st.pos = pos.immutable();
            st.hits = 0;
            st.required = requiredHits(level, pos, state, player.getMainHandItem());
        }

        boolean early = now < st.swingEndTick;
        st.pendingCounts = !early || level.random.nextDouble() >= ChopConfig.EARLY_MISS_CHANCE.get();
        st.pending = true;
        st.contactTick = now + ChopConfig.CONTACT_TICKS.get();
        st.swingEndTick = now + ChopConfig.SWING_TICKS.get();
        st.awayChecks = 0;
        send(player, ChopAnimPayload.SWING, ChopConfig.SWING_TICKS.get(), ChopConfig.CONTACT_TICKS.get());
    }

    // ---------- server tick: only iterates players who are actually chopping ----------
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        if (STATES.isEmpty()) return;
        MinecraftServer server = e.getServer();
        Iterator<Map.Entry<UUID, ChopState>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ChopState> en = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(en.getKey());
            ChopState st = en.getValue();
            if (player == null) { it.remove(); continue; }
            ServerLevel level = player.serverLevel();
            long now = level.getGameTime();

            if (st.pending && now >= st.contactTick) resolveContact(player, level, st);
            if (!st.pending && now % 5 == 0 && (st.hits > 0 || st.stuck)) awayCheck(player, level, st);
            if (st.pos == null && !st.pending && !st.stuck && now >= st.cooldownUntil) it.remove();
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Component.translatable("message.heavychop.loaded", HeavyChop.VERSION,
                    String.valueOf(ChopConfig.HITS_FACTOR.get()), String.valueOf(ChopConfig.SWING_TICKS.get())));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            ChopState st = STATES.remove(sp.getUUID());
            if (st != null && st.pos != null) sp.serverLevel().destroyBlockProgress(crackId(sp), st.pos, -1);
        }
    }

    // ---------- the moment the axe actually lands ----------
    private static void resolveContact(ServerPlayer player, ServerLevel level, ChopState st) {
        st.pending = false;
        BlockPos pos = st.pos;
        if (pos == null) return;
        BlockState state = level.getBlockState(pos);
        ItemStack tool = player.getMainHandItem();
        if (!ChopRules.isChoppable(player, state) || !player.canInteractWithBlock(pos, 1.0)) {
            resetProgress(player, st, level);
            return;
        }

        if (!st.pendingCounts) { // missed swing: the axe bounces off
            float p = 0.85F + level.random.nextFloat() * 0.2F;
            level.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.8F, 0.7F * p);
            level.playSound(null, pos, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.25F, 1.6F * p);
            fx(player, level, pos, state, ChopFxPayload.MISS);
            rollStuck(player, st, level, ChopConfig.MISS_STUCK_CHANCE.get());
            return;
        }

        st.hits++;
        if (ChopConfig.DEBUG.get()) HeavyChop.LOGGER.info("[HeavyChop] hit {}/{} at {}", st.hits, st.required, pos);
        boolean fin = st.hits >= st.required;
        float progress = Math.min(1.0F, (float) st.hits / st.required);
        hitSounds(level, pos, state, tool, progress, fin);
        fx(player, level, pos, state, fin ? ChopFxPayload.FINAL : ChopFxPayload.HIT);

        if (fin) { // final hit: real break, Tree Physics etc. react to the normal break event
            level.destroyBlockProgress(crackId(player), pos, -1);
            st.pos = null;
            st.hits = 0;
            st.required = 0;
            player.gameMode.destroyBlock(pos);
            return;
        }

        level.destroyBlockProgress(crackId(player), pos, Math.min(9, st.hits * 10 / st.required));
        int dmg = ChopConfig.DURABILITY_PER_HIT.get();
        if (dmg > 0) tool.hurtAndBreak(dmg, player, EquipmentSlot.MAINHAND);
        rollStuck(player, st, level, ChopConfig.NORMAL_STUCK_CHANCE.get());
    }

    /**
     * Layered sound: the log's own hit sound (so every wood type sounds different), pitch shifted by axe speed
     * (better axes ring sharper), plus a growing "crack" as the log nears breaking.
     */
    private static void hitSounds(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool,
                                  float progress, boolean fin) {
        SoundType sound = state.getSoundType();
        float rnd = 0.9F + level.random.nextFloat() * 0.2F;
        float tier = Mth.clamp((tool.getDestroySpeed(state) - 4.0F) * 0.02F, -0.08F, 0.12F);
        level.playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS, 1.0F,
                Math.max(0.5F, sound.getPitch() * 0.8F * rnd + tier));
        if (progress > 0.5F) {
            level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS,
                    (progress - 0.5F) * 0.9F, 1.3F * rnd);
        }
        if (fin) {
            level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0F, 0.7F * rnd);
        }
    }

    private static void rollStuck(ServerPlayer player, ChopState st, ServerLevel level, double chance) {
        if (st.pos == null || !ChopRules.isAxe(player.getMainHandItem())) return;
        if (level.random.nextDouble() < chance) {
            st.stuck = true;
            level.playSound(null, st.pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
            level.playSound(null, st.pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.15F, 1.8F);
            send(player, ChopAnimPayload.STUCK, 0, 0);
            fx(player, level, st.pos, level.getBlockState(st.pos), ChopFxPayload.STUCK);
        }
    }

    // ---------- cracks reset when the player looks away; stuck axe is freed if they leave ----------
    private static void awayCheck(ServerPlayer player, ServerLevel level, ChopState st) {
        boolean holdingAxe = ChopRules.isAxe(player.getMainHandItem());
        if (st.stuck) {
            if (st.pos == null) { st.stuck = false; return; }
            boolean far = player.distanceToSqr(st.pos.getX() + 0.5, st.pos.getY() + 0.5, st.pos.getZ() + 0.5) > 64;
            if (!holdingAxe || far) {
                st.stuck = false;
                send(player, ChopAnimPayload.RESET, 0, 0);
            }
            return;
        }
        HitResult hr = player.pick(player.blockInteractionRange() + 1.0, 1.0F, false);
        boolean looking = hr instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK
                && b.getBlockPos().equals(st.pos);
        if (holdingAxe && looking) { st.awayChecks = 0; return; }
        st.awayChecks++;
        if (st.awayChecks * 5 >= ChopConfig.RESET_AWAY_TICKS.get()) resetProgress(player, st, level);
    }

    private static void resetProgress(ServerPlayer player, ChopState st, ServerLevel level) {
        if (st.pos != null) level.destroyBlockProgress(crackId(player), st.pos, -1);
        st.pos = null;
        st.hits = 0;
        st.required = 0;
        st.awayChecks = 0;
    }

    /** Hits needed depend on the log's hardness and the axe's speed (tier), so modded woods/axes scale automatically. */
    /**
     * Vanilla never shows server-sent crack progress to the player who owns that breaker id (the client draws its own).
     * Using a separate id makes our cracks visible to the chopper too.
     */
    private static int crackId(ServerPlayer p) {
        return 0x40000000 + p.getId();
    }

    private static int requiredHits(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool) {
        float hardness = state.getDestroySpeed(level, pos);
        float speed = Math.max(0.5F, tool.getDestroySpeed(state));
        int hits = (int) Math.ceil(hardness / speed * ChopConfig.HITS_FACTOR.get());
        return Math.max(ChopConfig.MIN_HITS.get(), hits);
    }

    private static void send(ServerPlayer player, int kind, int duration, int contact) {
        PacketDistributor.sendToPlayer(player, new ChopAnimPayload(kind, duration, contact));
    }

    /** Effects go to everyone within 48 blocks; the client decides what to show and only kicks the chopper's camera. */
    private static void fx(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, int kind) {
        double dx = player.getX() - (pos.getX() + 0.5);
        double dz = player.getZ() - (pos.getZ() + 0.5);
        Direction toward = Math.abs(dx) > Math.abs(dz)
                ? (dx > 0 ? Direction.EAST : Direction.WEST)
                : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        ChopFxPayload payload = new ChopFxPayload(pos, kind, Block.getId(state), toward.get3DDataValue(), player.getId());
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        for (ServerPlayer sp : level.players()) {
            if (sp.distanceToSqr(cx, cy, cz) < 48.0 * 48.0) PacketDistributor.sendToPlayer(sp, payload);
        }
    }

    private ChopManager() {}
}
