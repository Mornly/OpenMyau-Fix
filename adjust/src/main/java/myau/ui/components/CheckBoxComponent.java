package myau.ui.components;

import myau.property.properties.BooleanProperty;
import myau.ui.ClickGui;
import myau.ui.Component;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CheckBoxComponent implements Component {
    private final BooleanProperty property;
    private final ModuleComponent module;
    private int offsetY;
    private int x;
    private int y;
    private boolean hovered;
    private float hoverAlpha;

    public CheckBoxComponent(BooleanProperty property, ModuleComponent parentModule, int offsetY) {
        this.property = property;
        this.module = parentModule;
        this.x = parentModule.category.getX();
        this.y = parentModule.category.getY() + parentModule.offsetY;
        this.offsetY = offsetY;
    }

    public void draw(AtomicInteger offset) {
        FontRenderer fr = ClickGui.getInstance().getCurrentRenderer();
        int baseX = this.module.getSettingX();
        int baseY = this.module.category.getY() + this.offsetY;
        int width = this.module.getSettingWidth();
        int accent = ClickGui.getHudColor((long) (baseX + baseY + offset.get()) * 18L);
        String value = this.property.getValue() ? "On" : "Off";

        hoverAlpha = ClickGui.animate(hoverAlpha, hovered ? 1.0F : 0.0F, 0.22F);
        Gui.drawRect(baseX, baseY, baseX + width, baseY + getHeight(), new Color(0, 0, 0, 50 + (int) (hoverAlpha * 32.0F)).getRGB());
        Gui.drawRect(baseX + 9, baseY + 5, baseX + 13, baseY + 9, this.property.getValue() ? ClickGui.withAlpha(accent, 230) : new Color(58, 58, 58, 210).getRGB());

        String rawName = this.property.getName().replace("-", " ");
        String name = ClickGui.trimToChars(rawName, 15);
        int labelColor = this.property.getValue()
                ? new Color(216, 216, 216).getRGB()
                : new Color(126 + (int) (hoverAlpha * 35.0F), 126 + (int) (hoverAlpha * 35.0F), 126 + (int) (hoverAlpha * 35.0F)).getRGB();
        ClickGui.drawText(fr, name, baseX + 21, baseY + 2, labelColor);
        ClickGui.drawText(fr, value, baseX + width - fr.getStringWidth(value) - 7, baseY + 2, this.property.getValue() ? accent : new Color(155, 155, 155).getRGB());
    }

    public void setComponentStartAt(int newOffsetY) {
        this.offsetY = newOffsetY;
    }

    @Override
    public int getHeight() {
        return 14;
    }

    public void update(int mousePosX, int mousePosY) {
        this.y = this.module.category.getY() + this.offsetY;
        this.x = this.module.getSettingX();
        this.hovered = this.isHovered(mousePosX, mousePosY);
    }

    public void mouseDown(int x, int y, int button) {
        if (this.isHovered(x, y) && button == 0 && this.module.panelExpand) {
            this.property.setValue(!this.property.getValue());
        }
    }

    @Override
    public void mouseReleased(int x, int y, int button) {
    }

    @Override
    public void keyTyped(char chatTyped, int keyCode) {
    }

    public boolean isHovered(int x, int y) {
        return x >= this.x && x <= this.x + this.module.getSettingWidth() && y >= this.y && y <= this.y + getHeight();
    }

    @Override
    public boolean isVisible() {
        return property.isVisible();
    }
}
