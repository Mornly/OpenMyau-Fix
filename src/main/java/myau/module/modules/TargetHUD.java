package myau.module.modules;

import myau.Myau;
import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.Render2DEvent;
import myau.font.UFontRenderer;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.ColorUtil;
import myau.util.RenderUtil;
import myau.util.TeamUtil;
import myau.util.TimerUtil;
import myau.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
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
import org.lwjgl.opengl.EXTFramebufferObject;
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
    private final TimerUtil animTimer = new TimerUtil();
    private final TimerUtil scaleAnimTimer = new TimerUtil();
    private final TimerUtil targetLostTimer = new TimerUtil();
    private EntityLivingBase lastTarget = null;
    private EntityLivingBase target = null;
    private ResourceLocation headTexture = null;
    private float oldHealth = 0.0F;
    private float newHealth = 0.0F;
    private float maxHealth = 0.0F;
    private float scaleAnimation = 0.0F;
    private boolean isAnimatingOut = false;
    private boolean targetLost = false;
    private EntityLivingBase lastRenderTarget = null;

    private final Map<Integer, Float> displayHealths = new HashMap<>();
    private final Map<Integer, Float> delayedHealths = new HashMap<>();
    private final Map<Integer, ResourceLocation> headTextures = new HashMap<>();
    private final Map<Integer, Float> entityAlphas = new HashMap<>();
    private final Map<Integer, EntityLivingBase> entityCache = new HashMap<>();
    private final TimerUtil healthDelayTimer = new TimerUtil();

    private boolean dragging = false;
    private int dragStartX = 0;
    private int dragStartY = 0;
    private int dragStartOffX = 0;
    private int dragStartOffY = 0;
    private boolean positionLocked = true;

    private UFontRenderer modernFont;

    public final ModeProperty style = new ModeProperty("Style", 0, new String[]{"Myau", "Adjust"});
    public final ModeProperty fontMode = new ModeProperty("font-mode", 0, new String[]{"Minecraft", "Modern"});
    public final ModeProperty color = new ModeProperty("color", 0, new String[]{"DEFAULT", "HUD"});
    public final ModeProperty posX = new ModeProperty("position-x", 1, new String[]{"LEFT", "MIDDLE", "RIGHT"});
    public final ModeProperty posY = new ModeProperty("position-y", 1, new String[]{"TOP", "MIDDLE", "BOTTOM"});
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final IntProperty offX = new IntProperty("offset-x", 0, -500, 500);
    public final IntProperty offY = new IntProperty("offset-y", 40, -500, 500);
    public final PercentProperty background = new PercentProperty("background", 25);
    public final BooleanProperty head = new BooleanProperty("head", true);
    public final BooleanProperty indicator = new BooleanProperty("indicator", true);
    public final BooleanProperty outline = new BooleanProperty("outline", false);
    public final BooleanProperty animations = new BooleanProperty("animations", true);
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);
    public final BooleanProperty kaOnly = new BooleanProperty("ka-only", true);
    public final BooleanProperty chatPreview = new BooleanProperty("chat-preview", false);
    public final BooleanProperty trackTarget = new BooleanProperty("track-target", false);
    public final ModeProperty trackingMode = new ModeProperty("tracking-mode", 0, new String[]{"TOP", "MIDDLE", "LEFT", "RIGHT"}, trackTarget::getValue);
    public final BooleanProperty distanceScale = new BooleanProperty("distance-scale", true, trackTarget::getValue);

    public TargetHUD() {
        super("TargetHUD", false, true);
    }

    private FontRenderer getFontRenderer() {
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

    private EntityLivingBase resolveTarget() {
        KillAura killAura = (KillAura) Myau.moduleManager.getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.isAttackAllowed() && TeamUtil.isEntityLoaded(killAura.getTarget())) {
            return killAura.getTarget();
        } else if (!this.kaOnly.getValue()
                && !this.lastAttackTimer.hasTimeElapsed(1500L)
                && TeamUtil.isEntityLoaded(this.lastTarget)) {
            return this.lastTarget;
        } else {
            return this.chatPreview.getValue() && mc.currentScreen instanceof GuiChat ? mc.thePlayer : null;
        }
    }

    private List<EntityLivingBase> resolveTargets() {
        List<EntityLivingBase> result = new ArrayList<>();
        KillAura killAura = (KillAura) Myau.moduleManager.getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.isAttackAllowed()) {
            List<EntityLivingBase> kaTargets = killAura.getTargets();
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

    private ResourceLocation getSkin(EntityLivingBase entity) {
        if (entity instanceof EntityPlayer) {
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(entity.getName());
            if (info != null) return info.getLocationSkin();
        }
        return null;
    }

    private Color getTargetColor(EntityLivingBase entity) {
        if (entity instanceof EntityPlayer) {
            if (TeamUtil.isFriend((EntityPlayer) entity)) {
                return Myau.friendManager.getColor();
            }
            if (TeamUtil.isTarget((EntityPlayer) entity)) {
                return Myau.targetManager.getColor();
            }
        }
        switch (this.color.getValue()) {
            case 0:
                if (!(entity instanceof EntityPlayer)) {
                    return new Color(-1);
                }
                return TeamUtil.getTeamColor((EntityPlayer) entity, 1.0F);
            case 1:
                HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
                if (hud != null) {
                    int rgb = hud.getColor(System.currentTimeMillis()).getRGB();
                    return new Color(rgb);
                }
                return new Color(-1);
            default:
                return new Color(-1);
        }
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) return;

        if (style.getValue() == 1) {
            renderAdjustMode();
            return;
        }

        checkSetupFBO();

        EntityLivingBase currentTarget = this.resolveTarget();

        if (currentTarget != null) {
            targetLost = false;
            if (!isAnimatingOut) {
                if (this.target != currentTarget) {
                    if (this.target == null) {
                        scaleAnimTimer.reset();
                    }
                    this.target = currentTarget;
                    this.lastRenderTarget = currentTarget;
                    this.headTexture = null;
                    this.animTimer.setTime();
                    float heal = this.target.getHealth() / 2.0F + this.target.getAbsorptionAmount() / 2.0F;
                    this.oldHealth = heal;
                    this.newHealth = heal;
                }
                updateScaleAnimation();

                renderMyauMode();
            }
        } else if (this.target != null) {
            if (!targetLost) {
                targetLost = true;
                targetLostTimer.reset();
            }

            if (targetLostTimer.hasTimeElapsed(50L)) {
                isAnimatingOut = true;
                scaleAnimTimer.reset();
                this.target = null;
            } else {
                renderMyauMode();
            }
        }

        if (isAnimatingOut) {
            updateScaleAnimation();
            if (scaleAnimation > 0.0F && lastRenderTarget != null) {
                this.target = lastRenderTarget;
                renderMyauMode();
                this.target = null;
            } else if (scaleAnimation <= 0.0F) {
                isAnimatingOut = false;
                targetLost = false;
                lastRenderTarget = null;
            }
        }
    }

    private void checkSetupFBO() {
        Framebuffer fbo = mc.getFramebuffer();
        if (fbo != null && fbo.depthBuffer > -1) {
            EXTFramebufferObject.glDeleteRenderbuffersEXT(fbo.depthBuffer);
            int stencil_depth_buffer_id = EXTFramebufferObject.glGenRenderbuffersEXT();
            EXTFramebufferObject.glBindRenderbufferEXT(36161, stencil_depth_buffer_id);
            EXTFramebufferObject.glRenderbufferStorageEXT(36161, 34041, mc.displayWidth, mc.displayHeight);
            EXTFramebufferObject.glFramebufferRenderbufferEXT(36160, 36128, 36161, stencil_depth_buffer_id);
            EXTFramebufferObject.glFramebufferRenderbufferEXT(36160, 36096, 36161, stencil_depth_buffer_id);
            fbo.depthBuffer = -1;
        }
    }

    private void updateScaleAnimation() {
        long elapsedTime = scaleAnimTimer.getElapsedTime();
        float animationDuration = 200.0F;

        if (!isAnimatingOut) {
            scaleAnimation = Math.min(elapsedTime / animationDuration, 1.0F);
        } else {
            scaleAnimation = Math.max(1.0F - (elapsedTime / animationDuration), 0.0F);
        }
        scaleAnimation = easeOutQuart(scaleAnimation);
    }

    private float easeOutQuart(float t) {
        return 1.0F - (float) Math.pow(1.0F - t, 4.0F);
    }

    private void renderMyauMode() {
        FontRenderer fr = getFontRenderer();
        float health = (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2.0F;
        float abs = this.target.getAbsorptionAmount() / 2.0F;
        float heal = this.target.getHealth() / 2.0F + abs;

        if (!this.animations.getValue() || this.animTimer.hasTimeElapsed(150L)) {
            this.oldHealth = this.newHealth;
            this.newHealth = heal;
            this.maxHealth = this.target.getMaxHealth() / 2.0F;
            if (this.oldHealth != this.newHealth) {
                this.animTimer.reset();
            }
        }

        ResourceLocation resourceLocation = this.getSkin(this.target);
        if (resourceLocation != null) {
            this.headTexture = resourceLocation;
        }

        float elapsedTime = (float) Math.min(Math.max(this.animTimer.getElapsedTime(), 0L), 150L);
        float healthRatio = Math.min(Math.max(RenderUtil.lerpFloat(this.newHealth, this.oldHealth, elapsedTime / 150.0F) / this.maxHealth, 0.0F), 1.0F);
        Color targetColor = this.getTargetColor(this.target);
        Color healthBarColor = this.color.getValue() == 0 ? ColorUtil.getHealthBlend(healthRatio) : targetColor;
        float healthDeltaRatio = Math.min(Math.max((health - heal + 1.0F) / 2.0F, 0.0F), 1.0F);
        Color healthDeltaColor = ColorUtil.getHealthBlend(healthDeltaRatio);

        ScaledResolution scaledResolution = new ScaledResolution(mc);
        String targetNameText = ChatColors.formatColor(String.format("&r%s&r", TeamUtil.stripName(this.target)));
        int targetNameWidth = fr.getStringWidth(targetNameText);
        String healthText = ChatColors.formatColor(
                String.format("&r&f%s%s❤&r", healthFormat.format(heal), abs > 0.0F ? "&6" : "&c")
        );
        int healthTextWidth = fr.getStringWidth(healthText);
        String statusText = ChatColors.formatColor(String.format("&r&l%s&r", heal == health ? "D" : (heal < health ? "W" : "L")));
        int statusTextWidth = fr.getStringWidth(statusText);
        String healthDiffText = ChatColors.formatColor(
                String.format("&r%s&r", heal == health ? "0.0" : diffFormat.format(health - heal))
        );
        int healthDiffWidth = fr.getStringWidth(healthDiffText);

        float barContentWidth = Math.max(
                (float) targetNameWidth + (this.indicator.getValue() ? 2.0F + (float) statusTextWidth + 2.0F : 0.0F),
                (float) healthTextWidth + (this.indicator.getValue() ? 2.0F + (float) healthDiffWidth + 2.0F : 0.0F)
        );
        float headIconOffset = this.head.getValue() && this.headTexture != null ? 25.0F : 0.0F;
        float barTotalWidth = Math.max(headIconOffset + 70.0F, headIconOffset + 2.0F + barContentWidth + 2.0F);

        float posX = this.offX.getValue().floatValue() / this.scale.getValue();
        switch (this.posX.getValue()) {
            case 1:
                posX += (float) scaledResolution.getScaledWidth() / this.scale.getValue() / 2.0F - barTotalWidth / 2.0F;
                break;
            case 2:
                posX *= -1.0F;
                posX += (float) scaledResolution.getScaledWidth() / this.scale.getValue() - barTotalWidth;
                break;
            default:
                break;
        }

        float posY = this.offY.getValue().floatValue() / this.scale.getValue();
        switch (this.posY.getValue()) {
            case 1:
                posY += (float) scaledResolution.getScaledHeight() / this.scale.getValue() / 2.0F - 13.5F;
                break;
            case 2:
                posY *= -1.0F;
                posY += (float) scaledResolution.getScaledHeight() / this.scale.getValue() - 27.0F;
                break;
            default:
                break;
        }

        int mouseX = Mouse.getX() * scaledResolution.getScaledWidth() / mc.displayWidth;
        int mouseY = scaledResolution.getScaledHeight() - Mouse.getY() * scaledResolution.getScaledHeight() / mc.displayHeight - 1;

        float renderX = posX * this.scale.getValue();
        float renderY = posY * this.scale.getValue();
        float renderWidth = barTotalWidth * this.scale.getValue();
        float renderHeight = 27.0F * this.scale.getValue();

        this.positionLocked = !(mc.currentScreen instanceof GuiChat);

        if (!this.positionLocked) {
            if (Mouse.isButtonDown(0) && !this.dragging) {
                if (mouseX >= renderX && mouseX <= renderX + renderWidth
                        && mouseY >= renderY && mouseY <= renderY + renderHeight) {
                    this.dragging = true;
                    this.dragStartX = mouseX;
                    this.dragStartY = mouseY;
                    this.dragStartOffX = this.offX.getValue();
                    this.dragStartOffY = this.offY.getValue();
                }
            } else if (!Mouse.isButtonDown(0)) {
                this.dragging = false;
            }

            if (this.dragging) {
                int deltaX = mouseX - this.dragStartX;
                int deltaY = mouseY - this.dragStartY;
                if (this.posX.getValue() == 2) deltaX = -deltaX;
                if (this.posY.getValue() == 2) deltaY = -deltaY;
                this.offX.setValue(this.dragStartOffX + deltaX);
                this.offY.setValue(this.dragStartOffY + deltaY);
            }
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(posX + barTotalWidth / 2.0F, posY + 13.5F, -450.0F);
        float finalScale = this.scale.getValue() * scaleAnimation;
        GlStateManager.scale(finalScale, finalScale, 0.0F);
        GlStateManager.translate(-barTotalWidth / 2.0F, -13.5F, 0.0F);

        RenderUtil.enableRenderState();
        int backgroundColor = new Color(0.0F, 0.0F, 0.0F, (float) this.background.getValue() / 100.0F).getRGB();
        int outlineColor = this.outline.getValue() ? targetColor.getRGB() : new Color(0, 0, 0, 0).getRGB();
        RenderUtil.drawOutlineRect(0.0F, 0.0F, barTotalWidth, 27.0F, 1.5F, backgroundColor, outlineColor);
        RenderUtil.drawRect(headIconOffset + 2.0F, 22.0F, barTotalWidth - 2.0F, 25.0F,
                ColorUtil.darker(healthBarColor, 0.2F).getRGB());
        RenderUtil.drawRect(headIconOffset + 2.0F, 22.0F,
                headIconOffset + 2.0F + healthRatio * (barTotalWidth - 2.0F - headIconOffset - 2.0F), 25.0F,
                healthBarColor.getRGB());
        RenderUtil.disableRenderState();

        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        fr.drawString(targetNameText, headIconOffset + 2.0F, 2.0F, -1, this.shadow.getValue());
        fr.drawString(healthText, headIconOffset + 2.0F, 12.0F, -1, this.shadow.getValue());

        if (this.indicator.getValue()) {
            fr.drawString(statusText, barTotalWidth - 2.0F - (float) statusTextWidth, 2.0F,
                    healthDeltaColor.getRGB(), this.shadow.getValue());
            fr.drawString(healthDiffText, barTotalWidth - 2.0F - (float) healthDiffWidth, 12.0F,
                    ColorUtil.darker(healthDeltaColor, 0.8F).getRGB(), this.shadow.getValue());
        }

        if (this.head.getValue() && this.headTexture != null) {
            GlStateManager.color(1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(this.headTexture);
            Gui.drawScaledCustomSizeModalRect(2, 2, 8.0F, 8.0F, 8, 8, 23, 23, 64.0F, 64.0F);
            Gui.drawScaledCustomSizeModalRect(2, 2, 40.0F, 8.0F, 8, 8, 23, 23, 64.0F, 64.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F);
        }

        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    private void renderAdjustMode() {
        List<EntityLivingBase> activeTargets = resolveTargets();
        Set<Integer> activeIds = new HashSet<>();

        for (EntityLivingBase t : activeTargets) {
            int id = t.getEntityId();
            activeIds.add(id);
            entityAlphas.putIfAbsent(id, 0f);
            entityCache.put(id, t);
            entityAlphas.put(id, Math.min(1f, entityAlphas.get(id) + ANIMATION_SPEED));
            displayHealths.putIfAbsent(id, t.getHealth());
            delayedHealths.putIfAbsent(id, t.getHealth());
            if (headTextures.get(id) == null) {
                headTextures.put(id, getSkin(t));
            }
        }

        Iterator<Map.Entry<Integer, Float>> iter = entityAlphas.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Integer, Float> entry = iter.next();
            int id = entry.getKey();
            if (!activeIds.contains(id)) {
                float newAlpha = Math.max(0f, entry.getValue() - ANIMATION_SPEED);
                if (newAlpha <= 0f) {
                    iter.remove();
                    entityCache.remove(id);
                    displayHealths.remove(id);
                    delayedHealths.remove(id);
                    headTextures.remove(id);
                } else {
                    entry.setValue(newAlpha);
                }
            }
        }

        if (entityAlphas.isEmpty() && !(mc.currentScreen instanceof GuiChat)) return;

        List<Map.Entry<Integer, Float>> sortedEntries = new ArrayList<>(entityAlphas.entrySet());
        sortedEntries.sort((a, b) -> {
            EntityLivingBase ea = entityCache.get(a.getKey());
            EntityLivingBase eb = entityCache.get(b.getKey());
            if (ea == null) return 1;
            if (eb == null) return -1;
            return Double.compare(RotationUtil.distanceToEntity(ea), RotationUtil.distanceToEntity(eb));
        });

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

        List<float[]> targetRects = new ArrayList<>();
        renderedCount = 0;

        for (Map.Entry<Integer, Float> entry : sortedEntries) {
            int id = entry.getKey();
            float alpha = entry.getValue();
            if (alpha <= 0.01f) continue;

            EntityLivingBase target = entityCache.get(id);
            if (target == null) continue;

            int row = renderedCount / 2, col = renderedCount % 2;
            float ox = x + col * (barW + 4 * s);
            float oy = y + row * 37f * s + row * 4f * s;
            float barH = 37f * s;

            targetRects.add(new float[]{ox, oy, barW, barH});
            renderedCount++;
        }

        if (targetRects.isEmpty()) return;

        int bg = new Color(0, 0, 0, (int) ((float) background.getValue() / 100f * 255)).getRGB();
        for (float[] rect : targetRects) {
            Gui.drawRect((int) rect[0], (int) rect[1], (int) (rect[0] + rect[2]), (int) (rect[1] + rect[3]), bg);
        }

        FontRenderer font = getFontRenderer();
        renderedCount = 0;

        for (Map.Entry<Integer, Float> entry : sortedEntries) {
            int id = entry.getKey();
            float alpha = entry.getValue();
            if (alpha <= 0.01f) continue;

            EntityLivingBase target = entityCache.get(id);
            if (target == null) continue;

            int row = renderedCount / 2, col = renderedCount % 2;
            float ox = x + col * (barW + 4 * s);
            float oy = y + row * 37f * s + row * 4f * s;
            float barH = 37f * s;
            float padding = 2f * s;

            float health = target.getHealth(), maxHealth = target.getMaxHealth();
            float disp = displayHealths.getOrDefault(id, health);
            float del = delayedHealths.getOrDefault(id, health);

            if (health < disp) { disp = health; healthDelayTimer.reset(); }
            else { disp += (health - disp) / 4f; if (Math.abs(disp - health) < 0.01f) disp = health; }
            if (healthDelayTimer.hasTimeElapsed(200L)) {
                del += (disp - del) / 4f; if (Math.abs(del - disp) < 0.01f) del = disp;
            }
            displayHealths.put(id, disp);
            delayedHealths.put(id, del);

            if (headTextures.get(id) == null) {
                headTextures.put(id, getSkin(target));
            }
            ResourceLocation skin = headTextures.get(id);

            float dispWidth = (barW - padding * 2) * (disp / maxHealth);
            float delWidth = (barW - padding * 2) * (del / maxHealth);

            String sheesh = healthFormat.format(Math.abs(mc.thePlayer.getHealth() - health));
            String healthDiff = mc.thePlayer.getHealth() < health ? "-" + sheesh : "+" + sheesh;

            HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
            Color leftC = hud != null ? new Color(hud.custom1.getValue()) : Color.WHITE;
            Color rightC = hud != null ? new Color(hud.custom2.getValue()) : Color.WHITE;

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

            if (head.getValue() && skin != null) {
                GlStateManager.color(1, 1, 1, alpha);
                mc.getTextureManager().bindTexture(skin);
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
        if (stack == null) return;

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
        if (!stack.hasEffect() || stack.getTagCompound() == null) return stack;
        ItemStack copy = stack.copy();
        NBTTagCompound tag = (NBTTagCompound) stack.getTagCompound().copy();
        tag.removeTag("ench");
        tag.removeTag("StoredEnchantments");
        copy.setTagCompound(tag);
        return copy;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.SEND && event.getPacket() instanceof C02PacketUseEntity) {
            C02PacketUseEntity packet = (C02PacketUseEntity) event.getPacket();
            if (packet.getAction() != Action.ATTACK) return;
            Entity entity = packet.getEntityFromWorld(mc.theWorld);
            if (entity instanceof EntityLivingBase && !(entity instanceof EntityArmorStand)) {
                this.lastAttackTimer.reset();
                this.lastTarget = (EntityLivingBase) entity;
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{style.getModeString()};
    }

    public EntityLivingBase getTarget() {
        return this.target;
    }
}