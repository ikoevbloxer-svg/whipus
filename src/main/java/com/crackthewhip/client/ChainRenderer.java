package com.crackthewhip.client;

import com.crackthewhip.CrackTheWhip;
import com.crackthewhip.chain.ChainManager;
import com.crackthewhip.network.ChainClientState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.Map;

/** Рисует железные цепи между пленником и тем, кто его держит (или блоком-якорем). */
@EventBusSubscriber(modid = CrackTheWhip.MODID, value = Dist.CLIENT)
public final class ChainRenderer {
    private ChainRenderer() {}

    private static final float WIDTH = 0.045F;
    // тёмное и светлое железо — чередуются, создавая «звенья»
    private static final float[] DARK = {0.20F, 0.21F, 0.24F};
    private static final float[] LIGHT = {0.58F, 0.60F, 0.66F};

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (ChainClientState.LINKS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        for (Map.Entry<Integer, ChainClientState.Link> entry : ChainClientState.LINKS.entrySet()) {
            Entity chained = level.getEntity(entry.getKey());
            if (chained == null || chained.isRemoved()) continue;
            ChainClientState.Link link = entry.getValue();

            Vec3 start = chained.getPosition(pt).add(0, chained.getBbHeight() * 0.55, 0);
            Vec3 end;
            if (link.holderId() >= 0) {
                Entity holder = level.getEntity(link.holderId());
                if (holder == null) continue;
                end = holder.getRopeHoldPosition(pt);
            } else if (link.anchor() != null) {
                end = anchorPoint(link.anchor(), start);
            } else {
                continue;
            }

            pose.pushPose();
            drawChain(pose, buffers, level, start, end, cam);
            pose.popPose();
        }
    }

    /** Точка крепления на поверхности блока, обращённой к пленнику. */
    private static Vec3 anchorPoint(BlockPos pos, Vec3 from) {
        Vec3 c = Vec3.atCenterOf(pos);
        Vec3 d = from.subtract(c);
        double m = Math.max(Math.abs(d.x), Math.max(Math.abs(d.y), Math.abs(d.z)));
        return m > 0.5 ? c.add(d.scale(0.5 / m)) : c;
    }

    private static void drawChain(PoseStack pose, MultiBufferSource.BufferSource buffers, ClientLevel level,
                                  Vec3 start, Vec3 end, Vec3 cam) {
        Vec3 delta = end.subtract(start);
        double len = delta.length();
        if (len < 1.0E-3) return;
        Vec3 dir = delta.scale(1.0 / len);

        // провисание: тем больше, чем слабее натянута цепь
        double sag = Math.max(0.0, ChainManager.SLACK - len) * 0.18 + 0.05;

        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-4) side = new Vec3(1, 0, 0);
        side = side.normalize().scale(WIDTH);
        Vec3 up = side.cross(dir).normalize().scale(WIDTH);

        int segments = Mth.clamp((int) (len * 5), 8, 160);
        Matrix4f m = pose.last().pose();

        strip(buffers, m, level, start, delta, sag, side, segments, cam, false);
        strip(buffers, m, level, start, delta, sag, up, segments, cam, true);
    }

    private static void strip(MultiBufferSource.BufferSource buffers, Matrix4f m, ClientLevel level,
                              Vec3 start, Vec3 delta, double sag, Vec3 offset, int segments, Vec3 cam, boolean shift) {
        VertexConsumer vc = buffers.getBuffer(RenderType.leash());
        for (int i = 0; i <= segments; i++) {
            float f = i / (float) segments;
            Vec3 p = start.add(delta.scale(f)).add(0, -sag * 4.0 * f * (1.0 - f), 0);
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(p));
            float[] col = ((i % 2 == 0) ^ shift) ? LIGHT : DARK;
            float x = (float) (p.x - cam.x), y = (float) (p.y - cam.y), z = (float) (p.z - cam.z);
            vc.addVertex(m, x - (float) offset.x, y - (float) offset.y, z - (float) offset.z)
                    .setColor(col[0], col[1], col[2], 1.0F).setLight(light);
            vc.addVertex(m, x + (float) offset.x, y + (float) offset.y, z + (float) offset.z)
                    .setColor(col[0], col[1], col[2], 1.0F).setLight(light);
        }
        // каждую ленту рисуем отдельным батчем, чтобы triangle strip'ы разных цепей не склеивались
        buffers.endBatch(RenderType.leash());
    }
}
