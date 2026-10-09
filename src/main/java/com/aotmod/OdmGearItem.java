package com.aotmod;

import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

/** Just a marker item. Carry it anywhere in your inventory and the controls work. */
public class OdmGearItem extends Item {
    public OdmGearItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Hold [R]: fire both hooks").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Hold [V]: gas boost").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Sneak while hooked: reel in").formatted(Formatting.GRAY));
    }
}
