package com.heavychop;

import com.mojang.logging.LogUtils;
import com.heavychop.net.ChopNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod("heavychop")
public class HeavyChop {
    public static final String MODID = "heavychop";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HeavyChop(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, ChopConfig.SPEC);
        modBus.addListener(ChopNetwork::register);
        LOGGER.info("[HeavyChop] loaded");
    }
}
