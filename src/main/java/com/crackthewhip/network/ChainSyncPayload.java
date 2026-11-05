package com.crackthewhip.network;

import com.crackthewhip.CrackTheWhip;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Optional;

/** Сервер → клиент: сущность entityId прикована к holderId (или к блоку anchor). linked=false — цепь снята. */
public record ChainSyncPayload(int entityId, int holderId, Optional<BlockPos> anchor, boolean linked)
        implements CustomPacketPayload {

    public static final Type<ChainSyncPayload> TYPE = new Type<>(CrackTheWhip.id("chain_sync"));

    public static final StreamCodec<ByteBuf, ChainSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChainSyncPayload::entityId,
            ByteBufCodecs.VAR_INT, ChainSyncPayload::holderId,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), ChainSyncPayload::anchor,
            ByteBufCodecs.BOOL, ChainSyncPayload::linked,
            ChainSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
