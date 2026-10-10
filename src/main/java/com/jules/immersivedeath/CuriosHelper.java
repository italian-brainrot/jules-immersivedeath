package com.jules.immersivedeath;

import java.lang.reflect.Method;
import java.util.Optional;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;

public class CuriosHelper {

    public static CompoundTag saveCuriosInventory(ServerPlayer player) {
        if (!ModList.get().isLoaded("curios")) {
            return new CompoundTag();
        }

        try {
            Class<?> curiosApiClass = Class.forName("top.theillusivec.curios.api.CuriosApi");
            Method getCuriosInventory = curiosApiClass.getMethod("getCuriosInventory", LivingEntity.class);
            Object result = getCuriosInventory.invoke(null, player);

            Object curiosHandler = null;
            if (result instanceof Optional<?> opt) {
                curiosHandler = opt.orElse(null);
            } else {
                curiosHandler = result;
            }

            if (curiosHandler == null) {
                return new CompoundTag();
            }

            CompoundTag tag = serializeHandler(curiosHandler, player.registryAccess());
            return tag != null ? tag : new CompoundTag();
        } catch (Throwable t) {
            ImmersiveDeath.LOGGER.error("Failed to save Curios inventory for player {}", player.getName().getString(), t);
            return new CompoundTag();
        }
    }

    public static void restoreCuriosInventory(ServerPlayer player, CompoundTag curiosTag) {
        if (!ModList.get().isLoaded("curios") || curiosTag == null || curiosTag.isEmpty()) {
            return;
        }

        try {
            Class<?> curiosApiClass = Class.forName("top.theillusivec.curios.api.CuriosApi");
            Method getCuriosInventory = curiosApiClass.getMethod("getCuriosInventory", LivingEntity.class);
            Object result = getCuriosInventory.invoke(null, player);

            Object curiosHandler = null;
            if (result instanceof Optional<?> opt) {
                curiosHandler = opt.orElse(null);
            } else {
                curiosHandler = result;
            }

            if (curiosHandler == null) {
                return;
            }

            deserializeHandler(curiosHandler, player.registryAccess(), curiosTag);
        } catch (Throwable t) {
            ImmersiveDeath.LOGGER.error("Failed to restore Curios inventory for player {}", player.getName().getString(), t);
        }
    }

    private static CompoundTag serializeHandler(Object handler, HolderLookup.Provider provider) {
        Class<?> clazz = handler.getClass();
        try {
            Method m = clazz.getMethod("serializeNBT", HolderLookup.Provider.class);
            return (CompoundTag) m.invoke(handler, provider);
        } catch (NoSuchMethodException e) {
            try {
                Method m = clazz.getMethod("serializeNBT");
                return (CompoundTag) m.invoke(handler);
            } catch (Throwable t) {
                // Ignore and try fallback
            }
        } catch (Throwable t) {
            // Ignore
        }

        try {
            Method m = clazz.getMethod("writeNBT");
            return (CompoundTag) m.invoke(handler);
        } catch (Throwable t) {
            // Ignore
        }

        return null;
    }

    private static void deserializeHandler(Object handler, HolderLookup.Provider provider, CompoundTag tag) {
        Class<?> clazz = handler.getClass();
        try {
            Method m = clazz.getMethod("deserializeNBT", HolderLookup.Provider.class, CompoundTag.class);
            m.invoke(handler, provider, tag);
            return;
        } catch (NoSuchMethodException e) {
            try {
                Method m = clazz.getMethod("deserializeNBT", CompoundTag.class);
                m.invoke(handler, tag);
                return;
            } catch (Throwable t) {
                // Ignore and try fallback
            }
        } catch (Throwable t) {
            // Ignore
        }

        try {
            Method m = clazz.getMethod("readNBT", CompoundTag.class);
            m.invoke(handler, tag);
        } catch (Throwable t) {
            // Ignore
        }
    }
}
