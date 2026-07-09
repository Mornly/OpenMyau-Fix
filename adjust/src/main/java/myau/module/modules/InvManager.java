package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.UpdateEvent;
import myau.events.WindowClickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.util.ItemUtil;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.*;
import net.minecraft.world.WorldSettings.GameType;
import org.apache.commons.lang3.RandomUtils;

import java.util.*;

public class InvManager extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty minDelay = new IntProperty("min-delay", 1, 0, 20);
    public final IntProperty maxDelay = new IntProperty("max-delay", 2, 0, 20);
    public final IntProperty openDelay = new IntProperty("open-delay", 1, 0, 20);
    public final BooleanProperty autoArmor = new BooleanProperty("auto-armor", true);
    public final IntProperty autoArmorInterval = new IntProperty("auto-armor-interval", 0, 0, 100, this.autoArmor::getValue);
    public final BooleanProperty dropTrash = new BooleanProperty("drop-trash", false);
    public final BooleanProperty checkDurability = new BooleanProperty("check-durability", true);
    public final BooleanProperty keepWaterBucket = new BooleanProperty("keep-water-bucket", true);
    public final BooleanProperty keepLavaBucket = new BooleanProperty("keep-lava-bucket", true);
    public final IntProperty swordSlot = new IntProperty("sword-slot", 1, 0, 9);
    public final IntProperty pickaxeSlot = new IntProperty("pickaxe-slot", 3, 0, 9);
    public final IntProperty shovelSlot = new IntProperty("shovel-slot", 4, 0, 9);
    public final IntProperty axeSlot = new IntProperty("axe-slot", 5, 0, 9);
    public final IntProperty blocksSlot = new IntProperty("blocks-slot", 2, 0, 9);
    public final IntProperty blocks = new IntProperty("blocks", 128, 64, 2304);
    public final IntProperty projectileSlot = new IntProperty("projectile-slot", 7, 0, 9);
    public final IntProperty projectiles = new IntProperty("projectiles", 64, 16, 2304);
    public final IntProperty goldAppleSlot = new IntProperty("gold-apple-slot", 9, 0, 9);
    public final IntProperty arrow = new IntProperty("arrow", 256, 0, 2304);
    public final IntProperty pearlSlot = new IntProperty("pearl-slot", 7, 0, 9);
    public final IntProperty bowSlot = new IntProperty("bow-slot", 8, 0, 9);
    private final TimerUtil autoArmorTime = new TimerUtil();
    private int actionDelay = 0;
    private int oDelay = 0;
    private boolean inventoryOpen = false;

    public InvManager() {
        super("InvManager", false);
    }

    private boolean isValidGameMode() {
        GameType gameType = mc.playerController.getCurrentGameType();
        return gameType == GameType.SURVIVAL || gameType == GameType.ADVENTURE;
    }

    private int convertSlotIndex(int slot) {
        if (slot >= 36) return 8 - (slot - 36);
        return slot <= 8 ? slot + 36 : slot;
    }

    private void clickSlot(int windowId, int slotId, int mouseButtonClicked, int mode) {
        mc.playerController.windowClick(windowId, slotId, mouseButtonClicked, mode, mc.thePlayer);
    }

    private int getStackSize(int slot) {
        if (slot == -1) return 0;
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
        return stack != null ? stack.stackSize : 0;
    }

    private int nextDelay() {
        if (maxDelay.getValue() == 0) return 0;
        return RandomUtils.nextInt(minDelay.getValue() + 1, maxDelay.getValue() + 2);
    }

    private boolean isWaterBucket(ItemStack stack) {
        return stack != null && stack.getItem() == Items.water_bucket;
    }

    private boolean isLavaBucket(ItemStack stack) {
        return stack != null && stack.getItem() == Items.lava_bucket;
    }

    private int findPearlSlot(int preferredSlot) {
        if (preferredSlot >= 0 && preferredSlot <= 8) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(preferredSlot);
            if (stack != null && stack.getItem() == Items.ender_pearl) return preferredSlot;
        }
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() == Items.ender_pearl) return i;
        }
        return -1;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;

        if (this.actionDelay > 0) this.actionDelay--;
        if (this.oDelay > 0) this.oDelay--;

        if (!(mc.currentScreen instanceof GuiInventory)) {
            this.inventoryOpen = false;
            return;
        }
        if (!(((GuiInventory) mc.currentScreen).inventorySlots instanceof ContainerPlayer)) {
            this.inventoryOpen = false;
            return;
        }

        if (!this.inventoryOpen) {
            this.inventoryOpen = true;
            this.oDelay = this.openDelay.getValue() + 1;
            this.autoArmorTime.reset();
            return;
        }

        if (this.oDelay > 0 || this.actionDelay > 0) return;
        if (!this.isEnabled() || !this.isValidGameMode()) return;

        ArrayList<Integer> equippedArmorSlots = new ArrayList<>(Arrays.asList(-1, -1, -1, -1));
        ArrayList<Integer> inventoryArmorSlots = new ArrayList<>(Arrays.asList(-1, -1, -1, -1));
        for (int i = 0; i < 4; i++) {
            equippedArmorSlots.set(i, ItemUtil.findArmorInventorySlot(i, true));
            inventoryArmorSlots.set(i, ItemUtil.findArmorInventorySlot(i, false));
        }

        int prefSword = swordSlot.getValue() - 1;
        int invSword = ItemUtil.findSwordInInventorySlot(prefSword, checkDurability.getValue());
        if (invSword == -1) invSword = ItemUtil.findSwordInInventorySlot(prefSword, false);

        int prefPick = pickaxeSlot.getValue() - 1;
        int invPick = ItemUtil.findInventorySlot("pickaxe", prefPick, checkDurability.getValue());
        if (invPick == -1) invPick = ItemUtil.findInventorySlot("pickaxe", prefPick, false);

        int prefShovel = shovelSlot.getValue() - 1;
        int invShovel = ItemUtil.findInventorySlot("shovel", prefShovel, checkDurability.getValue());
        if (invShovel == -1) invShovel = ItemUtil.findInventorySlot("shovel", prefShovel, false);

        int prefAxe = axeSlot.getValue() - 1;
        int invAxe = ItemUtil.findInventorySlot("axe", prefAxe, checkDurability.getValue());
        if (invAxe == -1) invAxe = ItemUtil.findInventorySlot("axe", prefAxe, false);

        int prefBlock = blocksSlot.getValue() - 1;
        int invBlock = ItemUtil.findInventorySlot(prefBlock, ItemUtil.ItemType.Block);

        int prefProj = projectileSlot.getValue() - 1;
        int invProj = ItemUtil.findInventorySlot(prefProj, ItemUtil.ItemType.Projectile);
        if (invProj == -1) invProj = ItemUtil.findInventorySlot(prefProj, ItemUtil.ItemType.FishRod);

        int prefApple = goldAppleSlot.getValue() - 1;
        int invApple = ItemUtil.findInventorySlot(prefApple, ItemUtil.ItemType.GoldApple);

        int prefBow = bowSlot.getValue() - 1;
        int invBow = ItemUtil.findBowInventorySlot(prefBow, checkDurability.getValue());
        if (invBow == -1) invBow = ItemUtil.findBowInventorySlot(prefBow, false);

        int prefPearl = pearlSlot.getValue() - 1;
        int invPearl = findPearlSlot(prefPearl);

        if (autoArmor.getValue() && autoArmorTime.hasTimeElapsed(autoArmorInterval.getValue() * 50L)) {
            for (int i = 0; i < 4; i++) {
                int eq = equippedArmorSlots.get(i);
                int inv = inventoryArmorSlots.get(i);
                if (eq != -1 || inv != -1) {
                    int armorSlot = 39 - i;
                    if (eq != armorSlot && inv != armorSlot) {
                        if (mc.thePlayer.inventory.getStackInSlot(armorSlot) != null) {
                            if (mc.thePlayer.inventory.getFirstEmptyStack() != -1) {
                                clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(armorSlot), 0, 1);
                            } else {
                                clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(armorSlot), 1, 4);
                            }
                        } else {
                            int toEquip = eq != -1 ? eq : inv;
                            clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(toEquip), 0, 1);
                            autoArmorTime.reset();
                        }
                        int d = nextDelay();
                        if (d > 0) { actionDelay = d; return; }
                    }
                }
            }
        }

        LinkedHashSet<Integer> used = new LinkedHashSet<>();

        if (prefSword >= 0 && prefSword <= 8 && invSword != -1) {
            used.add(prefSword);
            if (invSword != prefSword) {
                clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(invSword), prefSword, 2);
                int d = nextDelay();
                if (d > 0) { actionDelay = d; return; }
            }
        }
        if (prefPick >= 0 && prefPick <= 8 && !used.contains(prefPick) && invPick != -1) {
            used.add(prefPick);
            if (invPick != prefPick) {
                clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(invPick), prefPick, 2);
                int d = nextDelay();
                if (d > 0) { actionDelay = d; return; }
            }
        }
        if (prefShovel >= 0 && prefShovel <= 8 && !used.contains(prefShovel) && invShovel != -1) {
            used.add(prefShovel);
            if (invShovel != prefShovel) {
                clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(invShovel), prefShovel, 2);
                int d = nextDelay();
                if (d > 0) { actionDelay = d; return; }
            }
        }
        if (prefAxe >= 0 && prefAxe <= 8 && !used.contains(prefAxe) && invAxe != -1) {
            used.add(prefAxe);
            if (invAxe != prefAxe) {
                clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(invAxe), prefAxe, 2);
                int d = nextDelay();
                if (d > 0) { actionDelay = d; return; }
            }
        }
        if (prefBlock >= 0 && prefBlock <= 8 && !used.contains(prefBlock) && invBlock != -1) {
            used.add(prefBlock);
            if (invBlock != prefBlock) {
                clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(invBlock), prefBlock, 2);
                int d = nextDelay();
                if (d > 0) { actionDelay = d; return; }
            }
        }
        if (prefProj >= 0 && prefProj <= 8 && !used.contains(prefProj) && invProj != -1) {
            used.add(prefProj);
            if (invProj != prefProj) {
                clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(invProj), prefProj, 2);
                int d = nextDelay();
                if (d > 0) { actionDelay = d; return; }
            }
        }
        if (prefApple >= 0 && prefApple <= 8 && !used.contains(prefApple) && invApple != -1) {
            used.add(prefApple);
            if (invApple != prefApple) {
                clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(invApple), prefApple, 2);
                int d = nextDelay();
                if (d > 0) { actionDelay = d; return; }
            }
        }
        if (prefBow >= 0 && prefBow <= 8 && !used.contains(prefBow) && invBow != -1) {
            used.add(prefBow);
            if (invBow != prefBow) {
                clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(invBow), prefBow, 2);
                int d = nextDelay();
                if (d > 0) { actionDelay = d; return; }
            }
        }
        if (prefPearl >= 0 && prefPearl <= 8 && !used.contains(prefPearl) && invPearl != -1) {
            used.add(prefPearl);
            if (invPearl != prefPearl) {
                clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(invPearl), prefPearl, 2);
                int d = nextDelay();
                if (d > 0) { actionDelay = d; return; }
            }
        }

        if (dropTrash.getValue()) {
            int bestSword = ItemUtil.findSwordInInventorySlot(0, checkDurability.getValue());
            if (bestSword == -1) bestSword = ItemUtil.findSwordInInventorySlot(0, false);
            int bestPick = ItemUtil.findInventorySlot("pickaxe", 0, checkDurability.getValue());
            if (bestPick == -1) bestPick = ItemUtil.findInventorySlot("pickaxe", 0, false);
            int bestShovel = ItemUtil.findInventorySlot("shovel", 0, checkDurability.getValue());
            if (bestShovel == -1) bestShovel = ItemUtil.findInventorySlot("shovel", 0, false);
            int bestAxe = ItemUtil.findInventorySlot("axe", 0, checkDurability.getValue());
            if (bestAxe == -1) bestAxe = ItemUtil.findInventorySlot("axe", 0, false);
            int bestBow = ItemUtil.findBowInventorySlot(0, checkDurability.getValue());
            if (bestBow == -1) bestBow = ItemUtil.findBowInventorySlot(0, false);

            List<Integer> bestArmorSlots = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                int inv = ItemUtil.findArmorInventorySlot(i, false);
                if (inv != -1) bestArmorSlots.add(inv);
                int eq = ItemUtil.findArmorInventorySlot(i, true);
                if (eq != -1) bestArmorSlots.add(eq);
            }

            Set<Integer> keepSlots = new HashSet<>();
            if (bestSword != -1) keepSlots.add(bestSword);
            if (bestPick != -1) keepSlots.add(bestPick);
            if (bestShovel != -1) keepSlots.add(bestShovel);
            if (bestAxe != -1) keepSlots.add(bestAxe);
            if (bestBow != -1) keepSlots.add(bestBow);
            keepSlots.addAll(bestArmorSlots);

            int curBlock = ItemUtil.findInventorySlot(0, ItemUtil.ItemType.Block);
            int curProj = ItemUtil.findInventorySlot(0, ItemUtil.ItemType.Projectile);
            if (curProj == -1) curProj = ItemUtil.findInventorySlot(0, ItemUtil.ItemType.FishRod);
            int curApple = ItemUtil.findInventorySlot(0, ItemUtil.ItemType.GoldApple);
            int curArrow = ItemUtil.findInventorySlot(0, ItemUtil.ItemType.Arrow);
            int curPearl = findPearlSlot(0);
            if (curBlock != -1) keepSlots.add(curBlock);
            if (curProj != -1) keepSlots.add(curProj);
            if (curApple != -1) keepSlots.add(curApple);
            if (curArrow != -1) keepSlots.add(curArrow);
            if (curPearl != -1) keepSlots.add(curPearl);

            int currentBlockCount = getStackSize(curBlock);
            int currentProjectileCount = getStackSize(curProj);

            for (int i = 0; i < 36; i++) {
                if (keepSlots.contains(i)) continue;

                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                if (stack == null) continue;

                Item item = stack.getItem();

                if (item instanceof ItemSword || item instanceof ItemTool || item instanceof ItemBow || item instanceof ItemArmor) {
                    clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(i), 1, 4);
                    int d = nextDelay();
                    if (d > 0) { actionDelay = d; return; }
                    continue;
                }

                if (keepWaterBucket.getValue() && isWaterBucket(stack)) continue;
                if (keepLavaBucket.getValue() && isLavaBucket(stack)) continue;

                boolean isBlock = ItemUtil.isBlock(stack);
                boolean isProjectile = ItemUtil.isProjectile(stack);
                boolean shouldDrop = false;

                if (isBlock) {
                    currentBlockCount += stack.stackSize;
                    if (currentBlockCount > this.blocks.getValue()) shouldDrop = true;
                } else if (isProjectile) {
                    currentProjectileCount += stack.stackSize;
                    if (currentProjectileCount > this.projectiles.getValue()) shouldDrop = true;
                } else if (isNotSpecialItem(stack)) {
                    shouldDrop = true;
                }

                if (shouldDrop) {
                    clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(i), 1, 4);
                    int d = nextDelay();
                    if (d > 0) { actionDelay = d; return; }
                }
            }
        }
    }

    @EventTarget
    public void onClick(WindowClickEvent event) {
        if (maxDelay.getValue() != 0) {
            this.actionDelay = RandomUtils.nextInt(minDelay.getValue() + 1, maxDelay.getValue() + 2);
        }
    }

    @Override
    public void verifyValue(String mode) {
        switch (mode) {
            case "min-delay":
                if (minDelay.getValue() > maxDelay.getValue())
                    maxDelay.setValue(minDelay.getValue());
                break;
            case "max-delay":
                if (minDelay.getValue() > maxDelay.getValue())
                    minDelay.setValue(maxDelay.getValue());
                break;
        }
    }

    private static boolean isNotSpecialItem(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ItemArmor) return false;
        if (item instanceof ItemSword) return false;
        if (item instanceof ItemTool) return false;
        if (item instanceof ItemBow) return false;
        if (item instanceof ItemFishingRod) return false;
        if (item == Items.golden_apple) return false;
        if (item == Items.ender_pearl) return false;
        if (item == Items.arrow) return false;
        if (ItemUtil.isBlock(stack)) return false;
        if (ItemUtil.isProjectile(stack)) return false;
        return true;
    }
}