package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.font.UFontRenderer;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class PotionEffects extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final PercentProperty background = new PercentProperty("Background", 50);
    public final IntProperty offsetX = new IntProperty("OffsetX", 5, -1000, 1000);
    public final IntProperty offsetY = new IntProperty("OffsetY", 80, -1000, 1000);
    public final ModeProperty fontMode = new ModeProperty("font-mode", 0, new String[]{"Minecraft", "Modern"});
    public final BooleanProperty text = new BooleanProperty("Text", true);

    private float currentHeight = 0.0f;
    private final ResourceLocation inventoryTexture = new ResourceLocation("textures/gui/container/inventory.png");
    private UFontRenderer modernFont;

    private FontRenderer getFont() {
        if (fontMode.getValue() == 1) {
            if (modernFont == null) {
                try {
                    modernFont = new UFontRenderer("GoogleSans-Regular", 20);
                } catch (Exception e) {
                    modernFont = null;
                }
            }
            if (modernFont != null) {
                return modernFont;
            }
        }
        return mc.fontRendererObj;
    }

    private float getFontHeight() {
        FontRenderer fr = getFont();
        return fr instanceof UFontRenderer ? ((UFontRenderer) fr).getHeight() : fr.FONT_HEIGHT;
    }

    private int getStringWidth(String str) {
        return getFont().getStringWidth(str);
    }

    private void drawString(String str, float x, float y, int color, boolean shadow) {
        FontRenderer fr = getFont();
        if (fr instanceof UFontRenderer) {
            if (shadow) ((UFontRenderer) fr).drawStringWithShadow(str, x, y, color);
            else ((UFontRenderer) fr).drawString(str, x, y, color);
        } else {
            if (shadow) fr.drawStringWithShadow(str, x, y, color);
            else fr.drawString(str, x, y, color, false);
        }
    }

    public PotionEffects() {
        super("PotionEffects", false);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        Collection<PotionEffect> active = mc.thePlayer.getActivePotionEffects();
        if (active == null || active.isEmpty()) return;

        List<PotionEffect> potions = new ArrayList<>(active);
        potions.sort(Comparator.comparingInt(PotionEffect::getDuration).reversed());

        float padding = 5f;
        float fontHeight = getFontHeight();
        float iconSize = 9f;
        float rowHeight = this.text.getValue() ? fontHeight + padding : iconSize + padding;

        String title = "Potions";
        float titleWidth = getStringWidth(title);

        float maxWidth = this.text.getValue() ? (titleWidth + padding * 2) : (iconSize + padding * 2 + 4f);
        float listHeight = 0f;
        float topPadding = padding;
        float bottomPadding = padding;

        for (PotionEffect effect : potions) {
            Potion potion = Potion.potionTypes[effect.getPotionID()];
            if (potion == null) continue;

            float localWidth;
            if (this.text.getValue()) {
                String potionName = I18n.format(potion.getName());
                String amp = effect.getAmplifier() > 0 ? " " + I18n.format("enchantment.level." + (effect.getAmplifier() + 1)) : "";
                String nameText = potionName + amp;
                String durationText = Potion.getDurationString(effect);
                float nameW = getStringWidth(nameText);
                float durW = getStringWidth(durationText);
                localWidth = nameW + durW + padding * 3 + iconSize + 4f;
            } else {
                localWidth = iconSize + padding * 2 + 4f;
            }

            if (localWidth > maxWidth) maxWidth = localWidth;
            listHeight += rowHeight;
        }

        float width = Math.max(maxWidth, 80f);
        float headerHeight = this.text.getValue() ? fontHeight + padding * 2 : 0f;
        float extraTop = this.text.getValue() ? 1.25f + 7.5f : 0f;
        float targetHeight = topPadding + headerHeight + extraTop + listHeight + bottomPadding;

        if (currentHeight <= 0.0f) {
            currentHeight = targetHeight;
        } else {
            float speed = 0.22f;
            currentHeight += (targetHeight - currentHeight) * speed;
        }

        float height = Math.max(headerHeight + 1f, currentHeight);

        ScaledResolution sr = new ScaledResolution(mc);
        float x = sr.getScaledWidth() - width - 10 + offsetX.getValue();
        float y = 20 + offsetY.getValue();

        HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
        int accent = hud != null ? hud.getColor(System.currentTimeMillis()).getRGB() : 0xFF80FF95;

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        int bgAlpha = (int) ((float) background.getValue() / 100f * 255);
        int bg = new Color(0, 0, 0, Math.min(255, Math.max(0, bgAlpha))).getRGB();
        Gui.drawRect((int) x, (int) y, (int) (x + width), (int) (y + height), bg);

        float currentY = y + topPadding;

        if (this.text.getValue()) {
            float titleX = x + width / 2f - titleWidth / 2f;
            drawString(title, titleX, currentY, 0xFFFFFFFF, true);
            currentY += fontHeight + padding * 2;

            float dividerY = currentY;
            Color dividerColor = new Color(accent);
            dividerColor = new Color(
                    Math.max(0, dividerColor.getRed() - 60),
                    Math.max(0, dividerColor.getGreen() - 60),
                    Math.max(0, dividerColor.getBlue() - 60),
                    dividerColor.getAlpha()
            );
            Gui.drawRect((int) (x + 0.5f), (int) (dividerY + 1.5f), (int) (x + width - 0.5f), (int) (dividerY + 1.5f + 1.25f), dividerColor.getRGB());
            currentY = dividerY + 7.5f;
        }

        float iconX = x + padding;
        float textX = x + padding + iconSize + 4f;

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        ScaledResolution res = new ScaledResolution(mc);
        float scale = res.getScaleFactor();
        int scissorX = (int) (x * scale);
        int scissorY = (int) ((res.getScaledHeight() - (y + height)) * scale);
        int scissorW = (int) (width * scale);
        int scissorH = (int) (height * scale);
        GL11.glScissor(scissorX, scissorY, scissorW, scissorH);

        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        for (PotionEffect effect : potions) {
            Potion potion = Potion.potionTypes[effect.getPotionID()];
            if (potion == null) continue;

            float iconDrawY = currentY + (rowHeight - iconSize) / 2f;
            drawPotionStatusIcon(potion, iconX, iconDrawY, iconSize);

            if (this.text.getValue()) {
                String potionName = I18n.format(potion.getName());
                String amp = effect.getAmplifier() > 0 ? " " + I18n.format("enchantment.level." + (effect.getAmplifier() + 1)) : "";
                String nameText = potionName + amp;
                String durationText = Potion.getDurationString(effect);

                float durW = getStringWidth(durationText);
                drawString(nameText, textX, currentY, 0xFFFFFFFF, true);
                drawString(durationText, x + width - padding - durW, currentY, 0xFFFFFFFF, true);
            }

            currentY += rowHeight;
            if (currentY > y + height - padding) break;
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
    }

    private void drawPotionStatusIcon(Potion potion, float x, float y, float size) {
        if (potion == null) return;
        if (!potion.hasStatusIcon()) return;

        int idx = potion.getStatusIconIndex();
        int u = (idx % 8) * 18;
        int v = 198 + (idx / 8) * 18;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1f, 1f, 1f, 1f);
        mc.getTextureManager().bindTexture(inventoryTexture);
        float scale = size / 18f;
        GlStateManager.translate(x, y, 0f);
        GlStateManager.scale(scale, scale, 1f);
        Gui.drawModalRectWithCustomSizedTexture(0, 0, u, v, 18, 18, 256, 256);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}