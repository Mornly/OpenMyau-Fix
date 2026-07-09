package myau.ui.components;

import myau.property.properties.ColorProperty;
import myau.ui.ClickGui;
import myau.ui.Component;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.atomic.AtomicInteger;

public class ColorSliderComponent implements Component {
    private final ModuleComponent parentModule;
    private final ColorProperty property;
    private int offsetY;
    private boolean draggingHue, draggingSat, draggingBri;
    private float hue, saturation, brightness;
    private boolean hovered;
    private float hoverAlpha;

    public ColorSliderComponent(ColorProperty property, ModuleComponent parentModule, int offsetY) {
        this.parentModule = parentModule;
        this.offsetY = offsetY;
        this.property = property;

        Color c = new Color(property.getValue());
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
    }

    @Override
    public void draw(AtomicInteger offset) {
        int baseX = parentModule.getSettingX();
        int baseY = parentModule.category.getY() + offsetY;
        int width = parentModule.getSettingWidth();
        int trackX = baseX + 8;
        int trackWidth = width - 16;

        FontRenderer fr = ClickGui.getInstance().getCurrentRenderer();
        hoverAlpha = ClickGui.animate(hoverAlpha, hovered ? 1.0F : 0.0F, 0.22F);
        Gui.drawRect(baseX, baseY, baseX + width, baseY + getHeight(), new Color(0, 0, 0, 50 + (int) (hoverAlpha * 32.0F)).getRGB());
        String label = ClickGui.trimToChars(property.getName().replace("-", " "), 15);
        ClickGui.drawText(fr, label, baseX + 8, baseY + 2, new Color(215, 215, 215).getRGB());

        if (!draggingHue && !draggingSat && !draggingBri) {
            Color color = new Color(property.getValue());
            float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
            hue = hsb[0];
            saturation = hsb[1];
            brightness = hsb[2];
        }

        int previewColor = Color.HSBtoRGB(hue, saturation, brightness);
        Gui.drawRect(baseX + width - 15, baseY + 4, baseX + width - 7, baseY + 12, previewColor);

        int hueY = baseY + 15;
        int satY = hueY + 6;
        int briY = satY + 6;
        drawHueBar(trackX, hueY, trackWidth);
        drawPointer(trackX, hueY, trackWidth, hue);
        drawGradientRect(trackX, satY, trackX + trackWidth, satY + 4, Color.WHITE.getRGB(), Color.getHSBColor(hue, 1f, 1f).getRGB());
        drawPointer(trackX, satY, trackWidth, saturation);
        drawGradientRect(trackX, briY, trackX + trackWidth, briY + 4, Color.BLACK.getRGB(), Color.getHSBColor(hue, saturation, 1f).getRGB());
        drawPointer(trackX, briY, trackWidth, brightness);
    }

    private void drawHueBar(int x, int y, int width) {
        for (int i = 0; i < width; i++) {
            float hue = (float) i / (float) width;
            int color = Color.HSBtoRGB(hue, 1f, 1f);
            Gui.drawRect(x + i, y, x + i + 1, y + 4, color);
        }
    }

    private void drawPointer(int x, int y, int width, float value) {
        int posX = x + (int) (width * value);
        Gui.drawRect(posX - 1, y - 1, posX + 1, y + 5, new Color(235, 235, 235).getRGB());
    }

    @Override
    public void update(int mouseX, int mouseY) {
        int baseX = parentModule.getSettingX() + 8;
        int width = parentModule.getSettingWidth() - 16;
        int rowX = parentModule.getSettingX();
        int rowY = parentModule.category.getY() + offsetY;
        hovered = mouseX >= rowX && mouseX <= rowX + parentModule.getSettingWidth()
                && mouseY >= rowY && mouseY <= rowY + getHeight();
        boolean changed = false;

        if (draggingHue) {
            hue = getSliderValue(mouseX, baseX, width);
            changed = true;
        }
        if (draggingSat) {
            saturation = getSliderValue(mouseX, baseX, width);
            changed = true;
        }
        if (draggingBri) {
            brightness = getSliderValue(mouseX, baseX, width);
            changed = true;
        }

        if (changed) {
            int signed = Color.HSBtoRGB(hue, saturation, brightness);
            property.setValue(new Color(signed).getRGB());
        }
    }

    private float getSliderValue(int mouseX, int startX, int width) {
        double d = Math.min(width, Math.max(0, mouseX - startX));
        return (float) roundToPrecision(d / width, 3);
    }

    private static double roundToPrecision(double v, int precision) {
        BigDecimal bd = new BigDecimal(v);
        bd = bd.setScale(precision, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    @Override
    public void mouseDown(int mouseX, int mouseY, int button) {
        if (button != 0 || !parentModule.panelExpand) {
            return;
        }
        int baseY = parentModule.category.getY() + offsetY + 15;
        if (isHovered(mouseX, mouseY, baseY)) {
            draggingHue = true;
        } else if (isHovered(mouseX, mouseY, baseY + 6)) {
            draggingSat = true;
        } else if (isHovered(mouseX, mouseY, baseY + 12)) {
            draggingBri = true;
        }
    }

    @Override
    public void mouseReleased(int x, int y, int button) {
        draggingHue = draggingSat = draggingBri = false;
    }

    private boolean isHovered(int mx, int my, int sliderY) {
        int startX = parentModule.getSettingX() + 8;
        int endX = startX + parentModule.getSettingWidth() - 16;
        return mx >= startX && mx <= endX && my >= sliderY && my <= sliderY + 4;
    }

    @Override
    public boolean isVisible() {
        return property.isVisible();
    }

    @Override
    public void keyTyped(char chatTyped, int keyCode) {
    }

    @Override
    public void setComponentStartAt(int newOffsetY) {
        offsetY = newOffsetY;
    }

    @Override
    public int getHeight() {
        return 32;
    }

    private void drawGradientRect(int left, int top, int right, int bottom, int startColor, int endColor) {
        float sa = (float) (startColor >> 24 & 255) / 255.0F;
        float sr = (float) (startColor >> 16 & 255) / 255.0F;
        float sg = (float) (startColor >> 8 & 255) / 255.0F;
        float sb = (float) (startColor & 255) / 255.0F;
        float ea = (float) (endColor >> 24 & 255) / 255.0F;
        float er = (float) (endColor >> 16 & 255) / 255.0F;
        float eg = (float) (endColor >> 8 & 255) / 255.0F;
        float eb = (float) (endColor & 255) / 255.0F;
        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer world = tessellator.getWorldRenderer();
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_ALPHA_TEST);
        org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
        org.lwjgl.opengl.GL11.glShadeModel(org.lwjgl.opengl.GL11.GL_SMOOTH);
        world.begin(7, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
        world.pos(right, top, 0).color(er, eg, eb, ea).endVertex();
        world.pos(left, top, 0).color(sr, sg, sb, sa).endVertex();
        world.pos(left, bottom, 0).color(sr, sg, sb, sa).endVertex();
        world.pos(right, bottom, 0).color(er, eg, eb, ea).endVertex();
        tessellator.draw();
        org.lwjgl.opengl.GL11.glShadeModel(org.lwjgl.opengl.GL11.GL_FLAT);
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_BLEND);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_ALPHA_TEST);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
    }
}
