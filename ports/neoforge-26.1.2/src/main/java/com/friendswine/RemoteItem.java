package com.friendswine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
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
        if (level.isClientSide()) return;
        CustomData.update(DataComponents.CUSTOM_DATA, player.getItemInHand(hand), tag -> {
            CompoundTag target = new CompoundTag();
            target.putString("Dimension", level.dimension().identifier().toString());
            target.putLong("Pos", pos.asLong());
            tag.put(TARGET, target);
        });
        player.sendOverlayMessage(Component.translatable("message.friendswine.remote_bound",
                pos.getX(), pos.getY(), pos.getZ()));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof DollBlockEntity) {
            bind(context.getLevel(), context.getClickedPos(), player, context.getHand());
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isUsingItem()) return InteractionResult.CONSUME;
        // Vanilla's use latch prevents a held button from cycling every right-click delay.
        player.startUsingItem(hand);
        if (!level.isClientSide()) {
            CompoundTag target = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompoundOrEmpty(TARGET);
            String message;
            if (!target.getString("Dimension").isPresent() || !target.getLong("Pos").isPresent()) {
                message = "message.friendswine.remote_unbound";
            } else if (!target.getStringOr("Dimension", "").equals(level.dimension().identifier().toString())) {
                message = "message.friendswine.remote_dimension";
            } else {
                BlockPos pos = BlockPos.of(target.getLongOr("Pos", 0));
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
            player.sendOverlayMessage(Component.translatable(message));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }
}
