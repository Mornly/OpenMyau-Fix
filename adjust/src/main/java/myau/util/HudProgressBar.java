package myau.util;

import myau.Myau;
import myau.module.modules.HUD;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;

import java.awt.*;

public final class HudProgressBar {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int WIDTH = 120;
    private static final int HEIGHT = 3;
    private static final int Y_OFFSET = 17;

    private HudProgressBar() {
    }

    public static void drawBelowCrosshair(float progress) {
        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        ScaledResolution sr = new ScaledResolution(mc);
        int x = sr.getScaledWidth() / 2 - WIDTH / 2;
        int y = sr.getScaledHeight() / 2 + Y_OFFSET;

        Gui.drawRect(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, new Color(0, 0, 0, 190).getRGB());
        Gui.drawRect(x, y, x + WIDTH, y + HEIGHT, new Color(0, 0, 0, 150).getRGB());

        int fillWidth = Math.round(WIDTH * clamped);
        if (fillWidth <= 0) {
            return;
        }

        HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
        Color leftColor = hud != null ? hud.getCustomColor1() : new Color(116, 68, 255);
        Color rightColor = hud != null ? new Color(hud.custom2.getValue()) : new Color(195, 0, 76);

        for (int px = 0; px < fillWidth; px++) {
            float position = px / (float) Math.max(1, WIDTH - 1);
            Gui.drawRect(x + px, y, x + px + 1, y + HEIGHT, ColorUtil.interpolate(position, leftColor, rightColor).getRGB());
        }
    }
}
