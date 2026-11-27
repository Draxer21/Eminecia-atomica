package com.draxer.atomic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Geometría emisiva y de bajo coste para cada haz de Proyección activo. */
public final class ProjectionBeamRenderer extends EntityRenderer<ProjectionBeamEntity, ProjectionBeamRenderer.State> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "textures/misc/projection_beam.png");
    private static final RenderType EMISSIVE = RenderTypes.entityTranslucentEmissive(TEXTURE, false);
    private static final RenderType DISCHARGE = RenderTypes.lightning();
    private static final double CLEAR_START = 2.5;

    public ProjectionBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.shadowStrength = 0.0F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ProjectionBeamEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.delta = entity.beamDelta();
        state.radius = entity.beamRadius();
        state.channel = entity.isChannel();
        state.impact = entity.hasImpact();
        state.impactTick = entity.impactTick();
    }

    @Override
    public boolean shouldRender(ProjectionBeamEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return super.shouldRender(entity, frustum, camX, camY, camZ);
    }

    @Override
    protected AABB getBoundingBoxForCulling(ProjectionBeamEntity entity) {
        Vec3 from = entity.position();
        Vec3 to = from.add(entity.beamDelta());
        return new AABB(
                Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z),
                Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z))
                .inflate(entity.beamRadius() * 2.0 + 0.5);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Vec3 delta = state.delta;
        double length = delta.length();
        if (length < 0.01) return;

        ProjectionConfig.Quality quality = ProjectionConfig.quality();
        Vec3 axis = delta.scale(1.0 / length);
        Vec3 source = new Vec3(state.x, state.y, state.z);
        Vec3 view = camera.pos.subtract(source);
        Vec3 sideCandidate = axis.cross(view);
        if (sideCandidate.lengthSqr() < 1.0e-5) {
            Vec3 reference = Math.abs(axis.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            sideCandidate = axis.cross(reference);
        }
        final Vec3 side = sideCandidate.normalize();
        final Vec3 up = axis.cross(side).normalize();
        double startDistance = Math.min(CLEAR_START, length);
        Vec3 start = axis.scale(startDistance);
        double visibleLength = Math.max(0.0, length - startDistance);
        float pulse = (float) (0.88 + 0.12 * Math.sin(state.ageInTicks * 0.32));
        float radius = state.radius * (quality == ProjectionConfig.Quality.CINEMATIC ? 1.18F : 1.0F);
        float phase = state.ageInTicks * 0.075F;

        poseStack.pushPose();
        collector.submitCustomGeometry(poseStack, EMISSIVE, (pose, buffer) -> {
            if (visibleLength > 0.05) {
                int haloPlanes = switch (quality) {
                    case LIGHT -> 1;
                    case NORMAL -> 2;
                    case CINEMATIC -> 4;
                };
                for (int i = 0; i < haloPlanes; i++) {
                    double angle = Math.PI * i / haloPlanes + phase * 0.25;
                    Vec3 across = side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle))).normalize();
                    int alpha = quality == ProjectionConfig.Quality.LIGHT ? 44 : quality == ProjectionConfig.Quality.NORMAL ? 64 : 72;
                    emitRibbon(buffer, pose, start, delta, across, radius * (quality == ProjectionConfig.Quality.LIGHT ? 1.55 : 1.9),
                            phase, 190, 45, 255, (int) (alpha * pulse));
                }

                emitCore(buffer, pose, start, delta, side, up, state.channel ? radius * 0.24 : radius * 0.30,
                        phase, state.channel ? 235 : 250);

                int ringCount = switch (quality) {
                    case LIGHT -> 1;
                    case NORMAL -> 2;
                    case CINEMATIC -> 4;
                };
                int ringSegments = quality == ProjectionConfig.Quality.CINEMATIC ? 20 : quality == ProjectionConfig.Quality.NORMAL ? 16 : 12;
                for (int i = 0; i < ringCount; i++) {
                    double along = visibleLength * (0.34 + (i + 1.0) / (ringCount + 1.0) * 0.54);
                    double wobble = Math.sin(state.ageInTicks * 0.18 + i * 1.7) * radius * 0.18;
                    emitRing(buffer, pose, start.add(axis.scale(along)), axis, side, up,
                            radius * (1.55 + wobble), radius * 0.07, ringSegments, phase + i * 0.11F,
                            205, 98, 255, quality == ProjectionConfig.Quality.LIGHT ? 52 : 74);
                }
            }

            if (state.impact) {
                float impactAge = state.ageInTicks - state.impactTick;
                if (impactAge >= 0 && impactAge < 4) {
                    float fade = 1.0F - impactAge / 4.0F;
                    Vec3 end = delta;
                    double impactRadius = (0.10 + impactAge * 0.045) * (state.channel ? 0.72 : 1.0);
                    emitRing(buffer, pose, end, axis, side, up, impactRadius, 0.018, 16, phase,
                            245, 218, 255, (int) ((state.channel ? 105 : 180) * fade));
                }
            }
        });

        if (visibleLength > 0.05 && quality != ProjectionConfig.Quality.LIGHT) {
            int arcs = quality == ProjectionConfig.Quality.CINEMATIC ? 3 : 1;
            int segments = quality == ProjectionConfig.Quality.CINEMATIC ? 8 : 6;
            collector.submitCustomGeometry(poseStack, DISCHARGE, (pose, buffer) -> {
                for (int arc = 0; arc < arcs; arc++) {
                    emitDischarge(buffer, pose, start, axis, side, up, visibleLength, radius, phase, arc, segments,
                            quality == ProjectionConfig.Quality.CINEMATIC ? 0.35F : 0.25F);
                }
            });
        }
        poseStack.popPose();
    }

    private static void emitRibbon(VertexConsumer buffer, PoseStack.Pose pose, Vec3 start, Vec3 delta,
                                   Vec3 across, double halfWidth, float phase,
                                   int red, int green, int blue, int alpha) {
        Vec3 end = delta;
        float v0 = phase + (float) (start.length() * 0.22);
        float v1 = phase + (float) (delta.length() * 0.22);
        vertex(buffer, pose, start.subtract(across.scale(halfWidth)), 0, v0, red, green, blue, alpha);
        vertex(buffer, pose, start.add(across.scale(halfWidth)), 1, v0, red, green, blue, alpha);
        vertex(buffer, pose, end.add(across.scale(halfWidth)), 1, v1, red, green, blue, alpha);
        vertex(buffer, pose, end.subtract(across.scale(halfWidth)), 0, v1, red, green, blue, alpha);
    }

    private static void emitCore(VertexConsumer buffer, PoseStack.Pose pose, Vec3 start, Vec3 end,
                                 Vec3 side, Vec3 up, double coreRadius, float phase, int alpha) {
        int sides = 8;
        float v0 = phase + (float) (start.length() * 0.32);
        float v1 = phase + (float) (end.length() * 0.32);
        for (int i = 0; i < sides; i++) {
            double a0 = i * Math.PI * 2.0 / sides;
            double a1 = (i + 1) * Math.PI * 2.0 / sides;
            Vec3 radial0 = side.scale(Math.cos(a0)).add(up.scale(Math.sin(a0)));
            Vec3 radial1 = side.scale(Math.cos(a1)).add(up.scale(Math.sin(a1)));
            vertex(buffer, pose, start.add(radial0.scale(coreRadius)), 0.47F, v0, 245, 232, 255, alpha);
            vertex(buffer, pose, end.add(radial0.scale(coreRadius)), 0.47F, v1, 245, 232, 255, alpha);
            vertex(buffer, pose, end.add(radial1.scale(coreRadius)), 0.53F, v1, 245, 232, 255, alpha);
            vertex(buffer, pose, start.add(radial1.scale(coreRadius)), 0.53F, v0, 245, 232, 255, alpha);
        }
    }

    private static void emitRing(VertexConsumer buffer, PoseStack.Pose pose, Vec3 center, Vec3 axis,
                                 Vec3 side, Vec3 up, double radius, double thickness, int segments,
                                 float phase, int red, int green, int blue, int alpha) {
        for (int i = 0; i < segments; i++) {
            double a0 = i * Math.PI * 2.0 / segments + phase;
            double a1 = (i + 1) * Math.PI * 2.0 / segments + phase;
            Vec3 r0 = side.scale(Math.cos(a0)).add(up.scale(Math.sin(a0)));
            Vec3 r1 = side.scale(Math.cos(a1)).add(up.scale(Math.sin(a1)));
            Vec3 outer0 = center.add(r0.scale(radius + thickness));
            Vec3 inner0 = center.add(r0.scale(Math.max(0.01, radius - thickness)));
            Vec3 inner1 = center.add(r1.scale(Math.max(0.01, radius - thickness)));
            Vec3 outer1 = center.add(r1.scale(radius + thickness));
            float u0 = 0.47F + (float) i / segments * 0.06F;
            float u1 = 0.47F + (float) (i + 1) / segments * 0.06F;
            vertex(buffer, pose, outer0, u0, phase, red, green, blue, alpha);
            vertex(buffer, pose, inner0, u0, phase + 0.05F, red, green, blue, alpha);
            vertex(buffer, pose, inner1, u1, phase + 0.05F, red, green, blue, alpha);
            vertex(buffer, pose, outer1, u1, phase, red, green, blue, alpha);
        }
    }

    private static void emitDischarge(VertexConsumer buffer, PoseStack.Pose pose, Vec3 start, Vec3 axis,
                                      Vec3 side, Vec3 up, double length, double radius,
                                      float phase, int arc, int segments, float alpha) {
        Vec3 previous = null;
        Vec3 cameraFacing = side;
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            double angle = phase * 2.8 + arc * 2.1 + t * 17.0;
            double envelope = 0.35 + 0.65 * Math.sin(Math.PI * t);
            double jitter = Math.sin(t * 29.0 + arc * 3.0 + phase * 4.0) * radius * 0.75;
            Vec3 point = start.add(axis.scale(length * t))
                    .add(side.scale(Math.cos(angle) * radius * envelope + jitter))
                    .add(up.scale(Math.sin(angle) * radius * envelope - jitter * 0.45));
            if (previous != null) {
                Vec3 tangent = point.subtract(previous).normalize();
                Vec3 across = tangent.cross(cameraFacing);
                if (across.lengthSqr() < 1.0e-5) across = tangent.cross(up);
                across = across.normalize().scale(0.012);
                int a = (int) (alpha * 255.0F * (0.65 + 0.35 * Math.sin(t * Math.PI)));
                buffer.addVertex(pose, (float) previous.subtract(across).x, (float) previous.subtract(across).y, (float) previous.subtract(across).z).setColor(190, 115, 255, a);
                buffer.addVertex(pose, (float) point.subtract(across).x, (float) point.subtract(across).y, (float) point.subtract(across).z).setColor(218, 184, 255, a);
                buffer.addVertex(pose, (float) point.add(across).x, (float) point.add(across).y, (float) point.add(across).z).setColor(218, 184, 255, a);
                buffer.addVertex(pose, (float) previous.add(across).x, (float) previous.add(across).y, (float) previous.add(across).z).setColor(190, 115, 255, a);
            }
            previous = point;
        }
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 point,
                               float u, float v, int red, int green, int blue, int alpha) {
        buffer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0x00F000F0)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    public static final class State extends EntityRenderState {
        private Vec3 delta = Vec3.ZERO;
        private float radius;
        private boolean channel;
        private boolean impact;
        private int impactTick;
    }
}
