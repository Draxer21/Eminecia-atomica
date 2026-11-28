package com.draxer.atomic;

import com.mojang.blaze3d.vertex.PoseStack;
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

/** Cliente de la Culto de Diablos: cultistas y el Caballero de la Mesa Redonda (más grande). */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID, value = Dist.CLIENT)
public final class CultClient {
    private CultClient() {}

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "textures/entity/diablos_cult/" + name + ".png");
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Cult.CULTIST.value(), CultistRenderer::new);
        event.registerEntityRenderer(Cult.KNIGHT.value(), KnightRenderer::new);
    }

    public static final class CultistRenderState extends HumanoidRenderState {
        public boolean mage;
    }

    public static final class CultistRenderer
            extends HumanoidMobRenderer<CultistEntity, CultistRenderState, HumanoidModel<CultistRenderState>> {
        private static final Identifier KNIGHT = texture("knight");
        private static final Identifier MAGE = texture("mage");

        public CultistRenderer(EntityRendererProvider.Context context) {
            super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
        }

        @Override
        public CultistRenderState createRenderState() {
            return new CultistRenderState();
        }

        @Override
        public void extractRenderState(CultistEntity entity, CultistRenderState state, float partialTicks) {
            super.extractRenderState(entity, state, partialTicks);
            state.mage = entity.kind() == CultistEntity.Kind.MAGE;
        }

        @Override
        public Identifier getTextureLocation(CultistRenderState state) {
            return state.mage ? MAGE : KNIGHT;
        }
    }

    public static final class KnightRenderState extends HumanoidRenderState {
        public boolean awakened;
        public boolean voidRank;
        public int phase;
    }

    public static final class KnightRenderer
            extends HumanoidMobRenderer<RoundsKnightEntity, KnightRenderState, HumanoidModel<KnightRenderState>> {
        private static final Identifier KNIGHT = texture("round_knight");
        private static final Identifier AWAKENED = texture("round_knight_awakened");
        private static final Identifier VOID = texture("void_knight");
        private static final Identifier VOID_AWAKENED = texture("void_knight_awakened");

        public KnightRenderer(EntityRendererProvider.Context context) {
            super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.8F);
        }

        @Override
        public KnightRenderState createRenderState() {
            return new KnightRenderState();
        }

        @Override
        public void extractRenderState(RoundsKnightEntity entity, KnightRenderState state, float partialTicks) {
            super.extractRenderState(entity, state, partialTicks);
            state.awakened = entity.phase() >= 2;
            state.voidRank = entity.rank() == RoundsKnightEntity.Rank.VOID;
            state.phase = entity.phase();
        }

        @Override
        protected void scale(KnightRenderState state, PoseStack poseStack) {
            float s = state.voidRank ? (state.phase >= 3 ? 1.8F : 1.65F) : 1.5F;
            poseStack.scale(s, s, s);
        }

        @Override
        public Identifier getTextureLocation(KnightRenderState state) {
            if (state.voidRank) return state.awakened ? VOID_AWAKENED : VOID;
            return state.awakened ? AWAKENED : KNIGHT;
        }
    }
}
