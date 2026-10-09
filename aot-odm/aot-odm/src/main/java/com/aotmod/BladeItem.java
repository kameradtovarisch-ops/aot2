package com.aotmod;

import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;

/** Thin, fragile, fast blade. Damage 6, 120 durability. */
public class BladeItem extends SwordItem {
    public BladeItem(Settings settings) {
        super(Material.INSTANCE, 3, -1.4f, settings);
    }

    public static class Material implements ToolMaterial {
        public static final Material INSTANCE = new Material();
        @Override public int getDurability() { return 120; }
        @Override public float getMiningSpeedMultiplier() { return 6.0f; }
        @Override public float getAttackDamage() { return 2.0f; }
        @Override public int getMiningLevel() { return 2; }
        @Override public int getEnchantability() { return 14; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.ofItems(Items.IRON_INGOT); }
    }
}
