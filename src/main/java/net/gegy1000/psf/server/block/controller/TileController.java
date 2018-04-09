package net.gegy1000.psf.server.block.controller;

import lombok.Value;
import net.gegy1000.psf.PracticalSpaceFireworks;
import net.gegy1000.psf.api.IModule;
import net.gegy1000.psf.api.ISatellite;
import net.gegy1000.psf.server.capability.CapabilityController;
import net.gegy1000.psf.server.capability.CapabilityModule;
import net.gegy1000.psf.server.capability.CapabilitySatellite;
import net.gegy1000.psf.server.modules.ModuleController;
import net.gegy1000.psf.server.modules.Modules;
import net.gegy1000.psf.server.satellite.TileBoundSatellite;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

public class TileController extends TileEntity implements ITickable {

    @Value
    private class ScanValue {
        IBlockState state;
        IModule module;
    }

    @Nonnull
    private final ISatellite satellite = new TileBoundSatellite(this, "Unnamed Craft #" + hashCode() % 1000);
    private final ModuleController controller = (ModuleController) Modules.get().getValue(new ResourceLocation(PracticalSpaceFireworks.MODID, "controller_simple")).get();

    private boolean converted;

    private final CraftGraph craft = new CraftGraph(satellite);
    
    // A cache of the saved positions of the graph
    // Used to make sure the controller doesn't scan beyond its saved state during world load
    // Always null after onLoad()
    private Set<BlockPos> structureLimits;

    @Override
    public void update() {
        if (!world.isRemote) {
            satellite.tickSatellite(getWorld().getTotalWorldTime());
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!getWorld().isRemote) {
            PracticalSpaceFireworks.PROXY.getSatellites().register(satellite);
            scanStructure();
        }
        controller.setPos(getPos());
        structureLimits = null;
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        unregister();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        unregister();
    }

    private void unregister() {
        if (!getWorld().isRemote && !converted) {
            PracticalSpaceFireworks.PROXY.getSatellites().remove(satellite);
        }
    }

    public void converted() {
        this.converted = true;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == CapabilitySatellite.INSTANCE ||
                capability == CapabilityController.INSTANCE ||
                capability == CapabilityModule.INSTANCE ||
                super.hasCapability(capability, facing);
    }

    @Override
    @Nullable
    public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilitySatellite.INSTANCE) {
            return CapabilitySatellite.INSTANCE.cast(satellite);
        }
        if (capability == CapabilityController.INSTANCE) {
            return CapabilityController.INSTANCE.cast(controller);
        }
        if (capability == CapabilityModule.INSTANCE) {
            return CapabilityModule.INSTANCE.cast(controller);
        }
        return super.getCapability(capability, facing);
    }

    public CraftGraph getModules() {
        return craft;
    }

    @Override
    @Nullable
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(getPos(), 0, getUpdateTag());
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeSyncData(new NBTTagCompound());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        handleUpdateTag(pkt.getNbtCompound());
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        readSyncData(tag);
    }

    @Override
    public NBTTagCompound writeToNBT(@Nonnull NBTTagCompound compound) {
        compound = writeSyncData(compound);

        // Adjacencies are not serialized, so when the controller is converted to entity and back, the list becomes
        // empty. We still aren't scanning for them as we are in entity form, so they remain so until deconstruction.
        if (!craft.isEmpty()) {
            NBTTagList connectedTag = new NBTTagList();
            for (BlockPos pos : craft.getPositions()) {
                connectedTag.appendTag(new NBTTagLong(pos.toLong()));
            }
            compound.setTag("connected_blocks", connectedTag);
        }
        return compound;
    }

    @Override
    public void readFromNBT(@Nonnull NBTTagCompound compound) {
        readSyncData(compound);

        if (compound.hasKey("connected_blocks")) {
            NBTTagList list = compound.getTagList("connected_blocks", Constants.NBT.TAG_LONG);
            structureLimits = new HashSet<>();
            for (NBTBase tag : list) {
                structureLimits.add(BlockPos.fromLong(((NBTTagLong)tag).getLong()));
            }
        }
    }

    private NBTTagCompound writeSyncData(@Nonnull NBTTagCompound compound) {
        compound = super.writeToNBT(compound);

        compound.setTag("satellite_data", satellite.serializeNBT());
        compound.setTag("module_data", controller.serializeNBT());

        return compound;
    }

    private void readSyncData(@Nonnull NBTTagCompound compound) {
        super.readFromNBT(compound);
        satellite.deserializeNBT(compound.getCompoundTag("satellite_data"));
        controller.deserializeNBT(compound.getCompoundTag("module_data"));
        controller.setOwner(satellite);
    }

    public void scanStructure() {
        if (structureLimits == null) {
            craft.scan(getPos(), getWorld());
        } else {
            craft.scan(getPos(), getWorld(), d -> structureLimits.contains(d.getPos()));
        }
        markDirty();
    }
}
