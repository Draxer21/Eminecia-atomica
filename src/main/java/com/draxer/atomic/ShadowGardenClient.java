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
        event.registerEntityRenderer(ShadowGarden.STORY_NPC.value(), NpcRenderer::new);
        event.registerEntityRenderer(ShadowGarden.GUARD.value(), GuardRenderer::new);
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
                        "textures/entity/shadow_garden/" + member.skin + ".png");
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

    public static final class NpcRenderState extends HumanoidRenderState {
        public String character = "alexia";
    }

    /**
     * Personajes con nombre: textura en textures/entity/npc/<id>.png (reemplazable por tu propia skin).
     * Usan el modelo de jugador, así que sirve cualquier skin moderna de 64×64 con brazos clásicos.
     */
    public static final class NpcRenderer
            extends HumanoidMobRenderer<StoryNpcEntity, NpcRenderState, HumanoidModel<NpcRenderState>> {
        public NpcRenderer(EntityRendererProvider.Context context) {
            super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        }

        @Override
        public NpcRenderState createRenderState() {
            return new NpcRenderState();
        }

        @Override
        public void extractRenderState(StoryNpcEntity entity, NpcRenderState state, float partialTicks) {
            super.extractRenderState(entity, state, partialTicks);
            state.character = entity.character().id();
        }

        @Override
        public Identifier getTextureLocation(NpcRenderState state) {
            return Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "textures/entity/npc/" + state.character + ".png");
        }
    }

    public static final class GuardRenderState extends HumanoidRenderState {
        public boolean oriana;
    }

    public static final class GuardRenderer
            extends HumanoidMobRenderer<KingdomGuardEntity, GuardRenderState, HumanoidModel<GuardRenderState>> {
        private static final Identifier MIDGAR = Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "textures/entity/guard/midgar.png");
        private static final Identifier ORIANA = Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "textures/entity/guard/oriana.png");

        public GuardRenderer(EntityRendererProvider.Context context) {
            super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
        }

        @Override
        public GuardRenderState createRenderState() {
            return new GuardRenderState();
        }

        @Override
        public void extractRenderState(KingdomGuardEntity entity, GuardRenderState state, float partialTicks) {
            super.extractRenderState(entity, state, partialTicks);
            state.oriana = entity.kingdom().equals("oriana");
        }

        @Override
        public Identifier getTextureLocation(GuardRenderState state) {
            return state.oriana ? ORIANA : MIDGAR;
        }
    }
}
