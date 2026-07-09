package myau.ui.components;

import myau.config.Config;
import myau.ui.ClickGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ConfigurationComponent {
    private static final int ROW_HEIGHT = 15;
    private static final int MAX_HEIGHT = ROW_HEIGHT * 20;

    private final File configDir = new File("./config/Myau/");
    private final List<String> configs = new ArrayList<>();

    private int x;
    private int y;
    private int width = CategoryComponent.PANEL_WIDTH;
    private int headerWidth = CategoryComponent.HEADER_WIDTH;
    private int scroll;
    private int height;
    private double animScroll;
    private float openProgress = 1.0F;
    private boolean opened = true;
    private boolean dragging;
    private long lastRefresh;
    private String searchQuery = "";

    public int xx;
    public int yy;

    public ConfigurationComponent(int x, int y) {
        this.x = x;
        this.y = y;
        refreshConfigs();
    }

    public void render(FontRenderer renderer) {
        refreshConfigs();
        height = Math.max(ROW_HEIGHT, getVisibleConfigCount() * ROW_HEIGHT);
        int maxHeight = getMaxHeight();
        int maxScroll = Math.max(0, height - maxHeight);
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }
        if (animScroll > maxScroll) {
            animScroll = maxScroll;
        }
        animScroll += (scroll - animScroll) * 0.22D;
        openProgress = ClickGui.animate(openProgress, opened ? 1.0F : 0.0F, 0.24F);
        if (opened && openProgress > 0.985F) {
            openProgress = 1.0F;
        } else if (!opened && openProgress < 0.015F) {
            openProgress = 0.0F;
        }

        int displayHeight = (int) Math.ceil(Math.min(height, maxHeight) * openProgress);
        if (displayHeight > 0) {
            ClickGui.drawClickGuiBlur(x, y + CategoryComponent.HEADER_HEIGHT, x + width, y + CategoryComponent.HEADER_HEIGHT + displayHeight);
            Gui.drawRect(x, y + CategoryComponent.HEADER_HEIGHT, x + width, y + CategoryComponent.HEADER_HEIGHT + displayHeight, new Color(0, 0, 0, 82).getRGB());
        }

        headerWidth = CategoryComponent.HEADER_WIDTH;
        int headerX = getHeaderX();
        ClickGui.drawHudGradient(headerX, y, headerX + headerWidth, y + CategoryComponent.HEADER_HEIGHT);
        ClickGui.drawText(renderer, "Configuration", headerX + headerWidth / 2.0F - renderer.getStringWidth("Configuration") / 2.0F, y + 2, 0xFFFFFFFF);
        ClickGui.drawText(renderer, opened ? "-" : "+", headerX + headerWidth - 10, y + 2, 0xFFFFFFFF);

        if (displayHeight <= 0) {
            return;
        }

        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        double scale = sr.getScaleFactor();
        int top = y + CategoryComponent.HEADER_HEIGHT;
        int bottom = top + displayHeight;

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (x * scale), (int) ((sr.getScaledHeight() - bottom) * scale), (int) (width * scale), (int) (displayHeight * scale));

        if (configs.isEmpty()) {
            drawRow(renderer, "default", top, false, false);
        } else {
            int rowY = top - (int) animScroll;
            for (String config : configs) {
                if (!isConfigVisible(config)) {
                    continue;
                }
                if (rowY + ROW_HEIGHT > top && rowY < bottom) {
                    drawRow(renderer, config, rowY, config.equalsIgnoreCase(Config.lastConfig), true);
                }
                rowY += ROW_HEIGHT;
            }
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

    }

    private void drawRow(FontRenderer renderer, String config, int rowY, boolean active, boolean clickable) {
        int bgAlpha = active ? 96 : 54;
        Gui.drawRect(x, rowY, x + width, rowY + ROW_HEIGHT, new Color(0, 0, 0, bgAlpha).getRGB());

        int textColor = clickable ? new Color(215, 215, 215).getRGB() : new Color(130, 130, 130).getRGB();
        ClickGui.drawText(renderer, config, x + 1, rowY + 2, active ? 0xFFFFFFFF : textColor);
    }

    public boolean mouseDown(int mouseX, int mouseY, int button) {
        if (button == 0 && insideHeader(mouseX, mouseY)) {
            if (mouseX >= getHeaderX() + headerWidth - 16) {
                opened = !opened;
            } else {
                dragging = true;
                xx = mouseX - x;
                yy = mouseY - y;
            }
            return true;
        }

        if (!opened || configs.isEmpty() || !insideBody(mouseX, mouseY)) {
            return false;
        }

        List<String> visibleConfigs = getVisibleConfigs();
        int index = (mouseY - y - CategoryComponent.HEADER_HEIGHT + (int) animScroll) / ROW_HEIGHT;
        if (index < 0 || index >= visibleConfigs.size()) {
            return false;
        }

        String configName = visibleConfigs.get(index);
        if (button == 0) {
            new Config(configName, false).load();
            return true;
        }
        if (button == 1) {
            new Config(configName, true).save();
            refreshConfigs(true);
            return true;
        }
        return false;
    }

    public void mouseReleased(int mouseX, int mouseY, int button) {
        if (button == 0) {
            dragging = false;
        }
    }

    public void handleDrag(int mouseX, int mouseY) {
        if (dragging) {
            x = mouseX - xx;
            y = mouseY - yy;
        }
    }

    public void onScroll(int mouseX, int mouseY, int scrollAmount) {
        if (!opened || height <= getMaxHeight() || !insideBody(mouseX, mouseY)) {
            return;
        }

        scroll -= scrollAmount * 12;
        scroll = Math.max(0, Math.min(scroll, height - getMaxHeight()));
        updateAllOffsets();
    }

    public void updateAllOffsets() {
        int maxScroll = Math.max(0, height - getMaxHeight());
        scroll = Math.max(0, Math.min(scroll, maxScroll));
    }

    private void refreshConfigs() {
        if (System.currentTimeMillis() - lastRefresh < 1000L) {
            return;
        }
        refreshConfigs(false);
    }

    private void refreshConfigs(boolean force) {
        if (!force && System.currentTimeMillis() - lastRefresh < 1000L) {
            return;
        }

        lastRefresh = System.currentTimeMillis();
        configs.clear();
        File[] files = configDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        if (files == null) {
            return;
        }

        List<String> names = new ArrayList<>();
        for (File file : files) {
            String name = file.getName();
            names.add(name.substring(0, name.length() - 5));
        }
        names.sort(Comparator.comparing(String::toLowerCase));
        configs.addAll(names);
    }

    private boolean insideHeader(int mouseX, int mouseY) {
        int headerX = getHeaderX();
        return mouseX >= headerX && mouseX <= headerX + headerWidth && mouseY >= y && mouseY <= y + CategoryComponent.HEADER_HEIGHT;
    }

    private int getHeaderX() {
        return x - (CategoryComponent.HEADER_WIDTH - CategoryComponent.PANEL_WIDTH) / 2;
    }

    private boolean insideBody(int mouseX, int mouseY) {
        int bodyTop = y + CategoryComponent.HEADER_HEIGHT;
        int bodyBottom = bodyTop + Math.min(height, getMaxHeight());
        return mouseX >= x && mouseX <= x + width && mouseY >= bodyTop && mouseY <= bodyBottom;
    }

    private int getMaxHeight() {
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        return Math.max(ROW_HEIGHT, Math.min(MAX_HEIGHT, sr.getScaledHeight() - y - CategoryComponent.HEADER_HEIGHT - 8));
    }

    public String getName() {
        return "Configuration";
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public boolean isOpened() {
        return opened;
    }

    public void setOpened(boolean opened) {
        this.opened = opened;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery == null ? "" : searchQuery.trim().toLowerCase();
    }

    private boolean isConfigVisible(String config) {
        return searchQuery.isEmpty() || config.toLowerCase().contains(searchQuery);
    }

    private int getVisibleConfigCount() {
        int count = 0;
        for (String config : configs) {
            if (isConfigVisible(config)) {
                count++;
            }
        }
        return count;
    }

    private List<String> getVisibleConfigs() {
        List<String> visible = new ArrayList<>();
        for (String config : configs) {
            if (isConfigVisible(config)) {
                visible.add(config);
            }
        }
        return visible;
    }
}
