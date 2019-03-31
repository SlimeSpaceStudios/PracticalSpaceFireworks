package net.gegy1000.psf;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static net.gegy1000.psf.PracticalSpaceFireworks.DEPENDENCIES;
import static net.gegy1000.psf.PracticalSpaceFireworks.MODID;
import static net.gegy1000.psf.PracticalSpaceFireworks.NAME;
import static net.gegy1000.psf.PracticalSpaceFireworks.VERSION;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import net.gegy1000.psf.server.ServerProxy;
import net.gegy1000.psf.server.init.PSFBlocks;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.datafix.FixTypes;
import net.minecraft.util.datafix.IFixableData;
import net.minecraftforge.common.util.ModFixs;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;

@Mod(modid = MODID, name = NAME, version = VERSION, dependencies = DEPENDENCIES, acceptedMinecraftVersions = "[1.12]")
@ParametersAreNonnullByDefault
public class PracticalSpaceFireworks {
    @Nonnull
    public static final String MODID = "psf";
    public static final String VERSION = "0.2.0";
    public static final String NAME = "Practical Space Fireworks";
    public static final String DEPENDENCIES = "required-after:forge@[14.23.5.2807,15);required-after-client:ctm@[MC1.12.2-0.3.2.18,)";

    public static final String CLIENT_PROXY = "net.gegy1000.psf.client.ClientProxy";
    public static final String SERVER_PROXY = "net.gegy1000.psf.server.ServerProxy";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    private static final ModFixs DATA_FIXER = FMLCommonHandler.instance().getDataFixer().init(MODID, 1);

    @SidedProxy(clientSide = CLIENT_PROXY, serverSide = SERVER_PROXY)
    public static ServerProxy PROXY;
    
    @Instance
    public static PracticalSpaceFireworks instance;

    private static boolean deobfuscatedEnvironment;

    @Nonnull
    public static final CreativeTabs TAB = new CreativeTabs(MODID) {
        @Override
        public @Nonnull ItemStack createIcon() {
            //noinspection ConstantConditions
            return new ItemStack(PSFBlocks.STRUT_CUBE);
        }
    };

    static {
        FluidRegistry.enableUniversalBucket();
    }

    public static boolean isDeobfuscatedEnvironment() {
        return deobfuscatedEnvironment;
    }
    
    public static ResourceLocation namespace(final String path) {
        checkArgument(!isNullOrEmpty(path));
        return new ResourceLocation(MODID, path);
    }

    public static String namespace(final String path, final char delimiter) {
        checkArgument(!isNullOrEmpty(path));
        return MODID + delimiter + path;
    }

    @Mod.EventHandler
    public static void onPreInit(FMLPreInitializationEvent event) {
        initDataFixers();
        PROXY.onPreInit();
        deobfuscatedEnvironment = (boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment");
    }

    @Mod.EventHandler
    public static void onInit(FMLInitializationEvent event) {
        PROXY.onInit();
    }

    @Mod.EventHandler
    public static void onPostInit(FMLPostInitializationEvent event) {
        PROXY.onPostInit();
    }
    
    @Mod.EventHandler
    public static void onServerStopped(FMLServerStoppedEvent event) {
        PROXY.getSatellites().flush();
    }

    private static void initDataFixers() {
        DATA_FIXER.registerFix(FixTypes.BLOCK_ENTITY, new IFixableData() {
            @Override
            public int getFixVersion() {
                return 1;
            }

            @Override
            @Nonnull
            public NBTTagCompound fixTagCompound(@Nonnull NBTTagCompound compound) {
                String id = compound.getString("id");
                if (id.startsWith(MODID + '.')) {
                    compound.setString("id", id.replace(MODID + '.', MODID + ':'));
                } else if (id.equals(MODID + ":controller.simple")) {
                    compound.setString("id", MODID + ":controller_simple");
                }
                return compound;
            }
        });
    }
}
