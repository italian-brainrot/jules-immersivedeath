package com.jules.immersivedeath;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = ImmersiveDeath.MODID)
public class DeathEventHandler {

    public static final ResourceLocation LOST_MAX_HP_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(ImmersiveDeath.MODID, "lost_max_hp");

    public static void updateMaxHealthModifier(Player player, double lostMaxHp) {
        AttributeInstance maxHpAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHpAttr == null) return;

        maxHpAttr.removeModifier(LOST_MAX_HP_MODIFIER_ID);
        if (lostMaxHp > 0.001) {
            AttributeModifier modifier = new AttributeModifier(
                    LOST_MAX_HP_MODIFIER_ID,
                    -lostMaxHp,
                    AttributeModifier.Operation.ADD_VALUE
            );
            maxHpAttr.addTransientModifier(modifier);
        }

        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;

        DeathData deathData = player.getData(ModAttachments.DEATH_DATA);

        // Snapshot main inventory
        ListTag invTag = new ListTag();
        player.getInventory().save(invTag);
        deathData.setInventorySnapshot(invTag);

        long now = System.currentTimeMillis();
        long lastDeath = deathData.getLastDeathTime();
        boolean isDeathLoop = (lastDeath > 0L) && ((now - lastDeath) < 60000L); // 1 minute
        deathData.setLastDeathTime(now);

