package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.font.UFontRenderer;
import myau.management.StatsManager;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.ColorUtil;
import myau.util.RenderUtil;
import myau.util.render.PostProcessing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;

import java.awt.*;

public class Session extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private long startTime = -1;
    private String cachedPlayerName = "";
    private float cachedKDR = 0f;
    private long lastFetchTime = 0;
    private static final long FETCH_COOLDOWN = 30000;

    private UFontRenderer tahomaFont;
    private UFontRenderer tahomaBoldFont;
    private boolean fontsLoaded = false;

    private boolean dragging = false;
    private int dragStartX = 0;
    private int dragStartY = 0;
    private int dragStartOffX = 0;
    private int dragStartOffY = 0;
    private boolean positionLocked = true;

    public final IntProperty offX = new IntProperty("OffsetX", 100, -500, 900);
    public final IntProperty offY = new IntProperty("OffsetY", 100, -500, 500);

    public final BooleanProperty blur = new BooleanProperty("Blur", true);
    public final BooleanProperty bloom = new BooleanProperty("Bloom", false);
    public final ModeProperty bloomColorMode = new ModeProperty("BloomColor", 0, new String[]{"Background", "HUD"});

    public Session() {
        super("Session", false, false);
    }

    private void loadFonts() {
        if (!fontsLoaded) {
            try { tahomaFont = new UFontRenderer("tahoma", 18); } catch (Exception e) { tahomaFont = null; }
            try { tahomaBoldFont = new UFontRenderer("tahomabold", 18); } catch (Exception e) { tahomaBoldFont = null; }
            fontsLoaded = true;
        }
    }

    private FontRenderer getFont() {
        loadFonts();
        return tahomaFont != null ? tahomaFont : mc.fontRendererObj;
    }

    private FontRenderer getBoldFont() {
        loadFonts();
        return tahomaBoldFont != null ? tahomaBoldFont : mc.fontRendererObj;
    }

    private String getTimerText() {
        if (startTime == -1) startTime = System.currentTimeMillis();
        long elapsed = System.currentTimeMillis() - startTime;
        long totalSeconds = elapsed / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (totalSeconds == 0) return "0s";
        if (minutes == 0) return seconds + "s";
        return minutes + "m " + seconds + "s";
    }

    private int getBloomColor() {
        String mode = bloomColorMode.getModeString();
        HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);

        if (mode.equals("HUD") && hud != null) {
            return hud.getColor(System.currentTimeMillis()).getRGB();
        } else {
            // Background 模式 - 纯黑色
            return 0xFF000000;
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) return;
        if (startTime == -1) startTime = System.currentTimeMillis();

        String playerName = mc.thePlayer.getName();
        if (!playerName.equals(cachedPlayerName) || System.currentTimeMillis() - lastFetchTime > FETCH_COOLDOWN) {
            cachedPlayerName = playerName;
            lastFetchTime = System.currentTimeMillis();
            StatsManager.getInstance().getSkyWarsKDR(playerName).thenAccept(kdr -> {
                try { cachedKDR = Float.parseFloat(kdr); } catch (Exception e) { cachedKDR = 0f; }
            });
        }

        FontRenderer tahoma = getFont();
        FontRenderer tahomaBold = getBoldFont();

        float s = 1.2f;

        float avatarSize = 16 * s;
        float rectHeight = 34 * s;
        float titleBarHeight = 10 * s;
        float padding = 4 * s;

        float maxWidth = tahomaBold.getStringWidth("Myau") + avatarSize + 50 * s;
        if (maxWidth < 120 * s) maxWidth = 120 * s;

        float width = maxWidth;
        float height = rectHeight;

        ScaledResolution sr = new ScaledResolution(mc);

        float x = offX.getValue().floatValue();
        float y = offY.getValue().floatValue();

        int mouseX = Mouse.getX() * sr.getScaledWidth() / mc.displayWidth;
        int mouseY = sr.getScaledHeight() - Mouse.getY() * sr.getScaledHeight() / mc.displayHeight - 1;

        positionLocked = !(mc.currentScreen instanceof GuiChat);

        if (!positionLocked) {
            if (Mouse.isButtonDown(0) && !dragging) {
                if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
                    dragging = true;
                    dragStartX = mouseX;
                    dragStartY = mouseY;
                    dragStartOffX = offX.getValue();
                    dragStartOffY = offY.getValue();
                }
            } else if (!Mouse.isButtonDown(0)) {
                dragging = false;
            }
            if (dragging) {
                offX.setValue(dragStartOffX + mouseX - dragStartX);
                offY.setValue(dragStartOffY + mouseY - dragStartY);
            }
        }

        float rectLeft = x;
        float rectTop = y;
        float rectRight = x + width;
        float rectBottom = y + rectHeight;

        // ===== 1. Blur =====
        if (blur.getValue()) {
            final float rl = rectLeft, rt = rectTop, rr = rectRight, rb = rectBottom;
            PostProcessing.drawBlur(rl, rt, rr, rb, () -> () -> {
                GlStateManager.enableBlend();
                GlStateManager.disableTexture2D();
                GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                RenderUtil.setup2DRendering(() -> {
                    Gui.drawRect((int)rl, (int)rt, (int)rr, (int)rb, -1);
                });
                GlStateManager.enableTexture2D();
                GlStateManager.disableBlend();
            });
        }

        // ===== 2. Background =====
        int bgColor = new Color(10, 10, 10, 128).getRGB();
        int titleBarColor = new Color(0, 0, 0, 77).getRGB();
        int textColor = new Color(0xAAAAAA).getRGB();

        RenderUtil.enableRenderState();
        RenderUtil.drawRect(rectLeft, rectTop, rectRight, rectBottom, bgColor);
        RenderUtil.drawRect(rectLeft, rectTop, rectRight, rectTop + titleBarHeight, titleBarColor);
        RenderUtil.disableRenderState();

        // ===== 3. Bloom =====
        if (bloom.getValue()) {
            Framebuffer bloomBuffer = PostProcessing.beginBloom();
            if (bloomBuffer != null) {
                int color1 = ColorUtil.swapAlpha(getBloomColor(), 255);
                RenderUtil.drawRect(rectLeft, rectTop, rectRight, rectBottom, color1);
                PostProcessing.endBloom(bloomBuffer);
            }
        }

        // ===== 4. Avatar =====
        float avatarX = x + padding;
        float avatarY = y + rectHeight - avatarSize - padding;

        if (mc.thePlayer instanceof EntityPlayer) {
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
            if (info != null) {
                ResourceLocation skin = info.getLocationSkin();
                mc.getTextureManager().bindTexture(skin);
                GlStateManager.color(1, 1, 1, 1);
                Gui.drawScaledCustomSizeModalRect((int)avatarX, (int)avatarY, 8, 8, 8, 8, (int)avatarSize, (int)avatarSize, 64, 64);
                Gui.drawScaledCustomSizeModalRect((int)avatarX, (int)avatarY, 40, 8, 8, 8, (int)avatarSize, (int)avatarSize, 64, 64);
            }
        }

        float nameX = avatarX + avatarSize + 5 * s;
        float nameY = avatarY;

        float nameFontHeight = tahomaBold.FONT_HEIGHT * 0.7f;
        float infoFontHeight = tahoma.FONT_HEIGHT * 0.7f;

        float kdrY = nameY + nameFontHeight + 2 * s + 2 * s -2;
        float kdrHeight = 1 * s;

        float timerY = y + rectHeight - padding - infoFontHeight;

        GlStateManager.disableDepth();
        GlStateManager.enableBlend();

        // ===== 5. Name =====
        float nameScale = 0.7f;
        GlStateManager.pushMatrix();
        GlStateManager.scale(nameScale, nameScale, 1);
        tahomaBold.drawString(mc.thePlayer.getName(), (int)(nameX / nameScale), (int)(nameY / nameScale), textColor, false);
        GlStateManager.popMatrix();

        // ===== 6. KDR Line =====
        float kdrLineRightX = x + width - padding - 3 * s;
        float kdrLineWidth = kdrLineRightX - nameX;
        renderKDRLine(nameX, kdrY, kdrLineWidth, kdrHeight);

        // ===== 7. Info Text =====
        float infoScale = 0.7f;
        GlStateManager.pushMatrix();
        GlStateManager.scale(infoScale, infoScale, 1);

        String kdrText = String.format("%.2f KDR", cachedKDR);
        float kdrTextWidth = tahoma.getStringWidth(kdrText);
        float kdrTextX = kdrLineRightX / infoScale - kdrTextWidth;
        tahoma.drawString(kdrText, (int)kdrTextX, (int)(timerY / infoScale), textColor, false);

        tahoma.drawString(getTimerText(), (int)(nameX / infoScale), (int)(timerY / infoScale), textColor, false);

        GlStateManager.popMatrix();

        // ===== 8. Title =====
        String titleText = "Session Information";
        float titleWidth = tahoma.getStringWidth(titleText) * 0.7f;
        float titleX = x + (width - titleWidth) / 2f;
        float titleY = y + (titleBarHeight - tahoma.FONT_HEIGHT * 0.7f) / 2f;
        GlStateManager.pushMatrix();
        GlStateManager.scale(infoScale, infoScale, 1);
        tahoma.drawString(titleText, (int)(titleX / infoScale), (int)(titleY / infoScale), textColor, false);
        GlStateManager.popMatrix();

        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
    }

    private void renderKDRLine(float x, float y, float width, float height) {
        float progress = cachedKDR == 0 ? 0 : cachedKDR / (cachedKDR + 1);
        progress = Math.max(0, Math.min(1, progress));

        RenderUtil.enableRenderState();
        RenderUtil.drawRect(x, y, x + width, y + height, new Color(255, 255, 255, 60).getRGB());

        float fillWidth = width * progress;
        if (fillWidth > 0) {
            HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
            if (hud != null) {
                Color color = hud.getColor(System.currentTimeMillis());
                RenderUtil.drawRect(x, y, x + fillWidth, y + height, color.getRGB());
            }
        }
        RenderUtil.disableRenderState();
    }

    @Override
    public String[] getSuffix() {
        return new String[0];
    }
}