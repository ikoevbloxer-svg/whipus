package com.crackthewhip.client;

import com.crackthewhip.CrackTheWhip;
import com.crackthewhip.ModRegistry;
import com.crackthewhip.effect.AgonyEffect;
import com.crackthewhip.network.ChainClientState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class AgonyClientEffects {
    private AgonyClientEffects() {}

    /** Игровые события клиента: судороги камеры, запрет бега, очистка цепей при выходе. */
    @EventBusSubscriber(modid = CrackTheWhip.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer p = mc.player;
            if (p == null || mc.isPaused()) return;
            MobEffectInstance agony = p.getEffect(ModRegistry.AGONY);
            if (agony == null) return;
            int amp = agony.getAmplifier();
            RandomSource r = p.getRandom();

            // судорога — камеру резко дёргает
            if (r.nextFloat() < 0.035F + 0.025F * amp) {
                float s = 2.5F + 1.75F * amp;
                p.setYRot(p.getYRot() + (r.nextFloat() - 0.5F) * s * 2.0F);
                p.setXRot(Mth.clamp(p.getXRot() + (r.nextFloat() - 0.5F) * s, -90.0F, 90.0F));
            }
            // с Агонии III бежать невозможно
            if (amp >= 2 && p.isSprinting()) p.setSprinting(false);
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            ChainClientState.clear();
        }
    }

    /** Красная пульсирующая виньетка в такт сердцебиению. */
    @EventBusSubscriber(modid = CrackTheWhip.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void registerLayers(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, CrackTheWhip.id("agony_vignette"),
                    AgonyClientEffects::renderVignette);
        }
    }

    private static void renderVignette(GuiGraphics gg, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        MobEffectInstance agony = mc.player.getEffect(ModRegistry.AGONY);
        if (agony == null) return;
        int amp = agony.getAmplifier();

        float time = mc.player.tickCount + delta.getGameTimeDeltaPartialTick(false);
        float interval = AgonyEffect.pulseInterval(amp);
        float phase = (time % interval) / interval;              // 0..1 в такт пульсу боли
        float beat = (float) Math.exp(-phase * 7.0F);            // резкий удар и затухание

        // затухание в последние 2 секунды эффекта
        float fade = agony.getDuration() < 40 ? agony.getDuration() / 40.0F : 1.0F;
        float alpha = Mth.clamp((0.22F + 0.07F * amp + 0.35F * beat) * fade, 0.0F, 0.8F);

        int w = gg.guiWidth(), h = gg.guiHeight();
        int border = (int) (Math.min(w, h) * (0.16F + 0.035F * amp + 0.05F * beat));
        int steps = 20;
        for (int i = 0; i < steps; i++) {
            float t = 1.0F - i / (float) steps;
            int a = (int) (alpha * t * t * 255.0F);
            if (a <= 0) continue;
            int color = (a << 24) | 0x7A0000;
            int o1 = border * i / steps;
            int o2 = border * (i + 1) / steps;
            gg.fill(0, o1, w, o2, color);                 // верх
            gg.fill(0, h - o2, w, h - o1, color);         // низ
            gg.fill(o1, o2, o2, h - o2, color);           // лево
            gg.fill(w - o2, o2, w - o1, h - o2, color);   // право
        }
    }
}
