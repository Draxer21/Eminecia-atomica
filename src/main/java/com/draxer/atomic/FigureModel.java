package com.draxer.atomic;

import java.util.function.Predicate;
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

    private final ModelPart bust;
    private final Predicate<S> female;

    public FigureModel(ModelPart root, Predicate<S> female) {
        super(root);
        this.bust = root.getChild("body").getChild("bust");
        this.female = female;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
        mesh.getRoot().getChild("body").addOrReplaceChild("bust",
                CubeListBuilder.create().texOffs(18, 18).addBox(-4.0F, 2.0F, -4.0F, 8.0F, 4.0F, 2.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(S state) {
        super.setupAnim(state);
        bust.visible = female.test(state);
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
