package com.jules.immersivedeath;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

@Mod(ImmersiveDeath.MODID)
public class ImmersiveDeath {
    public static final String MODID = "immersivedeath";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ImmersiveDeath(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Initializing Immersive Death mod ({})", MODID);
        ModAttachments.register(modEventBus);
        ModItems.register(modEventBus);
    }
}
