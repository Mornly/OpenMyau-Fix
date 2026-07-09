package myau.module.modules;

import myau.Myau;
import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.ColorUtil;
import myau.util.RenderUtil;
import myau.util.TeamUtil;
import myau.util.TimerUtil;
import myau.util.render.PostProcessing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.List;

public class TargetHUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat healthFormat = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.US));
    private static final DecimalFormat diffFormat = new DecimalFormat("+0.0;-0.0", new DecimalFormatSymbols(Locale.US));
    private static final float FIXED_BAR_WIDTH = 130f;
    private static final float ANIMATION_SPEED = 0.1f;

    private final TimerUtil lastAttackTimer = new TimerUtil();
    private final TimerUtil healthDelayTimer = new TimerUtil();
    private final List<EntityLivingBase> targets = new ArrayList<>();
    private final List<Float> displayHealths = new ArrayList<>();
    private final List<Float> delayedHealths = new ArrayList<>();
    private final List<ResourceLocation> headTextures = new ArrayList<>();
    private final Map<Integer, Float> entityAlphas = new HashMap<>();
    private final Map<Integer, EntityLivingBase> entityCache = new HashMap<>();

    private boolean dragging = false;
    private int dragStartX = 0, dragStartY = 0, dragStartOffX = 0, dragStartOffY = 0;
    private boolean positionLocked = true;

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Myau", "Adjust"});
    public final ModeProperty color = new ModeProperty("color", 0, new String[]{"DEFAULT", "HUD"});
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final IntProperty offX = new IntProperty("OffsetX", 100, -500, 900);
    public final IntProperty offY = new IntProperty("OffsetY", 100, -500, 500);
    public final PercentProperty background = new PercentProperty("background", 25);
    public final BooleanProperty head = new BooleanProperty("head", true);
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);
    public final BooleanProperty kaOnly = new BooleanProperty("ka-only", true);
    public final BooleanProperty chatPreview = new BooleanProperty("chat-preview", false);

    public final BooleanProperty blur = new BooleanProperty("Blur", true);
    public final ModeProperty bloom = new ModeProperty("Bloom", 0, new String[]{"Disabled", "Background Color", "Theme Color"});

    public TargetHUD() {
        super("TargetHUD", false, true);
    }

    private FontRenderer getHudFont() {
        HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
        return hud != null ? hud.getArrayListFontRenderer() : mc.fontRendererObj;
    }

    private List<EntityLivingBase> resolveTargets() {
        List<EntityLivingBase> result = new ArrayList<>();
        KillAura killAura = (KillAura) Myau.moduleManager.getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.isAttackAllowed()) {
            java.util.List<EntityLivingBase> kaTargets = killAura.getTargets();
            if (kaTargets != null) {
                for (EntityLivingBase t : kaTargets) {
                    if (TeamUtil.isEntityLoaded(t)) result.add(t);
                }
            }
        }
        if (result.isEmpty() && chatPreview.getValue() && mc.currentScreen instanceof GuiChat) {
            result.add(mc.thePlayer);
        }
        return result;
    }

    private EntityLivingBase lastTarget = null;

    private ResourceLocation getSkin(EntityLivingBase e) {
        if (e instanceof EntityPlayer) {
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(e.getName());
            if (info != null) return info.getLocationSkin();
        }
        return null;
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) return;

        if (mode.getValue() == 0) {
            lastTarget = resolveTargets().isEmpty() ? null : resolveTargets().get(0);
            if (lastTarget != null) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(offX.getValue().floatValue(), offY.getValue().floatValue(), -450);
                renderMyauLegacy(FIXED_BAR_WIDTH);
                GlStateManager.popMatrix();
            }
            return;
        }

        // ===== Adjust 模式 =====
        List<EntityLivingBase> activeTargets = resolveTargets();
        Set<Integer> activeIds = new HashSet<>();

        for (EntityLivingBase t : activeTargets) {
            activeIds.add(t.getEntityId());
            entityAlphas.putIfAbsent(t.getEntityId(), 0f);
            entityCache.put(t.getEntityId(), t);
            entityAlphas.put(t.getEntityId(), Math.min(1f, entityAlphas.get(t.getEntityId()) + ANIMATION_SPEED));
        }

        Iterator<Map.Entry<Integer, Float>> iter = entityAlphas.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Integer, Float> entry = iter.next();
            if (!activeIds.contains(entry.getKey())) {
                float newAlpha = Math.max(0f, entry.getValue() - ANIMATION_SPEED);
                if (newAlpha <= 0f) {
                    iter.remove();
                    entityCache.remove(entry.getKey());
                } else {
                    entry.setValue(newAlpha);
                }
            }
        }

        if (entityAlphas.isEmpty() && !(mc.currentScreen instanceof GuiChat)) return;

        List<Map.Entry<Integer, Float>> sortedEntries = new ArrayList<>(entityAlphas.entrySet());
        int renderedCount = 0;

        float s = scale.getValue();
        float barW = FIXED_BAR_WIDTH * s;
        int rows = (sortedEntries.size() + 1) / 2;
        float totalWidth = sortedEntries.size() >= 2 ? barW * 2 + 4 * s : barW;
        float totalHeight = rows * 37f * s + (rows - 1) * 4f * s;

        ScaledResolution sr = new ScaledResolution(mc);
        int mouseX = Mouse.getX() * sr.getScaledWidth() / mc.displayWidth;
        int mouseY = sr.getScaledHeight() - Mouse.getY() * sr.getScaledHeight() / mc.displayHeight - 1;

        positionLocked = !(mc.currentScreen instanceof GuiChat);

        // 直接使用全局坐标 (和 Session 一样)
        float x = offX.getValue().floatValue();
        float y = offY.getValue().floatValue();

        if (!positionLocked) {
            if (Mouse.isButtonDown(0) && !dragging) {
                if (mouseX >= x && mouseX <= x + totalWidth && mouseY >= y && mouseY <= y + totalHeight) {
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
                x = offX.getValue().floatValue();
                y = offY.getValue().floatValue();
            }
        }

        // ===== 收集所有目标的矩形 (全局坐标) =====
        List<float[]> targetRects = new ArrayList<>();
        renderedCount = 0;

        for (Map.Entry<Integer, Float> entry : sortedEntries) {
            float alpha = entry.getValue();
            if (alpha <= 0.01f) continue;

            EntityLivingBase target = entityCache.get(entry.getKey());
            if (target == null) continue;

            int row = renderedCount / 2, col = renderedCount % 2;
            float ox = x + col * (barW + 4 * s);
            float oy = y + row * 37f * s + row * 4f * s;
            float barH = 37f * s;

            targetRects.add(new float[]{ox, oy, barW, barH});
            renderedCount++;
        }

        // ===== 获取主题色 =====
        HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
        int themeColor = hud != null ? hud.getColor(System.currentTimeMillis()).getRGB() : Color.WHITE.getRGB();

        // ===== 1. Blur =====
        if (!targetRects.isEmpty() && blur.getValue()) {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (float[] rect : targetRects) {
                minX = Math.min(minX, rect[0]);
                minY = Math.min(minY, rect[1]);
                maxX = Math.max(maxX, rect[0] + rect[2]);
                maxY = Math.max(maxY, rect[1] + rect[3]);
            }
            final float fminX = minX, fminY = minY, fmaxX = maxX, fmaxY = maxY;
            final List<float[]> rects = new ArrayList<>(targetRects);
            PostProcessing.drawBlur(fminX, fminY, fmaxX, fmaxY, () -> () -> {
                GlStateManager.enableBlend();
                GlStateManager.disableTexture2D();
                GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                RenderUtil.setup2DRendering(() -> {
                    for (float[] r : rects) {
                        Gui.drawRect((int) r[0], (int) r[1], (int) (r[0] + r[2]), (int) (r[1] + r[3]), -1);
                    }
                });
                GlStateManager.enableTexture2D();
                GlStateManager.disableBlend();
            });
        }

        // ===== 重置 OpenGL 状态 =====
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableDepth();
        GL11.glDisable(GL11.GL_STENCIL_TEST);

        // ===== 2. 半透明背景 =====
        if (!targetRects.isEmpty()) {
            int bg = new Color(0, 0, 0, (int) ((float) background.getValue() / 100f * 255)).getRGB();
            for (float[] rect : targetRects) {
                Gui.drawRect((int) rect[0], (int) rect[1], (int) (rect[0] + rect[2]), (int) (rect[1] + rect[3]), bg);
            }
        }

        // ===== 3. Bloom =====
        if (!targetRects.isEmpty() && bloom.getValue() != 0) {
            Framebuffer bloomBuffer = PostProcessing.beginBloom();
            if (bloomBuffer != null) {
                int index = 0;
                for (float[] rect : targetRects) {
                    int bloomColor;
                    if (bloom.getValue() == 1) {
                        bloomColor = ColorUtil.swapAlpha(0xFF000000, 255);
                    } else {
                        bloomColor = ColorUtil.swapAlpha(themeColor, 255);
                    }
                    Gui.drawRect((int) rect[0], (int) rect[1], (int) (rect[0] + rect[2]), (int) (rect[1] + rect[3]), bloomColor);
                    index++;
                }
                PostProcessing.endBloom(bloomBuffer);
            }
        }

        // ===== 4. 绘制内容 (直接使用全局坐标) =====
        FontRenderer font = getHudFont();
        renderedCount = 0;

        for (Map.Entry<Integer, Float> entry : sortedEntries) {
            float alpha = entry.getValue();
            if (alpha <= 0.01f) continue;

            EntityLivingBase target = entityCache.get(entry.getKey());
            if (target == null) continue;

            int row = renderedCount / 2, col = renderedCount % 2;
            float ox = x + col * (barW + 4 * s);
            float oy = y + row * 37f * s + row * 4f * s;
            float barH = 37f * s;
            float padding = 2f * s;

            int idx = renderedCount;
            while (displayHealths.size() <= idx) displayHealths.add(target.getHealth());
            while (delayedHealths.size() <= idx) delayedHealths.add(target.getHealth());
            while (headTextures.size() <= idx) headTextures.add(null);

            float health = target.getHealth(), maxHealth = target.getMaxHealth();
            float disp = displayHealths.get(idx), del = delayedHealths.get(idx);
            if (health < disp) { disp = health; healthDelayTimer.reset(); }
            else { disp += (health - disp) / 4f; if (Math.abs(disp - health) < 0.01f) disp = health; }
            if (healthDelayTimer.hasTimeElapsed(200L)) {
                del += (disp - del) / 4f; if (Math.abs(del - disp) < 0.01f) del = disp;
            }
            displayHealths.set(idx, disp); delayedHealths.set(idx, del);
            if (headTextures.get(idx) == null) headTextures.set(idx, getSkin(target));

            float dispWidth = (barW - padding * 2) * (disp / maxHealth);
            float delWidth = (barW - padding * 2) * (del / maxHealth);

            String sheesh = healthFormat.format(Math.abs(mc.thePlayer.getHealth() - health));
            String healthDiff = mc.thePlayer.getHealth() < health ? "-" + sheesh : "+" + sheesh;

            HUD hud2 = (HUD) Myau.moduleManager.getModule(HUD.class);
            Color leftC = hud2 != null ? new Color(hud2.custom1.getValue()) : Color.WHITE;
            Color rightC = hud2 != null ? new Color(hud2.custom2.getValue()) : Color.WHITE;

            float barLeft = ox + padding, barRight = ox + barW - padding;
            float barTop = oy + barH - 6f * s, barBottom = oy + barH - 2f * s;
            float barWidth = barRight - barLeft;

            RenderUtil.enableRenderState();
            RenderUtil.drawRect(barLeft, barTop, barRight, barBottom, ColorUtil.darker(new Color(0, 0, 0, (int)(100 * alpha)), 0.3f).getRGB());
            for (int i = 0; i < (int) delWidth; i++) {
                float prog = (float) i / barWidth;
                Color blended = ColorUtil.interpolate(prog, leftC, rightC);
                RenderUtil.drawRect(barLeft + i, barTop, barLeft + i + 1, barBottom, new Color(blended.getRed(), blended.getGreen(), blended.getBlue(), (int)(128 * alpha)).getRGB());
            }
            for (int i = 0; i < (int) dispWidth; i++) {
                float prog = (float) i / barWidth;
                Color blended = ColorUtil.interpolate(prog, leftC, rightC);
                RenderUtil.drawRect(barLeft + i, barTop, barLeft + i + 1, barBottom, new Color(blended.getRed(), blended.getGreen(), blended.getBlue(), (int)(255 * alpha)).getRGB());
            }
            RenderUtil.disableRenderState();

            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            font.drawString(target.getName(), (int)(ox + padding + 28f * s), (int)(oy + 3f * s), new Color(255, 255, 255, (int)(255 * alpha)).getRGB(), false);
            font.drawString(healthDiff, (int)(ox + barW - padding - font.getStringWidth(healthDiff)), (int)(oy + barH - 14f * s - padding), new Color(200, 200, 200, (int)(255 * alpha)).getRGB(), false);

            if (head.getValue() && headTextures.get(idx) != null) {
                GlStateManager.color(1, 1, 1, alpha);
                mc.getTextureManager().bindTexture(headTextures.get(idx));
                float headSize = 26f * s;
                Gui.drawScaledCustomSizeModalRect((int)(ox + padding), (int)(oy + padding), 8, 8, 8, 8, (int)headSize, (int)headSize, 64, 64);
                Gui.drawScaledCustomSizeModalRect((int)(ox + padding), (int)(oy + padding), 40, 8, 8, 8, (int)headSize, (int)headSize, 64, 64);
            }

            List<ItemStack> items = new ArrayList<>();
            if (target.getHeldItem() != null) items.add(target.getHeldItem());
            for (int index = 3; index >= 0; index--) {
                ItemStack stack = target.getCurrentArmor(index);
                if (stack != null) items.add(stack);
            }
            float itemX = ox + 28f * s + padding;
            for (ItemStack stack : items) {
                float itemScale = 0.7f * s;
                renderHudItem(stack, itemX, oy + 14f * s + padding, itemScale, alpha);
                itemX += 12f * s;
            }
            GlStateManager.disableBlend();
            GlStateManager.enableDepth();

            renderedCount++;
        }
    }

    private void renderHudItem(ItemStack stack, float x, float y, float itemScale, float alpha) {
        if (stack == null) {
            return;
        }

        float clampedAlpha = Math.max(0.0F, Math.min(1.0F, alpha));
        ItemStack renderStack = clampedAlpha < 0.99F ? stripGlintForAlpha(stack) : stack;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(itemScale, itemScale, 1.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, clampedAlpha);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemIntoGUI(renderStack, 0, 0);
        mc.getRenderItem().renderItemOverlays(mc.fontRendererObj, renderStack, 0, 0);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
    }

    private ItemStack stripGlintForAlpha(ItemStack stack) {
        if (!stack.hasEffect() || stack.getTagCompound() == null) {
            return stack;
        }

        ItemStack copy = stack.copy();
        NBTTagCompound tag = (NBTTagCompound) stack.getTagCompound().copy();
        tag.removeTag("ench");
        tag.removeTag("StoredEnchantments");
        copy.setTagCompound(tag);
        return copy;
    }

    private void renderMyauLegacy(float barTotalWidth) {
        float health = (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2f;
        float abs = lastTarget.getAbsorptionAmount() / 2f;
        float heal = lastTarget.getHealth() / 2f + abs;

        String targetNameText = ChatColors.formatColor(String.format("&r%s&r", TeamUtil.stripName(lastTarget)));
        String healthText = ChatColors.formatColor(String.format("&r&f%s%s❤&r", healthFormat.format(heal), abs > 0f ? "&6" : "&c"));
        String statusText = ChatColors.formatColor(String.format("&r&l%s&r", heal == health ? "D" : (heal < health ? "W" : "L")));
        String healthDiffText = ChatColors.formatColor(String.format("&r%s&r", heal == health ? "0.0" : diffFormat.format(health - heal)));

        FontRenderer font = getHudFont();
        int statusTextWidth = font.getStringWidth(statusText);
        int healthDiffWidth = font.getStringWidth(healthDiffText);
        float headIconOffset = head.getValue() && !headTextures.isEmpty() ? 25f : 0f;

        float rectLeft = 0;
        float rectTop = 0;
        float rectRight = barTotalWidth;
        float rectBottom = 27f;

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

        // ===== 重置 OpenGL 状态 =====
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableDepth();
        GL11.glDisable(GL11.GL_STENCIL_TEST);

        RenderUtil.enableRenderState();
        int bg = new Color(0, 0, 0, (int) ((float) background.getValue() / 100f * 255.0F)).getRGB();
        RenderUtil.drawOutlineRect(0, 0, barTotalWidth, 27, 1.5f, bg, 0);

        // ===== Bloom (Myau 模式) =====
        if (bloom.getValue() != 0) {
            Framebuffer bloomBuffer = PostProcessing.beginBloom();
            if (bloomBuffer != null) {
                int bloomColor;
                if (bloom.getValue() == 1) {
                    bloomColor = ColorUtil.swapAlpha(0xFF000000, 255);
                } else {
                    HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
                    int themeColor = hud != null ? hud.getColor(System.currentTimeMillis()).getRGB() : Color.WHITE.getRGB();
                    bloomColor = ColorUtil.swapAlpha(themeColor, 255);
                }
                Gui.drawRect((int) rectLeft, (int) rectTop, (int) rectRight, (int) rectBottom, bloomColor);
                PostProcessing.endBloom(bloomBuffer);
            }
        }

        RenderUtil.drawRect(headIconOffset + 2, 22, barTotalWidth - 2, 25, ColorUtil.darker(Color.WHITE, 0.2f).getRGB());
        RenderUtil.drawRect(headIconOffset + 2, 22, headIconOffset + 2 + health / lastTarget.getMaxHealth() * (barTotalWidth - 2 - headIconOffset - 2), 25, Color.WHITE.getRGB());
        RenderUtil.disableRenderState();

        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        font.drawString(targetNameText, headIconOffset + 2, 2, -1, shadow.getValue());
        font.drawString(healthText, headIconOffset + 2, 12, -1, shadow.getValue());
        font.drawString(statusText, barTotalWidth - 2 - statusTextWidth, 2, -1, shadow.getValue());
        font.drawString(healthDiffText, barTotalWidth - 2 - healthDiffWidth, 12, ColorUtil.darker(Color.WHITE, 0.8f).getRGB(), shadow.getValue());

        if (head.getValue()) {
            ResourceLocation skin = getSkin(lastTarget);
            if (skin != null) {
                GlStateManager.color(1, 1, 1, 1);
                mc.getTextureManager().bindTexture(skin);
                Gui.drawScaledCustomSizeModalRect(2, 2, 8, 8, 8, 8, 23, 23, 64, 64);
                Gui.drawScaledCustomSizeModalRect(2, 2, 40, 8, 8, 8, 23, 23, 64, 64);
            }
        }

        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.SEND && event.getPacket() instanceof C02PacketUseEntity) {
            C02PacketUseEntity p = (C02PacketUseEntity) event.getPacket();
            if (p.getAction() != Action.ATTACK) return;
            Entity e = p.getEntityFromWorld(mc.theWorld);
            if (e instanceof EntityLivingBase && !(e instanceof EntityArmorStand)) {
                lastAttackTimer.reset();
                lastTarget = (EntityLivingBase) e;
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{mode.getModeString()};
    }

    public EntityLivingBase getTarget() { return targets.isEmpty() ? null : targets.get(0); }
}
