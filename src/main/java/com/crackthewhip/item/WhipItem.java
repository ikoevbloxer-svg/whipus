package com.crackthewhip.item;

import com.crackthewhip.CrackTheWhip;
import com.crackthewhip.ModRegistry;
import com.crackthewhip.effect.AgonyEffect;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WhipItem extends Item {
    public static final double LASH_RANGE = 7.0;
    public static final int LASH_COOLDOWN = 24;

    public WhipItem(Properties properties) {
        super(properties);
    }

    /** 2 урона (1 сердце), медленный замах, +2.5 блока дальности удара. */
    public static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 1.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.2, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(CrackTheWhip.id("whip_reach"), 2.5, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    /** Накладывает/усиливает агонию: каждый удар +1 уровень (до V), продлевает длительность. */
    public static void inflictAgony(LivingEntity target, @Nullable LivingEntity source) {
        MobEffectInstance current = target.getEffect(ModRegistry.AGONY);
        int amp = current == null ? 0 : Math.min(current.getAmplifier() + 1, AgonyEffect.MAX_AMPLIFIER);
        int duration = 120 + amp * 40; // 6 — 14 секунд
        target.addEffect(new MobEffectInstance(ModRegistry.AGONY, duration, amp, false, true, true), source);
    }

    public static void playCrack(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, ModRegistry.WHIP_CRACK.get(), SoundSource.PLAYERS,
                1.0F, 0.9F + level.random.nextFloat() * 0.25F);
    }

    // ---- Обычный удар (ЛКМ) ----
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        inflictAgony(target, attacker);
        playCrack(attacker.level(), target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ());
        if (attacker.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    10, 0.3, 0.3, 0.3, 0.3);
        }
        return true;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
    }

    // ---- Щелчок хлыстом (ПКМ): бьёт на 7 блоков и подтягивает жертву к тебе ----
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.getCooldowns().addCooldown(this, LASH_COOLDOWN);

        if (level instanceof ServerLevel sl) {
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getViewVector(1.0F);
            Vec3 end = eye.add(look.scale(LASH_RANGE));

            BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (blockHit.getType() != HitResult.Type.MISS) end = blockHit.getLocation();

            AABB box = player.getBoundingBox().expandTowards(look.scale(LASH_RANGE)).inflate(1.0);
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                    e -> e instanceof LivingEntity && e != player && !e.isSpectator() && e.isPickable(),
                    eye.distanceToSqr(end));

            Vec3 tip = hit != null ? hit.getLocation() : end;
            // след хлыста
            Vec3 delta = tip.subtract(eye);
            for (int i = 2; i <= 14; i++) {
                Vec3 p = eye.add(delta.scale(i / 14.0)).add(0, -0.25 * Math.sin(Math.PI * i / 14.0), 0);
                sl.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
            sl.sendParticles(ParticleTypes.CRIT, tip.x, tip.y, tip.z, 8, 0.15, 0.15, 0.15, 0.25);
            playCrack(level, tip.x, tip.y, tip.z);

            if (hit != null && hit.getEntity() instanceof LivingEntity target) {
                target.hurt(player.damageSources().playerAttack(player), 1.5F);
                inflictAgony(target, player);
                Vec3 pull = eye.subtract(target.position()).normalize().scale(0.6);
                target.push(pull.x, 0.3, pull.z);
                target.hurtMarked = true;
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public int getEnchantmentValue() {
        return 15;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.crackthewhip.whip.1").withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("tooltip.crackthewhip.whip.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.crackthewhip.whip.3").withStyle(ChatFormatting.GRAY));
    }
}
