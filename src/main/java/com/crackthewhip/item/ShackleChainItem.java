package com.crackthewhip.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Вся логика цепей — в ChainEvents / ChainManager (работает и на мобах, и на игроках). */
public class ShackleChainItem extends Item {
    public ShackleChainItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.crackthewhip.chain.1").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.crackthewhip.chain.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.crackthewhip.chain.3").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.crackthewhip.chain.4").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.crackthewhip.chain.5").withStyle(ChatFormatting.GRAY));
    }
}
