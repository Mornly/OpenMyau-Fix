package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;

import java.awt.*;
import java.util.concurrent.ConcurrentLinkedDeque;

public class Notifications extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final ConcurrentLinkedDeque<NotificationEntry> queue = new ConcurrentLinkedDeque<>();
    private static final float ANIMATION_SPEED = 0.15f;

    public Notifications() {
        super("Notifications", false);
    }

    public static void postToggle(Module module, boolean enabled) {
        if (module instanceof Notifications) return;
        Notifications self = (Notifications) Myau.moduleManager.getModule(Notifications.class);
        if (self == null || !self.isEnabled()) return;
        String msg = (enabled ? "Enabled " : "Disabled ") + module.getName();
        queue.add(new NotificationEntry(msg, 2000L, enabled));
    }

    private float lerp(float current, float target, float speed) {
        return current + (target - current) * speed;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (queue.isEmpty()) return;

        ScaledResolution sr = new ScaledResolution(mc);
        float screenRight = sr.getScaledWidth();
        float screenBottom = sr.getScaledHeight();

        java.util.Iterator<NotificationEntry> it = queue.iterator();
        while (it.hasNext()) {
            NotificationEntry entry = it.next();
            int textWidth = mc.fontRendererObj.getStringWidth(entry.message);
            int textHeight = mc.fontRendererObj.FONT_HEIGHT;
            int padding = 6;
            int borderWidth = 3;
            int barHeight = 2;
            entry.boxWidth = textWidth + padding * 2 + borderWidth * 2 - 5;
            entry.boxHeight = textHeight + padding * 2 + borderWidth * 2 + barHeight - 5;

            if (entry.isFinished()) {
                entry.targetX = screenRight + 10;
                if (Math.abs(entry.animationX - entry.targetX) < 1.0f) {
                    it.remove();
                    continue;
                }
            } else {
                entry.targetX = screenRight - entry.boxWidth - 10;
            }
        }

        float currentY = screenBottom - 35;
        for (NotificationEntry entry : queue) {
            float yPos = currentY - entry.boxHeight;
            entry.targetY = yPos;
            currentY = yPos - 6;
        }

        for (NotificationEntry entry : queue) {
            if (entry.animationX == 0 && entry.targetX != 0) {
                entry.animationX = screenRight + 10;
            }
            if (entry.animationY == 0 && entry.targetY != 0) {
                entry.animationY = entry.targetY;
            }
            entry.animationX = lerp(entry.animationX, entry.targetX, ANIMATION_SPEED);
            entry.animationY = lerp(entry.animationY, entry.targetY, ANIMATION_SPEED);
        }

        for (NotificationEntry entry : queue) {
            float xPos = entry.animationX;
            float yPos = entry.animationY;

            int bgColor = new Color(0, 0, 0, 220).getRGB();
            int borderColor = new Color(26, 26, 26).getRGB();

            Gui.drawRect((int) xPos, (int) yPos, (int) (xPos + entry.boxWidth), (int) (yPos + entry.boxHeight), bgColor);
            Gui.drawRect((int) xPos, (int) yPos, (int) (xPos + entry.boxWidth), (int) (yPos + 3), borderColor);
            Gui.drawRect((int) xPos, (int) (yPos + entry.boxHeight - 3), (int) (xPos + entry.boxWidth), (int) (yPos + entry.boxHeight), borderColor);
            Gui.drawRect((int) xPos, (int) yPos, (int) (xPos + 3), (int) (yPos + entry.boxHeight), borderColor);
            Gui.drawRect((int) (xPos + entry.boxWidth - 3), (int) yPos, (int) (xPos + entry.boxWidth), (int) (yPos + entry.boxHeight), borderColor);

            float progress = entry.getProgress();
            float progressWidth = entry.boxWidth * progress;
            float progressX = xPos;
            float progressY = yPos + entry.boxHeight - 2;

            int progressColor = entry.isEnabled ? new Color(0, 255, 0).getRGB() : new Color(255, 0, 0).getRGB();
            Gui.drawRect((int) progressX, (int) progressY, (int) (progressX + progressWidth), (int) (progressY + 2), progressColor);

            String text = entry.message;
            int prefixColor = entry.isEnabled ? new Color(0, 255, 0).getRGB() : new Color(255, 0, 0).getRGB();
            int moduleColor = 0xFFFFFF;

            String prefix = entry.isEnabled ? "Enabled " : "Disabled ";
            String moduleName = text.substring(prefix.length());

            int prefixWidth = mc.fontRendererObj.getStringWidth(prefix);
            int moduleWidth = mc.fontRendererObj.getStringWidth(moduleName);
            int totalWidth = prefixWidth + moduleWidth;
            float textX = xPos + (entry.boxWidth - totalWidth) / 2;
            float textY = yPos + (entry.boxHeight - mc.fontRendererObj.FONT_HEIGHT) / 2;

            mc.fontRendererObj.drawString(prefix, textX, textY, prefixColor, true);
            mc.fontRendererObj.drawString(moduleName, textX + prefixWidth, textY, moduleColor, true);
        }
    }

    private static class NotificationEntry {
        private final String message;
        private final long startTime;
        private final long duration;
        private final boolean isEnabled;
        private float animationX;
        private float targetX;
        private float animationY;
        private float targetY;
        private int boxWidth;
        private int boxHeight;

        public NotificationEntry(String message, long duration, boolean isEnabled) {
            this.message = message;
            this.duration = duration;
            this.isEnabled = isEnabled;
            this.startTime = System.currentTimeMillis();
            this.animationX = 0;
            this.targetX = 0;
            this.animationY = 0;
            this.targetY = 0;
        }

        public boolean isFinished() {
            return System.currentTimeMillis() - startTime > duration;
        }

        public float getProgress() {
            long elapsed = System.currentTimeMillis() - startTime;
            return Math.min(1.0f, (float) elapsed / duration);
        }
    }
}