        if (isDeathLoop) {
            deathData.setPendingPenalty(false);
        } else {
            deathData.setPendingPenalty(true);

            double currentLost = deathData.getLostMaxHp();
            double baseMaxHp = player.getAttributeBaseValue(Attributes.MAX_HEALTH);
            double maxAllowedLost = Math.max(0.0, baseMaxHp - 1.0); // Down to 1 max HP
            double newLost = Math.min(currentLost + 2.0, maxAllowedLost);
            deathData.setLostMaxHp(newLost);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;

        DeathData deathData = player.getData(ModAttachments.DEATH_DATA);

        // Capture any item entities dropped by other mods/custom slots
        ListTag extraTag = new ListTag();
        for (ItemEntity drop : event.getDrops()) {
            if (drop != null && !drop.getItem().isEmpty()) {
                extraTag.add(drop.getItem().save(player.registryAccess(), new CompoundTag()));
            }
        }
        deathData.setExtraDropsSnapshot(extraTag);

        event.getDrops().clear();
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        if (event.getEntity().level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;

        if (event.getEntity() instanceof ServerPlayer newPlayer && event.getOriginal() instanceof ServerPlayer oldPlayer) {
            DeathData oldData = oldPlayer.getData(ModAttachments.DEATH_DATA);

            // Restore main inventory snapshot
            ListTag snapshot = oldData.getInventorySnapshot();
            if (snapshot != null && !snapshot.isEmpty()) {
                newPlayer.getInventory().load(snapshot);
                oldData.setInventorySnapshot(new ListTag());
            }

            // Restore any extra item drops (e.g. from custom slot mods / accessories)
            ListTag extraSnapshot = oldData.getExtraDropsSnapshot();
            if (extraSnapshot != null && !extraSnapshot.isEmpty()) {
                for (int i = 0; i < extraSnapshot.size(); i++) {
                    CompoundTag tag = extraSnapshot.getCompound(i);
                    ItemStack stack = ItemStack.parse(newPlayer.registryAccess(), tag).orElse(ItemStack.EMPTY);
                    if (!stack.isEmpty()) {
                        // Place into inventory or drop at feet if inventory is somehow full
                        if (!newPlayer.getInventory().add(stack)) {
                            newPlayer.drop(stack, false);
                        }
                    }
                }
                oldData.setExtraDropsSnapshot(new ListTag());
            }

            updateMaxHealthModifier(newPlayer, oldData.getLostMaxHp());
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;

        DeathData deathData = player.getData(ModAttachments.DEATH_DATA);
        updateMaxHealthModifier(player, deathData.getLostMaxHp());

        if (deathData.isPendingPenalty()) {
            deathData.setPendingPenalty(false);

            // Hunger to 6/20
            player.getFoodData().setFoodLevel(6);
            player.getFoodData().setSaturation(0.0f);

            // Set debuff tick counters in DeathData
            deathData.setNauseaTicks(600);   // 30s = 600 ticks
            deathData.setBlindnessTicks(1200); // 60s = 1200 ticks
            deathData.setWeaknessTicks(12000); // 600s = 12000 ticks

            // Apply initial debuff instances
            // Nausea IX (30s = 600 ticks, level 9 = amp 8)
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 600, 8, false, true, true));
            // Blindness XV (1m = 1200 ticks, level 15 = amp 14)
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 1200, 14, false, true, true));
            // Weakness XVIII (10m = 12000 ticks, level 18 = amp 17)
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 12000, 17, false, true, true));
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DeathData deathData = player.getData(ModAttachments.DEATH_DATA);
            updateMaxHealthModifier(player, deathData.getLostMaxHp());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        DeathData deathData = player.getData(ModAttachments.DEATH_DATA);

        // Nausea (CONFUSION) linear decay for death debuff
        int nTicks = deathData.getNauseaTicks();
        if (nTicks > 0) {
            if (!player.hasEffect(MobEffects.CONFUSION)) {
                // Milk or cure was consumed! Stop decay for death Nausea.
                deathData.setNauseaTicks(0);
            } else {
                nTicks--;
                deathData.setNauseaTicks(nTicks);
                int targetLevel = (int) Math.ceil(((double) nTicks / 600.0) * 9.0);
                targetLevel = Math.max(1, Math.min(9, targetLevel));
                int targetAmp = targetLevel - 1;

                MobEffectInstance inst = player.getEffect(MobEffects.CONFUSION);
                if (inst != null && inst.getAmplifier() != targetAmp && nTicks > 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, nTicks, targetAmp, inst.isAmbient(), inst.isVisible(), inst.showIcon()), null);
                }
            }
        }

        // Blindness linear decay for death debuff
        int bTicks = deathData.getBlindnessTicks();
        if (bTicks > 0) {
            if (!player.hasEffect(MobEffects.BLINDNESS)) {
                // Milk or cure was consumed! Stop decay for death Blindness.
                deathData.setBlindnessTicks(0);
            } else {
                bTicks--;
                deathData.setBlindnessTicks(bTicks);
                int targetLevel = (int) Math.ceil(((double) bTicks / 1200.0) * 15.0);
                targetLevel = Math.max(1, Math.min(15, targetLevel));
                int targetAmp = targetLevel - 1;

                MobEffectInstance inst = player.getEffect(MobEffects.BLINDNESS);
                if (inst != null && inst.getAmplifier() != targetAmp && bTicks > 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, bTicks, targetAmp, inst.isAmbient(), inst.isVisible(), inst.showIcon()), null);
                }
            }
        }

        // Weakness linear decay for death debuff
        int wTicks = deathData.getWeaknessTicks();
        if (wTicks > 0) {
            if (!player.hasEffect(MobEffects.WEAKNESS)) {
                // Milk or cure was consumed! Stop decay for death Weakness.
                deathData.setWeaknessTicks(0);
            } else {
                wTicks--;
                deathData.setWeaknessTicks(wTicks);
                int targetLevel = (int) Math.ceil(((double) wTicks / 12000.0) * 18.0);
                targetLevel = Math.max(1, Math.min(18, targetLevel));
                int targetAmp = targetLevel - 1;

                MobEffectInstance inst = player.getEffect(MobEffects.WEAKNESS);
                if (inst != null && inst.getAmplifier() != targetAmp && wTicks > 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, wTicks, targetAmp, inst.isAmbient(), inst.isVisible(), inst.showIcon()), null);
                }
            }
        }
    }
}
