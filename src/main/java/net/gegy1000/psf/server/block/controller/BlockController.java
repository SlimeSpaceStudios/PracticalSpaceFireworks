package net.gegy1000.psf.server.block.controller;

import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.gegy1000.psf.PracticalSpaceFireworks;
import net.gegy1000.psf.api.IModule;
import net.gegy1000.psf.api.ISatellite;
import net.gegy1000.psf.server.api.RegisterItemBlock;
import net.gegy1000.psf.server.api.RegisterItemModel;
import net.gegy1000.psf.server.api.RegisterTileEntity;
import net.gegy1000.psf.server.block.controller.TileController.ScanValue;
import net.gegy1000.psf.server.block.remote.packet.PacketCraftState;
import net.gegy1000.psf.server.block.remote.packet.PacketOpenRemoteControl.SatelliteState;
import net.gegy1000.psf.server.capability.CapabilitySatellite;
import net.gegy1000.psf.server.entity.spacecraft.EntitySpacecraft;
import net.gegy1000.psf.server.network.PSFNetworkHandler;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockController extends Block implements RegisterItemBlock, RegisterItemModel, RegisterTileEntity {
    
    public static final @Nonnull IProperty<ControllerType> TYPE = PropertyEnum.create("type", ControllerType.class);
    public static final @Nonnull IProperty<EnumFacing> DIRECTION = PropertyEnum.create("facing", EnumFacing.class);
    
    private final ControllerType type;

    public BlockController(ControllerType type) {
        super(Material.IRON);
        this.type = type;
        this.setCreativeTab(PracticalSpaceFireworks.TAB);
    }
    
    @Override
    protected @Nonnull BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, TYPE, DIRECTION);
    }
    
    @Override
    public boolean onBlockActivated(@Nonnull World worldIn, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nonnull EntityPlayer playerIn, @Nonnull EnumHand hand, @Nonnull EnumFacing facing, float hitX, float hitY, float hitZ) {
        TileEntity te = worldIn.getTileEntity(pos);
        if (te instanceof TileController) {
            ISatellite satellite = te.getCapability(CapabilitySatellite.INSTANCE, null);
            if (!worldIn.isRemote) {
                Map<BlockPos, ScanValue> modules = ((TileController) te).scanStructure();

                EntitySpacecraft spacecraft = new EntitySpacecraft(worldIn, modules.keySet(), pos, satellite.getId());
                ISatellite newsat = spacecraft.getCapability(CapabilitySatellite.INSTANCE, null);
                newsat.setName(satellite.getName());
                satellite.getTrackingPlayers().forEach(newsat::track);
                
                ((TileController) te).converted();

                modules.keySet().forEach(p -> worldIn.setBlockState(p, Blocks.AIR.getDefaultState(), 10));

                spacecraft.setPositionAndRotation(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180, 0);
                worldIn.spawnEntity(spacecraft);
                
                for (EntityPlayerMP player : newsat.getTrackingPlayers()) {
                    PSFNetworkHandler.network.sendTo(new PacketCraftState(SatelliteState.ORBIT, newsat.toListedCraft()), player);
                }
            }
            return true;
        }
        return super.onBlockActivated(worldIn, pos, state, playerIn, hand, facing, hitX, hitY, hitZ);
    }
    
    @Override
    public @Nonnull IBlockState getStateForPlacement(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull EnumFacing facing, float hitX, float hitY, float hitZ, int meta, @Nonnull EntityLivingBase placer, @Nonnull EnumHand hand) {
        return super.getStateForPlacement(world, pos, facing, hitX, hitY, hitZ, meta, placer, hand).withProperty(DIRECTION, facing);
    }

    @Override
    public boolean hasTileEntity(@Nonnull IBlockState state) {
        return true;
    }
    
    @Override
    @Nullable
    public TileEntity createTileEntity(@Nonnull World world, @Nonnull IBlockState state) {
        return new TileController();
    }
    
    @Override
    public int getMetaFromState(@Nonnull IBlockState state) {
        return state.getValue(DIRECTION).ordinal();
    }
    
    @Override
    public @Nonnull IBlockState getStateFromMeta(int meta) {
        meta = Math.abs(meta) % EnumFacing.values().length;
        return getDefaultState().withProperty(DIRECTION, EnumFacing.values()[meta]);
    }

    @Override
    public Class<? extends TileEntity> getEntityClass() {
        return TileController.class;
    }
}
