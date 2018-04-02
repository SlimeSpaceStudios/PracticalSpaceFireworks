package net.gegy1000.psf.server.block.remote;

import lombok.Getter;
import lombok.val;
import net.gegy1000.psf.PracticalSpaceFireworks;
import net.gegy1000.psf.api.IModule;
import net.gegy1000.psf.api.data.ITerrainScan;
import net.gegy1000.psf.client.render.spacecraft.model.SpacecraftModel;
import net.gegy1000.psf.server.block.remote.packet.PacketTrackCraft;
import net.gegy1000.psf.server.capability.CapabilityModuleData;
import net.gegy1000.psf.server.modules.ModuleTerrainScanner;
import net.gegy1000.psf.server.modules.data.EmptyTerrainScan;
import net.gegy1000.psf.server.network.PSFNetworkHandler;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fml.client.GuiScrollingList;
import net.minecraftforge.fml.client.config.GuiButtonExt;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.util.Rectangle;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class GuiControlSystem extends GuiContainer {

    enum PreviewMode {
        CRAFT,
        MAP,
        ;
    }
    
    @Nonnull
    private static final ResourceLocation TEXTURE_LOC = new ResourceLocation(PracticalSpaceFireworks.MODID, "textures/gui/control_system.png");
    
    @Nonnull
    private static final ResourceLocation PREVIEW_BG = new ResourceLocation(PracticalSpaceFireworks.MODID, "textures/gui/preview_bg.png");
    
    private static final boolean scissorAvailable = GLContext.getCapabilities().OpenGL20;

    @Getter
    private final ContainerControlSystem container;
    
    private GuiScrollingList craftList;
    
    private int selectedCraft = -1;

    private SpacecraftModel model;
    
    private PreviewMode mode = PreviewMode.CRAFT;
    
    @Nonnull
    private Collection<IModule> modules = new ArrayList<>();

    @Nonnull
    private Collection<IModule> terrainScannerModules = new ArrayList<>();
    
    private GuiButton buttonBack, buttonMode, buttonLaunch;
    
    private GuiTextField tfName;
    
    private final Rectangle panel;

    private MapRenderer mapRenderer;

    public GuiControlSystem(ContainerControlSystem inventorySlotsIn) {
        super(inventorySlotsIn);
        this.container = inventorySlotsIn;
        
        xSize = 256;
        ySize = 201;
        
        panel = new Rectangle(10, 10, (xSize / 2) - 20, ySize - 20);
    }
    
    @Override
    public void initGui() {
        super.initGui();

        craftList = new GuiCraftList(this, mc, xSize - 20, ySize - 10, guiTop + 10, guiTop + ySize - 10, guiLeft + 10, 20, width, height);

        IListedSpacecraft craft = getCraft();

        buttonBack = new GuiButtonExt(0, guiLeft + xSize - 50 - 10, guiTop + ySize - 20 - 10, 50, 20, "Back");
        buttonBack.visible = craft != null;
        addButton(buttonBack);
        
        buttonMode = new GuiButtonExt(1, guiLeft + panel.getX() + panel.getWidth() - 22, guiTop + panel.getY() + 2, 20, 20, "C");
        buttonMode.visible = craft != null;
        addButton(buttonMode);

        buttonLaunch = new GuiButtonExt(2, guiLeft + panel.getX() + panel.getWidth() + 10, guiTop + ySize - 20 - 10, 50, 20, "Launch");
        buttonLaunch.visible = craft != null && craft.canLaunch();
        addButton(buttonLaunch);
        
        tfName = new GuiTextField(99, mc.fontRenderer, guiLeft + (xSize / 2), guiTop + 10, 115, 20);
        if (craft != null) {
            tfName.setText(craft.getName());
        }
    }
    
    @Override
    public void handleMouseInput() throws IOException {
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;

        super.handleMouseInput();
        if (this.craftList != null)
            this.craftList.handleMouseInput(mouseX, mouseY);
    }
    
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        
        if (this.tfName != null) {
            this.tfName.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }
    
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.tfName != null) {
            if (this.tfName.textboxKeyTyped(typedChar, keyCode)) {
                return;
            }
        }
        super.keyTyped(typedChar, keyCode);
    }
    
    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        if (selectedCraft >= 0) {
            updateName();
            untrack();
        }
        if (mapRenderer != null) {
            mapRenderer.delete();
        }
    }
    
    private void updateName() {
        IListedSpacecraft craft = getCraft();
        if (craft != null) {
            craft.setName(tfName.getText());
        }
    }

    private void untrack() {
        IListedSpacecraft craft = getCraft();
        if (craft != null) {
            PSFNetworkHandler.network.sendToServer(new PacketTrackCraft(craft.getId(), false));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        super.actionPerformed(button);
        IListedSpacecraft craft = getCraft();
        if (button == buttonBack) {
            updateName();
            untrack();
            selectedCraft = -1;
            buttonBack.visible = false;
            buttonMode.visible = false;
            buttonLaunch.visible = false;
            model = null;
            modules.clear();
            terrainScannerModules.clear();
            tfName.setText("");
        } else if (button == buttonMode) {
            this.mode = PreviewMode.values()[(this.mode.ordinal() + 1) % PreviewMode.values().length];
            buttonMode.displayString = this.mode.name().substring(0, 1);
        } else if (button == buttonLaunch && craft != null) {
            craft.launch();
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1, 1, 1, 1);
        drawDefaultBackground();
        mc.getTextureManager().bindTexture(TEXTURE_LOC);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
        if (selectedCraft >= 0 && model != null) {
            IListedSpacecraft craft = getCraft();
            if (craft == null) {
                return;
            }

            drawBackground(craft);

            renderPreview();
            
            tfName.drawTextBox();

            drawStats(craft);
            
        } else {
            craftList.drawScreen(mouseX, mouseY, partialTicks);
        }
    }

    private void drawBackground(IListedSpacecraft craft) {
        drawRect(guiLeft + panel.getX() - 1, guiTop + panel.getY() - 1, guiLeft + panel.getX() + panel.getWidth() + 1, guiTop + panel.getY() + panel.getHeight() + 1, 0xFF8A8A8A);
        GlStateManager.color(1, 1, 1);

        mc.getTextureManager().bindTexture(PREVIEW_BG);
        GlStateManager.enableBlend();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0);
        int craftY = craft.getPosition().getY();
        float alpha = 0;
        if (craftY > 256) {
            alpha = Math.min((craftY - 256) / 500f, 1);
        }
        drawTexturedModalRect(guiLeft + panel.getX(), guiTop + panel.getY(), 0, 0, panel.getWidth(), panel.getHeight());
        GlStateManager.color(1, 1, 1, alpha);
        drawTexturedModalRect(guiLeft + panel.getX(), guiTop + panel.getY(), 128, 0, panel.getWidth(), panel.getHeight());
        GlStateManager.color(1, 1, 1);

        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
        GlStateManager.disableBlend();
    }

    private void renderPreview() {
        switch (mode) {
        case CRAFT:
            renderCraft();
            break;
        case MAP:
            renderMap();
            break;
        }
    }

    private void renderMap() {
        Optional<ITerrainScan> terrainScan = terrainScannerModules.stream()
                .map(module -> module.getCapability(CapabilityModuleData.TERRAIN_SCAN, null))
                .filter(Objects::nonNull)
                .findFirst();

        ITerrainScan buildScan = terrainScan.orElseGet(() -> new EmptyTerrainScan(ModuleTerrainScanner.SCAN_RANGE));
        if (mapRenderer == null || mapRenderer.shouldUpdate(buildScan)) {
            if (mapRenderer != null) {
                mapRenderer.delete();
            }
            mapRenderer = new MapRenderer(buildScan);
        }

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableDepth();

        if (scissorAvailable) {
            ScaledResolution sr = new ScaledResolution(mc);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor((guiLeft + panel.getX()) * sr.getScaleFactor(), mc.displayHeight - ((guiTop + panel.getY() + panel.getHeight()) * sr.getScaleFactor()),
                    panel.getWidth() * sr.getScaleFactor(), panel.getHeight() * sr.getScaleFactor());
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.translate(guiLeft + (xSize / 4), guiTop + ySize / 2, 500);

        GlStateManager.rotate(-45.0F, 1.0F, 0.0F, 0.0F);
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.rotate(mc.player.ticksExisted + mc.getRenderPartialTicks(), 0, 1, 0);
        GlStateManager.scale(-1.8, -1.8, -1.8);

        GlStateManager.translate(-8.0, 0.0, -8.0);

        mapRenderer.performUploads();
        mapRenderer.render();

        if (scissorAvailable) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }

        GlStateManager.disableDepth();
        GlStateManager.disableRescaleNormal();
        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.popMatrix();
    }

    private void renderCraft() {
        BlockPos from = model.getRenderWorld().getMinPos();
        BlockPos to = model.getRenderWorld().getMaxPos();
        AxisAlignedBB bb = new AxisAlignedBB(new Vec3d(from), new Vec3d(to).addVector(1, 1, 1));

        GlStateManager.pushMatrix();

        double lengthX = (bb.maxX - bb.minX) * 16;
        double lengthY = (bb.maxY - bb.minY) * 16;
        double lengthZ = (bb.maxZ - bb.minZ) * 16;

        double halfX = lengthX / 2;
        double halfY = lengthY / 2;
        double halfZ = lengthZ / 2;

        final double maxW = 6 * 16;
        final double maxH = 11 * 16;

        double overW = Math.max(lengthX - maxW, lengthZ - maxW);
        double overH = lengthY - maxH;

        double sc = 1;

        if (overW > 0 && overW >= overH) {
            sc = maxW / (overW + maxW);
        } else if (overH > 0 && overH >= overW) {
            sc = maxH / (overH + maxH);
        }

        halfX *= sc;
        halfY *= sc;
        halfZ *= sc;

        GlStateManager.translate(guiLeft + halfX + (xSize / 4), guiTop + halfY + (ySize / 2), 500);

        BlockPos min = model.getRenderWorld().getMinPos();
        

        GlStateManager.translate(-halfX, -halfY, -halfZ);
        GlStateManager.rotate(-10, 1, 0, 0);
        GlStateManager.rotate(mc.player.ticksExisted + mc.getRenderPartialTicks(), 0, 1, 0);

        GlStateManager.translate(halfX, halfY, halfZ);

        GlStateManager.scale(sc, sc, sc);
        GlStateManager.translate(min.getX() * 16, min.getY() * 16, min.getZ() * 16);

        GlStateManager.scale(-16, -16, -16);

        if (scissorAvailable) {
            ScaledResolution sr = new ScaledResolution(mc);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor((guiLeft + panel.getX()) * sr.getScaleFactor(), mc.displayHeight - ((guiTop + panel.getY() + panel.getHeight()) * sr.getScaleFactor()),
                    panel.getWidth() * sr.getScaleFactor(), panel.getHeight() * sr.getScaleFactor());
        }

        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        model.render(BlockRenderLayer.SOLID);

        GlStateManager.enableAlpha();
        model.render(BlockRenderLayer.CUTOUT_MIPPED);
        mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).setBlurMipmap(false, false);
        model.render(BlockRenderLayer.CUTOUT);
        mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).restoreLastBlurMipmap();

        GlStateManager.enableBlend();
        model.render(BlockRenderLayer.TRANSLUCENT);
        GlStateManager.disableBlend();
        
        if (scissorAvailable) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        GlStateManager.popMatrix();
    }

    private void drawStats(IListedSpacecraft craft) {
        int x = guiLeft + (xSize / 2);
        int y = guiTop + 35;
        int color = 0xFF333333;
        mc.fontRenderer.drawString("Modules:", x, y, color);
        y += 10;
        Map<String, List<IModule>> grouped = modules.stream().collect(Collectors.groupingBy(IModule::getLocalizedName));
        for (val e : grouped.entrySet()) {
            mc.fontRenderer.drawString(e.getValue().size() + "x " + e.getKey(), x, y, color);
            y += 10;
        }
        y += 5;
        int energy = modules.stream()
                .filter(m -> m.hasCapability(CapabilityEnergy.ENERGY, null))
                .map(m -> m.getCapability(CapabilityEnergy.ENERGY, null))
                .reduce(0, (e, m) -> e + m.getEnergyStored(), (a, b) -> a + b);
        mc.fontRenderer.drawString("Energy Stored: " + energy, x, y, color);
        y += 15;
        boolean orbiting = craft.isOrbiting();
        mc.fontRenderer.drawString(orbiting ? "Orbiting Over:" : "Position:", x, y, color);
        BlockPos pos = craft.getPosition();
        x += 5;
        y += 10;
        mc.fontRenderer.drawString("X: " + pos.getX(), x, y, color);
        if (!orbiting) {
            y += 10;
            mc.fontRenderer.drawString("Y: " + pos.getY(), x, y, color);
        }
        y += 10;
        mc.fontRenderer.drawString("Z: " + pos.getZ(), x, y, color);
    }

    private @Nullable IListedSpacecraft getCraft() {
        if (selectedCraft >= 0) {
            return container.getTe().getCrafts().get(selectedCraft);
        }
        return null;
    }

    public void selectCraft(int index) {
        this.selectedCraft = index;
        IListedSpacecraft craft = getCraft();
        craft.requestVisualData();
        buttonBack.visible = true;
        buttonMode.visible = true;
        buttonLaunch.visible = craft.canLaunch();
        tfName.setText(craft.getName());
        PSFNetworkHandler.network.sendToServer(new PacketTrackCraft(craft.getId(), true));
    }

    public void setVisual(IListedSpacecraft.Visual visual) {
        model = SpacecraftModel.build(visual.getBlockAccess());
        modules = visual.getModules();
        terrainScannerModules = modules.stream()
                .filter(module -> module.hasCapability(CapabilityModuleData.TERRAIN_SCAN, null))
                .collect(Collectors.toList());
    }

    public void updateModule(UUID id, NBTTagCompound tag) {
        modules.stream().filter(m -> m.getId().equals(id)).findFirst().ifPresent(m -> m.readUpdateTag(tag));
    }
}
