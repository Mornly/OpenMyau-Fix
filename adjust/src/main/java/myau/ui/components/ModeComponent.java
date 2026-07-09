package myau.ui.components;

import myau.property.properties.ModeProperty;
import myau.ui.ClickGui;
import myau.ui.Component;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ModeComponent implements Component {
    private static final int HEADER_HEIGHT = 14;
    private static final int OPTION_HEIGHT = 14;

    private final ModeProperty property;
    private final ModuleComponent parentModule;
    private int x;
    private int y;
    private int offsetY;
    private int mouseX;
    private int mouseY;
    private boolean expanded;
    private boolean hovered;
    private float hoverAlpha;
    private float openAlpha;

    public ModeComponent(ModeProperty desc, ModuleComponent parentModule, int offsetY) {
        this.property = desc;
        this.parentModule = parentModule;
        this.x = parentModule.category.getX();
        this.y = parentModule.category.getY() + parentModule.offsetY;
        this.offsetY = offsetY;
    }

    public void draw(AtomicInteger offset) {
        FontRenderer fr = ClickGui.getInstance().getCurrentRenderer();
        int baseX = this.parentModule.getSettingX();
        int baseY = this.parentModule.category.getY() + this.offsetY;
        int width = this.parentModule.getSettingWidth();
        int accent = ClickGui.getHudColor((long) (baseX + baseY + offset.get()) * 18L);

        hoverAlpha = ClickGui.animate(hoverAlpha, hovered ? 1.0F : 0.0F, 0.22F);
        openAlpha = ClickGui.animate(openAlpha, expanded ? 1.0F : 0.0F, 0.24F);
        snapOpenAlpha();

        Gui.drawRect(baseX, baseY, baseX + width, baseY + HEADER_HEIGHT, new Color(0, 0, 0, 50 + (int) (hoverAlpha * 32.0F)).getRGB());
        String name = ClickGui.trimToChars(formatLabel(this.property.getName()), 15);
        ClickGui.drawText(fr, name, baseX + width / 2.0F - fr.getStringWidth(name) / 2.0F, baseY + 2, new Color(218, 218, 218).getRGB());
        ClickGui.drawText(fr, expanded ? "-" : "+", baseX + width - 10, baseY + 2, new Color(165, 165, 165, 170 + (int) (hoverAlpha * 70.0F)).getRGB());

        if (openAlpha <= 0.02F) {
            return;
        }

        String[] modes = property.getModes();
        int visibleHeight = getOptionHeight();
        int optionsTop = baseY + HEADER_HEIGHT;
        Gui.drawRect(baseX, optionsTop, baseX + width, optionsTop + visibleHeight, new Color(0, 0, 0, (int) (54 * openAlpha)).getRGB());

        for (int i = 0; i < modes.length; i++) {
            int rowY = optionsTop + i * OPTION_HEIGHT;
            if (rowY + OPTION_HEIGHT > optionsTop + visibleHeight) {
                break;
            }

            boolean selected = property.getValue() == i;
            boolean optionHovered = mouseX >= baseX && mouseX <= baseX + width && mouseY >= rowY && mouseY <= rowY + OPTION_HEIGHT;
            if (optionHovered) {
                Gui.drawRect(baseX, rowY, baseX + width, rowY + OPTION_HEIGHT, new Color(0, 0, 0, (int) (42 * openAlpha)).getRGB());
            }
            String text = ClickGui.trimToChars(formatLabel(modes[i]), 15);
            int color = selected ? accent : new Color(162, 162, 162, (int) (230 * openAlpha)).getRGB();
            ClickGui.drawText(fr, text, baseX + width / 2.0F - fr.getStringWidth(text) / 2.0F, rowY + 2, color);
        }
    }

    public void update(int mousePosX, int mousePosY) {
        this.y = this.parentModule.category.getY() + this.offsetY;
        this.x = this.parentModule.getSettingX();
        this.mouseX = mousePosX;
        this.mouseY = mousePosY;
        this.hovered = isHeaderHovered(mousePosX, mousePosY);
        this.openAlpha = ClickGui.animate(openAlpha, expanded ? 1.0F : 0.0F, 0.24F);
        snapOpenAlpha();
    }

    public void setComponentStartAt(int newOffsetY) {
        this.offsetY = newOffsetY;
    }

    @Override
    public int getHeight() {
        return HEADER_HEIGHT + getOptionHeight();
    }

    public void mouseDown(int x, int y, int button) {
        if (isHeaderHovered(x, y)) {
            if (button == 0) {
                expanded = !expanded;
                this.parentModule.category.updateAllOffsets();
            } else if (button == 1) {
                this.property.previousMode();
            }
            return;
        }

        if (expanded && button == 0) {
            int option = getHoveredOption(x, y);
            if (option >= 0) {
                property.setValue(option);
                expanded = false;
                this.parentModule.category.updateAllOffsets();
            }
        }
    }

    @Override
    public void mouseReleased(int x, int y, int button) {
    }

    @Override
    public void keyTyped(char chatTyped, int keyCode) {
    }

    private boolean isHeaderHovered(int x, int y) {
        return x >= this.x && x <= this.x + this.parentModule.getSettingWidth() && y >= this.y && y <= this.y + HEADER_HEIGHT;
    }

    private int getHoveredOption(int mouseX, int mouseY) {
        int top = this.y + HEADER_HEIGHT;
        int index = (mouseY - top) / OPTION_HEIGHT;
        if (mouseX < this.x || mouseX > this.x + this.parentModule.getSettingWidth() || index < 0 || index >= property.getModes().length) {
            return -1;
        }
        int visibleHeight = getOptionHeight();
        if (mouseY > top + visibleHeight) {
            return -1;
        }
        return index;
    }

    private int getOptionHeight() {
        if (!expanded) {
            return 0;
        }
        int fullHeight = property.getModes().length * OPTION_HEIGHT;
        if (openAlpha >= 0.985F) {
            return fullHeight;
        }
        int optionHeight = (int) Math.ceil(fullHeight * openAlpha);
        if (optionHeight < OPTION_HEIGHT) {
            optionHeight = OPTION_HEIGHT;
        }
        return Math.min(fullHeight, optionHeight);
    }

    private void snapOpenAlpha() {
        if (expanded && openAlpha > 0.985F) {
            openAlpha = 1.0F;
        } else if (!expanded && openAlpha < 0.015F) {
            openAlpha = 0.0F;
        }
    }

    private String formatLabel(String value) {
        String mode = value == null ? "" : value.replace("_", " ").replace("-", " ");
        if (mode.isEmpty()) {
            return "?";
        }
        StringBuilder builder = new StringBuilder();
        for (String part : mode.split(" ")) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase()).append(part.substring(1).toLowerCase());
        }
        return builder.length() == 0 ? "?" : builder.toString();
    }

    @Override
    public boolean isVisible() {
        return property.isVisible();
    }
}
