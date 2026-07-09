package myau.ui.components;

import myau.property.properties.TextProperty;
import myau.ui.ClickGui;
import myau.ui.Component;
import myau.ui.callback.GuiInput;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TextComponent implements Component {
    private final TextProperty property;
    private final ModuleComponent module;
    private int offsetY;
    private int x;
    private int y;
    private boolean hovered;
    private float hoverAlpha;

    public TextComponent(TextProperty property, ModuleComponent parentModule, int offsetY) {
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
        String value = ClickGui.trimToChars(this.property.getValue(), 15);
        String name = ClickGui.trimToChars(this.property.getName().replace("-", " "), 15);

        hoverAlpha = ClickGui.animate(hoverAlpha, hovered ? 1.0F : 0.0F, 0.22F);
        Gui.drawRect(baseX, baseY, baseX + width, baseY + getHeight(), new Color(0, 0, 0, 50 + (int) (hoverAlpha * 32.0F)).getRGB());
        ClickGui.drawText(fr, name, baseX + 8, baseY + 2, new Color(205, 205, 205).getRGB());
        ClickGui.drawText(fr, value, baseX + width - fr.getStringWidth(value) - 7, baseY + 2, accent);
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
            GuiInput.prompt(property.getName().replace("-", " "), property.getValue(), property::setValue, ClickGui.getInstance());
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
