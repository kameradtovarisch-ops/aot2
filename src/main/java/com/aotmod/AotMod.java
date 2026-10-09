package com.aotmod;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;

public class AotMod implements ModInitializer {
    public static final String MOD_ID = "aotmod";
    public static final Identifier INPUT_PACKET = new Identifier(MOD_ID, "input");

    // Runtime toggles. Change with /aot trees <true|false> and /aot spawns <true|false>.
    // They reset to these defaults when the server restarts.
    public static boolean fallingTrees = false;   // hooking a natural tree makes it topple toward you
    public static boolean naturalTitans = true;   // rare natural titan spawns in plains/meadow/forest

    @Override
    public void onInitialize() {
        ModEntities.register();
        ModItems.register();
        OdmManager.register();
        ModCommands.register();
    }
}
