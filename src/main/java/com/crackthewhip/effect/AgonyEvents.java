package com.crackthewhip.effect;

import com.crackthewhip.CrackTheWhip;
import com.crackthewhip.ModRegistry;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

@EventBusSubscriber(modid = CrackTheWhip.MODID)
public final class AgonyEvents {
    private AgonyEvents() {}

    /** Уязвимость: +15% входящего урона за уровень агонии. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        MobEffectInstance agony = event.getEntity().getEffect(ModRegistry.AGONY);
        if (agony == null || event.getSource().is(ModRegistry.AGONY_DAMAGE)) return;
        event.setAmount(event.getAmount() * (1.0F + 0.15F * (agony.getAmplifier() + 1)));
    }

    /** Раны не заживают, пока длится агония. */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (event.getEntity().hasEffect(ModRegistry.AGONY)) {
            event.setCanceled(true);
        }
    }
}
