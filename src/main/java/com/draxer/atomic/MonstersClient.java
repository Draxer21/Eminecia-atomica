package com.draxer.atomic;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Cliente de los monstruos nuevos. Los humanoides usan el modelo de jugador con skins propias;
 * las bestias tienen modelos propios (cuadrúpedo, murciélago, gólem, bestia encorvada y espectro).
 */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID, value = Dist.CLIENT)
public final class MonstersClient {
    private MonstersClient() {}

    enum Shape { QUADRUPED, BAT, GOLEM, BEAST, WRAITH }

    static ModelLayerLocation layer(Shape shape) {
        return new ModelLayerLocation(Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "creature_" + shape.name().toLowerCase()), "main");
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "textures/entity/monster/" + name + ".png");
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(layer(Shape.QUADRUPED), MonstersClient::quadruped);
        event.registerLayerDefinition(layer(Shape.BAT), MonstersClient::bat);
        event.registerLayerDefinition(layer(Shape.GOLEM), MonstersClient::golem);
        event.registerLayerDefinition(layer(Shape.BEAST), MonstersClient::beast);
        event.registerLayerDefinition(layer(Shape.WRAITH), MonstersClient::wraith);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (var holder : java.util.List.of(Monsters.CHILD_3, Monsters.CHILD_2, Monsters.CHILD_1, Monsters.POSSESSED, Monsters.BANDIT, Monsters.FIGHTER)) {
            String id = holder.getId().getPath();
            float size = holder == Monsters.FIGHTER ? 1.12F : 1.0F;
            event.registerEntityRenderer(holder.value(), c -> new VampireClient.Renderer<>(c, texture(id), size, FigureModel.NONE));
        }
        event.registerEntityRenderer(Monsters.BLOOD_HOUND.value(), c -> new Creature<>(c, Shape.QUADRUPED, false, texture("blood_hound"), 0.5F, 0.9F, false));
        event.registerEntityRenderer(Monsters.CHIMERA.value(), c -> new Creature<>(c, Shape.QUADRUPED, true, texture("cult_chimera"), 0.7F, 1.15F, false));
        event.registerEntityRenderer(Monsters.CHIMERA_ALPHA.value(), c -> new Creature<>(c, Shape.QUADRUPED, true, texture("chimera_alpha"), 0.7F, 1.15F, false));
        event.registerEntityRenderer(Monsters.BLOOD_BAT.value(), c -> new Creature<>(c, Shape.BAT, false, texture("blood_bat"), 0.25F, 1.0F, false));
        event.registerEntityRenderer(Monsters.SLIME_GOLEM.value(), c -> new Creature<>(c, Shape.GOLEM, true, texture("slime_golem"), 0.6F, 1.0F, false));
        event.registerEntityRenderer(Monsters.SHADOW_BEAST.value(), c -> new Creature<>(c, Shape.BEAST, false, texture("shadow_beast"), 0.6F, 1.25F, false));
        event.registerEntityRenderer(Monsters.MANA_WRAITH.value(), c -> new Creature<>(c, Shape.WRAITH, true, texture("mana_wraith"), 0.4F, 1.0F, true));
    }

    /** Renderizador común de las bestias. */
    public static final class Creature<T extends Mob> extends MobRenderer<T, LivingEntityRenderState, CreatureModel> {
        private final Identifier skin;
        private final float size;
        private final boolean glow;

        public Creature(EntityRendererProvider.Context context, Shape shape, boolean variant, Identifier skin, float shadow, float size, boolean glow) {
            super(context, new CreatureModel(context.bakeLayer(layer(shape)), shape, variant), shadow * size);
            this.skin = skin;
            this.size = size;
            this.glow = glow;
        }

        @Override
        public LivingEntityRenderState createRenderState() {
            return new LivingEntityRenderState();
        }

        @Override
        protected void scale(LivingEntityRenderState state, PoseStack poseStack) {
            if (size != 1.0F) poseStack.scale(size, size, size);
        }

        @Override
        protected int getBlockLightLevel(T entity, BlockPos pos) {
            return glow ? 15 : super.getBlockLightLevel(entity, pos);
        }

        @Override
        public Identifier getTextureLocation(LivingEntityRenderState state) {
            return skin;
        }
    }

    // ---------------------------------------------------------------- modelos

    /**
     * Un modelo para cada forma. {@code variant}: en el cuadrúpedo, la quimera (cuernos y
     * cristales en el lomo); en el gólem y el espectro, que se dibujan translúcidos.
     */
    public static final class CreatureModel extends EntityModel<LivingEntityRenderState> {
        private final Shape shape;
        private final ModelPart root;

        public CreatureModel(ModelPart root, Shape shape, boolean variant) {
            super(root, RenderTypes::entityTranslucent);
            this.root = root;
            this.shape = shape;
            if (shape == Shape.QUADRUPED) {
                ModelPart body = root.getChild("body");
                for (int i = 0; i < 3; i++) body.getChild("spike" + i).visible = variant;
                root.getChild("head").getChild("horn_l").visible = variant;
                root.getChild("head").getChild("horn_r").visible = variant;
                root.getChild("head").getChild("ear_l").visible = !variant;
                root.getChild("head").getChild("ear_r").visible = !variant;
            }
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            float pos = state.walkAnimationPos;
            float speed = Math.min(1.0F, state.walkAnimationSpeed);
            float age = state.ageInTicks;
            ModelPart head = root.getChild("head");
            head.yRot = state.yRot * Mth.DEG_TO_RAD;
            head.xRot = state.xRot * Mth.DEG_TO_RAD;
            float swing = Mth.cos(pos * 0.6662F) * 1.4F * speed;
            switch (shape) {
                case QUADRUPED -> {
                    root.getChild("leg0").xRot = swing;
                    root.getChild("leg1").xRot = -swing;
                    root.getChild("leg2").xRot = -swing;
                    root.getChild("leg3").xRot = swing;
                    ModelPart tail = root.getChild("tail");
                    tail.yRot = Mth.sin(age * 0.15F) * 0.35F;
                    head.getChild("jaw").xRot = 0.15F + Mth.sin(age * 0.08F) * 0.1F;
                }
                case BAT -> {
                    float flap = Mth.cos(age * 1.6F) * 0.9F;
                    root.getChild("wing_l").zRot = flap;
                    root.getChild("wing_r").zRot = -flap;
                    root.getChild("body").y = 20.0F + Mth.sin(age * 0.3F) * 0.6F;
                }
                case GOLEM -> {
                    root.getChild("leg_l").xRot = swing * 0.6F;
                    root.getChild("leg_r").xRot = -swing * 0.6F;
                    root.getChild("arm_l").xRot = -swing * 0.5F;
                    root.getChild("arm_r").xRot = swing * 0.5F;
                    ModelPart body = root.getChild("body");
                    float squish = Mth.sin(age * 0.2F) * 0.04F;
                    body.yScale = 1.0F + squish;
                    body.xScale = 1.0F - squish * 0.5F;
                    body.zScale = 1.0F - squish * 0.5F;
                    body.getChild("core").yRot = age * 0.08F;
                }
                case BEAST -> {
                    root.getChild("leg_l").xRot = swing;
                    root.getChild("leg_r").xRot = -swing;
                    root.getChild("arm_l").xRot = -swing * 0.9F - 0.15F;
                    root.getChild("arm_r").xRot = swing * 0.9F - 0.15F;
                    head.getChild("jaw").xRot = 0.2F + Mth.sin(age * 0.1F) * 0.15F;
                }
                case WRAITH -> {
                    root.getChild("arm_l").xRot = -0.9F + Mth.sin(age * 0.1F) * 0.15F;
                    root.getChild("arm_r").xRot = -0.9F - Mth.sin(age * 0.1F) * 0.15F;
                    root.getChild("arm_l").zRot = -0.2F;
                    root.getChild("arm_r").zRot = 0.2F;
                    ModelPart tail = root.getChild("body").getChild("tail");
                    tail.xRot = 0.2F + Mth.sin(age * 0.1F) * 0.2F;
                    tail.getChild("tail2").xRot = 0.3F + Mth.sin(age * 0.1F + 1.0F) * 0.3F;
                    root.getChild("head").getChild("hood").visible = true;
                }
            }
        }
    }

    private static CubeListBuilder box(int u, int v, float x, float y, float z, float w, float h, float d) {
        return CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d);
    }

    /** Sabueso / quimera: cuerpo largo, cabeza con hocico y mandíbula, cuatro patas, cola, cuernos y cristales. */
    static LayerDefinition quadruped() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", box(0, 0, -4, -3.5F, -7, 8, 7, 14), PartPose.offset(0, 12, 0));
        for (int i = 0; i < 3; i++) {
            body.addOrReplaceChild("spike" + i, box(32, 36, -1, -4, -1, 2, 4, 2),
                    PartPose.offsetAndRotation(0, -3.0F, -4 + i * 4, 0.25F - i * 0.25F, 0, 0));
        }
        PartDefinition head = root.addOrReplaceChild("head", box(0, 22, -3, -3, -6, 6, 6, 6), PartPose.offset(0, 10, -7));
        head.addOrReplaceChild("snout", box(24, 22, -1.5F, -1.5F, -9, 3, 2, 3), PartPose.ZERO);
        head.addOrReplaceChild("jaw", box(44, 22, -1.5F, 0, -3, 3, 1, 3), PartPose.offset(0, 0.5F, -6));
        head.addOrReplaceChild("horn_l", box(36, 22, -3, -6, -3, 1, 3, 1), PartPose.offsetAndRotation(0, 0, 0, -0.4F, 0, -0.2F));
        head.addOrReplaceChild("horn_r", box(40, 22, 2, -6, -3, 1, 3, 1), PartPose.offsetAndRotation(0, 0, 0, -0.4F, 0, 0.2F));
        head.addOrReplaceChild("ear_l", box(36, 26, -3, -5, -2, 2, 2, 1), PartPose.ZERO);
        head.addOrReplaceChild("ear_r", box(42, 26, 1, -5, -2, 2, 2, 1), PartPose.ZERO);
        float[][] legs = {{-2.5F, -5}, {2.5F, -5}, {-2.5F, 5}, {2.5F, 5}};
        for (int i = 0; i < 4; i++) {
            root.addOrReplaceChild("leg" + i, box(0, 36, -1.5F, 0, -1.5F, 3, 8, 3), PartPose.offset(legs[i][0], 16, legs[i][1]));
        }
        root.addOrReplaceChild("tail", box(12, 36, -1, -1, 0, 2, 2, 8), PartPose.offsetAndRotation(0, 10, 7, 0.5F, 0, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Murciélago de sangre: cuerpo, cabeza con orejas y dos alas grandes. */
    static LayerDefinition bat() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", box(0, 0, -2, -2, -3, 4, 4, 6), PartPose.offset(0, 20, 0));
        PartDefinition head = root.addOrReplaceChild("head", box(0, 10, -2, -2, -3, 4, 4, 3), PartPose.offset(0, 19, -3));
        head.addOrReplaceChild("ear_l", box(14, 10, -2, -4, -2, 1, 2, 1), PartPose.ZERO);
        head.addOrReplaceChild("ear_r", box(18, 10, 1, -4, -2, 1, 2, 1), PartPose.ZERO);
        root.addOrReplaceChild("wing_l", box(0, 18, 0, 0, -3, 10, 1, 6), PartPose.offset(2, 19, 0));
        root.addOrReplaceChild("wing_r", box(0, 26, -10, 0, -3, 10, 1, 6), PartPose.offset(-2, 19, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Gólem de limo: un bloque de limo con brazos, piernas y un núcleo que se ve por dentro. */
    static LayerDefinition golem() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", box(0, 0, -6, -6, -5, 12, 12, 10), PartPose.offset(0, 10, 0));
        body.addOrReplaceChild("core", box(0, 38, -2, -2, -2, 4, 4, 4), PartPose.ZERO);
        root.addOrReplaceChild("head", box(0, 22, -4, -6, -4, 8, 6, 8), PartPose.offset(0, 4, 0));
        root.addOrReplaceChild("arm_l", box(32, 22, 0, 0, -2, 4, 12, 4), PartPose.offset(6, 5, 0));
        root.addOrReplaceChild("arm_r", box(32, 22, -4, 0, -2, 4, 12, 4), PartPose.offset(-6, 5, 0));
        root.addOrReplaceChild("leg_l", box(48, 22, -2, 0, -2, 4, 8, 4), PartPose.offset(3, 16, 0));
        root.addOrReplaceChild("leg_r", box(48, 22, -2, 0, -2, 4, 8, 4), PartPose.offset(-3, 16, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Bestia de sombra: encorvada, cabeza con mandíbula y cuernos, brazos largos que tocan el suelo. */
    static LayerDefinition beast() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", box(0, 0, -4, 0, -2.5F, 8, 12, 5), PartPose.offsetAndRotation(0, 5, -1, 0.45F, 0, 0));
        PartDefinition head = root.addOrReplaceChild("head", box(0, 17, -3.5F, -6, -6, 7, 6, 7), PartPose.offset(0, 5, -2));
        head.addOrReplaceChild("jaw", box(28, 17, -3, 0, -6, 6, 2, 6), PartPose.ZERO);
        head.addOrReplaceChild("horn_l", box(52, 17, -3, -9, -1, 1, 3, 1), PartPose.offsetAndRotation(0, 0, 0, -0.5F, 0, -0.3F));
        head.addOrReplaceChild("horn_r", box(56, 17, 2, -9, -1, 1, 3, 1), PartPose.offsetAndRotation(0, 0, 0, -0.5F, 0, 0.3F));
        root.addOrReplaceChild("arm_l", box(0, 30, -1.5F, 0, -1.5F, 3, 18, 3), PartPose.offset(5.5F, 6, -2));
        root.addOrReplaceChild("arm_r", box(0, 30, -1.5F, 0, -1.5F, 3, 18, 3), PartPose.offset(-5.5F, 6, -2));
        root.addOrReplaceChild("leg_l", box(12, 30, -1.5F, 0, -1.5F, 3, 10, 3), PartPose.offset(2.5F, 14, 4));
        root.addOrReplaceChild("leg_r", box(12, 30, -1.5F, 0, -1.5F, 3, 10, 3), PartPose.offset(-2.5F, 14, 4));
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Espectro de maná: cabeza encapuchada, torso, brazos que buscan y una cola de energía. */
    static LayerDefinition wraith() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", box(0, 0, -4, -8, -4, 8, 8, 8), PartPose.offset(0, 4, 0));
        head.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(32, 0).addBox(-4, -8, -4, 8, 8, 8, new CubeDeformation(0.6F)), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", box(0, 16, -4, 0, -2.5F, 8, 10, 5), PartPose.offset(0, 4, 0));
        PartDefinition tail = body.addOrReplaceChild("tail", box(26, 16, -3, 0, -2, 6, 7, 4), PartPose.offset(0, 10, 0));
        tail.addOrReplaceChild("tail2", box(46, 16, -1.5F, 0, -1, 3, 6, 2), PartPose.offset(0, 7, 0));
        root.addOrReplaceChild("arm_l", box(0, 31, -1.5F, 0, -1.5F, 3, 11, 3), PartPose.offset(5.5F, 5, 0));
        root.addOrReplaceChild("arm_r", box(0, 31, -1.5F, 0, -1.5F, 3, 11, 3), PartPose.offset(-5.5F, 5, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }
}
