package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.font.UFontRenderer;
import myau.module.Module;
import myau.property.Property;
import myau.property.properties.*;
import myau.util.ColorUtil;
import myau.util.KeyBindUtil;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public class TabGui extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final float START_X = 4.0F;
    private static final float START_Y = 15.0F;
    private static final float GAP = 4.0F;
    private static final int ITEM_HEIGHT = 12;
    private static final int CATEGORY_WIDTH = 80;
    private static final int MODULE_WIDTH = 82;
    private static final int SETTING_WIDTH = 82;
    private static final int SUB_WIDTH = 79;
    private static final float TEXT_PADDING = 3.0F;
    private static final int MAX_VISIBLE_ROWS = 20;
    private static final int MAX_TEXT_CHARS = 13;
    private static final Color BACKGROUND_COLOR = new Color(0x13, 0x13, 0x13);

    private final LinkedHashMap<String, List<Class<? extends Module>>> categories = new LinkedHashMap<>();
    private final ColumnAnim[] columns = new ColumnAnim[]{new ColumnAnim(), new ColumnAnim(), new ColumnAnim(), new ColumnAnim()};

    private int level;
    private int categoryIndex;
    private int moduleIndex;
    private int settingIndex;
    private int subIndex;
    private boolean editingSlider;

    private boolean upWasDown;
    private boolean downWasDown;
    private boolean leftWasDown;
    private boolean rightWasDown;
    private boolean enterWasDown;

    private UFontRenderer modernFont;

    public final ModeProperty fontMode = new ModeProperty("font-mode", 0, new String[]{"Minecraft", "Modern"});

    public TabGui() {
        super("TabGui", false, false);
        registerCategories();
    }

    private void registerCategories() {
        categories.put("Combat", Arrays.<Class<? extends Module>>asList(
                AimAssist.class,
                AutoClicker.class,
                KillAura.class,
                Wtap.class,
                Velocity.class,
                Reach.class,
                TargetStrafe.class,
                NoHitDelay.class,
                AntiFireball.class,
                LagRange.class,
                HitBox.class,
                MoreKB.class,
                HitSelect.class,
                BackTrack.class
        ));
        categories.put("Movement", Arrays.<Class<? extends Module>>asList(
                AntiAFK.class,
                Fly.class,
                Speed.class,
                LongJump.class,
                Sprint.class,
                Jesus.class,
                NoSlow.class,
                KeepSprint.class,
                Eagle.class,
                NoJumpDelay.class,
                AntiVoid.class,
                Stasis.class
        ));
        categories.put("Render", Arrays.<Class<? extends Module>>asList(
                Animations.class,
                ESP.class,
                Chams.class,
                FullBright.class,
                Tracers.class,
                NameTags.class,
                Xray.class,
                TargetHUD.class,
                Indicators.class,
                BedESP.class,
                ItemESP.class,
                ViewClip.class,
                NoHurtCam.class,
                HUD.class,
                TabGui.class,
                GuiModule.class,
                ChestESP.class,
                Trajectories.class,
                Notifications.class,
                WaterMark.class
        ));
        categories.put("Player", Arrays.<Class<? extends Module>>asList(
                AntiDebuff.class,
                Timer.class,
                NoRotate.class,
                Blink.class,
                NoFall.class,
                AutoHeal.class,
                AutoTool.class,
                AutoSwap.class,
                ChestAura.class,
                ChestStealer.class,
                FakeLag.class,
                InvManager.class,
                InvWalk.class,
                Scaffold.class,
                AutoBlockIn.class,
                SpeedMine.class,
                FastPlace.class,
                GhostHand.class,
                MCF.class
        ));
        categories.put("Misc", Arrays.<Class<? extends Module>>asList(
                Spammer.class,
                BedNuker.class,
                BedTracker.class,
                LightningTracker.class,
                NickHider.class,
                AntiObbyTrap.class,
                AntiObfuscate.class,
                AutoAnduril.class,
                InventoryClicker.class,
                ClientSpoofer.class,
                FlagDetector.class
        ));
    }

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

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled()) {
            return;
        }

        if (mc.currentScreen == null) {
            handleKeyboard();
        }
        clampSelections();

        FontRenderer font = getFont();
        float x = START_X;
        float y = START_Y;

        List<Row> categoryRows = getCategoryRows();
        List<Row> moduleRows = getModuleRows();
        List<Row> settingRows = getSettingRows();
        List<Row> subRows = getSubRows();

        int categoryStart = scrollStart(categoryIndex, categoryRows.size());
        int moduleStart = scrollStart(moduleIndex, moduleRows.size());
        int settingSelectedRow = getSettingsSelectedRow();
        int settingStart = scrollStart(settingSelectedRow, settingRows.size());

        float categoryColumnY = y;
        float moduleColumnY = categoryColumnY + Math.max(0, categoryIndex - categoryStart) * ITEM_HEIGHT;
        float settingColumnY = moduleColumnY + Math.max(0, moduleIndex - moduleStart) * ITEM_HEIGHT;
        float subColumnY = settingColumnY + Math.max(0, settingSelectedRow - settingStart) * ITEM_HEIGHT;

        drawColumn(font, 0, x, categoryColumnY, CATEGORY_WIDTH, categoryRows, categoryIndex, level == 0, true);
        x += CATEGORY_WIDTH + GAP;
        drawColumn(font, 1, x, moduleColumnY, MODULE_WIDTH, moduleRows, moduleIndex, level == 1, level >= 1);
        x += MODULE_WIDTH + GAP;
        drawColumn(font, 2, x, settingColumnY, SETTING_WIDTH, settingRows, settingSelectedRow, level == 2, level >= 2 && !settingRows.isEmpty());
        x += SETTING_WIDTH + GAP;
        drawColumn(font, 3, x, subColumnY, SUB_WIDTH, subRows, subIndex, level == 3, level >= 3 && !subRows.isEmpty());
    }

    private void handleKeyboard() {
        boolean upPressed = Keyboard.isKeyDown(Keyboard.KEY_UP);
        boolean downPressed = Keyboard.isKeyDown(Keyboard.KEY_DOWN);
        boolean leftPressed = Keyboard.isKeyDown(Keyboard.KEY_LEFT);
        boolean rightPressed = Keyboard.isKeyDown(Keyboard.KEY_RIGHT);
        boolean enterPressed = Keyboard.isKeyDown(Keyboard.KEY_RETURN) || Keyboard.isKeyDown(Keyboard.KEY_NUMPADENTER);

        if (editingSlider) {
            Property<?> property = getSelectedProperty();
            if (!isSliderProperty(property)) {
                editingSlider = false;
            } else {
                if (leftPressed && !leftWasDown) {
                    adjustProperty(property, -1);
                }
                if (rightPressed && !rightWasDown) {
                    adjustProperty(property, 1);
                }
                if (enterPressed && !enterWasDown) {
                    editingSlider = false;
                }

                upWasDown = upPressed;
                downWasDown = downPressed;
                leftWasDown = leftPressed;
                rightWasDown = rightPressed;
                enterWasDown = enterPressed;
                return;
            }
        }

        if (upPressed && !upWasDown) {
            moveSelection(-1);
        }
        if (downPressed && !downWasDown) {
            moveSelection(1);
        }
        if (leftPressed && !leftWasDown) {
            if (level > 0) {
                level--;
            }
        }
        if (rightPressed && !rightWasDown) {
            openOrAdjust(1);
        }
        if (enterPressed && !enterWasDown) {
            pressEnter();
        }

        upWasDown = upPressed;
        downWasDown = downPressed;
        leftWasDown = leftPressed;
        rightWasDown = rightPressed;
        enterWasDown = enterPressed;
    }

    private void moveSelection(int direction) {
        editingSlider = false;
        if (level == 0) {
            categoryIndex = wrap(categoryIndex + direction, categories.size());
            moduleIndex = 0;
            settingIndex = 0;
            syncSubIndex();
        } else if (level == 1) {
            moduleIndex = wrap(moduleIndex + direction, getModulesForSelectedCategory().size());
            settingIndex = 0;
            syncSubIndex();
        } else if (level == 2) {
            settingIndex = wrap(settingIndex + direction, getSettingsForSelectedModule().size());
            syncSubIndex();
        } else {
            ModeProperty mode = getSelectedModeProperty();
            subIndex = wrap(subIndex + direction, mode == null ? 0 : mode.getModes().length);
        }
    }

    private void openOrAdjust(int direction) {
        if (level == 0) {
            editingSlider = false;
            level = 1;
            return;
        }
        if (level == 1) {
            if (!getSettingsForSelectedModule().isEmpty()) {
                editingSlider = false;
                level = 2;
                settingIndex = 0;
                syncSubIndex();
            }
            return;
        }
        if (level == 2) {
            Property<?> property = getSelectedProperty();
            if (property instanceof ModeProperty) {
                editingSlider = false;
                level = 3;
                syncSubIndex();
            } else if (!isSliderProperty(property)) {
                adjustProperty(property, direction);
            }
        }
    }

    private void pressEnter() {
        if (level == 1) {
            Module module = getSelectedModule();
            if (module != null) {
                boolean old = module.isEnabled();
                module.toggle();
                if (old != module.isEnabled()) {
                    Notifications.postToggle(module, module.isEnabled());
                }
            }
            return;
        }
        if (level == 2) {
            Property<?> property = getSelectedProperty();
            if (property instanceof ModeProperty) {
                editingSlider = false;
                level = 3;
                syncSubIndex();
            } else if (isSliderProperty(property)) {
                editingSlider = !editingSlider;
            } else {
                adjustProperty(property, 1);
            }
            return;
        }
        if (level == 3) {
            ModeProperty mode = getSelectedModeProperty();
            if (mode != null) {
                mode.setValue(subIndex);
            }
        }
    }

    private boolean isSliderProperty(Property<?> property) {
        return property instanceof IntProperty || property instanceof PercentProperty || property instanceof FloatProperty;
    }

    private void adjustProperty(Property<?> property, int direction) {
        if (property == null || !property.isVisible()) {
            return;
        }
        if (property instanceof BooleanProperty) {
            property.setValue(!((BooleanProperty) property).getValue());
        } else if (property instanceof IntProperty) {
            IntProperty intProperty = (IntProperty) property;
            intProperty.setValue(clamp(intProperty.getValue() + direction, intProperty.getMinimum(), intProperty.getMaximum()));
        } else if (property instanceof PercentProperty) {
            PercentProperty percentProperty = (PercentProperty) property;
            percentProperty.setValue(clamp(percentProperty.getValue() + direction, percentProperty.getMinimum(), percentProperty.getMaximum()));
        } else if (property instanceof FloatProperty) {
            FloatProperty floatProperty = (FloatProperty) property;
            float next = clamp(floatProperty.getValue() + 0.1F * direction, floatProperty.getMinimum(), floatProperty.getMaximum());
            floatProperty.setValue(Math.round(next * 10.0F) / 10.0F);
        }
    }

    private List<Row> getCategoryRows() {
        List<Row> rows = new ArrayList<>();
        for (String category : categories.keySet()) {
            rows.add(new Row(category, "", false, new Color(0xAA, 0xAA, 0xAA).getRGB()));
        }
        return rows;
    }

    private List<Row> getModuleRows() {
        List<Row> rows = new ArrayList<>();
        for (Module module : getModulesForSelectedCategory()) {
            rows.add(new Row(module.getName(), "", false, module.isEnabled() ? Color.WHITE.getRGB() : new Color(165, 165, 165).getRGB()));
        }
        return rows;
    }

    private List<Row> getSettingRows() {
        List<Row> rows = new ArrayList<>();
        Module module = getSelectedModule();
        if (module == null) {
            return rows;
        }

        rows.add(new Row("Keybind", KeyBindUtil.getKeyName(module.getKey()), false, new Color(145, 145, 145).getRGB()));
        for (Property<?> property : getSettingsForSelectedModule()) {
            boolean hasSub = property instanceof ModeProperty;
            String name = formatLabel(property.getName()) + (hasSub ? "..." : "");
            rows.add(new Row(name, hasSub ? "" : formatValue(property), hasSub, propertyTextColor(property)));
        }
        return rows;
    }

    private List<Row> getSubRows() {
        List<Row> rows = new ArrayList<>();
        ModeProperty mode = getSelectedModeProperty();
        if (mode == null) {
            return rows;
        }

        String[] modes = mode.getModes();
        for (int i = 0; i < modes.length; i++) {
            boolean selected = i == mode.getValue();
            rows.add(new Row(formatLabel(modes[i]), selected ? "On" : "", false, selected ? Color.WHITE.getRGB() : new Color(170, 170, 170).getRGB()));
        }
        return rows;
    }

    private void drawColumn(FontRenderer font, int columnIndex, float targetX, float y, int width, List<Row> rows, int selected, boolean active, boolean visible) {
        ColumnAnim anim = columns[columnIndex];
        float hiddenX = targetX - 6.0F;
        anim.x = animate(anim.x == 0.0F ? (visible ? targetX : hiddenX) : anim.x, visible ? targetX : hiddenX, 0.24F);
        anim.alpha = animate(anim.alpha, visible ? 1.0F : 0.0F, 0.24F);
        if (anim.alpha < 0.015F) {
            anim.alpha = 0.0F;
        } else if (anim.alpha > 0.985F) {
            anim.alpha = 1.0F;
        }
        if (anim.alpha <= 0.02F) {
            return;
        }

        int count = Math.min(MAX_VISIBLE_ROWS, Math.max(1, rows.size()));
        int start = scrollStart(selected, rows.size());
        float height = count * ITEM_HEIGHT;
        float x = anim.x;
        int panelAlpha = Math.max(0, Math.min(255, (int) (255.0F * anim.alpha)));

        RenderUtil.enableRenderState();
        RenderUtil.drawRect(x, y, x + width, y + height, new Color(BACKGROUND_COLOR.getRed(), BACKGROUND_COLOR.getGreen(), BACKGROUND_COLOR.getBlue(), panelAlpha).getRGB());
        RenderUtil.disableRenderState();

        int visibleSelected = selected - start;
        if (visibleSelected >= 0 && visibleSelected < count && selected >= 0 && selected < rows.size()) {
            float targetSelectedY = y + visibleSelected * ITEM_HEIGHT;
            anim.selectedY = animate(anim.selectedY == 0.0F ? targetSelectedY : anim.selectedY, targetSelectedY, 0.34F);
            drawSelectedGradient(x, anim.selectedY, width, (int) (255.0F * anim.alpha));
        }

        GlStateManager.disableDepth();
        for (int i = 0; i < count; i++) {
            int rowIndex = start + i;
            if (rowIndex >= rows.size()) {
                break;
            }

            Row row = rows.get(rowIndex);
            boolean selectedRow = rowIndex == selected;
            int color = selectedRow ? Color.WHITE.getRGB() : row.color;
            color = swapAlpha(color, ((color >> 24) & 255) * anim.alpha);

            float rowY = y + i * ITEM_HEIGHT;
            float textY = getCenteredTextY(font, rowY);
            String value = row.value.isEmpty() ? "" : limitText(font, row.value, width / 2.0F - TEXT_PADDING * 2.0F);
            float valueWidth = font.getStringWidth(value);
            String name = limitText(font, row.name, width - TEXT_PADDING * 2.0F - valueWidth);

            drawText(font, name, x + TEXT_PADDING, textY, color);
            if (!value.isEmpty()) {
                int valueColor = selectedRow ? Color.WHITE.getRGB() : (row.hasSub ? new Color(205, 205, 205).getRGB() : Color.WHITE.getRGB());
                drawText(font, value, x + width - valueWidth - TEXT_PADDING, textY, swapAlpha(valueColor, 255.0F * anim.alpha));
            }
        }
        GlStateManager.enableDepth();
    }

    private void drawSelectedGradient(float x, float y, int width, int alpha) {
        HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
        Color left = hud != null ? new Color(hud.custom1.getValue()) : Color.WHITE;
        Color right = hud != null ? new Color(hud.custom2.getValue()) : Color.WHITE;
        RenderUtil.enableRenderState();
        for (int i = 0; i < width; i++) {
            float progress = i / (float) Math.max(1, width - 1);
            Color color = ColorUtil.interpolate(progress, left, right);
            RenderUtil.drawRect(x + i, y, x + i + 1.0F, y + ITEM_HEIGHT, new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha))).getRGB());
        }
        RenderUtil.disableRenderState();
    }

    private void drawText(FontRenderer font, String text, float x, float y, int color) {
        font.drawString(text, Math.round(x), Math.round(y), color, false);
    }

    private float getCenteredTextY(FontRenderer font, float rowY) {
        return rowY + (ITEM_HEIGHT - getFontHeight(font)) / 2.0F;
    }

    private int getFontHeight(FontRenderer font) {
        return font instanceof UFontRenderer ? ((UFontRenderer) font).getHeight() : font.FONT_HEIGHT;
    }

    private List<Module> getModulesForSelectedCategory() {
        List<String> keys = new ArrayList<>(categories.keySet());
        if (keys.isEmpty()) {
            return new ArrayList<>();
        }

        categoryIndex = clamp(categoryIndex, 0, keys.size() - 1);
        List<Class<? extends Module>> classes = categories.get(keys.get(categoryIndex));
        List<Module> modules = new ArrayList<>();
        if (classes == null) {
            return modules;
        }
        for (Class<? extends Module> moduleClass : classes) {
            Module module = Myau.moduleManager.getModule(moduleClass);
            if (module != null) {
                modules.add(module);
            }
        }
        return modules;
    }

    private Module getSelectedModule() {
        List<Module> modules = getModulesForSelectedCategory();
        if (modules.isEmpty()) {
            return null;
        }
        moduleIndex = clamp(moduleIndex, 0, modules.size() - 1);
        return modules.get(moduleIndex);
    }

    private List<Property<?>> getSettingsForSelectedModule() {
        Module module = getSelectedModule();
        List<Property<?>> settings = new ArrayList<>();
        if (module == null || Myau.propertyManager == null || Myau.propertyManager.properties.get(module.getClass()) == null) {
            return settings;
        }
        for (Property<?> property : Myau.propertyManager.properties.get(module.getClass())) {
            if (property.isVisible()) {
                settings.add(property);
            }
        }
        return settings;
    }

    private Property<?> getSelectedProperty() {
        List<Property<?>> settings = getSettingsForSelectedModule();
        if (settings.isEmpty()) {
            return null;
        }
        settingIndex = clamp(settingIndex, 0, settings.size() - 1);
        return settings.get(settingIndex);
    }

    private ModeProperty getSelectedModeProperty() {
        Property<?> property = getSelectedProperty();
        return property instanceof ModeProperty ? (ModeProperty) property : null;
    }

    private int getSettingsSelectedRow() {
        return getSettingsForSelectedModule().isEmpty() ? 0 : settingIndex + 1;
    }

    private void syncSubIndex() {
        ModeProperty mode = getSelectedModeProperty();
        subIndex = mode == null ? 0 : mode.getValue();
    }

    private void clampSelections() {
        categoryIndex = clamp(categoryIndex, 0, Math.max(0, categories.size() - 1));
        moduleIndex = clamp(moduleIndex, 0, Math.max(0, getModulesForSelectedCategory().size() - 1));
        settingIndex = clamp(settingIndex, 0, Math.max(0, getSettingsForSelectedModule().size() - 1));
        ModeProperty mode = getSelectedModeProperty();
        subIndex = clamp(subIndex, 0, mode == null ? 0 : Math.max(0, mode.getModes().length - 1));
        if (level > 1 && getSettingsForSelectedModule().isEmpty()) {
            level = 1;
        }
        if (level > 2 && mode == null) {
            level = 2;
        }
    }

    private int propertyTextColor(Property<?> property) {
        if (property instanceof BooleanProperty) {
            return ((BooleanProperty) property).getValue() ? Color.WHITE.getRGB() : new Color(150, 150, 150).getRGB();
        }
        return new Color(205, 205, 205).getRGB();
    }

    private String formatValue(Property<?> property) {
        if (property instanceof BooleanProperty) {
            return ((BooleanProperty) property).getValue() ? "On" : "Off";
        }
        if (property instanceof PercentProperty) {
            return ((PercentProperty) property).getValue() + "%";
        }
        if (property instanceof IntProperty) {
            return String.valueOf(((IntProperty) property).getValue());
        }
        if (property instanceof FloatProperty) {
            return formatFloat(((FloatProperty) property).getValue());
        }
        if (property instanceof ColorProperty) {
            return String.format("#%06X", ((ColorProperty) property).getValue() & 0xFFFFFF);
        }
        if (property instanceof TextProperty) {
            return String.valueOf(((TextProperty) property).getValue());
        }
        return String.valueOf(property.getValue());
    }

    private String formatFloat(float value) {
        if (Math.abs(value - Math.round(value)) < 0.01F) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private String formatLabel(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "?";
        }
        String normalized = value.replace('_', ' ').replace('-', ' ').trim();
        if (normalized.equals(normalized.toUpperCase(Locale.ROOT))) {
            normalized = normalized.toLowerCase(Locale.ROOT);
        }
        StringBuilder builder = new StringBuilder();
        for (String part : normalized.split(" ")) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private String limitText(FontRenderer font, String text, float maxWidth) {
        if (text == null) {
            return "";
        }
        String suffix = "...";
        String source = text;
        boolean overLimit = source.length() > MAX_TEXT_CHARS;
        int kept = Math.min(source.length(), MAX_TEXT_CHARS);
        String limited = overLimit ? source.substring(0, kept) + suffix : source;
        if (font.getStringWidth(limited) <= maxWidth) {
            return limited;
        }

        for (int i = kept; i >= 0; i--) {
            String candidate = source.substring(0, i) + suffix;
            if (font.getStringWidth(candidate) <= maxWidth) {
                return candidate;
            }
        }
        return "";
    }

    private int scrollStart(int selected, int size) {
        if (size <= MAX_VISIBLE_ROWS) {
            return 0;
        }
        return clamp(selected - MAX_VISIBLE_ROWS / 2, 0, size - MAX_VISIBLE_ROWS);
    }

    private int wrap(int value, int size) {
        if (size <= 0) {
            return 0;
        }
        int result = value % size;
        return result < 0 ? result + size : result;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float animate(float current, float target, float speed) {
        return current + (target - current) * speed;
    }

    private static int swapAlpha(int color, float alpha) {
        int a = Math.round(alpha);
        a = Math.min(255, Math.max(0, a));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static final class ColumnAnim {
        private float x;
        private float alpha;
        private float selectedY;
    }

    private static final class Row {
        private final String name;
        private final String value;
        private final boolean hasSub;
        private final int color;

        private Row(String name, String value, boolean hasSub, int color) {
            this.name = name;
            this.value = value == null ? "" : value;
            this.hasSub = hasSub;
            this.color = color;
        }
    }
}