package com.draxer.atomic;

import java.util.function.ToIntFunction;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Modelo de jugador (skins modernas de 64×64) con un relieve en el pecho para los personajes
 * femeninos, al estilo de Minecraft Comes Alive. El relieve usa la parte de arriba del frente
 * del torso de la skin, así que toma el color de la ropa.
 */
public class FigureModel<S extends HumanoidRenderState> extends HumanoidModel<S> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "figure"), "main");

    /** Sin relieve. */
    public static final int NONE = 0;
    /** Relieve normal, inclinado como en MCA. */
    public static final int NORMAL = 1;
    /** Relieve más grande (solo para personajes adultos). */
    public static final int LARGE = 2;

    private final ModelPart bust;
    private final ModelPart bustLarge;
    private final ToIntFunction<S> size;

    public FigureModel(ModelPart root, ToIntFunction<S> size) {
        super(root);
        this.bust = root.getChild("body").getChild("bust");
        this.bustLarge = root.getChild("body").getChild("bust_large");
        this.size = size;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
        // Inclinados hacia abajo y afuera, como en Minecraft Comes Alive.
        mesh.getRoot().getChild("body").addOrReplaceChild("bust",
                CubeListBuilder.create().texOffs(18, 18).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 4.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 1.5F, -1.9F, -0.35F, 0.0F, 0.0F));
        mesh.getRoot().getChild("body").addOrReplaceChild("bust_large",
                CubeListBuilder.create().texOffs(17, 17).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 5.0F, 3.0F),
                PartPose.offsetAndRotation(0.0F, 1.2F, -1.9F, -0.45F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(S state) {
        super.setupAnim(state);
        int s = size.applyAsInt(state);
        bust.visible = s == NORMAL;
        bustLarge.visible = s == LARGE;
    }

    @EventBusSubscriber(modid = DraxAtomic.MOD_ID, value = Dist.CLIENT)
    public static final class Layers {
        private Layers() {}

        @SubscribeEvent
        public static void register(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(LAYER, FigureModel::createLayer);
        }
    }
}
