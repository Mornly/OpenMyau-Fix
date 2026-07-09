package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.Render2DEvent;
import myau.events.TickEvent;
import myau.font.UFontRenderer;
import myau.mixin.IAccessorGuiChat;
import myau.module.Module;
import myau.util.ColorUtil;
import myau.util.RenderUtil;
import myau.property.properties.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class HUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final float ARRAYLIST_BASE_SCALE = 0.85F;
    private static final long COLOR_ROW_OFFSET_MS = 100L;
    private List<Module> activeModules = new ArrayList<>();
    private final Map<Module, ArrayListAnimation> arrayListAnimations = new LinkedHashMap<>();
    public final ModeProperty colorMode = new ModeProperty("color", 3, new String[]{"RAINBOW", "CHROMA", "ASTOLFO", "CUSTOM1", "CUSTOM12", "CUSTOM123"});
    public final FloatProperty colorSpeed = new FloatProperty("color-speed", 2.0F, 1.0F, 10.0F);
    public final PercentProperty colorSaturation = new PercentProperty("color-saturation", 50);
    public final PercentProperty colorBrightness = new PercentProperty("color-brightness", 100);
    public final ColorProperty custom1 = new ColorProperty("custom-color-1", Color.WHITE.getRGB(), () -> colorMode.getValue() == 3 || colorMode.getValue() == 4 || colorMode.getValue() == 5);
    public final ColorProperty custom2 = new ColorProperty("custom-color-2", Color.WHITE.getRGB(), () -> colorMode.getValue() == 4 || colorMode.getValue() == 5);
    public final ColorProperty custom3 = new ColorProperty("custom-color-3", Color.WHITE.getRGB(), () -> colorMode.getValue() == 5);
    public final ModeProperty posX = new ModeProperty("position-x", 0, new String[]{"LEFT", "RIGHT"});
    public final ModeProperty posY = new ModeProperty("position-y", 0, new String[]{"TOP", "BOTTOM"});
    public final IntProperty offsetX = new IntProperty("offset-x", 5, 0, 255);
    public final IntProperty offsetY = new IntProperty("offset-y", 70, 0, 255);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final PercentProperty backgroundAlpha = new PercentProperty("background-alpha", 43);
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);
    public final BooleanProperty suffixes = new BooleanProperty("suffixes", true);
    public final BooleanProperty lowerCase = new BooleanProperty("lower-case", false);
    public final BooleanProperty space = new BooleanProperty("space", false);
    public final BooleanProperty chatOutline = new BooleanProperty("chat-outline", true);
    public final BooleanProperty blinkTimer = new BooleanProperty("blink-timer", true);
    public final BooleanProperty toggleSound = new BooleanProperty("toggle-sounds", true);
    public final BooleanProperty toggleAlerts = new BooleanProperty("toggle-alerts", false);
    public final ModeProperty outline = new ModeProperty("Outline", 0, new String[]{"None", "Full", "Side"});
    public final BooleanProperty drawBackground = new BooleanProperty("Background", false);
    public final BooleanProperty alphabeticalSort = new BooleanProperty("Alphabetical sort", false);
    public final IntProperty moduleHeight = new IntProperty("module-height", 2, 0, 10);
    public final ModeProperty fontMode = new ModeProperty("Font", 0, new String[]{"Minecraft", "Tahoma"});

    private UFontRenderer tahomaFont;
    private boolean tahomaLoaded = false;
    private int posXVal = 5, posYVal = 70;

    private FontRenderer getFont() {
        if (fontMode.getValue() == 1) {
            if (!tahomaLoaded) {
                try { tahomaFont = new UFontRenderer("tahoma", 18); } catch (Exception e) { tahomaFont = null; }
                tahomaLoaded = true;
            }
            return tahomaFont != null ? tahomaFont : mc.fontRendererObj;
        }
        return mc.fontRendererObj;
    }

    public FontRenderer getArrayListFontRenderer() {
        return getFont();
    }

    public HUD() { super("HUD", true, true); }

    public Color getColor(long time) { return getColor(time, 0L); }

    public Color getColor(long time, long offset) {
        Color color = Color.white;
        switch (colorMode.getValue()) {
            case 0: color = getRainbowColor(time, offset); break;
            case 1: color = fadeBetween(new Color(custom1.getValue()), darker(new Color(custom1.getValue()), 0.3F), getPingPongProgress(time, getColorSpeedMillis() + offset)); break;
            case 2:
                color = getAstolfoColor(time, offset);
                break;
            case 3: color = new Color(custom1.getValue()); break;
            case 4:
                color = fadeBetween(new Color(custom1.getValue()), new Color(custom2.getValue()), getPingPongProgress(time, offset));
                break;
            case 5:
                color = cycleColors(time, offset, new Color(custom1.getValue()), new Color(custom2.getValue()), new Color(custom3.getValue()));
                break;
        }
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        return Color.getHSBColor(hsb[0], hsb[1] * (colorSaturation.getValue().floatValue() / 100.0F), hsb[2] * (colorBrightness.getValue().floatValue() / 100.0F));
    }

    public Color getCustomColor1() { return new Color(custom1.getValue()); }

    private long getColorSpeedMillis() {
        float seconds = Math.min(Math.max(colorSpeed.getValue(), 1.0F), 10.0F);
        return Math.max(100L, (long) (seconds * 1000.0F));
    }

    private float getCycleProgress(long time, long offset) {
        long speed = getColorSpeedMillis();
        long cycle = Math.floorMod(time + offset, speed);
        return (float) cycle / (float) speed;
    }

    private float getPingPongProgress(long time, long offset) {
        float progress = getCycleProgress(time, offset) * 2.0F;
        return progress > 1.0F ? 1.0F - progress % 1.0F : progress;
    }

    private Color getRainbowColor(long time, long offset) {
        double rainbowState = Math.ceil(time - offset / 5.0D) / 8.0D;
        rainbowState %= 360.0D;
        if (rainbowState < 0.0D) {
            rainbowState += 360.0D;
        }
        return ColorUtil.fromHSB((float) (rainbowState / 360.0D), 1.0F, 1.0F);
    }

    private Color getAstolfoColor(long time, long offset) {
        float progress = getCycleProgress(time, 0L);
        int delay = (int) (offset / COLOR_ROW_OFFSET_MS + progress * 100.0F);
        double rainbowDelay = Math.ceil(time + (long) delay * 107L) / 5.0D;
        rainbowDelay %= 360.0D;
        float hue = (float) (rainbowDelay / 360.0D);
        return Color.getHSBColor(hue < 0.5F ? -hue : hue, 1.0F, 1.0F);
    }

    private Color fadeBetween(Color startColor, Color endColor, float progress) {
        return ColorUtil.interpolate(progress, startColor, endColor);
    }

    private Color cycleColors(long time, long offset, Color first, Color second, Color third) {
        float progress = getCycleProgress(time, offset);
        if (progress < 1.0F / 3.0F) {
            return fadeBetween(first, second, progress * 3.0F);
        }
        if (progress < 2.0F / 3.0F) {
            return fadeBetween(second, third, (progress - 1.0F / 3.0F) * 3.0F);
        }
        return fadeBetween(third, first, (progress - 2.0F / 3.0F) * 3.0F);
    }

    private Color darker(Color color, float factor) {
        return new Color(
                Math.min(Math.max((int) (color.getRed() * factor), 0), 255),
                Math.min(Math.max((int) (color.getGreen() * factor), 0), 255),
                Math.min(Math.max((int) (color.getBlue() * factor), 0), 255)
        );
    }

    private static float animate(float current, float target, float speed) {
        return current + (target - current) * speed;
    }

    private float getArrayListScale() {
        return ARRAYLIST_BASE_SCALE * scale.getValue();
    }

    private float getArrayListTextHeight(FontRenderer font) {
        return font instanceof UFontRenderer ? ((UFontRenderer) font).getHeight() : font.FONT_HEIGHT;
    }

    private float getArrayListTextYOffset(FontRenderer font, float arrayScale) {
        return -1.0F * arrayScale;
    }

    private long getArrayListColorOffset(int index) {
        return (1L - index) * COLOR_ROW_OFFSET_MS;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.POST) {
            posXVal = offsetX.getValue();
            posYVal = offsetY.getValue();
            FontRenderer font = getFont();
            Comparator<Module> cmp;
            if (alphabeticalSort.getValue()) {
                cmp = Comparator.comparing(Module::getName);
            } else {
                cmp = Comparator.comparingInt((Module m) -> font.getStringWidth(getDisplayName(m))).reversed();
            }
            this.activeModules = Myau.moduleManager.modules.values().stream()
                    .filter(m -> m.isEnabled() && !m.isHidden())
                    .sorted(cmp)
                    .collect(Collectors.toList());
        }
    }

    private String getDisplayName(Module m) {
        return getModuleBaseName(m) + getSuffixText(m);
    }

    private String getModuleBaseName(Module m) {
        String name = m.getName();
        if (space.getValue()) {
            name = spaceName(name);
        }
        return lowerCase.getValue() ? name.toLowerCase(Locale.ROOT) : name;
    }

    private String spaceName(String name) {
        return name
                .replaceAll("(?<=[A-Z])(?=[A-Z][a-z])", " ")
                .replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ");
    }

    private String getSuffixText(Module m) {
        String suffixText = "";
        if (suffixes.getValue() && m.getSuffix().length > 0) {
            for (String s : m.getSuffix()) {
                String formatted = formatSuffix(s);
                if (!formatted.isEmpty()) {
                    suffixText += " " + (lowerCase.getValue() ? formatted.toLowerCase(Locale.ROOT) : formatted);
                }
            }
        }
        return suffixText;
    }

    private String formatSuffix(String suffix) {
        if (suffix == null) {
            return "";
        }
        String value = suffix.trim().replace('_', ' ').replace('-', ' ');
        if (value.isEmpty()) {
            return "";
        }
        boolean hasLetter = false;
        boolean hasLower = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
                if (Character.isLowerCase(c)) {
                    hasLower = true;
                }
            }
        }
        if (!hasLetter || hasLower) {
            return value;
        }

        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(lower.length());
        boolean upperNext = true;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isWhitespace(c)) {
                builder.append(c);
                upperNext = true;
            } else if (upperNext) {
                builder.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private List<ArrayListEntry> updateArrayListEntries(FontRenderer font) {
        ScaledResolution sr = new ScaledResolution(mc);
        List<Module> targets = activeModules.stream()
                .filter(module -> module != this)
                .collect(Collectors.toList());
        Set<Module> targetSet = new HashSet<>(targets);

        for (ArrayListAnimation animation : arrayListAnimations.values()) {
            animation.active = false;
            animation.target = 0.0F;
        }

        for (int i = 0; i < targets.size(); i++) {
            Module module = targets.get(i);
            ArrayListAnimation animation = arrayListAnimations.get(module);
            if (animation == null) {
                animation = new ArrayListAnimation();
                arrayListAnimations.put(module, animation);
            }
            animation.active = true;
            animation.target = 1.0F;
            animation.order = i;
        }

        List<Map.Entry<Module, ArrayListAnimation>> ordered = new ArrayList<>(arrayListAnimations.entrySet());
        ordered.sort((first, second) -> {
            int order = Integer.compare(first.getValue().order, second.getValue().order);
            if (order != 0) {
                return order;
            }
            return Boolean.compare(first.getValue().active, second.getValue().active);
        });

        List<ArrayListEntry> entries = new ArrayList<>();
        float y = posYVal;
        float arrayScale = getArrayListScale();
        float textHeight = getArrayListTextHeight(font) * arrayScale;
        float rowStep = textHeight + moduleHeight.getValue();
        int colorIndex = 0;

        for (Map.Entry<Module, ArrayListAnimation> mapEntry : ordered) {
            Module module = mapEntry.getKey();
            ArrayListAnimation animation = mapEntry.getValue();
            animation.progress = animate(animation.progress, animation.target, 0.11F);
            if (Math.abs(animation.progress - animation.target) < 0.01F) {
                animation.progress = animation.target;
            }

            if (!animation.active && animation.progress <= 0.01F) {
                continue;
            }

            String baseName = getModuleBaseName(module);
            String suffixText = getSuffixText(module);
            String moduleName = baseName + suffixText;
            float width = font.getStringWidth(moduleName) * arrayScale;
            float baseWidth = font.getStringWidth(baseName) * arrayScale;
            float targetX = posXVal;
            if (posX.getValue() == 1) {
                targetX = sr.getScaledWidth() - targetX - width;
            }

            float slide = (1.0F - animation.progress) * (width + 6.0F);
            float x = posX.getValue() == 1 ? targetX + slide : targetX - slide;
            float height = rowStep * animation.progress;
            float textY = y + (rowStep - textHeight) / 2.0F + getArrayListTextYOffset(font, arrayScale);
            entries.add(new ArrayListEntry(baseName, suffixText, width, baseWidth, x, y, textY, height, animation.progress, colorIndex));
            y += height;
            colorIndex++;
        }

        arrayListAnimations.entrySet().removeIf(entry -> !targetSet.contains(entry.getKey()) && entry.getValue().progress <= 0.01F);
        return entries;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (chatOutline.getValue() && mc.currentScreen instanceof GuiChat) {
            String text = ((IAccessorGuiChat) mc.currentScreen).getInputField().getText().trim();
            if (Myau.commandManager != null && Myau.commandManager.isTypingCommand(text)) {
                RenderUtil.enableRenderState();
                RenderUtil.drawOutlineRect(2.0F, (float)(mc.currentScreen.height - 14), (float)(mc.currentScreen.width - 2), (float)(mc.currentScreen.height - 2), 1.5F, 0, getColor(System.currentTimeMillis()).getRGB());
                RenderUtil.disableRenderState();
            }
        }

        FontRenderer font = getFont();
        List<ArrayListEntry> entries = updateArrayListEntries(font);
        drawArrayListBlur(entries);
        drawArrayListBloom(entries);

        OutlineBounds previousOutline = null;
        OutlineBounds lastOutline = null;
        float arrayScale = getArrayListScale();
        int index = 0;

        for (ArrayListEntry entry : entries) {
            if (entry.alpha <= 0.02F) {
                continue;
            }

            int color = ColorUtil.swapAlpha(getColor(System.currentTimeMillis(), getArrayListColorOffset(index)).getRGB(), 255.0F * entry.alpha);
            float xPos = entry.x;
            float yPos = entry.y;
            float rowBottom = yPos + Math.max(1.0F, entry.height);
            OutlineBounds outlineBounds = getArrayListOutlineBounds(entry, arrayScale);

            RenderUtil.enableRenderState();

            if (drawBackground.getValue()) {
                int alpha = (int) ((float) backgroundAlpha.getValue() / 100f * 255.0F * entry.alpha);
                RenderUtil.drawRect(outlineBounds.left, yPos, outlineBounds.right, rowBottom, new Color(0, 0, 0, alpha).getRGB());
            }

            drawArrayListOutline(previousOutline, outlineBounds, color);

            RenderUtil.disableRenderState();
            GlStateManager.disableDepth();
            myau.util.render.RenderUtil.scissorStart(xPos - 4.0F * arrayScale, yPos - 2.0F * arrayScale, entry.width + 8.0F * arrayScale, Math.max(1.0F, entry.height + 3.0F * arrayScale));
            drawScaledString(font, entry.baseName, xPos, entry.textY, color, shadow.getValue(), arrayScale);
            if (!entry.suffixText.isEmpty()) {
                drawScaledString(font, entry.suffixText, xPos + entry.baseWidth, entry.textY, ColorUtil.swapAlpha(Color.WHITE.getRGB(), 255.0F * entry.alpha), shadow.getValue(), arrayScale);
            }
            myau.util.render.RenderUtil.scissorEnd();
            GlStateManager.enableDepth();

            previousOutline = outlineBounds;
            lastOutline = outlineBounds;
            index++;
        }

        if (outline.getValue() == 1 && lastOutline != null) {
            RenderUtil.enableRenderState();
            int bottomColor = getColor(System.currentTimeMillis(), getArrayListColorOffset(index)).getRGB();
            drawArrayListHorizontal(lastOutline.left - getArrayListOutlineThickness(), lastOutline.right + getArrayListOutlineThickness(), lastOutline.bottom, bottomColor);
            RenderUtil.disableRenderState();
        }

        if (blinkTimer.getValue()) {
            BlinkModules blinkingModule = Myau.blinkManager.getBlinkingModule();
            if (blinkingModule != BlinkModules.NONE && blinkingModule != BlinkModules.AUTO_BLOCK) {
                long movementPacketSize = Myau.blinkManager.countMovement();
                if (movementPacketSize > 0L) {
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    mc.fontRendererObj.drawString(String.valueOf(movementPacketSize),
                            new ScaledResolution(mc).getScaledWidth() / 2f - mc.fontRendererObj.getStringWidth(String.valueOf(movementPacketSize)) / 2f,
                            new ScaledResolution(mc).getScaledHeight() / 5f * 3f,
                            getColor(System.currentTimeMillis(), getArrayListColorOffset(entries.size())).getRGB() & 16777215 | -1090519040, shadow.getValue());
                    GlStateManager.disableBlend();
                }
            }
        }
    }

    private float getArrayListOutlineThickness() {
        return 1.0F;
    }

    private OutlineBounds getArrayListOutlineBounds(ArrayListEntry entry, float arrayScale) {
        float left = entry.x - arrayScale;
        float right = entry.x + entry.width + 0.5F * arrayScale;
        float top = entry.y;
        float bottom = entry.y + Math.max(1.0F, entry.height);
        return new OutlineBounds(left, top, right, bottom);
    }

    private void drawArrayListOutline(OutlineBounds previous, OutlineBounds current, int color) {
        int mode = outline.getValue();
        if (mode == 0) {
            return;
        }

        float thickness = getArrayListOutlineThickness();
        if (mode == 2) {
            if (posX.getValue() == 1) {
                drawArrayListVertical(current.right, current.top, current.bottom, color);
            } else {
                drawArrayListVertical(current.left - thickness, current.top, current.bottom, color);
            }
            return;
        }

        if (previous == null) {
            drawArrayListHorizontal(current.left - thickness, current.right + thickness, current.top - thickness, color);
        } else {
            drawArrayListConnector(previous.left, current.left, current.top, true, color);
            drawArrayListConnector(previous.right, current.right, current.top, false, color);
        }

        drawArrayListVertical(current.left - thickness, current.top, current.bottom, color);
        drawArrayListVertical(current.right, current.top, current.bottom, color);
    }

    private void drawArrayListConnector(float previousEdge, float currentEdge, float y, boolean leftSide, int color) {
        if (Math.abs(previousEdge - currentEdge) < 0.5F) {
            return;
        }

        float thickness = getArrayListOutlineThickness();
        float left = Math.min(previousEdge, currentEdge);
        float right = Math.max(previousEdge, currentEdge);
        if (leftSide) {
            left -= thickness;
        } else {
            right += thickness;
        }
        drawArrayListCrispRect(left, y - thickness, right, y, color);
    }

    private void drawArrayListHorizontal(float left, float right, float y, int color) {
        float thickness = getArrayListOutlineThickness();
        drawArrayListCrispRect(left, y, right, y + thickness, color);
    }

    private void drawArrayListVertical(float x, float top, float bottom, int color) {
        float thickness = getArrayListOutlineThickness();
        drawArrayListCrispRect(x, top, x + thickness, bottom, color);
    }

    private void drawArrayListCrispRect(float left, float top, float right, float bottom, int color) {
        int rectLeft = Math.round(left);
        int rectTop = Math.round(top);
        int rectRight = Math.round(right);
        int rectBottom = Math.round(bottom);
        if (rectRight <= rectLeft) {
            rectRight = rectLeft + 1;
        }
        if (rectBottom <= rectTop) {
            rectBottom = rectTop + 1;
        }
        RenderUtil.drawRect(rectLeft, rectTop, rectRight, rectBottom, color);
    }

    private void drawArrayListBlur(List<ArrayListEntry> entries) {
        Bounds bounds = getArrayListBounds(entries);
        if (!bounds.valid) {
            return;
        }

        myau.util.render.PostProcessing.drawBlur(bounds.left, bounds.top, bounds.right, bounds.bottom, () -> () -> {
            GlStateManager.enableBlend();
            GlStateManager.disableTexture2D();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            RenderUtil.setup2DRendering(() -> drawArrayListMask(entries));
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
        });
    }

    private void drawArrayListMask(List<ArrayListEntry> entries) {
        float arrayScale = getArrayListScale();
        for (ArrayListEntry entry : entries) {
            if (entry.alpha <= 0.02F) {
                continue;
            }
            Gui.drawRect(Math.round(entry.x - arrayScale), Math.round(entry.y), Math.round(entry.x + entry.width + arrayScale), Math.round(entry.y + Math.max(1.0F, entry.height)), -1);
        }
    }

    private void drawArrayListBloom(List<ArrayListEntry> entries) {
        Framebuffer bloomBuffer = myau.util.render.PostProcessing.beginBloom();
        if (bloomBuffer == null) {
            return;
        }

        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.0F);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableDepth();
        float arrayScale = getArrayListScale();
        for (ArrayListEntry entry : entries) {
            if (entry.alpha <= 0.02F) {
                continue;
            }

            int color = ColorUtil.swapAlpha(getColor(System.currentTimeMillis(), getArrayListColorOffset(entry.colorIndex)).getRGB(), 255.0F * entry.alpha);
            Gui.drawRect(Math.round(entry.x - 2.0F * arrayScale), Math.round(entry.y - 2.0F * arrayScale), Math.round(entry.x + entry.width + 2.0F * arrayScale), Math.round(entry.y + Math.max(1.0F, entry.height)), color);
        }
        GlStateManager.enableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        myau.util.render.PostProcessing.endBloomOutside(bloomBuffer, () -> drawArrayListBackgroundStencil(entries));
    }

    private void drawArrayListBackgroundStencil(List<ArrayListEntry> entries) {
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        float arrayScale = getArrayListScale();
        for (ArrayListEntry entry : entries) {
            if (entry.alpha <= 0.02F) {
                continue;
            }

            Gui.drawRect(Math.round(entry.x - 2.0F * arrayScale), Math.round(entry.y - 2.0F * arrayScale), Math.round(entry.x + entry.width + 2.0F * arrayScale), Math.round(entry.y + Math.max(1.0F, entry.height)), 0xFFFFFFFF);
        }
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
    }

    private Bounds getArrayListBounds(List<ArrayListEntry> entries) {
        Bounds bounds = new Bounds();
        float arrayScale = getArrayListScale();
        for (ArrayListEntry entry : entries) {
            if (entry.alpha <= 0.02F) {
                continue;
            }

            bounds.include(Math.round(entry.x - arrayScale), Math.round(entry.y), Math.round(entry.x + entry.width + arrayScale), Math.round(entry.y + Math.max(1.0F, entry.height)));
        }
        return bounds;
    }

    private void drawScaledString(FontRenderer font, String text, float x, float y, int color, boolean shadow, float arrayScale) {
        GlStateManager.pushMatrix();
        GlStateManager.scale(arrayScale, arrayScale, 1.0F);
        font.drawString(text, x / arrayScale, y / arrayScale, color, shadow);
        GlStateManager.popMatrix();
    }

    private static final class ArrayListAnimation {
        private float progress;
        private float target;
        private int order = Integer.MAX_VALUE;
        private boolean active;
    }

    private static final class ArrayListEntry {
        private final String baseName;
        private final String suffixText;
        private final float width;
        private final float baseWidth;
        private final float x;
        private final float y;
        private final float textY;
        private final float height;
        private final float alpha;
        private final int colorIndex;

        private ArrayListEntry(String baseName, String suffixText, float width, float baseWidth, float x, float y, float textY, float height, float alpha, int colorIndex) {
            this.baseName = baseName;
            this.suffixText = suffixText;
            this.width = width;
            this.baseWidth = baseWidth;
            this.x = x;
            this.y = y;
            this.textY = textY;
            this.height = height;
            this.alpha = alpha;
            this.colorIndex = colorIndex;
        }
    }

    private static final class OutlineBounds {
        private final float left;
        private final float top;
        private final float right;
        private final float bottom;

        private OutlineBounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    private static final class Bounds {
        private int left = Integer.MAX_VALUE;
        private int top = Integer.MAX_VALUE;
        private int right = Integer.MIN_VALUE;
        private int bottom = Integer.MIN_VALUE;
        private boolean valid;

        private void include(int left, int top, int right, int bottom) {
            this.left = Math.min(this.left, left);
            this.top = Math.min(this.top, top);
            this.right = Math.max(this.right, right);
            this.bottom = Math.max(this.bottom, bottom);
            this.valid = true;
        }
    }
}
