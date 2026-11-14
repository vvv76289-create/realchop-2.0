package com.heavychop;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ChopConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue HITS_FACTOR;
    public static final ModConfigSpec.IntValue MIN_HITS;
    public static final ModConfigSpec.IntValue SWING_TICKS;
    public static final ModConfigSpec.IntValue CONTACT_TICKS;
    public static final ModConfigSpec.IntValue PULL_TICKS;
    public static final ModConfigSpec.DoubleValue EARLY_MISS_CHANCE;
    public static final ModConfigSpec.DoubleValue MISS_STUCK_CHANCE;
    public static final ModConfigSpec.DoubleValue NORMAL_STUCK_CHANCE;
    public static final ModConfigSpec.IntValue DURABILITY_PER_HIT;
    public static final ModConfigSpec.IntValue RESET_AWAY_TICKS;
    public static final ModConfigSpec.BooleanValue DEBUG;
    public static final ModConfigSpec.DoubleValue FX_PARTICLES;
    public static final ModConfigSpec.DoubleValue FX_CAMERA_SHAKE;
    public static final ModConfigSpec.BooleanValue FX_LEAVES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("chopping");
        HITS_FACTOR = b.comment("Hits needed = ceil(blockHardness / toolSpeed * factor). Higher = more hits.")
                .defineInRange("hitsFactor", 10.0, 0.1, 500.0);
        MIN_HITS = b.comment("Minimum hits for any log.").defineInRange("minHits", 3, 1, 100);
        SWING_TICKS = b.comment("Full swing length in ticks. Clicking earlier counts as an early click.")
                .defineInRange("swingTicks", 12, 4, 60);
        CONTACT_TICKS = b.comment("Tick of the swing when the axe actually hits.")
                .defineInRange("contactTicks", 4, 1, 60);
        PULL_TICKS = b.comment("Cooldown after pulling a stuck axe out, in ticks.")
                .defineInRange("pullTicks", 12, 0, 100);
        EARLY_MISS_CHANCE = b.comment("Chance that an early click deals no damage.")
                .defineInRange("earlyMissChance", 0.7, 0.0, 1.0);
        MISS_STUCK_CHANCE = b.comment("Chance the axe gets stuck after a missed (early) swing.")
                .defineInRange("missStuckChance", 0.4, 0.0, 1.0);
        NORMAL_STUCK_CHANCE = b.comment("Chance the axe gets stuck after a normal, successful hit.")
                .defineInRange("normalStuckChance", 0.05, 0.0, 1.0);
        DURABILITY_PER_HIT = b.comment("Axe durability lost per successful hit.")
                .defineInRange("durabilityPerHit", 1, 0, 20);
        RESET_AWAY_TICKS = b.comment("Cracks reset after the player looks away from the log for this many ticks.")
                .defineInRange("resetAwayTicks", 20, 5, 200);
        DEBUG = b.comment("Write chopping events to the game log (troubleshooting).")
                .define("debugLog", true);
        b.pop();
        b.push("effects");
        FX_PARTICLES = b.comment("Multiplier for wood chip and dust amount (client side). 0 = off.")
                .defineInRange("particleAmount", 1.0, 0.0, 4.0);
        FX_CAMERA_SHAKE = b.comment("Multiplier for the camera kick while chopping (client side). 0 = off.")
                .defineInRange("cameraShake", 1.0, 0.0, 3.0);
        FX_LEAVES = b.comment("Leaves shake down from the tree on the final hit (client side).")
                .define("fallingLeaves", true);
        b.pop();
        SPEC = b.build();
    }

    private ChopConfig() {}
}
