package com.aotmod;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.BiomeKeys;

public class ModEntities {
    public static final EntityType<TitanEntity> TITAN = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(AotMod.MOD_ID, "titan"),
            FabricEntityTypeBuilder.<TitanEntity>createMob()
                    .spawnGroup(SpawnGroup.MONSTER)
                    .entityFactory(TitanEntity::new)
                    .spawnRestriction(SpawnRestriction.Location.ON_GROUND,
                            Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, TitanEntity::canTitanSpawn)
                    .dimensions(EntityDimensions.fixed(4.0f, 15.0f))
                    .trackRangeBlocks(256)
                    .build());

    public static void register() {
        FabricDefaultAttributeRegistry.register(TITAN, TitanEntity.createTitanAttributes());
        BiomeModifications.addSpawn(
                BiomeSelectors.includeByKey(BiomeKeys.PLAINS, BiomeKeys.SUNFLOWER_PLAINS, BiomeKeys.MEADOW, BiomeKeys.FOREST),
                SpawnGroup.MONSTER, TITAN, 1, 1, 1);
    }
}
