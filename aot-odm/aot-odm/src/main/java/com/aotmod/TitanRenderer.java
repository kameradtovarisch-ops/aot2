package com.aotmod;

import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class TitanRenderer extends BipedEntityRenderer<TitanEntity, BipedEntityModel<TitanEntity>> {
    private static final Identifier TEXTURE = new Identifier(AotMod.MOD_ID, "textures/entity/titan.png");

    public TitanRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new BipedEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE)), 4.0f);
    }

    @Override
    protected void scale(TitanEntity entity, MatrixStack matrices, float amount) {
        matrices.scale(7.5f, 7.5f, 7.5f); // 2 block model * 7.5 = 15 blocks
    }

    @Override
    public Identifier getTexture(TitanEntity entity) {
        return TEXTURE;
    }
}
