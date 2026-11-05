package com.crackthewhip.chain;

import com.crackthewhip.ModRegistry;
import com.crackthewhip.network.ChainSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

public final class ChainManager {
    private ChainManager() {}

    /** Длина цепи: дальше этого начинает тянуть. */
    public static final double SLACK = 6.0;
    /** Если жертву унесло дальше (эндер-жемчуг, /tp, смерть) — цепь возвращает её. Цепь не рвётся никогда. */
    public static final double SNAP_BACK = 24.0;
    public static final double SEARCH_RADIUS = SNAP_BACK + 16.0;

    /** Какой holderId последний раз отправлен клиентам (новый объект сущности → пересылка). */
    private static final Map<Entity, Integer> LAST_SENT = new WeakHashMap<>();

    // ------------------------------------------------------------------ state

    @Nullable
    public static ChainLink get(Entity e) {
        if (!e.hasData(ModRegistry.CHAIN)) return null;
        ChainLink link = e.getData(ModRegistry.CHAIN);
        return link.isValid() ? link : null;
    }

    public static boolean isChained(Entity e) {
        return get(e) != null;
    }

    public static void chainTo(Entity target, Entity holder) {
        target.setData(ModRegistry.CHAIN, ChainLink.toEntity(holder.getUUID()));
        if (target instanceof Mob mob) mob.setPersistenceRequired();
        sync(target);
    }

    public static void anchorTo(Entity target, Level level, BlockPos pos) {
        target.setData(ModRegistry.CHAIN, ChainLink.toBlock(GlobalPos.of(level.dimension(), pos.immutable())));
        if (target instanceof Mob mob) {
            mob.setPersistenceRequired();
            mob.getNavigation().stop();
        }
        sync(target);
    }

    public static void release(Entity target, boolean dropItem) {
        target.removeData(ModRegistry.CHAIN);
        if (dropItem && !target.level().isClientSide()) {
            target.spawnAtLocation(new ItemStack(ModRegistry.SHACKLE_CHAIN.get()));
        }
        target.level().playSound(null, target.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F);
        sync(target);
    }

    public static List<Entity> heldBy(Player player) {
        return player.level().getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(SEARCH_RADIUS),
                e -> e != player && isHeld(e, player));
    }

    private static boolean isHeld(Entity e, Player player) {
        ChainLink link = get(e);
        return link != null && link.isHeldBy(player.getUUID());
    }

    public static List<Entity> anchoredAt(Level level, BlockPos pos) {
        GlobalPos gp = GlobalPos.of(level.dimension(), pos);
        return level.getEntitiesOfClass(Entity.class, new net.minecraft.world.phys.AABB(pos).inflate(SEARCH_RADIUS), e -> {
            ChainLink link = get(e);
            return link != null && link.anchor().isPresent() && link.anchor().get().equals(gp);
        });
    }

    // ------------------------------------------------------------------ sync

    public static ChainSyncPayload payloadFor(Entity e) {
        ChainLink link = get(e);
        if (link == null) return new ChainSyncPayload(e.getId(), -1, Optional.empty(), false);
        int holderId = -1;
        if (link.holder().isPresent() && e.level() instanceof ServerLevel sl) {
            Entity h = sl.getEntity(link.holder().get());
            if (h != null) holderId = h.getId();
        }
        Optional<BlockPos> anchor = link.anchor()
                .filter(gp -> gp.dimension().equals(e.level().dimension()))
                .map(GlobalPos::pos);
        return new ChainSyncPayload(e.getId(), holderId, anchor, true);
    }

    public static void sync(Entity e) {
        if (e.level().isClientSide()) return;
        ChainSyncPayload payload = payloadFor(e);
        LAST_SENT.put(e, payload.linked() ? payload.holderId() : Integer.MIN_VALUE);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, payload);
    }

    public static void syncTo(Entity e, ServerPlayer player) {
        if (isChained(e)) PacketDistributor.sendToPlayer(player, payloadFor(e));
    }

    // ------------------------------------------------------------------ physics

    public static void tick(Entity e) {
        if (!(e.level() instanceof ServerLevel level)) return;
        ChainLink link = get(e);
        if (link == null) return;

        Vec3 target = null;
        BlockPos anchorPos = null;
        int holderId = -1;

        if (link.holder().isPresent()) {
            Entity holder = level.getEntity(link.holder().get());
            if (holder != null && holder.isAlive()) {
                target = holder.position().add(0, holder.getBbHeight() * 0.5, 0);
                holderId = holder.getId();
            }
        } else if (link.anchor().isPresent() && link.anchor().get().dimension().equals(level.dimension())) {
            anchorPos = link.anchor().get().pos();
            if (e.tickCount % 20 == 0 && level.isLoaded(anchorPos) && level.getBlockState(anchorPos).isAir()) {
                // блок, к которому приковали, сломан — цепь падает на землю (сама цепь цела)
                release(e, true);
                return;
            }
            target = Vec3.atCenterOf(anchorPos);
        }

        Integer last = LAST_SENT.get(e);
        if (last == null || last != holderId) sync(e);

        if (target == null || e.isPassenger()) return;

        Vec3 center = e.position().add(0, e.getBbHeight() * 0.5, 0);
        Vec3 toTarget = target.subtract(center);
        double dist = toTarget.length();

        if (dist > SNAP_BACK) {
            snapBack(e, level, target, anchorPos);
            return;
        }

        if (dist > SLACK) {
            Vec3 dir = toTarget.scale(1.0 / dist);
            Vec3 vel = e.getDeltaMovement();
            double outward = -vel.dot(dir);
            if (outward > 0) vel = vel.add(dir.scale(outward)); // гасим движение «от цепи»
            double pull = Math.min((dist - SLACK) * 0.14, 1.2);
            vel = vel.add(dir.scale(pull));
            e.setDeltaMovement(vel);
            e.hasImpulse = true;
            e.hurtMarked = true; // для игроков: отправить скорость клиенту
            e.resetFallDistance();
        }

        if (e instanceof Mob mob && anchorPos == null && dist > SLACK * 0.6 && e.tickCount % 10 == 0) {
            mob.getNavigation().moveTo(target.x, target.y, target.z, 1.1);
        }
    }

    private static void snapBack(Entity e, ServerLevel level, Vec3 target, @Nullable BlockPos anchor) {
        Vec3 dest = target.subtract(0, e.getBbHeight() * 0.5, 0);
        if (anchor != null) {
            dest = Vec3.atBottomCenterOf(anchor.above());
            for (Direction d : Direction.values()) {
                Vec3 cand = Vec3.atBottomCenterOf(anchor.relative(d));
                if (level.noCollision(e, e.getDimensions(e.getPose()).makeBoundingBox(cand))) {
                    dest = cand;
                    break;
                }
            }
        }
        e.teleportTo(dest.x, dest.y, dest.z);
        e.setDeltaMovement(Vec3.ZERO);
        e.resetFallDistance();
        level.playSound(null, e.blockPosition(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 1.2F, 0.6F);
    }

    public static void forget(Entity e) {
        LAST_SENT.remove(e);
    }
}
