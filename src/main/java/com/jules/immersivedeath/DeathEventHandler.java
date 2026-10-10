package com.jules.immersivedeath;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;

        DeathData deathData = player.getData(ModAttachments.DEATH_DATA);

        // Save vanilla inventory snapshot to DeathData (persisted via attachment)
        ListTag invTag = new ListTag();
        player.getInventory().save(invTag);
        deathData.setInventorySnapshot(invTag);

        long now = player.level().getGameTime();
        long lastDeath = deathData.getLastDeathTime();
        boolean isDeathLoop = (lastDeath >= 0L) && ((now - lastDeath) < 1200L); // 1 minute = 1200 ticks
        deathData.setLastDeathTime(now);

        if (isDeathLoop) {
            deathData.setPendingPenalty(false);
        } else {
            deathData.setPendingPenalty(true);

            double currentLost = deathData.getLostMaxHp();
            double baseMaxHp = player.getAttributeBaseValue(Attributes.MAX_HEALTH);
            double maxAllowedLost = Math.max(0.0, baseMaxHp - 1.0); // Down to 1 max HP (0.5 heart)
            double newLost = Math.min(currentLost + 2.0, maxAllowedLost); // 1 full heart = 2.0 HP
            deathData.setLostMaxHp(newLost);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;

        DeathData deathData = player.getData(ModAttachments.DEATH_DATA);
        ListTag invSnapshot = deathData.getInventorySnapshot();
        if (invSnapshot == null || invSnapshot.isEmpty()) return;

        List<ItemStack> expectedVanillaInventory = new ArrayList<>();
        for (int i = 0; i < invSnapshot.size(); i++) {
            CompoundTag slotTag = invSnapshot.getCompound(i);
            ItemStack stack = ItemStack.parse(player.registryAccess(), slotTag).orElse(ItemStack.EMPTY);
            if (!stack.isEmpty()) {
                expectedVanillaInventory.add(stack.copy());
            }
        }

        // Remove vanilla inventory drops from drops list so player doesn't drop vanilla items on the ground.
        // Leave any extra drops (e.g. from accessories, backpacks, custom slots) in event.getDrops()
        // so other mods (trinkets, curios, gravestones, etc.) can handle them naturally.
        Iterator<ItemEntity> dropIterator = event.getDrops().iterator();
        while (dropIterator.hasNext()) {
            ItemEntity drop = dropIterator.next();
            if (drop == null || drop.getItem().isEmpty()) continue;

            ItemStack remainingDrop = drop.getItem();

            for (Iterator<ItemStack> it = expectedVanillaInventory.iterator(); it.hasNext(); ) {
                ItemStack mainStack = it.next();
                if (ItemStack.isSameItemSameComponents(mainStack, remainingDrop)) {
                    int countToDeduct = Math.min(mainStack.getCount(), remainingDrop.getCount());
                    mainStack.shrink(countToDeduct);
                    remainingDrop.shrink(countToDeduct);

                    if (mainStack.isEmpty()) {
                        it.remove();
                    }
                    if (remainingDrop.isEmpty()) {
                        dropIterator.remove();
                        break;
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        if (event.getEntity().level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;

        if (event.getEntity() instanceof ServerPlayer newPlayer && event.getOriginal() instanceof ServerPlayer oldPlayer) {
            DeathData oldData = oldPlayer.getData(ModAttachments.DEATH_DATA);
            ListTag snapshot = oldData.getInventorySnapshot();
            if (snapshot != null && !snapshot.isEmpty()) {
                newPlayer.getInventory().load(snapshot);
                oldData.setInventorySnapshot(new ListTag());
            }

            // Standard vanilla player experience resetting on death when keepInventory is false
            newPlayer.experienceLevel = 0;
            newPlayer.experienceProgress = 0.0f;
            newPlayer.totalExperience = 0;

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

            // Hunger set to 6/20
            player.getFoodData().setFoodLevel(6);
            player.getFoodData().setSaturation(0.0f);

            // Set debuff tick counters in DeathData
            deathData.setNauseaTicks(600);    // 30s = 600 ticks
            deathData.setBlindnessTicks(1200); // 60s = 1200 ticks
            deathData.setWeaknessTicks(12000); // 10m = 12000 ticks

            // Apply initial debuff instances
            // Nausea IX (30s = 600 ticks, level 9 = amp 8)
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 600, 8, false, true, true));
            // Blindness XV (60s = 1200 ticks, level 15 = amp 14)
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

        // Nausea (CONFUSION) linear decay: level 9 down to 1 over 30s (600 ticks)
        int nTicks = deathData.getNauseaTicks();
        if (nTicks > 0) {
            if (!player.hasEffect(MobEffects.CONFUSION)) {
                // Milk or cure was consumed! Stop decay for death Nausea.
                deathData.setNauseaTicks(0);
            } else {
                nTicks--;
                deathData.setNauseaTicks(nTicks);
                if (nTicks == 0) {
                    player.removeEffect(MobEffects.CONFUSION);
                } else {
                    int targetLevel = (int) Math.ceil(((double) nTicks / 600.0) * 9.0);
                    targetLevel = Math.max(1, Math.min(9, targetLevel));
                    int targetAmp = targetLevel - 1;

                    MobEffectInstance inst = player.getEffect(MobEffects.CONFUSION);
                    if (inst != null && inst.getAmplifier() != targetAmp) {
                        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, nTicks, targetAmp, inst.isAmbient(), inst.isVisible(), inst.showIcon()));
                    }
                }
            }
        }

        // Blindness linear decay: level 15 down to 1 over 60s (1200 ticks)
        int bTicks = deathData.getBlindnessTicks();
        if (bTicks > 0) {
            if (!player.hasEffect(MobEffects.BLINDNESS)) {
                // Milk or cure was consumed! Stop decay for death Blindness.
                deathData.setBlindnessTicks(0);
            } else {
                bTicks--;
                deathData.setBlindnessTicks(bTicks);
                if (bTicks == 0) {
                    player.removeEffect(MobEffects.BLINDNESS);
                } else {
                    int targetLevel = (int) Math.ceil(((double) bTicks / 1200.0) * 15.0);
                    targetLevel = Math.max(1, Math.min(15, targetLevel));
                    int targetAmp = targetLevel - 1;

                    MobEffectInstance inst = player.getEffect(MobEffects.BLINDNESS);
                    if (inst != null && inst.getAmplifier() != targetAmp) {
                        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, bTicks, targetAmp, inst.isAmbient(), inst.isVisible(), inst.showIcon()));
                    }
                }
            }
        }

        // Weakness linear decay: level 18 down to 1 over 10m (12000 ticks)
        int wTicks = deathData.getWeaknessTicks();
        if (wTicks > 0) {
            if (!player.hasEffect(MobEffects.WEAKNESS)) {
                // Milk or cure was consumed! Stop decay for death Weakness.
                deathData.setWeaknessTicks(0);
            } else {
                wTicks--;
                deathData.setWeaknessTicks(wTicks);
                if (wTicks == 0) {
                    player.removeEffect(MobEffects.WEAKNESS);
                } else {
                    int targetLevel = (int) Math.ceil(((double) wTicks / 12000.0) * 18.0);
                    targetLevel = Math.max(1, Math.min(18, targetLevel));
                    int targetAmp = targetLevel - 1;

                    MobEffectInstance inst = player.getEffect(MobEffects.WEAKNESS);
                    if (inst != null && inst.getAmplifier() != targetAmp) {
                        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, wTicks, targetAmp, inst.isAmbient(), inst.isVisible(), inst.showIcon()));
                    }
                }
            }
        }
    }
}
