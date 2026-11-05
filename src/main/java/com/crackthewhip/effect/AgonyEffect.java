package com.crackthewhip.effect;

import com.crackthewhip.CrackTheWhip;
import com.crackthewhip.ModRegistry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.joml.Vector3f;

/**
 * АГОНИЯ.
 *  I.  Пульсирующая боль: урон сквозь броню, тем чаще, чем выше уровень. Агония не убивает — оставляет 1 HP.
 *  II. Скованность: -15% скорости за уровень, -10% урона атак за уровень.
 *  III.Уязвимость: +15% входящего урона за уровень (см. AgonyEvents).
 *  IV. Раны не заживают: любое лечение заблокировано (см. AgonyEvents).
 *  V.  Судороги: тело дёргает в случайную сторону; у игроков — дёргается камера, с III уровня нельзя бежать.
 *  VI. Стук сердца и красная пульсирующая виньетка на экране (клиент).
 */
public class AgonyEffect extends MobEffect {
    public static final int MAX_AMPLIFIER = 4; // Агония V
    private static final Vector3f BLOOD = new Vector3f(0.55f, 0.02f, 0.02f);

    public AgonyEffect() {
        super(MobEffectCategory.HARMFUL, 0x7A0A0A);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, CrackTheWhip.id("agony_slowness"),
                -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.ATTACK_DAMAGE, CrackTheWhip.id("agony_weakness"),
                -0.10, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    public static int pulseInterval(int amplifier) {
        return Math.max(10, 40 - amplifier * 7); // 40, 33, 26, 19, 12 тиков
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true; // каждый тик: судороги считаются по шансу, боль — по интервалу
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity.level() instanceof ServerLevel level)) return true;
        RandomSource rand = entity.getRandom();

        // --- Пульс боли ---
        if (entity.tickCount % pulseInterval(amplifier) == 0) {
            if (entity.getHealth() > 1.0F) {
                float dmg = Math.min(1.0F + amplifier * 0.25F, entity.getHealth() - 1.0F);
                DamageSource src = new DamageSource(level.registryAccess()
                        .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ModRegistry.AGONY_DAMAGE));
                entity.hurt(src, dmg);
            }
            level.playSound(null, entity.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE,
                    0.7F + amplifier * 0.1F, 1.1F + amplifier * 0.08F);
            level.sendParticles(new DustParticleOptions(BLOOD, 1.2F),
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
                    6 + amplifier * 3, entity.getBbWidth() * 0.4, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.4, 0.0);
        }

        // --- Судороги ---
        if (rand.nextFloat() < 0.03F + amplifier * 0.02F && entity.onGround()) {
            double s = 0.18 + amplifier * 0.06;
            entity.push((rand.nextDouble() - 0.5) * s * 2, 0.08 + rand.nextDouble() * 0.1, (rand.nextDouble() - 0.5) * s * 2);
            entity.hurtMarked = true;
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.8, entity.getZ(),
                    1 + amplifier / 2, 0.2, 0.1, 0.2, 0.05);
        }
        return true;
    }
}
