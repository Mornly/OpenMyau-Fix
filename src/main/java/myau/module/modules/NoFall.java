package myau.module.modules;

import com.google.common.base.CaseFormat;
import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.MoveInputEvent;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.management.RotationState;
import myau.mixin.IAccessorC03PacketPlayer;
import myau.mixin.IAccessorMinecraft;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.AxisAlignedBB;

public class NoFall extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil packetDelayTimer = new TimerUtil();
    private final TimerUtil scoreboardResetTimer = new TimerUtil();
    private boolean slowFalling = false;
    private boolean lastOnGround = false;

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Packet", "Blink", "No_Ground", "Spoof", "MLG"});

    public final FloatProperty distance = new FloatProperty("distance", 3.0F, 0.0F, 20.0F);
    public final IntProperty delay = new IntProperty("delay", 0, 0, 10000);

    public final BooleanProperty autoSwitch = new BooleanProperty("Auto Switch", true, () -> mode.getValue() == 4);
    public final ModeProperty moveFix = new ModeProperty("Move Fix", 1, new String[]{"NONE", "SILENT"}, () -> mode.getValue() == 4);
    public final IntProperty priority = new IntProperty("Priority", 2, 1, 10, () -> mode.getValue() == 4);

    private boolean active = false;
    private boolean onDistance = false;
    private boolean prevOnGround = false;
    private double highestY = 0.0;
    private float originalYaw = 0.0f;
    private boolean firstClickDone = false;
    private boolean secondClickDone = false;
    private int lastSlot = -1;

    private boolean canTrigger() {
        return this.scoreboardResetTimer.hasTimeElapsed(3000) && this.packetDelayTimer.hasTimeElapsed(this.delay.getValue().longValue());
    }

    public NoFall() {
        super("NoFall", false);
    }

    @EventTarget(Priority.HIGH)
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.RECEIVE && event.getPacket() instanceof S08PacketPlayerPosLook) {
            if (mode.getValue() == 4) {
                resetMLGState();
                restoreSlot();
            } else {
                this.onDisabled();
            }
        } else if (this.isEnabled() && event.getType() == EventType.SEND && !event.isCancelled()) {
            if (mode.getValue() == 4) return;
            if (event.getPacket() instanceof C03PacketPlayer) {
                C03PacketPlayer packet = (C03PacketPlayer) event.getPacket();
                switch (this.mode.getValue()) {
                    case 0:
                        if (this.slowFalling) {
                            this.slowFalling = false;
                            ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
                        } else if (!packet.isOnGround()) {
                            AxisAlignedBB aabb = mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0);
                            if (PlayerUtil.canFly(this.distance.getValue())
                                    && !PlayerUtil.checkInWater(aabb)
                                    && this.canTrigger()) {
                                this.packetDelayTimer.reset();
                                this.slowFalling = true;
                                ((IAccessorMinecraft) mc).getTimer().timerSpeed = 0.5F;
                            }
                        }
                        break;
                    case 1:
                        boolean allowed = !mc.thePlayer.isOnLadder() && !mc.thePlayer.capabilities.allowFlying && mc.thePlayer.hurtTime == 0;
                        if (Myau.blinkManager.getBlinkingModule() != BlinkModules.NO_FALL) {
                            if (this.lastOnGround
                                    && !packet.isOnGround()
                                    && allowed
                                    && PlayerUtil.canFly(this.distance.getValue().intValue())
                                    && mc.thePlayer.motionY < 0.0) {
                                Myau.blinkManager.setBlinkState(false, Myau.blinkManager.getBlinkingModule());
                                Myau.blinkManager.setBlinkState(true, BlinkModules.NO_FALL);
                            }
                        } else if (!allowed) {
                            Myau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
                            ChatUtil.sendFormatted(String.format("%s%s: &cFailed player check!&r", Myau.clientName, this.getName()));
                        } else if (PlayerUtil.checkInWater(mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0))) {
                            Myau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
                            ChatUtil.sendFormatted(String.format("%s%s: &cFailed void check!&r", Myau.clientName, this.getName()));
                        } else if (packet.isOnGround()) {
                            for (Packet<?> blinkedPacket : Myau.blinkManager.blinkedPackets) {
                                if (blinkedPacket instanceof C03PacketPlayer) {
                                    ((IAccessorC03PacketPlayer) blinkedPacket).setOnGround(true);
                                }
                            }
                            Myau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
                            this.packetDelayTimer.reset();
                        }
                        this.lastOnGround = packet.isOnGround() && allowed && this.canTrigger();
                        break;
                    case 2:
                        ((IAccessorC03PacketPlayer) packet).setOnGround(false);
                        break;
                    case 3:
                        if (!packet.isOnGround()) {
                            AxisAlignedBB aabb = mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0);
                            if (PlayerUtil.canFly(this.distance.getValue())
                                    && !PlayerUtil.checkInWater(aabb)
                                    && this.canTrigger()) {
                                this.packetDelayTimer.reset();
                                ((IAccessorC03PacketPlayer) packet).setOnGround(true);
                                mc.thePlayer.fallDistance = 0.0F;
                            }
                        }
                }
            }
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (mode.getValue() == 4) return;
            if (ServerUtil.hasPlayerCountInfo()) {
                this.scoreboardResetTimer.reset();
            }
            if (this.mode.getValue() == 0 && this.slowFalling) {
                PacketUtil.sendPacketNoEvent(new C03PacketPlayer(true));
                mc.thePlayer.fallDistance = 0.0F;
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.currentScreen != null) return;

        if (mode.getValue() == 4) {
            Module scaffold = Myau.moduleManager.getModule("Scaffold");
            if (scaffold != null && scaffold.isEnabled()) {
                if (active) {
                    restoreSlot();
                    resetMLGState();
                }
                return;
            }

            fallCheck();

            if (!active && onDistance && !mc.thePlayer.onGround) {
                active = true;
                originalYaw = mc.thePlayer.rotationYaw;
                firstClickDone = false;
                secondClickDone = false;

                if (autoSwitch.getValue()) {
                    lastSlot = mc.thePlayer.inventory.currentItem;
                    int bucketSlot = findWaterBucketSlot();
                    if (bucketSlot != -1) {
                        mc.thePlayer.inventory.currentItem = bucketSlot;
                    }
                }
            }

            if (active) {
                if (mc.thePlayer.onGround && !secondClickDone) {
                    performRightClick();
                    secondClickDone = true;
                    active = false;
                    restoreSlot();
                    return;
                }

                if (autoSwitch.getValue()) {
                    ItemStack held = mc.thePlayer.inventory.getCurrentItem();
                    if (held == null || held.getItem() != Items.water_bucket) {
                        active = false;
                        restoreSlot();
                        return;
                    }
                }

                event.setRotation(originalYaw, 90.0f, priority.getValue());
                event.setPervRotation(originalYaw, priority.getValue());

                if (!firstClickDone) {
                    double dist = getDistanceToGround();
                    if (dist >= 0 && dist <= 3.0) {
                        performRightClick();
                        firstClickDone = true;
                    }
                }
            }
        }
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (!isEnabled()) return;
        if (mode.getValue() != 4) return;

        Module scaffold = Myau.moduleManager.getModule("Scaffold");
        if (scaffold != null && scaffold.isEnabled()) {
            return;
        }

        if (active && moveFix.getValue() == 1
                && RotationState.isActived()
                && RotationState.getPriority() == priority.getValue()
                && MoveUtil.isForwardPressed()) {
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
        }
    }

    private void fallCheck() {
        boolean onGround = mc.thePlayer.onGround;
        if (onGround) {
            onDistance = false;
            highestY = mc.thePlayer.posY;
        } else if (prevOnGround) {
            highestY = mc.thePlayer.posY;
        } else {
            if (highestY - mc.thePlayer.posY > 3.0) {
                onDistance = true;
            }
        }
        prevOnGround = onGround;
    }

    private double getDistanceToGround() {
        RotationUtil.RotationVec rotation = new RotationUtil.RotationVec(originalYaw, 90.0f);
        RayCastUtil.RayCastResult result = RayCastUtil.rayCast(rotation, 10.0, 0.0f);
        if (result != null && result.typeOfHit == RayCastUtil.RayCastResult.Type.BLOCK && result.hitVec != null) {
            double footY = mc.thePlayer.getEntityBoundingBox().minY;
            return footY - result.hitVec.yCoord;
        }
        return -1;
    }

    private void performRightClick() {
        RotationUtil.RotationVec rotation = new RotationUtil.RotationVec(originalYaw, 90.0f);
        RayCastUtil.RayCastResult result = RayCastUtil.rayCast(rotation, 10.0, 0.0f);
        if (result != null && result.typeOfHit == RayCastUtil.RayCastResult.Type.BLOCK && result.hitVec != null) {
            mc.playerController.onPlayerRightClick(
                    mc.thePlayer,
                    mc.theWorld,
                    mc.thePlayer.getHeldItem(),
                    result.getBlockPos(),
                    result.sideHit,
                    result.hitVec
            );
            mc.thePlayer.swingItem();
        }
    }

    private int findWaterBucketSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() == Items.water_bucket) {
                return i;
            }
        }
        return -1;
    }

    private void restoreSlot() {
        if (autoSwitch.getValue() && lastSlot != -1 && mc.thePlayer != null
                && mc.thePlayer.inventory.currentItem != lastSlot) {
            mc.thePlayer.inventory.currentItem = lastSlot;
        }
    }

    private void resetMLGState() {
        active = false;
        onDistance = false;
        prevOnGround = false;
        highestY = 0.0;
        firstClickDone = false;
        secondClickDone = false;
    }

    @Override
    public void onEnabled() {
        if (mode.getValue() == 4) {
            resetMLGState();
            if (mc.thePlayer != null) {
                originalYaw = mc.thePlayer.rotationYaw;
                highestY = mc.thePlayer.posY;
                prevOnGround = mc.thePlayer.onGround;
            }
        } else {
            this.lastOnGround = false;
        }
    }

    @Override
    public void onDisabled() {
        if (mode.getValue() == 4) {
            resetMLGState();
            restoreSlot();
        } else {
            this.lastOnGround = false;
            Myau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
            if (this.slowFalling) {
                this.slowFalling = false;
                ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
            }
        }
    }

    @Override
    public void verifyValue(String mode) {
        if (this.isEnabled()) {
            this.onDisabled();
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }
}