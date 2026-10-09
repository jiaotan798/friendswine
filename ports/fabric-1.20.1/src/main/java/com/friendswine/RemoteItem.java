package com.friendswine;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class RemoteItem extends Item {
    private static final String TARGET = "FriendsWineTarget";

    public RemoteItem(Properties properties) {
        super(properties);
    }

    static void bind(Level level, BlockPos pos, Player player, InteractionHand hand) {
        if (player.isUsingItem()) return;
        player.startUsingItem(hand);
        if (level.isClientSide) return;
        CompoundTag tag = player.getItemInHand(hand).getOrCreateTag();
        {
            CompoundTag target = new CompoundTag();
            target.putString("Dimension", level.dimension().location().toString());
            target.putLong("Pos", pos.asLong());
            tag.put(TARGET, target);
        }
        player.displayClientMessage(Component.translatable("message.friendswine.remote_bound",
                pos.getX(), pos.getY(), pos.getZ()), true);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof DollBlockEntity) {
            bind(context.getLevel(), context.getClickedPos(), player, context.getHand());
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isUsingItem()) return InteractionResultHolder.consume(stack);
        // Vanilla's use latch prevents a held button from cycling every right-click delay.
        player.startUsingItem(hand);
        if (!level.isClientSide) {
            CompoundTag target = stack.hasTag() ? stack.getTag().getCompound(TARGET) : new CompoundTag();
            String message;
            if (!target.contains("Dimension", Tag.TAG_STRING) || !target.contains("Pos", Tag.TAG_LONG)) {
                message = "message.friendswine.remote_unbound";
            } else if (!target.getString("Dimension").equals(level.dimension().location().toString())) {
                message = "message.friendswine.remote_dimension";
            } else {
                BlockPos pos = BlockPos.of(target.getLong("Pos"));
                // Test chunk availability before reading the block entity: no chunk tickets or implicit loads.
                if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof DollBlockEntity doll) {
                    doll.cycleRemote();
                    message = switch (doll.getMode()) {
                        case DollBlockEntity.SQUASH_ONLY -> "message.friendswine.remote_squash";
                        case DollBlockEntity.FULL -> "message.friendswine.remote_full";
                        case DollBlockEntity.ORBIT -> "message.friendswine.remote_orbit";
                        default -> "message.friendswine.remote_stopped";
                    };
                } else {
                    message = "message.friendswine.remote_unavailable";
                }
            }
            player.displayClientMessage(Component.translatable(message), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }
}
