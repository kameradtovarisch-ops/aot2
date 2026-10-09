package com.aotmod;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {
    public static final Item ODM_GEAR = add("odm_gear", new OdmGearItem(new Item.Settings().maxCount(1)));
    public static final Item BLADE = add("odm_blade", new BladeItem(new Item.Settings().maxCount(1)));
    public static final Item TITAN_SPAWN_EGG = add("titan_spawn_egg",
            new SpawnEggItem(ModEntities.TITAN, 0xD9A58A, 0x5A2E2E, new Item.Settings()));

    private static Item add(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(AotMod.MOD_ID, name), item);
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(e -> {
            e.add(ODM_GEAR);
            e.add(BLADE);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(e -> e.add(TITAN_SPAWN_EGG));
    }
}
