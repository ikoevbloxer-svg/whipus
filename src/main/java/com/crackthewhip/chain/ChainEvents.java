package com.crackthewhip.chain;

import com.crackthewhip.CrackTheWhip;
import com.crackthewhip.ModRegistry;
import com.crackthewhip.network.ChainClientState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.List;

/**
 * Управление:
 *  ПКМ цепью по мобу/игроку          — заковать (цепь в твоей руке).
 *  ПКМ цепью по блоку                — приковать к блоку всех, кого ведёшь.
 *  ПКМ пустой рукой по блоку-якорю   — снять пленников с блока и снова вести их.
 *  ПКМ пустой рукой по прикованному  — забрать его с блока себе.
 *  Shift+ПКМ пустой рукой/цепью      — расковать (свой пленник или прикованный к блоку), цепь выпадает.
 */
@EventBusSubscriber(modid = CrackTheWhip.MODID)
public final class ChainEvents {
    private ChainEvents() {}

    private static boolean isChain(ItemStack stack) {
        return stack.is(ModRegistry.SHACKLE_CHAIN.get());
    }

    private static void consume(PlayerInteractEvent event) {
        if (event instanceof PlayerInteractEvent.EntityInteract ei) {
            ei.setCanceled(true);
            ei.setCancellationResult(InteractionResult.SUCCESS);
        } else if (event instanceof PlayerInteractEvent.RightClickBlock rb) {
            rb.setCanceled(true);
            rb.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private static void say(Player p, String key, Object... args) {
        p.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.GRAY), true);
    }

    // ------------------------------------------------------------ click on entity

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Player player = event.getEntity();
        Entity target = event.getTarget();
        ItemStack stack = event.getItemStack();
        boolean client = event.getLevel().isClientSide();
        boolean chainInHand = isChain(stack);

        boolean targetChained = client ? ChainClientState.isChained(target.getId()) : ChainManager.isChained(target);

        if (targetChained) {
            if (!chainInHand && !stack.isEmpty()) return;
            consume(event);
            if (client) return;

            ChainLink link = ChainManager.get(target);
            boolean mine = link.isHeldBy(player.getUUID());
            boolean anchored = link.anchor().isPresent();

            if (player.isShiftKeyDown()) {
                if (mine || anchored || player.getAbilities().instabuild) {
                    ChainManager.release(target, true);
                    say(player, "message.crackthewhip.released", target.getDisplayName());
                    if (target instanceof ServerPlayer sp) say(sp, "message.crackthewhip.you_are_free");
                } else {
                    say(player, "message.crackthewhip.not_yours");
                }
            } else if (anchored) {
                ChainManager.chainTo(target, player);
                playChain(player.level(), target.blockPosition(), SoundEvents.CHAIN_PLACE);
                say(player, "message.crackthewhip.taken", target.getDisplayName());
            } else if (!mine) {
                say(player, "message.crackthewhip.not_yours");
            }
            return;
        }

        if (!chainInHand || !(target instanceof LivingEntity) || target == player) return;
        consume(event);
        if (client) return;

        // нельзя заковать того, кто держит на цепи тебя самого
        ChainLink own = ChainManager.get(player);
        if (own != null && own.isHeldBy(target.getUUID())) {
            say(player, "message.crackthewhip.cant");
            return;
        }

        ChainManager.chainTo(target, player);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        playChain(player.level(), target.blockPosition(), SoundEvents.CHAIN_PLACE);
        say(player, "message.crackthewhip.chained", target.getDisplayName());
        if (target instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.crackthewhip.you_are_chained", player.getDisplayName())
                    .withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    // ------------------------------------------------------------ click on block

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        BlockPos pos = event.getPos();
        Level level = event.getLevel();
        boolean client = level.isClientSide();

        if (isChain(stack)) {
            boolean holds = client ? ChainClientState.holdsAny(player.getId()) : !ChainManager.heldBy(player).isEmpty();
            if (!holds) return;
            consume(event);
            if (client) return;
            List<Entity> held = ChainManager.heldBy(player);
            for (Entity e : held) ChainManager.anchorTo(e, level, pos);
            playChain(level, pos, SoundEvents.CHAIN_PLACE);
            playChain(level, pos, SoundEvents.ANVIL_PLACE);
            say(player, "message.crackthewhip.anchored", held.size());
        } else if (stack.isEmpty()) {
            boolean any = client ? ChainClientState.anchoredAt(pos) : !ChainManager.anchoredAt(level, pos).isEmpty();
            if (!any) return;
            consume(event);
            if (client) return;
            List<Entity> list = ChainManager.anchoredAt(level, pos);
            for (Entity e : list) ChainManager.chainTo(e, player);
            playChain(level, pos, SoundEvents.CHAIN_BREAK);
            say(player, "message.crackthewhip.unanchored", list.size());
        }
    }

    private static void playChain(Level level, BlockPos pos, net.minecraft.sounds.SoundEvent sound) {
        level.playSound(null, pos, sound, SoundSource.PLAYERS, 1.0F, 0.9F + level.random.nextFloat() * 0.2F);
    }

    // ------------------------------------------------------------ lifecycle

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        ChainManager.tick(event.getEntity());
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            ChainManager.syncTo(event.getTarget(), sp);
        }
    }

    /** Мобы при смерти роняют цепь. Игроки остаются закованными и после смерти — цепь неразрушима. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (!e.level().isClientSide() && !(e instanceof Player) && ChainManager.isChained(e)) {
            ChainManager.release(e, true);
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            ChainClientState.remove(event.getEntity().getId());
        } else {
            ChainManager.forget(event.getEntity());
        }
    }
}
