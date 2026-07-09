package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.util.PacketUtil;
import myau.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;

import java.util.Comparator;
import java.util.concurrent.ConcurrentLinkedQueue;

public class FakeLag extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();



    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Normal", "Dynamic"});
    public final IntProperty delay = new IntProperty("delay-ms", 200, 50, 5000, () -> mode.getValue() == 0);
    public final FloatProperty range = new FloatProperty("range", 4.0F, 1.0F, 10.0F, () -> mode.getValue() == 1);
    public final IntProperty minDelay = new IntProperty("min-delay-ms", 100, 50, 3000, () -> mode.getValue() == 1);
    public final IntProperty maxDelay = new IntProperty("max-delay-ms", 400, 100, 5000, () -> mode.getValue() == 1);

    private final ConcurrentLinkedQueue<PacketData> packetQueue = new ConcurrentLinkedQueue<>();
    private boolean isDispatching = false;
    private double nearestEnemyDistance = Double.MAX_VALUE;

    public FakeLag() {
        super("FakeLag", false);
    }

    @Override
    public void onEnabled() {
        packetQueue.clear();
        this.isDispatching = false;
        this.nearestEnemyDistance = Double.MAX_VALUE;
    }

    @Override
    public void onDisabled() {
        this.isDispatching = true;
        while (!packetQueue.isEmpty()) {
            PacketUtil.sendPacket(packetQueue.poll().packet);
        }
        this.isDispatching = false;
        this.nearestEnemyDistance = Double.MAX_VALUE;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.enabled) return;
        if (event.getType() != EventType.SEND) return;
        if (this.isDispatching) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        Packet<?> packet = event.getPacket();

        if (mode.getValue() == 0) {
            event.setCancelled(true);
            packetQueue.add(new PacketData(packet, System.currentTimeMillis(), this.delay.getValue()));
        } else {
            if (nearestEnemyDistance > this.range.getValue()) {
            } else {
                event.setCancelled(true);
                long randomDelay = minDelay.getValue() + (long)(Math.random() * (maxDelay.getValue() - minDelay.getValue() + 1));
                packetQueue.add(new PacketData(packet, System.currentTimeMillis(), randomDelay));
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null) return;

        if (mode.getValue() == 1) {
            EntityLivingBase nearest = findNearestEnemy();
            if (nearest != null) {
                nearestEnemyDistance = RotationUtil.distanceToEntity(nearest);
            } else {
                nearestEnemyDistance = Double.MAX_VALUE;
            }

            if (nearestEnemyDistance > this.range.getValue()) {
                while (!packetQueue.isEmpty()) {
                    this.isDispatching = true;
                    PacketUtil.sendPacket(packetQueue.poll().packet);
                    this.isDispatching = false;
                }
                return;
            }
        }

        long currentTime = System.currentTimeMillis();
        while (!packetQueue.isEmpty()) {
            PacketData data = packetQueue.peek();
            if (currentTime - data.timestamp >= data.delayMs) {
                packetQueue.poll();
                this.isDispatching = true;
                PacketUtil.sendPacket(data.packet);
                this.isDispatching = false;
            } else {
                break;
            }
        }
    }

    private EntityLivingBase findNearestEnemy() {
        if (mc.theWorld == null) return null;
        return mc.theWorld.loadedEntityList.stream()
                .filter(e -> e instanceof EntityLivingBase)
                .map(e -> (EntityLivingBase) e)
                .filter(e -> e != mc.thePlayer && e.isEntityAlive() && !(e instanceof EntityPlayer && ((EntityPlayer) e).isPlayerSleeping()))
                .min(Comparator.comparingDouble(RotationUtil::distanceToEntity))
                .orElse(null);
    }

    private static class PacketData {
        private final Packet<?> packet;
        private final long timestamp;
        private final long delayMs;

        public PacketData(Packet<?> packet, long timestamp, long delayMs) {
            this.packet = packet;
            this.timestamp = timestamp;
            this.delayMs = delayMs;
        }
    }
}