package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class PotionEffects extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final float LINE_HEIGHT = 9.0F;
    private static final float RIGHT_MARGIN = 6.0F;
    private static final float BOTTOM_MARGIN = 6.0F;

    public PotionEffects() {
        super("PotionEffect", false);
    }

    private FontRenderer getFont() {
        HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
        return hud != null ? hud.getArrayListFontRenderer() : mc.fontRendererObj;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        Collection<PotionEffect> active = mc.thePlayer.getActivePotionEffects();
        boolean preview = mc.currentScreen instanceof GuiChat;
        if ((active == null || active.isEmpty()) && !preview) {
            return;
        }

        List<PotionEffect> potions = new ArrayList<>();
        if (active == null || active.isEmpty()) {
            potions.add(new PotionEffect(Potion.moveSpeed.id, 20 * 120, 1));
            potions.add(new PotionEffect(Potion.damageBoost.id, 20 * 45, 0));
            potions.add(new PotionEffect(Potion.fireResistance.id, 20 * 300, 0));
        } else {
            potions.addAll(active);
        }
        FontRenderer font = getFont();
        potions.sort(Comparator
                .comparingInt((PotionEffect effect) -> getPotionNameWidth(font, effect))
                .reversed()
                .thenComparing(Comparator.comparingInt(PotionEffect::getDuration).reversed()));

        ScaledResolution sr = new ScaledResolution(mc);
        float widgetWidth = getWidth(font, potions);
        float widgetHeight = Math.max(20.0F, potions.size() * LINE_HEIGHT);
        float x = sr.getScaledWidth() - RIGHT_MARGIN - widgetWidth;
        float y = sr.getScaledHeight() - BOTTOM_MARGIN - widgetHeight;

        float yOff = 0.0F;
        GlStateManager.disableDepth();
        for (PotionEffect potionEffect : potions) {
            Potion potionType = Potion.potionTypes[potionEffect.getPotionID()];
            if (potionType == null) {
                continue;
            }

            String potionName = getPotionName(potionType, potionEffect);
            String duration = " " + Potion.getDurationString(potionEffect);
            int nameColor = new Color(potionType.getLiquidColor()).getRGB();
            int durationColor = getDurationColor(potionEffect);
            float lineWidth = font.getStringWidth(potionName) + font.getStringWidth(duration);
            float drawX = x + widgetWidth - lineWidth;
            float drawY = y + widgetHeight - LINE_HEIGHT - yOff;

            font.drawString(potionName, drawX, drawY, nameColor, false);
            font.drawString(duration, drawX + font.getStringWidth(potionName), drawY, durationColor, false);
            yOff += LINE_HEIGHT;
        }
        GlStateManager.enableDepth();
    }

    private float getWidth(FontRenderer font, List<PotionEffect> potions) {
        float max = 80.0F;
        for (PotionEffect potionEffect : potions) {
            Potion potionType = Potion.potionTypes[potionEffect.getPotionID()];
            if (potionType == null) {
                continue;
            }
            String potionName = getPotionName(potionType, potionEffect);
            String duration = " " + Potion.getDurationString(potionEffect);
            max = Math.max(max, font.getStringWidth(potionName) + font.getStringWidth(duration));
        }
        return max;
    }

    private int getPotionNameWidth(FontRenderer font, PotionEffect effect) {
        Potion potionType = Potion.potionTypes[effect.getPotionID()];
        return potionType == null ? 0 : font.getStringWidth(getPotionName(potionType, effect));
    }

    private String getPotionName(Potion potion, PotionEffect effect) {
        String potionName = I18n.format(potion.getName());
        if (effect.getAmplifier() == 1) {
            potionName += " II";
        } else if (effect.getAmplifier() == 2) {
            potionName += " III";
        } else if (effect.getAmplifier() == 3) {
            potionName += " IV";
        }
        return potionName;
    }

    private int getDurationColor(PotionEffect effect) {
        if (effect.getDuration() < 300) {
            return 0xFFFF5555;
        }
        if (effect.getDuration() < 600) {
            return 0xFFFFAA00;
        }
        return 0xFFAAAAAA;
    }
}
