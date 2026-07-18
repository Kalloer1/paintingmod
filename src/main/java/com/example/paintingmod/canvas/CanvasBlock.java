package com.example.paintingmod.canvas;

import com.example.paintingmod.config.ModConfig;
import com.example.paintingmod.network.CanvasOpenPacket;
import com.example.paintingmod.network.ModPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A thin paint-paper that can be attached to any solid block face. Right-click opens
 * the drawing GUI. (The handheld paint-paper item was removed; the canvas lives on
 * this block, which is crafted directly from paper + a stick.)
 */
public class CanvasBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    // 2 px thick boxes centered on each face
    private static final VoxelShape SHAPE_DOWN = box(0, 0, 0, 16, 2, 16);
    private static final VoxelShape SHAPE_UP = box(0, 14, 0, 16, 16, 16);
    private static final VoxelShape SHAPE_NORTH = box(0, 0, 14, 16, 16, 16);
    private static final VoxelShape SHAPE_SOUTH = box(0, 0, 0, 16, 16, 2);
    private static final VoxelShape SHAPE_WEST = box(14, 0, 0, 16, 16, 16);
    private static final VoxelShape SHAPE_EAST = box(0, 0, 0, 2, 16, 16);

    public CanvasBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.SOUTH));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CanvasBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        return this.defaultBlockState().setValue(FACING, face.getOpposite());
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        // Always survive so the canvas can be mounted anywhere the player clicks (like a
        // painting) instead of popping off when there is no perfect "center support".
        return true;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case DOWN -> SHAPE_DOWN;
            case UP -> SHAPE_UP;
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof CanvasBlockEntity be) {
            int size = Math.max(16, Math.min(ModConfig.maxCanvasSize.get(), ModConfig.defaultCanvasSize.get()));
            be.initSize(size, size);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        openGui(level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        openGui(level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void openGui(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) return;
        if (level.getBlockEntity(pos) instanceof CanvasBlockEntity be && player instanceof ServerPlayer sp) {
            ModPackets.sendToPlayer(CanvasOpenPacket.fromBlockEntity(be), sp);
        }
    }
}
