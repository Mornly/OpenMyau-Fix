package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.font.UFontRenderer;
import myau.module.Module;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;

public class WaterMark extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty fontMode = new ModeProperty("FontMode", 0, new String[]{"Minecraft", "Modern"});

    private UFontRenderer modernFont;
    private boolean modernFontLoaded = false;

    public WaterMark() {
        super("WaterMark", false, false);
    }

    private FontRenderer getFontRenderer() {
        if (fontMode.getValue() == 1) { // Modern
            if (!modernFontLoaded) {
                try {
                    modernFont = new UFontRenderer("GoogleSans-Regular", 18);
                } catch (Exception e) {
                    modernFont = null;
                }
                modernFontLoaded = true;
            }
            if (modernFont != null) {
                return modernFont;
            }
        }
        return mc.fontRendererObj;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled()) return;
        renderExhibition();
    }

    private void renderExhibition() {
        int fps = Minecraft.getDebugFPS();
        int ping = 0;

        if (mc.thePlayer != null && mc.theWorld != null) {
            if (mc.thePlayer.sendQueue != null
                    && mc.thePlayer.sendQueue.getPlayerInfo(mc.thePlayer.getUniqueID()) != null) {
                ping = mc.thePlayer.sendQueue.getPlayerInfo(mc.thePlayer.getUniqueID()).getResponseTime();
            }
        }

        String exhibitionText = "M";
        String restText = "yau-Fix ";
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
}