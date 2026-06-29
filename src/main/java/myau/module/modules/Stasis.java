package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.MoveInputEvent;
import myau.events.PacketEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

public class Stasis extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private boolean allowNextC03;

    public Stasis() {
        super("Stasis", false);
    }

    @Override
    public void onEnabled() {
        allowNextC03 = false;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
<<<<<<< HEAD
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer.hurtTime != 0) return;
=======
        if (!this.isEnabled()) return;
        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.thePlayer.hurtTime != 0) return;

>>>>>>> e7b1dde9d663c728a1ea63266e41a22ddd0a1e61
        mc.thePlayer.motionX = 0.0D;
        mc.thePlayer.motionY = 0.0D;
        mc.thePlayer.motionZ = 0.0D;
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
<<<<<<< HEAD
        if (!isEnabled() || mc.thePlayer == null) return;
=======
        if (!this.isEnabled()) return;
>>>>>>> e7b1dde9d663c728a1ea63266e41a22ddd0a1e61
        mc.thePlayer.movementInput.moveForward = 0.0F;
        mc.thePlayer.movementInput.moveStrafe = 0.0F;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
<<<<<<< HEAD
        if (!isEnabled() || mc.thePlayer == null) return;
        if (event.getType() == EventType.SEND && event.getPacket() instanceof C03PacketPlayer) {
            if (event.getPacket() instanceof C03PacketPlayer.C05PacketPlayerLook) {
                return;
            }
=======
        if (!this.isEnabled()) return;
        if (event.getType() == EventType.SEND && event.getPacket() instanceof C03PacketPlayer) {
>>>>>>> e7b1dde9d663c728a1ea63266e41a22ddd0a1e61
            if (allowNextC03) {
                allowNextC03 = false;
                return;
            }
<<<<<<< HEAD
            if (mc.thePlayer.hurtTime == 0) {
=======
            if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.hurtTime != 0) return;

            if (!(event.getPacket() instanceof C03PacketPlayer.C05PacketPlayerLook)) {
>>>>>>> e7b1dde9d663c728a1ea63266e41a22ddd0a1e61
                event.setCancelled(true);
            }
        } else if (event.getType() == EventType.RECEIVE && event.getPacket() instanceof S08PacketPlayerPosLook) {
            allowNextC03 = true;
        }
    }
}