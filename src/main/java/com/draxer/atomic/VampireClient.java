package com.draxer.atomic;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Cliente de los vampiros: siervos, Crimson y Elizabeth (un poco más alta). */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID, value = Dist.CLIENT)
public final class VampireClient {
    private VampireClient() {}

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "textures/entity/vampire/" + name + ".png");
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Vampires.SERVANT.value(), c -> new Renderer<>(c, texture("servant"), 1.0F));
        event.registerEntityRenderer(Vampires.CRIMSON.value(), c -> new Renderer<>(c, texture("crimson"), 1.05F));
        event.registerEntityRenderer(Vampires.ELIZABETH.value(), c -> new Renderer<>(c, texture("elizabeth"), 1.1F));
        event.registerEntityRenderer(Vampires.AURORA.value(), c -> new Renderer<>(c,
                Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "textures/entity/npc/claire_aurora.png"), 1.0F));
    }

    public static final class Renderer<T extends Mob> extends HumanoidMobRenderer<T, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
        private final Identifier skin;
        private final float size;

        public Renderer(EntityRendererProvider.Context context, Identifier skin, float size) {
            super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F * size);
            this.skin = skin;
            this.size = size;
        }

        @Override
        public HumanoidRenderState createRenderState() {
            return new HumanoidRenderState();
        }

        @Override
        protected void scale(HumanoidRenderState state, PoseStack poseStack) {
            if (size != 1.0F) poseStack.scale(size, size, size);
        }

        @Override
        public Identifier getTextureLocation(HumanoidRenderState state) {
            return skin;
        }
    }
}
