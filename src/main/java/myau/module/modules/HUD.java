package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.Render2DEvent;
import myau.events.TickEvent;
import myau.font.UFontRenderer;
import myau.mixin.IAccessorGuiChat;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.ColorUtil;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class HUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private List<Module> activeModules = new ArrayList<>();
    private final Map<Module, Float> animationMap = new HashMap<>();

    public final ModeProperty colorMode = new ModeProperty(
            "color", 3, new String[]{"RAINBOW", "CHROMA", "ASTOLFO", "CUSTOM1", "CUSTOM12", "CUSTOM123"}
    );
    public final FloatProperty colorSpeed = new FloatProperty("color-speed", 1.0F, 0.5F, 1.5F);
    public final PercentProperty colorSaturation = new PercentProperty("color-saturation", 50);
    public final PercentProperty colorBrightness = new PercentProperty("color-brightness", 100);
    public final ColorProperty custom1 = new ColorProperty("custom-color-1", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 3 || this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom2 = new ColorProperty("custom-color-2", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom3 = new ColorProperty("custom-color-3", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 5);
    public final ModeProperty fontMode = new ModeProperty("font-mode", 0, new String[]{"Minecraft", "Modern"});
    public final ModeProperty posX = new ModeProperty("position-x", 0, new String[]{"LEFT", "RIGHT"});
    public final ModeProperty posY = new ModeProperty("position-y", 0, new String[]{"TOP", "BOTTOM"});

    public final BooleanProperty showBar = new BooleanProperty("bar", true);
    public final ModeProperty sidebarMode = new ModeProperty("sidebar-mode", 0, new String[]{"RIGHT", "LEFT", "TOP", "OUTLINE", "NONE"}, this.showBar::getValue);
    public final FloatProperty barWidth = new FloatProperty("bar-width", 1.0F, 0.1F, 1.5F,
            () -> this.showBar.getValue() && this.sidebarMode.getValue() != 4 && this.sidebarMode.getValue() != 3);
    public final BooleanProperty animation = new BooleanProperty("animation", false);

    public final IntProperty offsetX = new IntProperty("offset-x", 2, -3, 255,
            () -> !(showBar.getValue() && sidebarMode.getValue() == 3 && animation.getValue()));
    public final IntProperty offsetY = new IntProperty("offset-y", 2, -3, 255,
            () -> !(showBar.getValue() && sidebarMode.getValue() == 3 && animation.getValue()));

    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final PercentProperty background = new PercentProperty("background", 25);
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);
    public final BooleanProperty suffixes = new BooleanProperty("suffixes", true);
    public final BooleanProperty interval = new BooleanProperty("interval", false);
    public final BooleanProperty lowerCase = new BooleanProperty("lower-case", false);
    public final BooleanProperty chatOutline = new BooleanProperty("chat-outline", true);
    public final BooleanProperty blinkTimer = new BooleanProperty("blink-timer", true);
    public final BooleanProperty toggleSound = new BooleanProperty("toggle-sounds", true);
    public final BooleanProperty toggleAlerts = new BooleanProperty("toggle-alerts", false);

    private UFontRenderer modernFontRenderer;
    private int savedOffsetX = 2;
    private int savedOffsetY = 2;
    private boolean wasOutlineAnimation = false;

    private FontRenderer getFontRenderer() {
        if (fontMode.getValue() == 1) {
            if (modernFontRenderer == null) {
                try {
                    modernFontRenderer = new UFontRenderer("GoogleSans-Regular", 20);
                } catch (Exception e) {
                    modernFontRenderer = null;
                }
            }
            if (modernFontRenderer != null) {
                return modernFontRenderer;
            }
        }
        return mc.fontRendererObj;
    }

    private boolean isModernFont() {
        return fontMode.getValue() == 1 && getFontRenderer() instanceof UFontRenderer;
    }

    private float getFontHeight() {
        FontRenderer fr = getFontRenderer();
        if (isModernFont()) {
            return ((UFontRenderer) fr).getHeight() + 1.0F;
        }
        return fr.FONT_HEIGHT;
    }

    private int getStringWidth(String text) {
        return getFontRenderer().getStringWidth(text);
    }

    private void drawStringWithShadow(String text, float x, float y, int color) {
        getFontRenderer().drawStringWithShadow(text, x, y, color);
    }

    private void drawString(String text, float x, float y, int color, boolean shadow) {
        getFontRenderer().drawString(text, x, y, color, shadow);
    }

    private String getModuleName(Module module) {
        String moduleName = module.getName();
        if (this.interval.getValue()) {
            moduleName = moduleName.replaceAll("(?<=[a-z])(?=[A-Z])", " ");
        }
        if (this.lowerCase.getValue()) {
            moduleName = moduleName.toLowerCase(Locale.ROOT);
        }
        return moduleName;
    }

    private String[] getModuleSuffix(Module module) {
        String[] moduleSuffix = module.getSuffix();
        if (this.lowerCase.getValue()) {
            for (int i = 0; i < moduleSuffix.length; i++) {
                moduleSuffix[i] = moduleSuffix[i].toLowerCase();
            }
        }
        return moduleSuffix;
    }

    private int getModuleWidth(Module module) {
        return this.calculateStringWidth(
                this.getModuleName(module), this.getModuleSuffix(module)
        );
    }

    private int calculateStringWidth(String string, String[] arr) {
        int width = getStringWidth(string);
        if (this.suffixes.getValue()) {
            for (String str : arr) {
                width += 3 + getStringWidth(str);
            }
        }
        return width;
    }

    private float getColorCycle(long long3, long long4) {
        long speed = (long) (3000.0 / Math.pow(Math.min(Math.max(0.5F, this.colorSpeed.getValue()), 1.5F), 3.0));
        return 1.0F - (float) (Math.abs(long3 - long4 * 300L) % speed) / (float) speed;
    }

    public HUD() {
        super("HUD", true, true);
    }

    public Color getColor(long time) {
        return this.getColor(time, 0L);
    }

    public Color getColor(long time, long offset) {
        Color color = Color.white;
        switch (this.colorMode.getValue()) {
            case 0:
                color = ColorUtil.fromHSB(this.getColorCycle(time, offset), 1.0F, 1.0F);
                break;
            case 1:
                color = ColorUtil.fromHSB(this.getColorCycle(time / 3L, 0L), 1.0F, 1.0F);
                break;
            case 2:
                float cycle = this.getColorCycle(time, offset);
                if (cycle % 1.0F < 0.5F) {
                    cycle = 1.0F - cycle % 1.0F;
                }
                color = ColorUtil.fromHSB(cycle, 1.0F, 1.0F);
                break;
            case 3:
                color = new Color(this.custom1.getValue());
                break;
            case 4:
                double cycle1 = this.getColorCycle(time, offset);
                color = ColorUtil.interpolate(
                        (float) (2.0 * Math.abs(cycle1 - Math.floor(cycle1 + 0.5))),
                        new Color(this.custom1.getValue()),
                        new Color(this.custom2.getValue())
                );
                break;
            case 5:
                double cycle2 = this.getColorCycle(time, offset);
                float floor = (float) (2.0 * Math.abs(cycle2 - Math.floor(cycle2 + 0.5)));
                if (floor <= 0.5F) {
                    color = ColorUtil.interpolate(floor * 2.0F, new Color(this.custom1.getValue()), new Color(this.custom2.getValue()));
                } else {
                    color = ColorUtil.interpolate((floor - 0.5F) * 2.0F, new Color(this.custom2.getValue()), new Color(this.custom3.getValue()));
                }
        }
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        return Color.getHSBColor(
                hsb[0],
                hsb[1] * (this.colorSaturation.getValue().floatValue() / 100.0F),
                hsb[2] * (this.colorBrightness.getValue().floatValue() / 100.0F)
        );
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.POST) {
            boolean isOutlineAnimation = this.showBar.getValue() && this.sidebarMode.getValue() == 3 && this.animation.getValue();
            if (isOutlineAnimation) {
                if (!wasOutlineAnimation) {
                    savedOffsetX = this.offsetX.getValue();
                    savedOffsetY = this.offsetY.getValue();
                    this.offsetX.setValue(-2);
                    this.offsetY.setValue(-1);
                    wasOutlineAnimation = true;
                }
            } else {
                if (wasOutlineAnimation) {
                    this.offsetX.setValue(savedOffsetX);
                    this.offsetY.setValue(savedOffsetY);
                    wasOutlineAnimation = false;
                }
            }

            List<Module> newActive;
            if (this.animation.getValue()) {
                newActive = Myau.moduleManager.modules.values().stream()
                        .filter(module -> !module.isHidden() && (module.isEnabled() || this.animationMap.getOrDefault(module, 0.0F) > 0.01F))
                        .collect(Collectors.toList());
            } else {
                newActive = Myau.moduleManager.modules.values().stream()
                        .filter(module -> module.isEnabled() && !module.isHidden())
                        .collect(Collectors.toList());
            }
            newActive.sort(Comparator.comparingInt(this::getModuleWidth).reversed());
            this.activeModules = newActive;
        }
    }

    @Override
    public void onEnabled() {
        wasOutlineAnimation = false;
        savedOffsetX = 2;
        savedOffsetY = 2;
        if (!(showBar.getValue() && sidebarMode.getValue() == 3 && animation.getValue())) {
            this.offsetX.setValue(2);
            this.offsetY.setValue(2);
        }
    }

    private static float animateSmooth(float target, float current, float speed, float deltaTime) {
        float diff = target - current;
        float change = diff * Math.min(1.0f, speed * deltaTime);
        if (Math.abs(change) < 0.01f && Math.abs(diff) < 0.01f) return target;
        return current + change;
    }

    private boolean hasSidebar() {
        return this.showBar.getValue() && this.sidebarMode.getValue() != 4;
    }

    private float getModuleRenderWidth(Module module) {
        return (float) this.calculateStringWidth(this.getModuleName(module), this.getModuleSuffix(module));
    }

    private void renderSidebar(Module module, int index, float bgX1, float bgY1, float bgX2, float bgY2, int color) {
        if (!this.hasSidebar()) {
            return;
        }

        float thickness;
        if (this.sidebarMode.getValue() == 3) {
            thickness = 1.0F;
        } else {
            thickness = this.barWidth.getValue();
        }

        boolean first = index == 0;
        boolean last = index == this.activeModules.size() - 1;
        boolean topList = this.posY.getValue() == 0;
        boolean visualFirst = topList ? first : last;
        boolean visualLast = topList ? last : first;

        switch (this.sidebarMode.getValue()) {
            case 1:
                this.drawVerticalSidebar(bgX1 - thickness, bgY1, bgY2, thickness, color);
                break;
            case 2:
                if (visualFirst) {
                    RenderUtil.drawRect(bgX1, bgY1 - thickness, bgX2, bgY1, color);
                }
                break;
            case 3:
                this.drawVerticalSidebar(bgX2, bgY1, bgY2, thickness, color);
                this.drawVerticalSidebar(bgX1 - thickness, bgY1, bgY2, thickness, color);
                if (visualFirst) {
                    RenderUtil.drawRect(bgX1 - thickness, bgY1 - thickness, bgX2 + thickness, bgY1, color);
                } else {
                    this.renderOutlineConnector(module, index, bgX1, bgY1, bgX2, bgY2, thickness, color);
                }
                if (visualLast) {
                    RenderUtil.drawRect(bgX1 - thickness, bgY2, bgX2 + thickness, bgY2 + thickness, color);
                }
                break;
            default:
                this.drawVerticalSidebar(bgX2, bgY1, bgY2, thickness, color);
                break;
        }
    }

    private void renderOutlineConnector(Module module, int index, float bgX1, float bgY1, float bgX2, float bgY2, float thickness, int color) {
        if (index <= 0) {
            return;
        }

        Module previous = this.activeModules.get(index - 1);
        float previousWidth = this.getModuleRenderWidth(previous);
        float rectExtraWidth = 2.0F;
        float boundaryY1 = this.posY.getValue() == 0 ? bgY1 - thickness : bgY2;
        float boundaryY2 = this.posY.getValue() == 0 ? bgY1 : bgY2 + thickness;

        if (this.posX.getValue() == 0) {
            float previousRight = bgX1 + previousWidth + rectExtraWidth;
            float start = Math.min(previousRight, bgX2);
            float end = Math.max(previousRight, bgX2) + thickness;
            RenderUtil.drawRect(start, boundaryY1, end, boundaryY2, color);
        } else {
            float previousLeft = bgX2 - previousWidth - rectExtraWidth;
            float start = Math.min(previousLeft, bgX1) - thickness;
            float end = Math.max(previousLeft, bgX1);
            RenderUtil.drawRect(start, boundaryY1, end, boundaryY2, color);
        }
    }

    private void drawVerticalSidebar(float x, float y1, float y2, float width, int color) {
        RenderUtil.drawRect(x, y1, x + width, y2, color);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (this.chatOutline.getValue() && mc.currentScreen instanceof GuiChat) {
            String text = ((IAccessorGuiChat) mc.currentScreen).getInputField().getText().trim();
            if (Myau.commandManager != null && Myau.commandManager.isTypingCommand(text)) {
                RenderUtil.enableRenderState();
                RenderUtil.drawOutlineRect(
                        2.0F,
                        (float) (mc.currentScreen.height - 14),
                        (float) (mc.currentScreen.width - 2),
                        (float) (mc.currentScreen.height - 2),
                        1.5F,
                        0,
                        this.getColor(System.currentTimeMillis()).getRGB()
                );
                RenderUtil.disableRenderState();
            }
        }
        if (!this.isEnabled() || mc.gameSettings.showDebugInfo) return;

        if (this.animation.getValue()) {
            this.renderAnimated();
        } else {
            this.renderStatic();
        }
    }

    private void renderStatic() {
        float height = getFontHeight();
        if (!isModernFont()) {
            height -= 1.0F;
        }
        float x = (float) this.offsetX.getValue()
                + (1.0F + (this.hasSidebar() ? 1.0F : 0.0F)) * this.scale.getValue();
        float y = (float) this.offsetY.getValue() + 1.0F * this.scale.getValue();
        if (this.posX.getValue() == 1) {
            x = (float) new ScaledResolution(mc).getScaledWidth() - x;
        }
        if (this.posY.getValue() == 1) {
            y = (float) new ScaledResolution(mc).getScaledHeight() - y - height * this.scale.getValue();
        }
        GlStateManager.pushMatrix();
        GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 0.0F);
        long l = System.currentTimeMillis();
        long offset = 0L;
        for (Module module : this.activeModules) {
            String moduleName = this.getModuleName(module);
            String[] moduleSuffix = this.getModuleSuffix(module);
            float totalWidth = this.getModuleRenderWidth(module);
            int color = this.getColor(l, offset).getRGB();
            float pad = 0.0F;
            float bgX1 = x / this.scale.getValue() - 1.0F - pad - (this.posX.getValue() == 0 ? 0.0F : totalWidth);
            float bgY1 = y / this.scale.getValue() - pad - (this.posY.getValue() == 0 ? (offset == 0L ? 1.0F : 0.0F) : (this.shadow.getValue() ? 1.0F : 0.0F));
            float bgX2 = x / this.scale.getValue() + 1.0F + pad + (this.posX.getValue() == 0 ? totalWidth : 0.0F);
            float bgY2 = y / this.scale.getValue() + height + pad + (this.posY.getValue() == 0 ? (this.shadow.getValue() ? 1.0F : 0.0F) : (offset == 0L ? 1.0F : 0.0F));
            RenderUtil.enableRenderState();
            if (this.background.getValue() > 0) {
                RenderUtil.drawRect(
                        bgX1, bgY1, bgX2, bgY2,
                        new Color(0.0F, 0.0F, 0.0F, this.background.getValue().floatValue() / 100.0F).getRGB()
                );
            }
            this.renderSidebar(module, (int) offset, bgX1, bgY1, bgX2, bgY2, color);
            RenderUtil.disableRenderState();
            GlStateManager.disableDepth();
            float textX = x / this.scale.getValue() - (this.posX.getValue() == 1 ? totalWidth : 0.0F);
            float textY = y / this.scale.getValue();
            if (isModernFont()) {
                textY -= 1.0F;
            }
            if (this.shadow.getValue()) {
                drawStringWithShadow(moduleName, textX, textY, color);
            } else {
                drawString(moduleName, textX, textY + (this.posY.getValue() == 1 ? 1.0F : 0.0F), color, false);
            }
            if (this.suffixes.getValue() && moduleSuffix.length > 0) {
                float width = getStringWidth(moduleName) + 3.0F;
                for (String string : moduleSuffix) {
                    float suffixX = textX + width;
                    if (this.shadow.getValue()) {
                        drawStringWithShadow(string, suffixX, textY, ChatColors.GRAY.toAwtColor());
                    } else {
                        drawString(string, suffixX, textY + (this.posY.getValue() == 1 ? 1.0F : 0.0F), ChatColors.GRAY.toAwtColor(), false);
                    }
                    width += getStringWidth(string) + (this.shadow.getValue() ? 3.0F : 2.0F);
                }
            }
            y += (height + (this.shadow.getValue() ? 1.0F : 0.0F)) * this.scale.getValue() * (this.posY.getValue() == 0 ? 1.0F : -1.0F);
            offset++;
        }
        if (this.blinkTimer.getValue()) {
            BlinkModules blinkingModule = Myau.blinkManager.getBlinkingModule();
            if (blinkingModule != BlinkModules.NONE && blinkingModule != BlinkModules.AUTO_BLOCK) {
                long movementPacketSize = Myau.blinkManager.countMovement();
                if (movementPacketSize > 0L) {
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    String blinkText = String.valueOf(movementPacketSize);
                    float blinkX = (float) new ScaledResolution(mc).getScaledWidth() / 2.0F / this.scale.getValue()
                            - (float) getStringWidth(blinkText) / 2.0F;
                    float blinkY = (float) new ScaledResolution(mc).getScaledHeight() / 5.0F * 3.0F / this.scale.getValue();
                    if (this.shadow.getValue()) {
                        drawStringWithShadow(blinkText, blinkX, blinkY,
                                this.getColor(System.currentTimeMillis(), offset).getRGB() & 16777215 | -1090519040);
                    } else {
                        drawString(blinkText, blinkX, blinkY,
                                this.getColor(System.currentTimeMillis(), offset).getRGB() & 16777215 | -1090519040, false);
                    }
                    GlStateManager.disableBlend();
                }
            }
        }
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    private void renderAnimated() {
        float scaleVal = this.scale.getValue();
        ScaledResolution sr = new ScaledResolution(mc);
        float scaledWidth = sr.getScaledWidth() / scaleVal;
        float scaledHeight = sr.getScaledHeight() / scaleVal;

        boolean alignLeft = this.posX.getValue() == 0;
        boolean alignTop = this.posY.getValue() == 0;

        float baseOffsetX = (1.0F + (this.hasSidebar() ? 1.0F : 0.0F));
        float baseOffsetY = 1.0F;

        float startX = alignLeft ? this.offsetX.getValue() + baseOffsetX : scaledWidth - this.offsetX.getValue() - baseOffsetX;
        float startY = alignTop ? this.offsetY.getValue() + baseOffsetY : scaledHeight - this.offsetY.getValue() - baseOffsetY;

        GlStateManager.pushMatrix();
        GlStateManager.scale(scaleVal, scaleVal, 1.0F);

        long l = System.currentTimeMillis();
        long offset = 0L;
        float deltaTime = 1.0F / Math.max(Minecraft.getDebugFPS(), 5);

        float currentY = startY;
        FontRenderer fr = getFontRenderer();

        float height = getFontHeight();
        if (!isModernFont()) {
            height -= 1.0F;
        }
        float fullHeight = height + 1.0F + (this.shadow.getValue() ? 1.0F : 0.0F);

        for (Module module : this.activeModules) {
            String moduleName = this.getModuleName(module);
            String[] moduleSuffix = this.getModuleSuffix(module);

            float textWidth = fr.getStringWidth(moduleName);
            float totalWidth = textWidth;
            if (this.suffixes.getValue() && moduleSuffix.length > 0) {
                for (String s : moduleSuffix) {
                    totalWidth += 3.0F + fr.getStringWidth(s);
                }
            }

            float targetSlide = module.isEnabled() ? totalWidth : 0.0F;
            float currentSlide = this.animationMap.getOrDefault(module, 0.0F);
            currentSlide = animateSmooth(targetSlide, currentSlide, 12.0F, deltaTime);
            currentSlide = Math.max(0.0F, Math.min(totalWidth, currentSlide));
            this.animationMap.put(module, currentSlide);

            float heightFactor = (totalWidth > 0.0F) ? (currentSlide / totalWidth) : 0.0F;
            float currentHeight = fullHeight * heightFactor;
            if (currentSlide <= 0.1F) {
                offset++;
                currentY += alignTop ? currentHeight : -currentHeight;
                continue;
            }

            float bgWidth = totalWidth + 2.0F;
            float totalModuleWidth = bgWidth + (this.hasSidebar() ? 1.0F : 0.0F);
            float xSlideAmount = (1.0F - heightFactor) * (totalModuleWidth + 5.0F);
            float currentX = startX + (alignLeft ? -xSlideAmount : xSlideAmount);

            float bgLeft, bgRight, textStartX;
            if (alignLeft) {
                bgLeft = currentX;
                bgRight = currentX + bgWidth;
                textStartX = bgLeft + 1.0F;
            } else {
                bgRight = currentX;
                bgLeft = currentX - bgWidth;
                textStartX = bgRight - 1.0F - totalWidth;
            }

            float fullTop = alignTop ? currentY : currentY - fullHeight;
            float fullBottom = fullTop + fullHeight;
            float drawTop, drawBottom;
            if (alignTop) {
                drawTop = fullTop;
                drawBottom = drawTop + currentHeight;
            } else {
                drawBottom = fullBottom;
                drawTop = drawBottom - currentHeight;
            }

            int color = this.getColor(l, offset).getRGB();
            int animatedColor = color;
            if (heightFactor < 1.0F) {
                int alpha = Math.max(0, Math.min(255, (int) (heightFactor * 255.0F)));
                animatedColor = (color & 0x00FFFFFF) | (alpha << 24);
            }

            RenderUtil.enableRenderState();

            if (this.background.getValue() > 0 && heightFactor > 0.02F) {
                int alpha = (int) (heightFactor * this.background.getValue().floatValue() / 100.0F * 255.0F);
                alpha = Math.min(255, Math.max(0, alpha));
                int bgColor = new Color(0, 0, 0, alpha).getRGB();
                RenderUtil.drawRect(bgLeft, drawTop, bgRight, drawBottom, bgColor);
            }

            this.renderSidebar(module, (int) offset, bgLeft, drawTop, bgRight, drawBottom, animatedColor);

            RenderUtil.disableRenderState();

            GlStateManager.disableDepth();
            if (heightFactor > 0.05F) {
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

                float textY = drawTop + 1.0F + (isModernFont() ? -1.0F : 0.0F) + (this.posY.getValue() == 1 ? 1.0F : 0.0F);
                float currentTextX = textStartX;

                if (this.shadow.getValue()) {
                    fr.drawStringWithShadow(moduleName, currentTextX, textY, animatedColor);
                } else {
                    fr.drawString(moduleName, currentTextX, textY, animatedColor, false);
                }
                currentTextX += textWidth;

                if (this.suffixes.getValue() && moduleSuffix.length > 0 && heightFactor > 0.5F) {
                    int suffixAlpha = Math.min(255, (int) (((heightFactor - 0.5F) / 0.5F) * 255.0F));
                    int suffixColor = (ChatColors.GRAY.toAwtColor() & 0x00FFFFFF) | (suffixAlpha << 24);
                    for (String suffix : moduleSuffix) {
                        currentTextX += 3.0F;
                        if (this.shadow.getValue()) {
                            fr.drawStringWithShadow(suffix, currentTextX, textY, suffixColor);
                        } else {
                            fr.drawString(suffix, currentTextX, textY, suffixColor, false);
                        }
                        currentTextX += fr.getStringWidth(suffix);
                    }
                }

                GlStateManager.disableBlend();
            }

            currentY += alignTop ? currentHeight : -currentHeight;
            offset++;
        }

        if (this.blinkTimer.getValue()) {
            BlinkModules blinkingModule = Myau.blinkManager.getBlinkingModule();
            if (blinkingModule != BlinkModules.NONE && blinkingModule != BlinkModules.AUTO_BLOCK) {
                long movementPacketSize = Myau.blinkManager.countMovement();
                if (movementPacketSize > 0L) {
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    String bText = String.valueOf(movementPacketSize);
                    int blinkColor = this.getColor(System.currentTimeMillis(), offset).getRGB() & 0x00FFFFFF | 0xBF000000;
                    float blinkX = scaledWidth / 2.0F - (float) fr.getStringWidth(bText) / 2.0F;
                    float blinkY = scaledHeight * 0.6F;
                    if (this.shadow.getValue()) {
                        fr.drawStringWithShadow(bText, blinkX, blinkY, blinkColor);
                    } else {
                        fr.drawString(bText, blinkX, blinkY, blinkColor, false);
                    }
                    GlStateManager.disableBlend();
                }
            }
        }

        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    @Override
    public void verifyValue(String mode) {
    }
}