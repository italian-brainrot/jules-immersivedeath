package com.jules.immersivedeath;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
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
                            Component.translatable("item.com.jules.immersivedeath.heart_container.restored", heartsRestored),
                            true
                    );

                    return InteractionResultHolder.consume(itemStack);
                } else {
                    serverPlayer.displayClientMessage(
                            Component.translatable("item.com.jules.immersivedeath.heart_container.full_health"),
                            true
                    );
                    return InteractionResultHolder.fail(itemStack);
                }
            }
        }

        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
    }
}
