package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.util.ColorUtil;
import myau.util.RenderUtil;
import myau.util.render.PostProcessing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.concurrent.ConcurrentLinkedDeque;

public class Notifications extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final ConcurrentLinkedDeque<NotificationEntry> queue = new ConcurrentLinkedDeque<>();
    private static final float SCALE = 0.9F;
    private static final float ANIMATION_SPEED = 0.22F;
    private static final float ALPHA_SPEED = 0.18F;
    private static final String TITLE = "Module";
    private static final int MIN_WIDTH = 160;
    private static final int BOX_HEIGHT = 25;
    private static final int RIGHT_MARGIN = 8;
    private static final int BOTTOM_MARGIN = 42;
    private static final int GAP = 5;
    public Notifications() {
        super("Notifications", false);
    }

    public static void postToggle(Module module, boolean enabled) {
        if (module instanceof Notifications) return;
        Notifications self = (Notifications) Myau.moduleManager.getModule(Notifications.class);
        if (self == null || !self.isEnabled()) return;
        queue.add(new NotificationEntry(module.getName(), enabled, 2000L));
    }

    private float lerp(float current, float target, float speed) {
        return current + (target - current) * speed;
    }

    private FontRenderer getFont() {
        HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
        return hud != null ? hud.getArrayListFontRenderer() : mc.fontRendererObj;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (queue.isEmpty()) return;

        ScaledResolution sr = new ScaledResolution(mc);
        FontRenderer font = getFont();
        float screenRight = sr.getScaledWidth();
        float screenBottom = sr.getScaledHeight();

        java.util.Iterator<NotificationEntry> it = queue.iterator();
        while (it.hasNext()) {
            NotificationEntry entry = it.next();
            int textWidth = Math.max(font.getStringWidth(TITLE), font.getStringWidth(entry.moduleName) + 4 + font.getStringWidth(entry.statusText));
            entry.boxWidth = Math.round(Math.max(MIN_WIDTH, textWidth + 18) * SCALE);
            entry.boxHeight = Math.round(BOX_HEIGHT * SCALE);

            if (entry.isFinished()) {
                entry.targetX = screenRight - entry.boxWidth - RIGHT_MARGIN;
                entry.targetY = screenBottom + GAP;
                entry.targetAlpha = 0.0F;
                if (Math.abs(entry.animationY - entry.targetY) < 1.0F && entry.alpha < 0.04F) {
                    it.remove();
                    continue;
                }
            } else {
                entry.targetX = screenRight - entry.boxWidth - RIGHT_MARGIN;
                entry.targetAlpha = 1.0F;
            }
        }

        float currentY = sr.getScaledHeight() - BOTTOM_MARGIN;
        for (NotificationEntry entry : queue) {
            if (entry.isFinished()) {
                continue;
            }
            entry.targetY = currentY - entry.boxHeight;
            currentY -= entry.boxHeight + GAP * SCALE;
        }

        for (NotificationEntry entry : queue) {
            if (!entry.initialized) {
                entry.animationX = entry.targetX;
                entry.animationY = screenBottom + GAP;
                entry.alpha = 0.0F;
                entry.initialized = true;
            }
            entry.animationX = lerp(entry.animationX, entry.targetX, ANIMATION_SPEED);
            entry.animationY = lerp(entry.animationY, entry.targetY, ANIMATION_SPEED);
            entry.alpha = lerp(entry.alpha, entry.targetAlpha, ALPHA_SPEED);
        }

        drawNotificationBlur();

        for (NotificationEntry entry : queue) {
            float xPos = entry.animationX;
            float yPos = entry.animationY;
            float alpha = Math.max(0.0F, Math.min(1.0F, entry.alpha));
            if (alpha <= 0.02F) {
                continue;
            }
            int left = Math.round(xPos);
            int top = Math.round(yPos);
            int right = left + entry.boxWidth;
            int bottom = top + entry.boxHeight;

            drawNotificationBackground(left, top, right, bottom, alpha);
            drawNotificationFrame(left, top, right, bottom, alpha);

            float textX = left + 11.0F * SCALE;
            drawScaledString(font, TITLE, textX, top + 2.0F * SCALE, hudColor(0.5F, (int) (205.0F * alpha)));
            drawScaledString(font, entry.moduleName, textX, top + 13.0F * SCALE, alphaColor(255, 255, 255, (int) (245.0F * alpha)));
            drawScaledString(font, entry.statusText, textX + (font.getStringWidth(entry.moduleName) + 4.0F) * SCALE, top + 13.0F * SCALE, alphaColor(255, 255, 255, (int) (245.0F * alpha)));
        }
    }

    private void drawNotificationBackground(int left, int top, int right, int bottom, float alpha) {
        drawCrispRect(left, top, right, bottom, alphaColor(0, 0, 0, (int) (148.0F * alpha)));
    }

    private void drawNotificationFrame(int left, int top, int right, int bottom, float alpha) {
        int frameAlpha = (int) (178.0F * alpha);
        drawHudGradient(left, top, right, top + 1, frameAlpha);
        drawHudGradient(left, bottom - 1, right, bottom, frameAlpha);
        drawCrispRect(left, top, left + 1, bottom, hudColor(0.0F, frameAlpha));
        drawCrispRect(right - 1, top, right, bottom, hudColor(1.0F, frameAlpha));
    }

    private void drawNotificationBlur() {
        int leftBound = Integer.MAX_VALUE;
        int topBound = Integer.MAX_VALUE;
        int rightBound = Integer.MIN_VALUE;
        int bottomBound = Integer.MIN_VALUE;
        boolean hasVisible = false;

        for (NotificationEntry entry : queue) {
            float alpha = Math.max(0.0F, Math.min(1.0F, entry.alpha));
            if (alpha <= 0.02F) {
                continue;
            }

            int left = Math.round(entry.animationX);
            int top = Math.round(entry.animationY);
            int right = left + entry.boxWidth;
            int bottom = top + entry.boxHeight;
            leftBound = Math.min(leftBound, left);
            topBound = Math.min(topBound, top);
            rightBound = Math.max(rightBound, right);
            bottomBound = Math.max(bottomBound, bottom);
            hasVisible = true;
        }

        if (!hasVisible) {
            return;
        }

        final int blurLeft = leftBound;
        final int blurTop = topBound;
        final int blurRight = rightBound;
        final int blurBottom = bottomBound;
        PostProcessing.drawBlur(blurLeft, blurTop, blurRight, blurBottom, () -> () -> {
            GlStateManager.enableBlend();
            GlStateManager.disableTexture2D();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            RenderUtil.setup2DRendering(() -> {
                for (NotificationEntry entry : queue) {
                    float alpha = Math.max(0.0F, Math.min(1.0F, entry.alpha));
                    if (alpha <= 0.02F) {
                        continue;
                    }
                    int left = Math.round(entry.animationX);
                    int top = Math.round(entry.animationY);
                    Gui.drawRect(left, top, left + entry.boxWidth, top + entry.boxHeight, -1);
                }
            });
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
        });
    }

    private void drawHudGradient(int left, int top, int right, int bottom, int alpha) {
        if (right <= left || bottom <= top || alpha <= 0) {
            return;
        }

        for (int x = left; x < right; x++) {
            float progress = (x - left) / (float) Math.max(1, right - left - 1);
            drawCrispRect(x, top, x + 1, bottom, hudColor(progress, alpha));
        }
    }

    private Color hudColorObject(boolean right) {
        HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
        if (hud != null) {
            return right ? new Color(hud.custom2.getValue()) : hud.getCustomColor1();
        }
        return Color.WHITE;
    }

    private int hudColor(float progress, int alpha) {
        Color color = ColorUtil.interpolate(Math.max(0.0F, Math.min(1.0F, progress)), hudColorObject(false), hudColorObject(true));
        return alphaColor(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private void drawCrispRect(float left, float top, float right, float bottom, int color) {
        int rectLeft = Math.round(left);
        int rectTop = Math.round(top);
        int rectRight = Math.round(right);
        int rectBottom = Math.round(bottom);
        if (rectRight <= rectLeft) {
            rectRight = rectLeft + 1;
        }
        if (rectBottom <= rectTop) {
            rectBottom = rectTop + 1;
        }
        Gui.drawRect(rectLeft, rectTop, rectRight, rectBottom, color);
    }

    private void drawScaledString(FontRenderer font, String text, float x, float y, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.scale(SCALE, SCALE, 1.0F);
        font.drawString(text, x / SCALE, y / SCALE, color, false);
        GlStateManager.popMatrix();
    }

    private int alphaColor(int red, int green, int blue, int alpha) {
        return new Color(red, green, blue, Math.max(0, Math.min(255, alpha))).getRGB();
    }

    private static class NotificationEntry {
        private final String moduleName;
        private final String statusText;
        private final long startTime;
        private final long duration;
        private float animationX;
        private float targetX;
        private float animationY;
        private float targetY;
        private int boxWidth;
        private int boxHeight;
        private float alpha;
        private float targetAlpha;
        private boolean initialized;

        public NotificationEntry(String moduleName, boolean enabled, long duration) {
            this.moduleName = moduleName;
            this.statusText = enabled ? "Enabled" : "Disabled";
            this.duration = duration;
            this.startTime = System.currentTimeMillis();
            this.animationX = 0;
            this.targetX = 0;
            this.animationY = 0;
            this.targetY = 0;
            this.alpha = 0.0F;
            this.targetAlpha = 1.0F;
            this.initialized = false;
        }

        public boolean isFinished() {
            return System.currentTimeMillis() - startTime > duration;
        }
    }
}
