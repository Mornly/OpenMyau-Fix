package myau.ui.components;

import myau.module.modules.GuiModule;
import myau.ui.ClickGui;
import myau.ui.Component;
import myau.ui.dataset.BindStage;
import myau.util.KeyBindUtil;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

public class BindComponent implements Component {
    public boolean isBinding;
    private final ModuleComponent parentModule;
    private int offsetY;
    private int x;
    private int y;
    private boolean hovered;
    private float hoverAlpha;

    public BindComponent(ModuleComponent b, int offsetY) {
        this.parentModule = b;
        this.x = b.category.getX();
        this.y = b.category.getY() + b.offsetY;
        this.offsetY = offsetY;
    }

    public void draw(AtomicInteger offset) {
        FontRenderer fr = ClickGui.getInstance().getCurrentRenderer();
        int baseX = this.parentModule.getSettingX();
        int baseY = this.parentModule.category.getY() + this.offsetY;
        int width = this.parentModule.getSettingWidth();
        int accent = ClickGui.getHudColor((long) (baseX + baseY + offset.get()) * 18L);
        String value = this.isBinding ? BindStage.binding : KeyBindUtil.getKeyName(this.parentModule.mod.getKey());
        value = ClickGui.trimToWidth(fr, value, width / 2 - 6);

        hoverAlpha = ClickGui.animate(hoverAlpha, hovered ? 1.0F : 0.0F, 0.22F);
        Gui.drawRect(baseX, baseY, baseX + width, baseY + getHeight(), new Color(0, 0, 0, 54 + (int) (hoverAlpha * 30.0F)).getRGB());
        ClickGui.drawText(fr, BindStage.bind, baseX + 6, baseY + 2, new Color(205, 205, 205).getRGB());
        ClickGui.drawText(fr, value, baseX + width - fr.getStringWidth(value) - 5, baseY + 2, this.isBinding ? accent : new Color(165, 165, 165).getRGB());
    }

    public void update(int mousePosX, int mousePosY) {
        this.y = this.parentModule.category.getY() + this.offsetY;
        this.x = this.parentModule.getSettingX();
        this.hovered = this.isHovered(mousePosX, mousePosY);
    }

    public void mouseDown(int x, int y, int button) {
        if (this.isHovered(x, y) && button == 0 && this.parentModule.panelExpand) {
            this.isBinding = !this.isBinding;
        } else if (this.isBinding && this.parentModule.panelExpand) {
            int keyIndex = button - 100;
            if (button == 0) {
                this.isBinding = false;
                return;
            }
            this.parentModule.mod.setKey(keyIndex);
            this.isBinding = false;
        }
    }

    public void mouseReleased(int x, int y, int button) {
    }

    public void keyTyped(char chatTyped, int keyCode) {
        if (this.isBinding) {
            if (keyCode == 1) {
                this.parentModule.mod.setKey(0);
                this.isBinding = false;
                return;
            }
            if (keyCode == 11) {
                if (this.parentModule.mod instanceof GuiModule) {
                    this.parentModule.mod.setKey(54);
                } else {
                    this.parentModule.mod.setKey(0);
                }
                this.isBinding = false;
                return;
            }
            this.parentModule.mod.setKey(keyCode);
            this.isBinding = false;
        }
    }

    public void setComponentStartAt(int newOffsetY) {
        this.offsetY = newOffsetY;
    }

    public boolean isHovered(int x, int y) {
        return x >= this.x && x <= this.x + this.parentModule.getSettingWidth() && y >= this.y && y <= this.y + getHeight();
    }

    public int getHeight() {
        return 14;
    }

    public boolean isVisible() {
        return true;
    }
}
