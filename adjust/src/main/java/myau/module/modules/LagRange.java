package myau.module.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.events.Render2DEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.mixin.IAccessorRenderManager;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.util.ItemUtil;
import myau.util.HudProgressBar;
import myau.util.RenderUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class LagRange extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Delay Blink", "Lag"});
    public final IntProperty blinkTick = new IntProperty("Blink Tick", 3, 0, 10, () -> mode.getValue() == 0);
    public final IntProperty delay = new IntProperty("Delay", 150, 0, 1000, () -> mode.getValue() == 1);
    public final FloatProperty range = new FloatProperty("Range", 10.0F, 3.0F, 100.0F);
    public final BooleanProperty weaponsOnly = new BooleanProperty("Weapons Only", true);
    public final BooleanProperty allowTools = new BooleanProperty("Allow Tools", false, this.weaponsOnly::getValue);
    public final BooleanProperty botCheck = new BooleanProperty("Bot Check", true);
    public final BooleanProperty teams = new BooleanProperty("Teams", true);
    public final ModeProperty showPosition = new ModeProperty("Show Position", 0, new String[]{"None", "Default", "Hud"});
    private int tickIndex = -1;
    private long delayCounter = 0L;
    private boolean hasTarget = false;
    private Vec3 lastPosition = null;
    private Vec3 currentPosition = null;

    public LagRange() {
        super("LagRange", false);
    }

    private boolean isValidTarget(EntityPlayer entityPlayer) {
        if (entityPlayer != mc.thePlayer && entityPlayer != mc.thePlayer.ridingEntity) {
            if (entityPlayer == mc.getRenderViewEntity() || entityPlayer == mc.getRenderViewEntity().ridingEntity) {
                return false;
            } else if (entityPlayer.deathTime > 0) {
                return false;
            } else if (TeamUtil.isFriend(entityPlayer)) {
                return false;
            } else {
                return (!this.teams.getValue() || !TeamUtil.isSameTeam(entityPlayer)) && (!this.botCheck.getValue() || !TeamUtil.isBot(entityPlayer));
            }
        } else {
            return false;
        }
    }

    private boolean shouldResetOnPacket(Packet<?> packet) {
        if (packet instanceof C02PacketUseEntity) {
            return true;
        } else if (packet instanceof C07PacketPlayerDigging) {
            return ((C07PacketPlayerDigging) packet).getStatus() != Action.RELEASE_USE_ITEM;
        } else if (packet instanceof C08PacketPlayerBlockPlacement) {
            ItemStack item = ((C08PacketPlayerBlockPlacement) packet).getStack();
            return item == null || !(item.getItem() instanceof ItemSword);
        } else {
            return false;
        }
    }

    @EventTarget(Priority.LOW)
    public void onTick(TickEvent event) {
        if (this.isEnabled()) {
            switch (event.getType()) {
                case PRE:
                    // 检查 KillAura 是否在自动格挡（如果存在）
                    KillAura killAura = (KillAura) Myau.moduleManager.getModule(KillAura.class);
                    if (killAura != null && killAura.isEnabled() && killAura.shouldAutoBlock() && mode.getValue() == 0) {
                        Myau.blinkManager.setBlinkState(false, BlinkModules.BLINK);
                        return;
                    }
                    Myau.lagManager.setDelay(0);
                    this.hasTarget = false;

                    // 检查 BedNuker 是否激活（如果存在）
                    BedNuker bedNuker = (BedNuker) Myau.moduleManager.getModule(BedNuker.class);
                    boolean bedNukerActive = bedNuker != null && bedNuker.isEnabled() && bedNuker.isReady();

                    if ((!bedNukerActive)
                            && !((IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock()
                            && (!mc.thePlayer.isUsingItem() || mc.thePlayer.isBlocking())
                            && (
                            !(Boolean) this.weaponsOnly.getValue()
                                    || ItemUtil.hasRawUnbreakingEnchant()
                                    || this.allowTools.getValue() && ItemUtil.isHoldingTool()
                    )) {
                        List<EntityPlayer> players = mc.theWorld
                                .loadedEntityList
                                .stream()
                                .filter(entity -> entity instanceof EntityPlayer)
                                .map(entity -> (EntityPlayer) entity)
                                .filter(this::isValidTarget)
                                .collect(Collectors.toList());
                        if (players.isEmpty()) {
                            Myau.blinkManager.setBlinkState(false, BlinkModules.BLINK);
                            this.tickIndex = -1;
                        } else {
                            double height = mc.thePlayer.getEyeHeight();
                            Vec3 eyePosition = Myau.lagManager.getLastPosition().addVector(0.0, height, 0.0);
                            Vec3 targetEyePosition = new Vec3(mc.thePlayer.lastTickPosX, mc.thePlayer.lastTickPosY + height, mc.thePlayer.lastTickPosZ);
                            Vec3 playerEyePosition = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + height, mc.thePlayer.posZ);
                            for (EntityPlayer player : players) {
                                double distance = RotationUtil.distanceToBox(player, playerEyePosition);
                                if (!(distance > (double) this.range.getValue())) {
                                    double targetDist = RotationUtil.distanceToBox(player, targetEyePosition);
                                    double eyeDist = RotationUtil.distanceToBox(player, eyePosition);
                                    if (distance < targetDist || distance < eyeDist) {
                                        if (this.tickIndex < 0) {
                                            this.tickIndex = 0;
                                            for (this.delayCounter = this.delayCounter + (long) this.delay.getValue();
                                                 this.delayCounter > 0L;
                                                 this.delayCounter = this.delayCounter - 50
                                            ) {
                                                this.tickIndex++;
                                            }
                                        }
                                        if (mode.getValue() == 1) {
                                            Myau.lagManager.setDelay(this.tickIndex);
                                        }
                                        if (mode.getValue() == 0) {
                                            Myau.blinkManager.setBlinkState(true, BlinkModules.BLINK);
                                            if (Myau.blinkManager.countMovement() > blinkTick.getValue().longValue()) {
                                                Myau.blinkManager.setBlinkState(false, BlinkModules.BLINK);
                                            }
                                        }
                                        this.hasTarget = true;
                                        return;
                                    }
                                }
                            }
                        }
                    } else {
                        this.tickIndex = -1;
                        Myau.blinkManager.setBlinkState(false, BlinkModules.BLINK);
                    }
                    if (!hasTarget) {
                        Myau.blinkManager.setBlinkState(false, BlinkModules.BLINK);
                    }
                    break;
                case POST:
                    Vec3 savedPosition = Myau.lagManager.getLastPosition();
                    if (this.currentPosition == null) {
                        this.lastPosition = savedPosition;
                    } else {
                        this.lastPosition = this.currentPosition;
                    }
                    this.currentPosition = savedPosition;
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isEnabled()) {
            if (this.shouldResetOnPacket(event.getPacket())) {
                Myau.lagManager.setDelay(0);
                this.tickIndex = -1;
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || this.mode.getValue() != 0 || !this.hasTarget || this.blinkTick.getValue() <= 0) {
            return;
        }
        if (Myau.blinkManager.getBlinkingModule() != BlinkModules.BLINK || !Myau.blinkManager.isBlinking()) {
            return;
        }

        float progress = Myau.blinkManager.countMovement() / (float) Math.max(1, this.blinkTick.getValue());
        HudProgressBar.drawBelowCrosshair(progress);
    }

    @EventTarget(Priority.HIGH)
    public void onRender3D(Render3DEvent event) {
        if (this.isEnabled()) {
            if (this.showPosition.getValue() != 0
                    && mc.gameSettings.thirdPersonView != 0
                    && this.hasTarget
                    && this.lastPosition != null
                    && this.currentPosition != null) {
                Color color = new Color(-1);
                switch (this.showPosition.getValue()) {
                    case 1:
                        color = TeamUtil.getTeamColor(mc.thePlayer, 1.0F);
                        break;
                    case 2:
                        HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
                        if (hud != null) {
                            color = hud.getColor(System.currentTimeMillis());
                        }
                        break;
                }
                double x = RenderUtil.lerpDouble(this.currentPosition.xCoord, this.lastPosition.xCoord, event.getPartialTicks());
                double y = RenderUtil.lerpDouble(this.currentPosition.yCoord, this.lastPosition.yCoord, event.getPartialTicks());
                double z = RenderUtil.lerpDouble(this.currentPosition.zCoord, this.lastPosition.zCoord, event.getPartialTicks());
                float size = mc.thePlayer.getCollisionBorderSize();
                AxisAlignedBB aabb = new AxisAlignedBB(
                        x - (double) mc.thePlayer.width / 2.0,
                        y,
                        z - (double) mc.thePlayer.width / 2.0,
                        x + (double) mc.thePlayer.width / 2.0,
                        y + (double) mc.thePlayer.height,
                        z + (double) mc.thePlayer.width / 2.0
                )
                        .expand(size, size, size)
                        .offset(
                                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()
                        );
                RenderUtil.enableRenderState();
                RenderUtil.drawFilledBox(aabb, color.getRed(), color.getGreen(), color.getBlue());
                RenderUtil.disableRenderState();
            }
        }
    }

    @Override
    public void onDisabled() {
        Myau.lagManager.setDelay(0);
        this.tickIndex = -1;
        this.delayCounter = 0L;
        this.hasTarget = false;
        this.lastPosition = null;
        this.currentPosition = null;
    }

    @Override
    public String[] getSuffix() {
        if (this.mode.getValue() == 1) {
            return new String[]{String.format("%dms", this.delay.getValue())};
        } else {
            return new String[]{blinkTick.getValue().toString()};
        }
    }
}
