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
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class TargetHUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat healthFormat = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.US));
    private static final DecimalFormat diffFormat = new DecimalFormat("+0.0;-0.0", new DecimalFormatSymbols(Locale.US));

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

    private boolean dragging = false;
    private int dragStartX = 0;
    private int dragStartY = 0;
    private int dragStartOffX = 0;
    private int dragStartOffY = 0;
    private boolean positionLocked = true;

    private UFontRenderer modernFont;

    public final ModeProperty style = new ModeProperty("Style", 0, new String[]{"Myau", "Astolfo"});
    public final ModeProperty fontMode = new ModeProperty("font-mode", 0, new String[]{"Minecraft", "Modern"});
    public final ModeProperty color = new ModeProperty("color", 0, new String[]{"DEFAULT", "HUD"});
    public final ModeProperty posX = new ModeProperty("position-x", 1, new String[]{"LEFT", "MIDDLE", "RIGHT"});
    public final ModeProperty posY = new ModeProperty("position-y", 1, new String[]{"TOP", "MIDDLE", "BOTTOM"});
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final IntProperty offX = new IntProperty("offset-x", 0, -255, 255);
    public final IntProperty offY = new IntProperty("offset-y", 40, -255, 255);
    public final PercentProperty background = new PercentProperty("background", 25);
    public final BooleanProperty head = new BooleanProperty("head", true);
    public final BooleanProperty indicator = new BooleanProperty("indicator", true);
    public final BooleanProperty outline = new BooleanProperty("outline", false);
    public final BooleanProperty animations = new BooleanProperty("animations", true);
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);
    public final BooleanProperty kaOnly = new BooleanProperty("ka-only", true);
    public final BooleanProperty chatPreview = new BooleanProperty("chat-preview", false);

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

    private ResourceLocation getSkin(EntityLivingBase entityLivingBase) {
        if (entityLivingBase instanceof EntityPlayer) {
            NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(entityLivingBase.getName());
            if (playerInfo != null) {
                return playerInfo.getLocationSkin();
            }
        }
        return null;
    }

    private Color getTargetColor(EntityLivingBase entityLivingBase) {
        if (entityLivingBase instanceof EntityPlayer) {
            if (TeamUtil.isFriend((EntityPlayer) entityLivingBase)) {
                return Myau.friendManager.getColor();
            }
            if (TeamUtil.isTarget((EntityPlayer) entityLivingBase)) {
                return Myau.targetManager.getColor();
            }
        }
        switch (this.color.getValue()) {
            case 0:
                if (!(entityLivingBase instanceof EntityPlayer)) {
                    return new Color(-1);
                }
                return TeamUtil.getTeamColor((EntityPlayer) entityLivingBase, 1.0F);
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

                switch (this.style.getValue()) {
                    case 0:
                        renderMyauMode();
                        break;
                    case 1:
                        renderAstolfoMode();
                        break;
                }
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
                switch (this.style.getValue()) {
                    case 0:
                        renderMyauMode();
                        break;
                    case 1:
                        renderAstolfoMode();
                        break;
                }
            }
        }

        if (isAnimatingOut) {
            updateScaleAnimation();
            if (scaleAnimation > 0.0F && lastRenderTarget != null) {
                this.target = lastRenderTarget;
                switch (this.style.getValue()) {
                    case 0:
                        renderMyauMode();
                        break;
                    case 1:
                        renderAstolfoMode();
                        break;
                }
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

    private void renderAstolfoMode() {
        ScaledResolution sr = new ScaledResolution(mc);
        FontRenderer fr = getFontRenderer();

        String name = TeamUtil.stripName(this.target);
        float health = this.target.getHealth() / 2.0F + this.target.getAbsorptionAmount() / 2.0F;
        float maxH = this.target.getMaxHealth() / 2.0F;
        float healthPct = Math.min(health / maxH, 1.0F);

        int nameWidth = fr.getStringWidth(name);
        float width = Math.max(130, nameWidth + 60);
        float height = 56;

        float posX = this.offX.getValue().floatValue() / this.scale.getValue();
        switch (this.posX.getValue()) {
            case 1:
                posX += sr.getScaledWidth() / this.scale.getValue() / 2.0F - width / 2.0F;
                break;
            case 2:
                posX = sr.getScaledWidth() / this.scale.getValue() - width - posX;
                break;
        }
        float posY = this.offY.getValue().floatValue() / this.scale.getValue();
        switch (this.posY.getValue()) {
            case 1:
                posY += sr.getScaledHeight() / this.scale.getValue() / 2.0F - height / 2.0F;
                break;
            case 2:
                posY = sr.getScaledHeight() / this.scale.getValue() - height - posY;
                break;
        }

        int mouseX = Mouse.getX() * sr.getScaledWidth() / mc.displayWidth;
        int mouseY = sr.getScaledHeight() - Mouse.getY() * sr.getScaledHeight() / mc.displayHeight - 1;
        float renderX = posX * this.scale.getValue();
        float renderY = posY * this.scale.getValue();
        float renderW = width * this.scale.getValue();
        float renderH = height * this.scale.getValue();
        this.positionLocked = !(mc.currentScreen instanceof GuiChat);
        if (!this.positionLocked) {
            if (Mouse.isButtonDown(0) && !this.dragging) {
                if (mouseX >= renderX && mouseX <= renderX + renderW && mouseY >= renderY && mouseY <= renderY + renderH) {
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
                int dx = mouseX - this.dragStartX;
                int dy = mouseY - this.dragStartY;
                this.offX.setValue(this.dragStartOffX + dx);
                this.offY.setValue(this.dragStartOffY + dy);
            }
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(posX + width / 2.0F, posY + height / 2.0F, -450.0F);
        float finalScale = this.scale.getValue() * scaleAnimation;
        GlStateManager.scale(finalScale, finalScale, 0.0F);
        GlStateManager.translate(-width / 2.0F, -height / 2.0F, 0.0F);

        RenderUtil.enableRenderState();
        int bgAlpha = (int) (this.background.getValue() / 100.0F * 255);
        int bgColor = new Color(0, 0, 0, bgAlpha).getRGB();
        RenderUtil.drawRect(0, 0, width, height, bgColor);
        RenderUtil.disableRenderState();

        drawEntityOnScreen(25, 45, this.target);

        Color textColor = this.getTargetColor(this.target);
        fr.drawString(name, 50, 6, -1, this.shadow.getValue());

        GlStateManager.pushMatrix();
        GlStateManager.scale(1.5F, 1.5F, 1.5F);
        fr.drawString(healthFormat.format(health) + " ❤", 50 / 1.5F, 22 / 1.5F, textColor.getRGB(), this.shadow.getValue());
        GlStateManager.popMatrix();

        float barWidth = width - 54;
        RenderUtil.enableRenderState();
        RenderUtil.drawRect(48, 42, 48 + barWidth, 49, ColorUtil.darker(textColor, 0.3F).getRGB());
        float barFill = Math.max(0, Math.min(barWidth * healthPct, barWidth));
        int barColor = textColor.getRGB();
        RenderUtil.drawRect(48, 42, 48 + barFill, 49, barColor);
        RenderUtil.disableRenderState();

        GlStateManager.popMatrix();
    }

    private void drawEntityOnScreen(int x, int y, EntityLivingBase ent) {
        GlStateManager.enableColorMaterial();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 50.0F);
        float largestSize = Math.max(ent.height, ent.width);
        float relativeScale = Math.max(largestSize / 1.8F, 1);
        GlStateManager.scale((float) -16 / relativeScale, (float) 16 / relativeScale, (float) 16 / relativeScale);
        GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(135.0F, 0.0F, 1.0F, 0.0F);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.rotate(-135.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-((float) Math.atan((double) ((float) 17 / 40.0F))) * 20.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.translate(0.0F, 0.0F, 0.0F);
        RenderManager renderManager = mc.getRenderManager();
        renderManager.setPlayerViewY(180.0F);
        renderManager.setRenderShadow(false);
        renderManager.renderEntityWithPosYaw(ent, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F);
        renderManager.setRenderShadow(true);
        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
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