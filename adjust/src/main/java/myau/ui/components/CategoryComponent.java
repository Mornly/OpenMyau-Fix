package myau.ui.components;

import myau.module.Module;
import myau.ui.ClickGui;
import myau.ui.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class CategoryComponent {
    public static final int PANEL_WIDTH = 92;
    public static final int HEADER_WIDTH = 100;
    public static final int HEADER_HEIGHT = 15;
    public static final int MODULE_HEIGHT = 15;

    private static final int MAX_HEIGHT = MODULE_HEIGHT * 20;

    public ArrayList<Component> modulesInCategory = new ArrayList<>();
    public String categoryName;
    public boolean dragging;
    public int xx;
    public int yy;
    public boolean pin = false;

    private boolean categoryOpened;
    private int width;
    private int y;
    private int x;
    private final int bh;
    private int scroll = 0;
    private double animScroll = 0;
    private int height = 0;
    private float openProgress = 1.0F;
    private int bodyClipTop;
    private int bodyClipBottom;
    private String searchQuery = "";

    public CategoryComponent(String category, List<Module> modules) {
        this.categoryName = category;
        this.width = PANEL_WIDTH;
        this.x = 5;
        this.y = 5;
        this.bh = HEADER_HEIGHT;
        this.xx = 0;
        this.categoryOpened = true;
        this.dragging = false;

        int tY = this.bh;
        for (Module mod : modules) {
            ModuleComponent b = new ModuleComponent(mod, this, tY);
            this.modulesInCategory.add(b);
            tY += MODULE_HEIGHT;
        }
        updateAllOffsets();
    }

    public ArrayList<Component> getModules() {
        return this.modulesInCategory;
    }

    public void setX(int n) {
        this.x = n;
    }

    public void setY(int y) {
        this.y = y;
    }

    public void mousePressed(boolean d) {
        this.dragging = d;
    }

    public boolean isPin() {
        return this.pin;
    }

    public void setPin(boolean on) {
        this.pin = on;
    }

    public boolean isOpened() {
        return this.categoryOpened;
    }

    public void setOpened(boolean on) {
        this.categoryOpened = on;
        if (on) {
            updateAllOffsets();
        }
    }

    public void render(FontRenderer renderer) {
        this.width = PANEL_WIDTH;
        height = 0;
        for (Component moduleRenderManager : this.modulesInCategory) {
            if (!isComponentVisible(moduleRenderManager)) {
                continue;
            }
            height += moduleRenderManager.getHeight();
        }

        int maxHeight = getMaxHeight();
        int maxScroll = Math.max(0, height - maxHeight);
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }
        if (animScroll > maxScroll) {
            animScroll = maxScroll;
        }
        animScroll += (scroll - animScroll) * 0.22D;
        openProgress = ClickGui.animate(openProgress, this.categoryOpened ? 1.0F : 0.0F, 0.24F);
        if (this.categoryOpened && openProgress > 0.985F) {
            openProgress = 1.0F;
        } else if (!this.categoryOpened && openProgress < 0.015F) {
            openProgress = 0.0F;
        }
        updateAllOffsets();

        int fullDisplayHeight = Math.min(height, maxHeight);
        int displayHeight = (int) Math.ceil(fullDisplayHeight * openProgress);
        if (!this.modulesInCategory.isEmpty() && displayHeight > 0) {
            ClickGui.drawClickGuiBlur(this.x, this.y + this.bh, this.x + this.width, this.y + this.bh + displayHeight);
            Gui.drawRect(this.x, this.y + this.bh, this.x + this.width, this.y + this.bh + displayHeight, new Color(0, 0, 0, 82).getRGB());
        }

        int headerX = getHeaderX();
        int headerWidth = getHeaderWidth();
        ClickGui.drawHudGradient(headerX, this.y, headerX + headerWidth, this.y + this.bh);
        ClickGui.drawText(renderer, this.categoryName, headerX + headerWidth / 2.0F - renderer.getStringWidth(this.categoryName) / 2.0F, this.y + 2, 0xFFFFFFFF);
        ClickGui.drawText(renderer, this.categoryOpened ? "-" : "+", headerX + headerWidth - 10, this.y + 2, Color.white.getRGB());

        if (displayHeight > 0 && !this.modulesInCategory.isEmpty()) {
            int renderHeight = 0;
            int top = this.y + this.bh;
            int bottom = top + displayHeight;
            this.bodyClipTop = top;
            this.bodyClipBottom = bottom;
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            for (Component c2 : this.modulesInCategory) {
                if (!isComponentVisible(c2)) {
                    continue;
                }
                int compHeight = c2.getHeight();
                if (renderHeight + compHeight > animScroll && renderHeight < animScroll + displayHeight) {
                    applyBodyScissor(top, bottom);
                    c2.draw(new AtomicInteger(renderHeight));
                }
                renderHeight += compHeight;
            }
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        } else {
            this.bodyClipTop = this.y + this.bh;
            this.bodyClipBottom = this.bodyClipTop;
        }
    }

    public void updateAllOffsets() {
        int yOffset = this.bh;
        for (Component comp : this.modulesInCategory) {
            if (!isComponentVisible(comp)) {
                comp.setComponentStartAt(-10000);
                continue;
            }
            comp.setComponentStartAt(yOffset - (int) animScroll);
            yOffset += comp.getHeight();
        }
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeaderWidth() {
        return HEADER_WIDTH;
    }

    public int getBodyClipTop() {
        return bodyClipTop;
    }

    public int getBodyClipBottom() {
        return bodyClipBottom;
    }

    private int getHeaderX() {
        return this.x - (HEADER_WIDTH - PANEL_WIDTH) / 2;
    }

    private void applyBodyScissor(int top, int bottom) {
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        double scale = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (this.x * scale), (int) ((sr.getScaledHeight() - bottom) * scale), (int) (this.width * scale), (int) ((bottom - top) * scale));
    }

    public void handleDrag(int x, int y) {
        if (this.dragging) {
            this.setX(x - this.xx);
            this.setY(y - this.yy);
        }
    }

    public boolean isHovered(int x, int y) {
        int headerX = getHeaderX();
        int headerWidth = getHeaderWidth();
        return x >= headerX + headerWidth - 32 && x < headerX + headerWidth - 16 && y >= this.y && y <= this.y + this.bh;
    }

    public boolean mousePressed(int x, int y) {
        int headerX = getHeaderX();
        int headerWidth = getHeaderWidth();
        return x >= headerX + headerWidth - 16 && x <= headerX + headerWidth && y >= this.y && y <= this.y + this.bh;
    }

    public boolean insideArea(int x, int y) {
        return x >= getHeaderX() && x <= getHeaderX() + getHeaderWidth() && y >= this.y && y <= this.y + this.bh;
    }

    public String getName() {
        return categoryName;
    }

    public void setLocation(int parseInt, int parseInt1) {
        this.x = parseInt;
        this.y = parseInt1;
    }

    public void onScroll(int mouseX, int mouseY, int scrollAmount) {
        int maxHeight = getMaxHeight();
        if (!categoryOpened || height <= maxHeight) {
            return;
        }

        int areaTop = this.y + this.bh;
        int areaBottom = this.y + this.bh + maxHeight;
        if (mouseX >= this.x && mouseX <= this.x + width && mouseY >= areaTop && mouseY <= areaBottom) {
            scroll -= scrollAmount * 12;
            scroll = Math.max(0, Math.min(scroll, height - maxHeight));
            updateAllOffsets();
        }
    }

    private int getMaxHeight() {
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        return Math.max(MODULE_HEIGHT, Math.min(MAX_HEIGHT, sr.getScaledHeight() - this.y - this.bh - 8));
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery == null ? "" : searchQuery.trim().toLowerCase();
    }

    public boolean isComponentVisible(Component component) {
        if (searchQuery.isEmpty()) {
            return true;
        }
        return component instanceof ModuleComponent && ((ModuleComponent) component).matchesSearch(searchQuery);
    }
}
