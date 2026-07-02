package myau.module.modules;

import com.google.common.base.CaseFormat;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.*;
import myau.management.RotationState;
import myau.mixin.IAccessorEntity;
import myau.mixin.IAccessorMinecraft;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;

public class Speed extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"TimerBalance", "Normal"});
    public final FloatProperty timerBoostMultiplier = new FloatProperty("TimerBoostMultiplier", 0.5f, 0.1f, 1f, () -> this.mode.getValue() == 0);
    public final IntProperty lowTimerTicks = new IntProperty("LowTimerTicks", 6, 1, 10, () -> this.mode.getValue() == 0);
    public final BooleanProperty rotation = new BooleanProperty("Rotation", false, () -> this.mode.getValue() == 0);
    public final FloatProperty multiplier = new FloatProperty("Multiplier", 1.0F, 0.0F, 10.0F, () -> this.mode.getValue() == 1);
    public final FloatProperty friction = new FloatProperty("Friction", 1.0F, 0.0F, 10.0F, () -> this.mode.getValue() == 1);
    public final PercentProperty strafe = new PercentProperty("Strafe", 0, () -> this.mode.getValue() == 1);

    public Speed() {
        super("Speed", false);
    }

    private int ticks = 0;
    private float yaw = 0f;
    private YawOffsetMode yawOffsetMode = YawOffsetMode.AIR;

    public enum YawOffsetMode {
        GROUND("Ground"),
        AIR("Air"),
        CONSTANT("Constant");

        private final String tag;

        YawOffsetMode(String tag) {
            this.tag = tag;
        }

        public String getTag() {
            return tag;
        }
    }

    private boolean finished = false;

    private void computeGroundYawOffset(EntityPlayer player) {
        if (player.onGround) {
            yaw = getYawOffsetFromKeys();
        } else {
            yaw = 0f;
        }
    }

    private void computeAirYawOffset(EntityPlayer player) {
        if (!player.onGround
                && mc.gameSettings.keyBindForward.isKeyDown()
                && !mc.gameSettings.keyBindLeft.isKeyDown()
                && !mc.gameSettings.keyBindRight.isKeyDown()) {
            yaw = -45f;
        } else {
            yaw = 0f;
        }
    }

    private void computeConstantYawOffset(EntityPlayer player) {
        yaw = getYawOffsetFromKeys();
    }

    private float getYawOffsetFromKeys() {
        KeyBinding forward = mc.gameSettings.keyBindForward;
        KeyBinding back = mc.gameSettings.keyBindBack;
        KeyBinding left = mc.gameSettings.keyBindLeft;
        KeyBinding right = mc.gameSettings.keyBindRight;

        if (forward.isKeyDown() && left.isKeyDown()) return 45f;
        if (forward.isKeyDown() && right.isKeyDown()) return -45f;
        if (back.isKeyDown() && left.isKeyDown()) return 135f;
        if (back.isKeyDown() && right.isKeyDown()) return -135f;
        if (back.isKeyDown()) return 180f;
        if (left.isKeyDown()) return 90f;
        if (right.isKeyDown()) return -90f;
        return 0f;
    }

    private boolean canBoost() {
        Scaffold scaffold = (Scaffold) Myau.moduleManager.getModule(Scaffold.class);
        return !scaffold.isEnabled() && MoveUtil.isForwardPressed()
                && mc.thePlayer.getFoodStats().getFoodLevel() > 6
                && !mc.thePlayer.isSneaking()
                && !mc.thePlayer.isInWater()
                && !mc.thePlayer.isInLava()
                && !((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (isEnabled() && this.mode.getValue() == 0 && event.getType() == EventType.PRE) {
            if (canBoost()) {
                if (!mc.thePlayer.onGround) {
                    if (ticks < lowTimerTicks.getValue() && !finished && mc.thePlayer.motionY < 0) {
                        ticks++;
                        ((IAccessorMinecraft) mc).getTimer().timerSpeed = timerBoostMultiplier.getValue();
                        if (ticks == lowTimerTicks.getValue()) {
                            finished = true;
                        }
                    }
                    if (finished) {
                        if (ticks > 0) {
                            ticks--;
                            ((IAccessorMinecraft) mc).getTimer().timerSpeed = 2.0F;
                            if (ticks == 0) {
                                ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
                                finished = false;
                            }
                        }
                    }
                } else {
                    finished = false;
                    ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
                    ticks = 0;
                }
            } else {
                finished = false;
                ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
                ticks = 0;
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (isEnabled() && this.mode.getValue() == 0 && event.getType() == EventType.PRE && rotation.getValue()) {
            if (canBoost() && !Myau.moduleManager.getModule(KillAura.class).isEnabled()) {
                switch (yawOffsetMode) {
                    case GROUND:
                        computeGroundYawOffset(mc.thePlayer);
                        break;
                    case AIR:
                        computeAirYawOffset(mc.thePlayer);
                        break;
                    case CONSTANT:
                        computeConstantYawOffset(mc.thePlayer);
                        break;
                }
                event.setRotation(mc.thePlayer.rotationYaw - yaw, mc.thePlayer.rotationPitch, 2);
                event.setPervRotation(mc.thePlayer.rotationYaw - yaw, 2);
            }
        }
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (this.isEnabled() && this.mode.getValue() == 0 && rotation.getValue() && canBoost() && !Myau.moduleManager.getModule(KillAura.class).isEnabled()) {
            if (RotationState.isActived() && RotationState.getPriority() == 2.0F && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            }
        }
    }

    @EventTarget(Priority.LOW)
    public void onStrafe(StrafeEvent event) {
        if (this.mode.getValue() == 1) {
            if (this.isEnabled() && this.canBoost()) {
                if (mc.thePlayer.onGround) {
                    mc.thePlayer.motionY = 0.42F;
                    MoveUtil.setSpeed(
                            MoveUtil.getJumpMotion() * (double) this.multiplier.getValue(),
                            MoveUtil.getMoveYaw()
                    );
                } else {
                    if (this.friction.getValue() != 1.0F) {
                        event.setFriction(event.getFriction() * this.friction.getValue());
                    }
                    if (this.strafe.getValue() > 0) {
                        double speed = MoveUtil.getSpeed();
                        MoveUtil.setSpeed(speed * (double) ((float) (100 - this.strafe.getValue()) / 100.0F), MoveUtil.getDirectionYaw());
                        MoveUtil.addSpeed(
                                speed * (double) ((float) this.strafe.getValue() / 100.0F), MoveUtil.getMoveYaw()
                        );
                        MoveUtil.setSpeed(speed);
                    }
                }
            }
        }
    }

    @Override
    public void onDisabled() {
        finished = false;
        ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
        ticks = 0;
    }

    @EventTarget(Priority.LOW)
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.mode.getValue() == 1) {
            if (this.isEnabled() && this.canBoost()) {
                mc.thePlayer.movementInput.jump = false;
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, mode.getModeString())};
    }
}