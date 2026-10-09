package com.aotmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import org.lwjgl.glfw.GLFW;

public class AotModClient implements ClientModInitializer {
    private static int lastFlags = 0;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.TITAN, TitanRenderer::new);

        KeyBinding hookKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.aotmod.hook", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.aotmod"));
        KeyBinding boostKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.aotmod.boost", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.aotmod"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                lastFlags = 0;
                return;
            }
            int flags = 0;
            if (client.currentScreen == null) {
                if (hookKey.isPressed()) flags |= 1;
                if (boostKey.isPressed()) flags |= 2;
            }
            if (flags != lastFlags) {
                lastFlags = flags;
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeByte(flags);
                ClientPlayNetworking.send(AotMod.INPUT_PACKET, buf);
            }
        });
    }
}
