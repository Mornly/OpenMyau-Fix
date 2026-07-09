package myau.ui.mainmenu;

import me.ksyz.accountmanager.gui.GuiAccountManager;
import myau.font.CFontRenderer;
import myau.ui.ClickGui;
import myau.util.render.PostProcessing;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AdjustMainMenu extends GuiScreen {
    private static final long GLITCH_INTERVAL_MS = 2000L;
    private static final long GLITCH_DURATION_MS = 260L;
    private static final float TITLE_JOIN_OFFSET = 1.0F;
    private static final VideoPlayer VIDEO_PLAYER = new VideoPlayer();

    private final List<MenuButton> buttons = new ArrayList<>();
    private final Random random = new Random();

    private CFontRenderer titleFont;
    private CFontRenderer buttonFont;
    private int buttonX;
    private int buttonY;
    private int buttonWidth;
    private int buttonHeight;
    private long menuOpenTime;
    private long glitchSeedTime;
    private int glitchX;
    private int glitchY;

    @Override
    public void initGui() {
        if (titleFont == null) {
            titleFont = new CFontRenderer("GoogleSans-Regular", 44);
        }
        if (buttonFont == null) {
            buttonFont = new CFontRenderer("GoogleSans-Regular", 18);
        }

        VIDEO_PLAYER.reset();
        buttons.clear();
        menuOpenTime = System.currentTimeMillis();
        buttonWidth = 154;
        buttonHeight = 20;
        buttonX = this.width / 2 - buttonWidth / 2;
        buttonY = this.height / 2 + 22;

        addButton(I18n.format("menu.singleplayer"), () -> mc.displayGuiScreen(new GuiSelectWorld(this)));
        addButton(I18n.format("menu.multiplayer"), () -> mc.displayGuiScreen(new GuiMultiplayer(this)));
        addButton("Accounts", () -> mc.displayGuiScreen(new GuiAccountManager(this)));
        addButton(I18n.format("menu.options"), () -> mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings)));
        addButton(I18n.format("menu.quit"), () -> mc.shutdownMinecraftApplet());
    }

    private void addButton(String label, Runnable action) {
        int index = buttons.size();
        buttons.add(new MenuButton(label, buttonX, buttonY + index * buttonHeight, buttonWidth, buttonHeight, action));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Gui.drawRect(0, 0, this.width, this.height, Color.BLACK.getRGB());
        VIDEO_PLAYER.render(this.width, this.height);
        Gui.drawRect(0, 0, this.width, this.height, new Color(0, 0, 0, 72).getRGB());

        int panelTop = buttonY;
        int panelBottom = buttonY + buttons.size() * buttonHeight;
        PostProcessing.drawBlur(buttonX, panelTop, buttonX + buttonWidth, panelBottom, () -> () -> {
            Gui.drawRect(buttonX, panelTop, buttonX + buttonWidth, panelBottom, -1);
        });

        drawGlitchTitle();
        drawButtons(mouseX, mouseY);
    }

    private void drawGlitchTitle() {
        String first = "A";
        String rest = "djust";
        int fullWidth = titleFont.getStringWidth(first + rest);
        float x = this.width / 2.0F - fullWidth / 2.0F;
        float y = this.height / 2.0F - 68.0F;
        int hudColor = ClickGui.getHudColor(0L);
        long now = System.currentTimeMillis();
        boolean glitching = (now - menuOpenTime) % GLITCH_INTERVAL_MS < GLITCH_DURATION_MS;

        if (glitching && now - glitchSeedTime > 70L) {
            glitchSeedTime = now;
            glitchX = random.nextInt(5) - 2;
            glitchY = random.nextInt(3) - 1;
        }

        if (glitching) {
            GlStateManager.enableBlend();
            drawTitleLayer(x - glitchX - 1.0F, y + glitchY, new Color(0, 210, 255, 120).getRGB(), new Color(255, 255, 255, 88).getRGB());
            drawTitleLayer(x + glitchX + 1.0F, y - glitchY, new Color(255, 0, 110, 120).getRGB(), new Color(255, 255, 255, 88).getRGB());

            if ((now / 120L) % 2L == 0L) {
                int sliceY = (int) y + 8 + random.nextInt(20);
                Gui.drawRect((int) x - 2, sliceY, (int) x + fullWidth + 2, sliceY + 1, new Color(255, 0, 140, 150).getRGB());
                Gui.drawRect((int) x + random.nextInt(10) - 5, sliceY + 3, (int) x + fullWidth - random.nextInt(10) + 5, sliceY + 4, new Color(0, 210, 255, 125).getRGB());
            }
            GlStateManager.disableBlend();
        }

        drawTitleA(x, y, hudColor);
        ClickGui.drawText(titleFont, rest, x + titleFont.getStringWidth(first) - TITLE_JOIN_OFFSET, y, Color.WHITE.getRGB());
    }

    private void drawTitleLayer(float x, float y, int firstColor, int restColor) {
        String first = "A";
        ClickGui.drawText(titleFont, first, x, y, firstColor);
        ClickGui.drawText(titleFont, "djust", x + titleFont.getStringWidth(first) - TITLE_JOIN_OFFSET, y, restColor);
    }

    private void drawTitleA(float x, float y, int color) {
        Color c = new Color(color, true);
        int shadowColor = new Color(c.getRed(), c.getGreen(), c.getBlue(), 112).getRGB();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 1.0F, y + 1.0F, 0.0F);
        GlStateManager.scale(1.06F, 1.06F, 1.0F);
        ClickGui.drawText(titleFont, "A", 0.0F, 0.0F, shadowColor);
        GlStateManager.popMatrix();

        ClickGui.drawText(titleFont, "A", x, y, color);
    }

    private void drawButtons(int mouseX, int mouseY) {
        for (int i = 0; i < buttons.size(); i++) {
            MenuButton button = buttons.get(i);
            boolean hovered = button.isHovered(mouseX, mouseY);
            int background = hovered ? new Color(0, 0, 0, 118).getRGB() : new Color(0, 0, 0, 74).getRGB();
            Gui.drawRect(button.x, button.y, button.x + button.width, button.y + button.height, background);
            if (i > 0) {
                Gui.drawRect(button.x, button.y, button.x + button.width, button.y + 1, new Color(255, 255, 255, 22).getRGB());
            }
            if (hovered) {
                ClickGui.drawHudGradient(button.x, button.y, button.x + button.width, button.y + 2);
            }

            int color = hovered ? Color.WHITE.getRGB() : new Color(198, 198, 198).getRGB();
            ClickGui.drawText(buttonFont, button.label, button.x + button.width / 2.0F - buttonFont.getStringWidth(button.label) / 2.0F, button.y + 5, color);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            for (MenuButton button : buttons) {
                if (button.isHovered(mouseX, mouseY)) {
                    button.action.run();
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void onGuiClosed() {
    }

    public static void preloadVideo() {
        VIDEO_PLAYER.reset();
    }

    public static void stopVideo() {
        VIDEO_PLAYER.stop();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static final class MenuButton {
        private final String label;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final Runnable action;

        private MenuButton(String label, int x, int y, int width, int height, Runnable action) {
            this.label = label;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.action = action;
        }

        private boolean isHovered(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }
}
