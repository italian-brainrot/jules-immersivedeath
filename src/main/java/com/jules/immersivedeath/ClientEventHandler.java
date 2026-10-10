package com.jules.immersivedeath;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.Locale;

@EventBusSubscriber(modid = ImmersiveDeath.MODID, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || player.isSpectator() || mc.options.hideGui) {
            return;
        }

        AttributeInstance maxHpAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHpAttr == null) return;

        AttributeModifier modifier = maxHpAttr.getModifier(DeathEventHandler.LOST_MAX_HP_MODIFIER_ID);
        if (modifier == null) return;

        double lostHp = Math.abs(modifier.amount());

        if (lostHp > 0.01) {
            double lostHearts = lostHp / 2.0;
            String text;
            if (lostHearts == (int) lostHearts) {
                text = Component.translatable("hud.com.jules.immersivedeath.lost_max_hp_int", (int) lostHearts).getString();
            } else {
                text = Component.translatable("hud.com.jules.immersivedeath.lost_max_hp_float", String.format(Locale.ROOT, "%.1f", lostHearts)).getString();
            }

            GuiGraphics guiGraphics = event.getGuiGraphics();
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();

            int x = screenWidth / 2 - 91;
            int y = screenHeight - 49; // Above health bar

            guiGraphics.drawString(mc.font, text, x, y, 0xFF5555, true);
        }
    }
}
