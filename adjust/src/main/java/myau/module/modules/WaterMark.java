package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;

import java.awt.*;

public class WaterMark extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty watermarkMode = new ModeProperty("WatermarkMode", 0, new String[]{"Myau", "Adjust"});

    public WaterMark() {
        super("WaterMark", false, false);
    }

    private FontRenderer getFontRenderer() {
        HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
        return hud != null ? hud.getArrayListFontRenderer() : mc.fontRendererObj;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled()) return;
        renderExhibition();
    }

    private void renderExhibition() {
        if (watermarkMode.getValue() == 0) { // Myau
            renderMyau();
        } else { // Adjust
            renderAdjust();
        }
    }

    private void renderMyau() {
        int fps = Minecraft.getDebugFPS();
        int ping = 0;

        if (mc.thePlayer != null && mc.theWorld != null) {
            if (mc.thePlayer.sendQueue != null
                    && mc.thePlayer.sendQueue.getPlayerInfo(mc.thePlayer.getUniqueID()) != null) {
                ping = mc.thePlayer.sendQueue.getPlayerInfo(mc.thePlayer.getUniqueID()).getResponseTime();
            }
        }

        String exhibitionText = "O";
        String restText = "PenMyau-Fix ";
        String fpsValue = fps + "FPS";
        String pingValue = ping + "ms";

        HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
        FontRenderer font = getFontRenderer();

        float x = 2.0f;
        float y = 2.0f;

        GlStateManager.pushMatrix();

        long time = System.currentTimeMillis();
        int rainbowColor = hud != null ? hud.getColor(time).getRGB() : 0xFFFFFFFF;

        font.drawStringWithShadow(exhibitionText, x, y, rainbowColor);
        float currentX = x + font.getStringWidth(exhibitionText);

        int whiteColor = 0xFFFFFFFF;
        font.drawStringWithShadow(restText, currentX, y, whiteColor);
        currentX += font.getStringWidth(restText);

        int grayColor = 0xFFAAAAAA;
        font.drawStringWithShadow("[", currentX, y, grayColor);
        currentX += font.getStringWidth("[");

        font.drawStringWithShadow(fpsValue, currentX, y, whiteColor);
        currentX += font.getStringWidth(fpsValue);

        font.drawStringWithShadow("]", currentX, y, grayColor);
        currentX += font.getStringWidth("]");

        String space = " ";
        font.drawStringWithShadow(space, currentX, y, whiteColor);
        currentX += font.getStringWidth(space);

        font.drawStringWithShadow("[", currentX, y, grayColor);
        currentX += font.getStringWidth("[");

        font.drawStringWithShadow(pingValue, currentX, y, whiteColor);
        currentX += font.getStringWidth(pingValue);

        font.drawStringWithShadow("]", currentX, y, grayColor);

        GlStateManager.popMatrix();
    }

    private void renderAdjust() {
        HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
        FontRenderer font = getFontRenderer();

        float x = 4.0f;
        float y = 2.0f;
        float joinGap = 0.15f;

        GlStateManager.pushMatrix();

        int customColor1 = hud != null ? hud.getCustomColor1().getRGB() : 0xFFFFFFFF;
        int whiteColor = 0xFFFFFFFF;

        drawAdjustA(font, x, y, customColor1);
        font.drawString("djust", x + font.getStringWidth("\u00a7lA\u00a7r") + joinGap, y, whiteColor, false);

        GlStateManager.popMatrix();
    }

    private void drawAdjustA(FontRenderer font, float x, float y, int color) {
        Color c = new Color(color, true);
        int shadowColor = new Color(c.getRed(), c.getGreen(), c.getBlue(), 128).getRGB();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.35F, y + 0.35F, 0.0F);
        GlStateManager.scale(1.035F, 1.035F, 1.0F);
        font.drawString("\u00a7lA\u00a7r", 0.0F, 0.0F, shadowColor, false);
        GlStateManager.popMatrix();

        font.drawString("\u00a7lA\u00a7r", x, y, color, false);
    }
}
