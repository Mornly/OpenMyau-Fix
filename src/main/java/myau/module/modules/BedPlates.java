package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render3DEvent;
import myau.mixin.IAccessorEntityRenderer;
import myau.mixin.IAccessorRenderManager;
import myau.module.Module;
import myau.property.properties.IntProperty;
import myau.util.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockBed.EnumPartType;
import net.minecraft.block.BlockStainedGlass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import javax.vecmath.Vector4d;
import java.awt.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BedPlates extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty range = new IntProperty("Range", 1, 1, 7);

    public BedPlates() {
        super("BedPlates", false);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

        BedESP bedESP = (BedESP) Myau.moduleManager.modules.get(BedESP.class);
        if (bedESP == null || bedESP.beds.isEmpty()) return;

        IntBuffer viewport = GLAllocation.createDirectIntBuffer(16);
        FloatBuffer modelView = GLAllocation.createDirectFloatBuffer(16);
        FloatBuffer projection = GLAllocation.createDirectFloatBuffer(16);

        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);

        double renderPosX = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double renderPosY = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double renderPosZ = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();

        List<BedRenderData> renderDataList = new ArrayList<>();

        for (BlockPos bedPos : bedESP.beds) {
            IBlockState state = mc.theWorld.getBlockState(bedPos);
            if (!(state.getBlock() instanceof BlockBed)) continue;
            if (state.getValue(BlockBed.PART) != EnumPartType.HEAD) continue;

            BlockPos footPos = bedPos.offset(state.getValue(BlockBed.FACING).getOpposite());
            IBlockState footState = mc.theWorld.getBlockState(footPos);
            if (!(footState.getBlock() instanceof BlockBed)) continue;

            double minX = Math.min(bedPos.getX(), footPos.getX());
            double minY = bedPos.getY();
            double minZ = Math.min(bedPos.getZ(), footPos.getZ());
            double maxX = Math.max(bedPos.getX(), footPos.getX()) + 1.0;
            double maxY = bedPos.getY() + 1.0;
            double maxZ = Math.max(bedPos.getZ(), footPos.getZ()) + 1.0;

            Vector4d pos = projectToScreenOptimized(minX, minY, minZ, maxX, maxY, maxZ, viewport, modelView, projection, renderPosX, renderPosY, renderPosZ);

            if (pos == null) continue;

            float screenX = (float) ((pos.x + pos.z) / 2.0);
            float screenY = (float) pos.y - 30;

            List<BlockEntry> blocks = collectProtectionBlocks(bedPos, footPos);
            if (blocks.isEmpty()) continue;

            blocks.sort((a, b) -> Float.compare(b.hardness, a.hardness));

            float itemSize = 16;
            float padding = 2;
            float totalWidth = blocks.size() * (itemSize + padding) + padding;
            float bgHeight = itemSize + padding * 2;

            double centerX = (bedPos.getX() + footPos.getX()) / 2.0 + 0.5;
            double centerY = bedPos.getY() + 0.5;
            double centerZ = (bedPos.getZ() + footPos.getZ()) / 2.0 + 0.5;
            float dist = (float) mc.thePlayer.getDistance(centerX, centerY, centerZ);

            renderDataList.add(new BedRenderData(screenX - totalWidth / 2, screenY - bgHeight / 2, totalWidth, bgHeight, blocks, dist));
        }

        if (renderDataList.isEmpty()) return;

        GlStateManager.pushMatrix();
        mc.entityRenderer.setupOverlayRendering();

        for (BedRenderData data : renderDataList) {
            float dist = data.dist;
            float scale = MathHelper.clamp_float(1.0f / (1.0f + dist * 0.08f) * 1.5f, 0.4f, 2.0f);

            float cx = data.bgX + data.totalWidth / 2;
            float cy = data.bgY + data.bgHeight / 2;

            GlStateManager.pushMatrix();
            GlStateManager.translate(cx, cy, 0);
            GlStateManager.scale(scale, scale, 1);
            GlStateManager.translate(-cx, -cy, 0);

            Gui.drawRect((int) data.bgX, (int) data.bgY, (int) (data.bgX + data.totalWidth), (int) (data.bgY + data.bgHeight), new Color(0, 0, 0, 110).getRGB());

            float itemX = data.bgX + 2;
            float itemY = data.bgY + 2;

            for (BlockEntry entry : data.blocks) {
                ItemStack stack = new ItemStack(Item.getItemFromBlock(entry.block));

                GlStateManager.pushMatrix();
                RenderHelper.enableGUIStandardItemLighting();

                GlStateManager.translate(itemX + 8, itemY + 8, 0);
                GlStateManager.scale(1.0F, -1.0F, 1.0F);
                GlStateManager.translate(-(itemX + 8), -(itemY + 8), 0);

                RenderUtil.renderItemInGUI(stack, (int) itemX, (int) itemY);

                RenderHelper.disableStandardItemLighting();
                GlStateManager.popMatrix();

                itemX += 18;
            }

            GlStateManager.popMatrix();
        }

        GlStateManager.popMatrix();
        ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(event.getPartialTicks(), 0);
    }

    private Vector4d projectToScreenOptimized(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, IntBuffer viewport, FloatBuffer modelView, FloatBuffer projection, double renderPosX, double renderPosY, double renderPosZ) {
        FloatBuffer coords = GLAllocation.createDirectFloatBuffer(4);
        Vector4d result = null;
        double screenScale = new net.minecraft.client.gui.ScaledResolution(mc).getScaleFactor();

        double[][] corners = {
                {minX, minY, minZ}, {minX, maxY, minZ}, {maxX, minY, minZ}, {maxX, maxY, minZ},
                {minX, minY, maxZ}, {minX, maxY, maxZ}, {maxX, minY, maxZ}, {maxX, maxY, maxZ}
        };

        for (double[] corner : corners) {
            float x = (float) (corner[0] - renderPosX);
            float y = (float) (corner[1] - renderPosY);
            float z = (float) (corner[2] - renderPosZ);

            if (GLU.gluProject(x, y, z, modelView, projection, viewport, coords)) {
                double screenX = coords.get(0) / screenScale;
                double screenY = (mc.displayHeight - coords.get(1)) / screenScale;
                double depth = coords.get(2);

                if (depth < 0.0 || depth >= 1.0) continue;

                if (result == null) {
                    result = new Vector4d(screenX, screenY, screenX, screenY);
                } else {
                    result.x = Math.min(screenX, result.x);
                    result.y = Math.min(screenY, result.y);
                    result.z = Math.max(screenX, result.z);
                    result.w = Math.max(screenY, result.w);
                }
            }
        }
        return result;
    }

    private static final Block[] ALLOWED_BLOCKS = {
            Blocks.wool,
            Blocks.hardened_clay,
            Blocks.glass,
            Blocks.stained_glass,
            Blocks.end_stone,
            Blocks.ladder,
            Blocks.planks,
            Blocks.log,
            Blocks.obsidian,
            Blocks.packed_ice
    };

    private boolean isBlockAllowed(Block block) {
        for (Block allowed : ALLOWED_BLOCKS) {
            if (block == allowed) {
                return true;
            }
        }
        return false;
    }

    private List<BlockEntry> collectProtectionBlocks(BlockPos head, BlockPos foot) {
        Map<Block, BlockEntry> blockMap = new LinkedHashMap<>();
        int centerX = (head.getX() + foot.getX()) / 2;
        int centerY = head.getY();
        int centerZ = (head.getZ() + foot.getZ()) / 2;
        int r = range.getValue();

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = 0; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (r == 1 && Math.abs(dx) + Math.abs(dz) > 1) continue;

                    BlockPos pos = new BlockPos(centerX + dx, centerY + dy, centerZ + dz);
                    Block block = mc.theWorld.getBlockState(pos).getBlock();

                    if (!isBlockAllowed(block)) continue;

                    Block displayBlock = block instanceof BlockStainedGlass ? Blocks.glass : block;
                    if (blockMap.containsKey(displayBlock)) continue;

                    float hardness = block.getBlockHardness(mc.theWorld, pos);
                    blockMap.put(displayBlock, new BlockEntry(displayBlock, hardness < 0 ? 100 : hardness));
                }
            }
        }
        return new ArrayList<>(blockMap.values());
    }

    private static class BlockEntry {
        final Block block;
        final float hardness;
        BlockEntry(Block block, float hardness) { this.block = block; this.hardness = hardness; }
    }

    private static class BedRenderData {
        float bgX, bgY, totalWidth, bgHeight;
        List<BlockEntry> blocks;
        float dist;
        BedRenderData(float x, float y, float w, float h, List<BlockEntry> b, float d) {
            this.bgX = x; this.bgY = y; this.totalWidth = w; this.bgHeight = h; this.blocks = b; this.dist = d;
        }
    }
}