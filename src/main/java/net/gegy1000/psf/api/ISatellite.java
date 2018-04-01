package net.gegy1000.psf.api;

import net.gegy1000.psf.server.entity.spacecraft.SpacecraftBlockAccess;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.INBTSerializable;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@ParametersAreNonnullByDefault
public interface ISatellite extends INBTSerializable<NBTTagCompound> {

    default String getName() {
        return "Unnamed Craft #" + hashCode() % 1000;
    }
    
    default void setName(String name) {}
    
    UUID getId();

    IController getController();

    Collection<IModule> getModules();

    default <T> Collection<T> getModuleCaps(Capability<T> capability) {
        List<T> caps = new ArrayList<>();
        for (IModule module : this.getModules()) {
            if (module.hasCapability(capability, null)) {
                caps.add(module.getCapability(capability, null));
            }
        }
        return caps;
    }

    BlockPos getPosition();

    SpacecraftBlockAccess buildBlockAccess(BlockPos origin, World world);

    void requestModules();

    World getWorld();

    @Override
    default NBTTagCompound serializeNBT() {
        return new NBTTagCompound();
    }
    
    @Override
    default void deserializeNBT(@Nullable NBTTagCompound tag) {}

    default void tickSatellite(int ticksExisted) {
        for (IModule module : getModules()) {
            if (ticksExisted % module.getTickInterval() == 0) {
                module.onSatelliteTick(this);
            }
        }
    }
}
