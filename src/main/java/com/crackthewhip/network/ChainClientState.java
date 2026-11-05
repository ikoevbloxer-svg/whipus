package com.crackthewhip.network;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Клиентская копия цепей (без клиентских импортов — безопасно грузится на сервере). */
public final class ChainClientState {
    private ChainClientState() {}

    public record Link(int holderId, @Nullable BlockPos anchor) {}

    public static final Map<Integer, Link> LINKS = new ConcurrentHashMap<>();

    public static void handle(ChainSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.linked()) {
                LINKS.put(payload.entityId(), new Link(payload.holderId(), payload.anchor().orElse(null)));
            } else {
                LINKS.remove(payload.entityId());
            }
        });
    }

    public static boolean isChained(int entityId) {
        return LINKS.containsKey(entityId);
    }

    public static boolean holdsAny(int holderId) {
        for (Link l : LINKS.values()) if (l.holderId() == holderId) return true;
        return false;
    }

    public static boolean anchoredAt(BlockPos pos) {
        for (Link l : LINKS.values()) if (pos.equals(l.anchor())) return true;
        return false;
    }

    public static void remove(int entityId) {
        LINKS.remove(entityId);
    }

    public static void clear() {
        LINKS.clear();
    }
}
