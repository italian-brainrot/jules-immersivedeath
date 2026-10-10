package com.jules.immersivedeath;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class HeartContainerItem extends Item {
    public HeartContainerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        AttributeInstance maxHpAttr = player.getAttribute(Attributes.MAX_HEALTH);
        AttributeModifier modifier = maxHpAttr != null ? maxHpAttr.getModifier(DeathEventHandler.LOST_MAX_HP_MODIFIER_ID) : null;
        double lostHp = modifier != null ? Math.abs(modifier.amount()) : 0.0;

        if (lostHp <= 0.001) {
            player.displayClientMessage(
                    Component.translatable("item.immersivedeath.heart_container.full_health"),
                    true
            );
            return InteractionResultHolder.fail(itemStack);
        }

        if (!level.isClientSide()) {
            if (player instanceof ServerPlayer serverPlayer) {
                DeathData deathData = serverPlayer.getData(ModAttachments.DEATH_DATA);
                double lost = deathData.getLostMaxHp();

                if (lost > 0.001) {
                    double newLost = Math.max(0.0, lost - 2.0);
                    deathData.setLostMaxHp(newLost);
                    DeathEventHandler.updateMaxHealthModifier(serverPlayer, newLost);

                    serverPlayer.heal(2.0f);

                    level.playSound(
                            null,
                            serverPlayer.getX(),
                            serverPlayer.getY(),
                            serverPlayer.getZ(),
                            SoundEvents.PLAYER_LEVELUP,
                            SoundSource.PLAYERS,
                            0.8f,
                            1.2f
                    );

                    if (!serverPlayer.getAbilities().instabuild) {
                        itemStack.shrink(1);
                    }

                    int heartsRestored = 1;
                    serverPlayer.displayClientMessage(
                            Component.translatable("item.immersivedeath.heart_container.restored", heartsRestored),
                            true
                    );

                    return InteractionResultHolder.consume(itemStack);
                }
            }
        }

        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
    }
}
