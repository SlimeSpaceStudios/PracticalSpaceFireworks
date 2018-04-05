package net.gegy1000.psf.server.satellite;

import lombok.Setter;
import net.gegy1000.psf.api.IController;
import net.gegy1000.psf.api.IModule;
import net.gegy1000.psf.api.ISatellite;
import net.gegy1000.psf.server.block.PSFBlockRegistry;
import net.gegy1000.psf.server.block.remote.IListedSpacecraft;
import net.gegy1000.psf.server.block.remote.entity.EntityListedSpacecraft;
import net.gegy1000.psf.server.entity.spacecraft.EntitySpacecraft;
import net.gegy1000.psf.server.entity.spacecraft.PacketLaunchCraft;
import net.gegy1000.psf.server.entity.spacecraft.SpacecraftBlockAccess;
import net.gegy1000.psf.server.network.PSFNetworkHandler;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class EntityBoundSatellite extends AbstractSatellite {

    private final EntitySpacecraft spacecraft;
    private UUID uuid;

    private final List<IModule> modules = new ArrayList<>();
    private IController controller;
    @Setter
    @Nonnull
    private String name = "";

    public EntityBoundSatellite(EntitySpacecraft spacecraft, UUID uuid) {
        this.spacecraft = spacecraft;
        this.uuid = uuid;
    }

    public void detectModules() {
        SpacecraftBlockAccess blockAccess = this.spacecraft.getBlockAccess();

        this.modules.clear();
        this.modules.addAll(blockAccess.findModules());
        this.modules.forEach(module -> module.setOwner(this));

        this.controller = blockAccess.findController();
    }

    @Override
    public String getName() {
        return name.isEmpty() ? super.getName() : name;
    }

    @Override
    public UUID getId() {
        return this.uuid;
    }

    @Override
    public IController getController() {
        return this.controller;
    }

    @Override
    public Collection<IModule> getModules() {
        return this.modules;
    }

    @Override
    public boolean isInvalid() {
        return !spacecraft.isEntityAlive();
    }

    @Override
    public BlockPos getPosition() {
        return this.spacecraft.getPosition();
    }

    @Override
    public SpacecraftBlockAccess buildBlockAccess(World world) {
        return this.spacecraft.getBlockAccess();
    }

    @Override
    public IListedSpacecraft toListedCraft() {
        return new EntityListedSpacecraft(spacecraft, uuid);
    }

    @Override
    public World getWorld() {
        return spacecraft.getEntityWorld();
    }

    @Override
    public boolean canLaunch() {
        return true;
    }

    @Override
    public void launch() {
        PSFNetworkHandler.network.sendToServer(new PacketLaunchCraft(spacecraft.getEntityId()));
    }

    @Override
    public int hashCode() {
        return this.getId().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ISatellite && ((ISatellite) obj).getId().equals(this.getId());
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound tag = super.serializeNBT();
        tag.setString("name", name);
        tag.setUniqueId("uuid", uuid);
        return tag;
    }

    @Override
    public void deserializeNBT(@Nullable NBTTagCompound tag) {
        super.deserializeNBT(tag);
        if (tag != null) {
            this.name = tag.getString("name");
            this.uuid = tag.getUniqueId("uuid");
        }
    }

    public ISatellite toOrbiting() {
        SpacecraftBlockAccess blockAccess = this.spacecraft.getBlockAccess();
        SpacecraftBlockAccess[] split = blockAccess.splitVertically(getWorld(), PSFBlockRegistry.payloadSeparator);
        SpacecraftBlockAccess topPart = split[split.length - 1];
        return new OrbitingSatellite(this.getWorld(), this.name, this.getId(), this.getPosition(), topPart, getTrackingPlayers());
    }
}
