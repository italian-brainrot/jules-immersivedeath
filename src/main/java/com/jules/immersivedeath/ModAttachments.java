package com.jules.immersivedeath;

import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ImmersiveDeath.MODID);

    public static final Supplier<AttachmentType<DeathData>> DEATH_DATA = ATTACHMENT_TYPES.register(
            "death_data",
            () -> AttachmentType.serializable(DeathData::new).copyOnDeath().build()
    );

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
