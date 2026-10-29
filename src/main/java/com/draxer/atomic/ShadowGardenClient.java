package com.draxer.atomic;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Cliente de la Shadow Garden: cada integrante usa el modelo humanoide con su propia textura. */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID, value = Dist.CLIENT)
public final class ShadowGardenClient {
    private ShadowGardenClient() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ShadowGarden.ALLY.value(), AllyRenderer::new);
    }

    public static final class AllyRenderState extends HumanoidRenderState {
        public int member;
    }

    public static final class AllyRenderer
            extends HumanoidMobRenderer<ShadowAllyEntity, AllyRenderState, HumanoidModel<AllyRenderState>> {
        private static final Identifier[] TEXTURES = new Identifier[ShadowGarden.Member.values().length];

        static {
            for (ShadowGarden.Member member : ShadowGarden.Member.values()) {
                TEXTURES[member.ordinal()] = Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID,
                        "textures/entity/shadow_garden/" + member.id + ".png");
            }
        }

        public AllyRenderer(EntityRendererProvider.Context context) {
            super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
        }

        @Override
        public AllyRenderState createRenderState() {
            return new AllyRenderState();
        }

        @Override
        public void extractRenderState(ShadowAllyEntity entity, AllyRenderState state, float partialTicks) {
            super.extractRenderState(entity, state, partialTicks);
            state.member = entity.member().ordinal();
        }

        @Override
        public Identifier getTextureLocation(AllyRenderState state) {
            return TEXTURES[Math.floorMod(state.member, TEXTURES.length)];
        }
    }
}
