package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
<<<<<<< HEAD
import myau.events.SwapItemEvent;
=======
>>>>>>> e7b1dde9d663c728a1ea63266e41a22ddd0a1e61
import myau.module.Module;
import myau.util.ItemUtil;
import myau.util.KeyBindUtil;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public class AutoTool extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int currentToolSlot = -1;
    private int previousSlot = -1;
    private int tickDelayCounter = 0;
<<<<<<< HEAD
    private int clientSlot = -1;
    public final IntProperty switchDelay = new IntProperty("delay", 0, 0, 5);
    public final BooleanProperty switchBack = new BooleanProperty("switch-back", true);
    public final BooleanProperty sneakOnly = new BooleanProperty("sneak-only", true);
    public final BooleanProperty itemSpoof = new BooleanProperty("item-spoof", false);
=======
    public final IntProperty switchDelay = new IntProperty("delay", 0, 0, 5);
    public final BooleanProperty switchBack = new BooleanProperty("switch-back", true);
    public final BooleanProperty sneakOnly = new BooleanProperty("sneak-only", true);
>>>>>>> e7b1dde9d663c728a1ea63266e41a22ddd0a1e61

    public AutoTool() {
        super("AutoTool", false);
    }

    public boolean isKillAura() {
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (!killAura.isEnabled()) return false;
        return TeamUtil.isEntityLoaded(killAura.getTarget()) && killAura.isAttackAllowed();
    }

<<<<<<< HEAD
    public int getSlot() {
        return this.clientSlot;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
=======
    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (this.currentToolSlot != -1 && this.currentToolSlot != mc.thePlayer.inventory.currentItem) {
                this.currentToolSlot = -1;
                this.previousSlot = -1;
            }
>>>>>>> e7b1dde9d663c728a1ea63266e41a22ddd0a1e61
            if (mc.objectMouseOver != null
                    && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK
                    && mc.gameSettings.keyBindAttack.isKeyDown()
                    && !mc.thePlayer.isUsingItem()
                    && !isKillAura()) {
                if (this.tickDelayCounter >= this.switchDelay.getValue()
                        && (!(Boolean) this.sneakOnly.getValue() || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()))) {
                    int slot = ItemUtil.findInventorySlot(
                            mc.thePlayer.inventory.currentItem, mc.theWorld.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock()
                    );
<<<<<<< HEAD
                    if (this.itemSpoof.getValue()) {
                        if (this.clientSlot == -1) {
                            this.clientSlot = mc.thePlayer.inventory.currentItem;
                        }
                        if (mc.thePlayer.inventory.currentItem != slot) {
                            mc.thePlayer.inventory.currentItem = slot;
                        }
                    } else {
                        if (mc.thePlayer.inventory.currentItem != slot) {
                            if (this.previousSlot == -1) {
                                this.previousSlot = mc.thePlayer.inventory.currentItem;
                            }
                            mc.thePlayer.inventory.currentItem = slot;
                        }
=======
                    if (mc.thePlayer.inventory.currentItem != slot) {
                        if (this.previousSlot == -1) {
                            this.previousSlot = mc.thePlayer.inventory.currentItem;
                        }
                        mc.thePlayer.inventory.currentItem = this.currentToolSlot = slot;
>>>>>>> e7b1dde9d663c728a1ea63266e41a22ddd0a1e61
                    }
                }
                this.tickDelayCounter++;
            } else {
<<<<<<< HEAD
                if (this.itemSpoof.getValue() && this.clientSlot != -1) {
                    mc.thePlayer.inventory.currentItem = this.clientSlot;
                } else if (this.switchBack.getValue() && this.previousSlot != -1) {
=======
                if (this.switchBack.getValue() && this.previousSlot != -1) {
>>>>>>> e7b1dde9d663c728a1ea63266e41a22ddd0a1e61
                    mc.thePlayer.inventory.currentItem = this.previousSlot;
                }
                this.currentToolSlot = -1;
                this.previousSlot = -1;
<<<<<<< HEAD
                this.clientSlot = -1;
=======
>>>>>>> e7b1dde9d663c728a1ea63266e41a22ddd0a1e61
                this.tickDelayCounter = 0;
            }
        }
    }

<<<<<<< HEAD
    @EventTarget
    public void onSwap(SwapItemEvent event) {
        if (this.isEnabled() && this.itemSpoof.getValue() && this.clientSlot != -1) {
            this.clientSlot = event.setSlot(this.clientSlot);
            event.setCancelled(true);
        }
    }

    @Override
    public void onDisabled() {
        if (this.clientSlot != -1) {
            mc.thePlayer.inventory.currentItem = this.clientSlot;
        }
        this.currentToolSlot = -1;
        this.previousSlot = -1;
        this.clientSlot = -1;
=======
    @Override
    public void onDisabled() {
        this.currentToolSlot = -1;
        this.previousSlot = -1;
>>>>>>> e7b1dde9d663c728a1ea63266e41a22ddd0a1e61
        this.tickDelayCounter = 0;
    }
}
