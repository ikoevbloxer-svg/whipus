package com.crackthewhip.chain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;

import java.util.Optional;
import java.util.UUID;

/** Куда пристёгнута цепь: к сущности (holder) или к блоку (anchor). */
public record ChainLink(Optional<UUID> holder, Optional<GlobalPos> anchor) {
    public static final ChainLink EMPTY = new ChainLink(Optional.empty(), Optional.empty());

    public static final Codec<ChainLink> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.optionalFieldOf("holder").forGetter(ChainLink::holder),
            GlobalPos.CODEC.optionalFieldOf("anchor").forGetter(ChainLink::anchor)
    ).apply(i, ChainLink::new));

    public static ChainLink toEntity(UUID holder) {
        return new ChainLink(Optional.of(holder), Optional.empty());
    }

    public static ChainLink toBlock(GlobalPos pos) {
        return new ChainLink(Optional.empty(), Optional.of(pos));
    }

    public boolean isHeldBy(UUID uuid) {
        return holder.isPresent() && holder.get().equals(uuid);
    }

    public boolean isValid() {
        return holder.isPresent() || anchor.isPresent();
    }
}
