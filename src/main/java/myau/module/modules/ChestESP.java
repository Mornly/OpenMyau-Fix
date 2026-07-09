package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.mixin.IAccessorMinecraft;
import myau.mixin.IAccessorRenderManager;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.ModeProperty;
import myau.util.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S24PacketBlockAction;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ChestESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Myau", "State"});
    public final ColorProperty chest = new ColorProperty("chest", new Color(255, 170, 0).getRGB(), () -> this.mode.getValue() == 0);
    public final ColorProperty trappedChest = new ColorProperty("trapped-chest", new Color(255, 43, 0).getRGB(), () -> this.mode.getValue() == 0);
    public final ColorProperty enderChest = new ColorProperty("ender-chest", new Color(26, 17, 0).getRGB(), () -> this.mode.getValue() == 0);
    public final BooleanProperty tracers = new BooleanProperty("tracers", false);

    private final List<BlockPos> openedChests = new ArrayList<>();

    public ChestESP() {
        super("ChestESP", false);
    }

    private void addOpenedChest(BlockPos pos) {
        if (!openedChests.contains(pos)) {
            openedChests.add(pos);
        }
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        if (block instanceof BlockChest) {
            for (EnumFacing facing : EnumFacing.HORIZONTALS) {
                BlockPos neighbor = pos.offset(facing);
                if (mc.theWorld.getBlockState(neighbor).getBlock() == block) {
                    if (!openedChests.contains(neighbor)) {
                        openedChests.add(neighbor);
                    }
                }
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE) return;
        if (!(event.getPacket() instanceof S24PacketBlockAction)) return;
        S24PacketBlockAction packet = (S24PacketBlockAction) event.getPacket();
        if (packet.getData2() == 1) {
            addOpenedChest(packet.getBlockPosition());
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!this.isEnabled()) return;

        RenderUtil.enableRenderState();
        boolean isStateMode = mode.getValue() == 1;

        for (TileEntity tileEntity : mc.theWorld.loadedTileEntityList) {
            if (!(tileEntity instanceof TileEntityChest) && !(tileEntity instanceof TileEntityEnderChest)) continue;

            Block block = mc.theWorld.getBlockState(tileEntity.getPos()).getBlock();
            double minX = 0.0625;
            double minZ = 0.0625;
            double maxX = 0.9375;
            double maxZ = 0.9375;

            Color color = null;
            boolean isTrapped = false;

            if (block instanceof BlockChest) {
                isTrapped = block.canProvidePower();
                if (!isStateMode) {
                    color = isTrapped ? new Color(trappedChest.getValue()) : new Color(chest.getValue());
                }

                EnumFacing facing = mc.theWorld.getBlockState(tileEntity.getPos()).getValue(BlockChest.FACING);
                switch (facing) {
                    case NORTH:
                        if (mc.theWorld.getBlockState(tileEntity.getPos().east()).getBlock() == block) continue;
                        else if (mc.theWorld.getBlockState(tileEntity.getPos().west()).getBlock() == block) minX -= 1;
                        break;
                    case SOUTH:
                        if (mc.theWorld.getBlockState(tileEntity.getPos().west()).getBlock() == block) continue;
                        else if (mc.theWorld.getBlockState(tileEntity.getPos().east()).getBlock() == block) maxX += 1;
                        break;
                    case WEST:
                        if (mc.theWorld.getBlockState(tileEntity.getPos().north()).getBlock() == block) continue;
                        else if (mc.theWorld.getBlockState(tileEntity.getPos().south()).getBlock() == block) maxZ += 1;
                        break;
                    case EAST:
                        if (mc.theWorld.getBlockState(tileEntity.getPos().south()).getBlock() == block) continue;
                        else if (mc.theWorld.getBlockState(tileEntity.getPos().north()).getBlock() == block) minZ -= 1;
                        break;
                }
            } else {
                if (!isStateMode) {
                    color = new Color(enderChest.getValue());
                }
            }

            double renderX = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
            double renderY = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
            double renderZ = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();

            AxisAlignedBB aabb = new AxisAlignedBB(
                    tileEntity.getPos().getX() + minX,
                    tileEntity.getPos().getY(),
                    tileEntity.getPos().getZ() + minZ,
                    tileEntity.getPos().getX() + maxX,
                    tileEntity.getPos().getY() + 0.875,
                    tileEntity.getPos().getZ() + maxZ
            ).offset(-renderX, -renderY, -renderZ);

            if (isStateMode) {
                boolean isOpened = openedChests.contains(tileEntity.getPos());
                Color stateColor = isOpened ? new Color(255, 0, 0, 100) : new Color(0, 255, 0, 100);
                drawFilledBoxAlpha(aabb, stateColor.getRed(), stateColor.getGreen(), stateColor.getBlue(), stateColor.getAlpha());
                color = stateColor;
            } else {
                RenderUtil.drawBoundingBox(
                        aabb, color.getRed(), color.getGreen(), color.getBlue(), 255, 1.5F
                );
            }

            if (this.tracers.getValue() && color != null) {
                Vec3 vec;
                if (mc.gameSettings.thirdPersonView == 0) {
                    vec = new Vec3(0.0, 0.0, 1.0)
                            .rotatePitch((float) (-Math.toRadians(RenderUtil.lerpFloat(
                                    mc.getRenderViewEntity().rotationPitch,
                                    mc.getRenderViewEntity().prevRotationPitch,
                                    ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                            ))))
                            .rotateYaw((float) (-Math.toRadians(RenderUtil.lerpFloat(
                                    mc.getRenderViewEntity().rotationYaw,
                                    mc.getRenderViewEntity().prevRotationYaw,
                                    ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                            ))));
                } else {
                    vec = new Vec3(0.0, 0.0, 0.0)
                            .rotatePitch((float) (-Math.toRadians(RenderUtil.lerpFloat(
                                    mc.thePlayer.cameraPitch, mc.thePlayer.prevCameraPitch,
                                    ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                            ))))
                            .rotateYaw((float) (-Math.toRadians(RenderUtil.lerpFloat(
                                    mc.thePlayer.cameraYaw, mc.thePlayer.prevCameraYaw,
                                    ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                            ))));
                }
                vec = new Vec3(vec.xCoord, vec.yCoord + mc.getRenderViewEntity().getEyeHeight(), vec.zCoord);
                float opacity = (float) ((Tracers) Myau.moduleManager.modules.get(Tracers.class)).opacity.getValue() / 100.0F;
                RenderUtil.drawLine3D(
                        vec,
                        tileEntity.getPos().getX() + 0.5,
                        tileEntity.getPos().getY() + 0.5,
                        tileEntity.getPos().getZ() + 0.5,
                        (float) color.getRed() / 255.0F,
                        (float) color.getGreen() / 255.0F,
                        (float) color.getBlue() / 255.0F,
                        opacity,
                        1.5F
                );
            }
        }
        RenderUtil.disableRenderState();
    }

    private void drawFilledBoxAlpha(AxisAlignedBB bb, int red, int green, int blue, int alpha) {
        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        worldRenderer.begin(7, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);

        worldRenderer.pos(bb.minX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();

        worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();

        worldRenderer.pos(bb.minX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();

        worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();

        worldRenderer.pos(bb.minX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();

        worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();

        tessellator.draw();
    }
}