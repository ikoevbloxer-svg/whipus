package com.crackthewhip;

import com.crackthewhip.chain.ChainLink;
import com.crackthewhip.effect.AgonyEffect;
import com.crackthewhip.item.ShackleChainItem;
import com.crackthewhip.item.WhipItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModRegistry {
    private ModRegistry() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CrackTheWhip.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, CrackTheWhip.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CrackTheWhip.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CrackTheWhip.MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CrackTheWhip.MODID);

    // ---- Sounds ----
    public static final DeferredHolder<SoundEvent, SoundEvent> WHIP_CRACK =
            SOUNDS.register("whip_crack", () -> SoundEvent.createVariableRangeEvent(CrackTheWhip.id("whip_crack")));

    // ---- Effects ----
    public static final DeferredHolder<MobEffect, AgonyEffect> AGONY = EFFECTS.register("agony", AgonyEffect::new);

    // ---- Items ----
    public static final DeferredItem<WhipItem> WHIP = ITEMS.register("whip",
            () -> new WhipItem(new Item.Properties()
                    .durability(640)
                    .rarity(Rarity.UNCOMMON)
                    .attributes(WhipItem.createAttributes())));

    public static final DeferredItem<ShackleChainItem> SHACKLE_CHAIN = ITEMS.register("shackle_chain",
            () -> new ShackleChainItem(new Item.Properties()
                    .stacksTo(16)
                    .rarity(Rarity.UNCOMMON)
                    .fireResistant()));

    // ---- Creative tab ----
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.crackthewhip"))
                    .icon(() -> new ItemStack(WHIP.get()))
                    .displayItems((params, out) -> {
                        out.accept(WHIP.get());
                        out.accept(SHACKLE_CHAIN.get());
                    })
                    .build());

    // ---- Data attachment: who/what an entity is chained to (saved to disk, survives death) ----
    public static final Supplier<AttachmentType<ChainLink>> CHAIN = ATTACHMENTS.register("chain",
            () -> AttachmentType.builder(() -> ChainLink.EMPTY)
                    .serialize(ChainLink.CODEC)
                    .copyOnDeath()
                    .build());

    // ---- Damage type (data-driven, see data/crackthewhip/damage_type/agony.json) ----
    public static final ResourceKey<DamageType> AGONY_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, CrackTheWhip.id("agony"));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        EFFECTS.register(bus);
        SOUNDS.register(bus);
        TABS.register(bus);
        ATTACHMENTS.register(bus);
    }
}
