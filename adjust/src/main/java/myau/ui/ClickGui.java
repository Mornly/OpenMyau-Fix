package myau.ui;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import myau.Myau;
import myau.font.CFontRenderer;
import myau.module.Module;
import myau.module.modules.*;
import myau.ui.components.BindComponent;
import myau.ui.components.CategoryComponent;
import myau.ui.components.ConfigurationComponent;
import myau.util.RenderUtil;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClickGui extends GuiScreen {
    private static final int STYLE_VERSION = 3;
    private static final Color BAR_LEFT = new Color(116, 68, 255);
    private static final Color BAR_RIGHT = new Color(195, 0, 76);

    private static ClickGui instance;

    private final File configFile = new File("./config/Myau/", "clickgui.txt");
    private final ArrayList<CategoryComponent> categoryList;
    private final ConfigurationComponent configurationComponent;

    private CFontRenderer cFontRenderer;
    private FontRenderer currentRenderer;
    private String searchText = "";
    private boolean searchFocused;

    public ClickGui() {
        instance = this;

        List<Module> movementModules = new ArrayList<>();
        addModule(movementModules, AntiAFK.class);
        addModule(movementModules, Fly.class);
        addModule(movementModules, Speed.class);
        addModule(movementModules, LongJump.class);
        addModule(movementModules, Sprint.class);
        addModule(movementModules, Jesus.class);
        addModule(movementModules, NoSlow.class);
        addModule(movementModules, KeepSprint.class);
        addModule(movementModules, Eagle.class);
        addModule(movementModules, NoJumpDelay.class);
        addModule(movementModules, AntiVoid.class);
        addModule(movementModules, Stasis.class);

        List<Module> playerModules = new ArrayList<>();
        addModule(playerModules, AntiDebuff.class);
        addModule(playerModules, myau.module.modules.Timer.class);
        addModule(playerModules, NoRotate.class);
        addModule(playerModules, Blink.class);
        addModule(playerModules, NoFall.class);
        addModule(playerModules, AutoHeal.class);
        addModule(playerModules, AutoTool.class);
        addModule(playerModules, AutoSwap.class);
        addModule(playerModules, ChestAura.class);
        addModule(playerModules, ChestStealer.class);
        addModule(playerModules, FakeLag.class);
        addModule(playerModules, InvManager.class);
        addModule(playerModules, InvWalk.class);
        addModule(playerModules, Scaffold.class);
        addModule(playerModules, AutoBlockIn.class);
        addModule(playerModules, SpeedMine.class);
        addModule(playerModules, FastPlace.class);
        addModule(playerModules, GhostHand.class);
        addModule(playerModules, MCF.class);

        List<Module> exploitModules = new ArrayList<>();
        addModule(exploitModules, Spammer.class);
        addModule(exploitModules, BedNuker.class);
        addModule(exploitModules, BedTracker.class);
        addModule(exploitModules, LightningTracker.class);
        addModule(exploitModules, NickHider.class);
        addModule(exploitModules, AntiObbyTrap.class);
        addModule(exploitModules, AntiObfuscate.class);
        addModule(exploitModules, AutoAnduril.class);
        addModule(exploitModules, InventoryClicker.class);
        addModule(exploitModules, ClientSpoofer.class);
        addModule(exploitModules, FlagDetector.class);

        List<Module> renderModules = new ArrayList<>();
        addModule(renderModules, Animations.class);
        addModule(renderModules, ESP.class);
        addModule(renderModules, Chams.class);
        addModule(renderModules, FullBright.class);
        addModule(renderModules, Tracers.class);
        addModule(renderModules, NameTags.class);
        addModule(renderModules, Xray.class);
        addModule(renderModules, TargetHUD.class);
        addModule(renderModules, Indicators.class);
        addModule(renderModules, BedESP.class);
        addModule(renderModules, ItemESP.class);
        addModule(renderModules, ViewClip.class);
        addModule(renderModules, NoHurtCam.class);
        addModule(renderModules, HUD.class);
        addModule(renderModules, PostProcessing.class);
        addModule(renderModules, TabGui.class);
        addModule(renderModules, Session.class);
        addModule(renderModules, GuiModule.class);
        addModule(renderModules, ChestESP.class);
        addModule(renderModules, Trajectories.class);
        addModule(renderModules, Notifications.class);
        addModule(renderModules, PotionEffects.class);
        addModule(renderModules, WaterMark.class);

        List<Module> combatModules = new ArrayList<>();
        addModule(combatModules, AimAssist.class);
        addModule(combatModules, AutoClicker.class);
        addModule(combatModules, KillAura.class);
        addModule(combatModules, Wtap.class);
        addModule(combatModules, Velocity.class);
        addModule(combatModules, Reach.class);
        addModule(combatModules, TargetStrafe.class);
        addModule(combatModules, NoHitDelay.class);
        addModule(combatModules, AntiFireball.class);
        addModule(combatModules, LagRange.class);
        addModule(combatModules, HitBox.class);
        addModule(combatModules, MoreKB.class);
        addModule(combatModules, HitSelect.class);
        addModule(combatModules, BackTrack.class);

        Comparator<Module> comparator = Comparator.comparing(m -> m.getName().toLowerCase());
        movementModules.sort(comparator);
        playerModules.sort(comparator);
        exploitModules.sort(comparator);
        renderModules.sort(comparator);
        combatModules.sort(comparator);

        Set<Module> registered = new HashSet<>();
        registered.addAll(movementModules);
        registered.addAll(playerModules);
        registered.addAll(exploitModules);
        registered.addAll(renderModules);
        registered.addAll(combatModules);

        for (Module module : Myau.moduleManager.modules.values()) {
            if (!registered.contains(module)) {
                throw new RuntimeException(module.getClass().getName() + " is unregistered to click gui.");
            }
        }

        this.categoryList = new ArrayList<>();
        addCategory("Movement", movementModules, 198, 54);
        addCategory("Player", playerModules, 318, 36);
        addCategory("Exploits", exploitModules, 450, 51);
        addCategory("Render", renderModules, 550, 21);
        addCategory("Combat", combatModules, 655, 21);

        this.configurationComponent = new ConfigurationComponent(776, 56);

        loadPositions();
        for (CategoryComponent cat : categoryList) {
            cat.updateAllOffsets();
        }
        configurationComponent.updateAllOffsets();
    }

    private void addModule(List<Module> modules, Class<? extends Module> moduleClass) {
        Module module = Myau.moduleManager.getModule(moduleClass);
        if (module != null) {
            modules.add(module);
        }
    }

    private void addCategory(String name, List<Module> modules, int x, int y) {
        CategoryComponent category = new CategoryComponent(name, modules);
        category.setX(x);
        category.setY(y);
        category.setOpened(true);
        categoryList.add(category);
    }

    public static ClickGui getInstance() {
        return instance;
    }

    public FontRenderer getCurrentRenderer() {
        return currentRenderer == null ? mc.fontRendererObj : currentRenderer;
    }

    private FontRenderer getFontRenderer() {
        if (cFontRenderer == null) {
            try {
                cFontRenderer = new CFontRenderer("tahoma", 14);
            } catch (Exception e) {
                cFontRenderer = null;
            }
        }
        return cFontRenderer != null ? cFontRenderer : mc.fontRendererObj;
    }

    @Override
    public void drawScreen(int x, int y, float p) {
        Gui.drawRect(0, 0, this.width, this.height, new Color(0, 0, 0, 90).getRGB());
        drawHudGradient(0, 0, this.width, 2);

        currentRenderer = getFontRenderer();

        for (CategoryComponent category : categoryList) {
            category.setSearchQuery(searchText);
            category.render(currentRenderer);
            category.handleDrag(x, y);

            for (Component module : category.getModules()) {
                if (!category.isComponentVisible(module)) {
                    continue;
                }
                module.update(x, y);
            }
        }

        configurationComponent.setSearchQuery(searchText);
        configurationComponent.render(currentRenderer);
        configurationComponent.handleDrag(x, y);
        drawSearchBar();

        drawText(currentRenderer, "Myau " + Myau.version, 4, this.height - 26, 0xFFFFFFFF);
        drawText(currentRenderer, "dev, Mornly", 4, this.height - 14, new Color(200, 200, 200).getRGB());

        int wheel = Mouse.getDWheel();
        if (wheel != 0) {
            int scrollDir = wheel > 0 ? 1 : -1;
            for (CategoryComponent category : categoryList) {
                category.onScroll(x, y, scrollDir);
            }
            configurationComponent.onScroll(x, y, scrollDir);
        }
    }

    @Override
    public void mouseClicked(int x, int y, int mouseButton) {
        for (CategoryComponent category : categoryList) {
            category.updateAllOffsets();
        }
        configurationComponent.updateAllOffsets();

        if (configurationComponent.mouseDown(x, y, mouseButton)) {
            return;
        }

        for (CategoryComponent category : categoryList) {
            if (handleCategoryHeader(category, x, y, mouseButton)) {
                return;
            }

            if (!category.isOpened() || category.getModules().isEmpty()) {
                continue;
            }

            for (Component c : category.getModules()) {
                if (!category.isComponentVisible(c)) {
                    continue;
                }
                c.mouseDown(x, y, mouseButton);
            }
        }
    }

    private boolean handleCategoryHeader(CategoryComponent category, int x, int y, int mouseButton) {
        if (mouseButton != 0 || !category.insideArea(x, y)) {
            return false;
        }

        if (category.mousePressed(x, y)) {
            category.setOpened(!category.isOpened());
            return true;
        }

        if (category.isHovered(x, y)) {
            category.setPin(!category.isPin());
            return true;
        }

        category.mousePressed(true);
        category.xx = x - category.getX();
        category.yy = y - category.getY();
        return true;
    }

    @Override
    public void mouseReleased(int x, int y, int mouseButton) {
        for (CategoryComponent category : categoryList) {
            category.updateAllOffsets();
            if (mouseButton == 0) {
                category.mousePressed(false);
            }
        }
        configurationComponent.mouseReleased(x, y, mouseButton);

        for (CategoryComponent category : categoryList) {
            if (!category.isOpened() || category.getModules().isEmpty()) {
                continue;
            }

            for (Component component : category.getModules()) {
                if (!category.isComponentVisible(component)) {
                    continue;
                }
                component.mouseReleased(x, y, mouseButton);
            }
        }
    }

    @Override
    public void keyTyped(char typedChar, int key) {
        boolean hasBinding = false;
        for (CategoryComponent cat : categoryList) {
            for (Component comp : cat.getModules()) {
                if (comp instanceof BindComponent && ((BindComponent) comp).isBinding) {
                    hasBinding = true;
                    break;
                }
            }
            if (hasBinding) {
                break;
            }
        }

        if (hasBinding) {
            for (CategoryComponent cat : categoryList) {
                for (Component comp : cat.getModules()) {
                    comp.keyTyped(typedChar, key);
                }
            }
            return;
        }

        if (key == 1) {
            if (searchFocused || !searchText.isEmpty()) {
                searchText = "";
                searchFocused = false;
                return;
            }
            this.mc.displayGuiScreen(null);
            return;
        }

        if (key == Keyboard.KEY_RETURN) {
            searchFocused = !searchFocused;
            return;
        }

        if (searchFocused) {
            if (key == Keyboard.KEY_BACK) {
                if (!searchText.isEmpty()) {
                    searchText = searchText.substring(0, searchText.length() - 1);
                }
                return;
            }
            if (ChatAllowedCharacters.isAllowedCharacter(typedChar)) {
                searchText += typedChar;
            }
            return;
        }

        for (CategoryComponent cat : categoryList) {
            if (!cat.isOpened()) {
                continue;
            }
            for (Component comp : cat.getModules()) {
                if (!cat.isComponentVisible(comp)) {
                    continue;
                }
                comp.keyTyped(typedChar, key);
            }
        }
    }

    @Override
    public void onGuiClosed() {
        savePositions();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    public static int getHudColor(long offset) {
        HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
        if (hud != null) {
            return hud.getColor(System.currentTimeMillis(), offset).getRGB();
        }
        float hue = ((System.currentTimeMillis() % 4500L) / 4500.0F + offset / 2400.0F) % 1.0F;
        return Color.HSBtoRGB(hue, 0.75F, 1.0F);
    }

    public static int withAlpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    public static float animate(float current, float target, float speed) {
        return current + (target - current) * speed;
    }

    public static int alpha(int alpha) {
        return Math.max(0, Math.min(255, alpha));
    }

    public static int alphaColor(int red, int green, int blue, int alpha) {
        return new Color(red, green, blue, alpha(alpha)).getRGB();
    }

    public static int blendColor(Color from, Color to, float progress) {
        float p = Math.max(0.0F, Math.min(1.0F, progress));
        int red = (int) (from.getRed() + (to.getRed() - from.getRed()) * p);
        int green = (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * p);
        int blue = (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * p);
        int alpha = (int) (from.getAlpha() + (to.getAlpha() - from.getAlpha()) * p);
        return new Color(red, green, blue, alpha).getRGB();
    }

    public static void drawHudGradient(int left, int top, int right, int bottom) {
        if (right <= left || bottom <= top) {
            return;
        }

        HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
        Color leftColor = hud != null ? hud.getCustomColor1() : BAR_LEFT;
        Color rightColor = hud != null ? new Color(hud.custom2.getValue()) : BAR_RIGHT;

        for (int px = left; px < right; px++) {
            float progress = (px - left) / (float) Math.max(1, right - left - 1);
            Gui.drawRect(px, top, px + 1, bottom, blendColor(leftColor, rightColor, progress));
        }
    }

    public static boolean isClickGuiBlurEnabled() {
        GuiModule guiModule = (GuiModule) Myau.moduleManager.getModule(GuiModule.class);
        return guiModule != null && guiModule.blur.getValue();
    }

    public static void drawClickGuiBlur(float left, float top, float right, float bottom) {
        if (!isClickGuiBlurEnabled() || right <= left || bottom <= top) {
            return;
        }
        myau.util.render.PostProcessing.drawBlur(left, top, right, bottom, () -> () -> {
            GlStateManager.enableBlend();
            GlStateManager.disableTexture2D();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            RenderUtil.setup2DRendering(() -> Gui.drawRect((int) left, (int) top, (int) right, (int) bottom, -1));
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
        });
    }

    public static void drawText(FontRenderer renderer, String text, float x, float y, int color) {
        renderer.drawString(text, x, y, color, false);
    }

    public String getSearchText() {
        return searchText;
    }

    private void drawSearchBar() {
        int boxWidth = 230;
        int boxHeight = 14;
        int x = this.width / 2 - boxWidth / 2;
        int y = 7;
        Gui.drawRect(x, y, x + boxWidth, y + boxHeight, new Color(0, 0, 0, 92).getRGB());
        drawHudGradient(x, y, x + boxWidth, y + 2);
        String text = searchText.isEmpty() ? (searchFocused ? "" : "Press ENTER to search...") : searchText;
        int color = searchText.isEmpty() ? new Color(170, 170, 170).getRGB() : new Color(225, 225, 225).getRGB();
        drawText(currentRenderer, text, x + 16, y + 2, color);
        drawText(currentRenderer, searchFocused ? "_" : "", x + 17 + currentRenderer.getStringWidth(text), y + 2, color);
    }

    public static String trimToWidth(FontRenderer renderer, String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (renderer.getStringWidth(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int suffixWidth = renderer.getStringWidth(suffix);
        if (maxWidth <= suffixWidth) {
            return suffix;
        }
        return renderer.trimStringToWidth(text, maxWidth - suffixWidth) + suffix;
    }

    public static String trimToChars(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        int limit = Math.max(0, maxChars);
        if (text.length() <= limit) {
            return text;
        }
        if (limit <= 3) {
            return text.substring(0, limit);
        }
        return text.substring(0, limit - 3) + "...";
    }

    private void savePositions() {
        JsonObject json = new JsonObject();
        json.addProperty("style", STYLE_VERSION);
        for (CategoryComponent cat : categoryList) {
            savePanel(json, cat.getName(), cat.getX(), cat.getY(), cat.isOpened());
        }
        savePanel(json, configurationComponent.getName(), configurationComponent.getX(), configurationComponent.getY(), configurationComponent.isOpened());

        try {
            File parent = configFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(configFile)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void savePanel(JsonObject json, String name, int x, int y, boolean opened) {
        JsonObject pos = new JsonObject();
        pos.addProperty("x", x);
        pos.addProperty("y", y);
        pos.addProperty("open", opened);
        json.add(name, pos);
    }

    private void loadPositions() {
        if (!configFile.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            if (!json.has("style") || json.get("style").getAsInt() != STYLE_VERSION) {
                return;
            }

            for (CategoryComponent cat : categoryList) {
                loadPanel(json, cat.getName(), cat);
            }

            if (json.has(configurationComponent.getName())) {
                JsonObject pos = json.getAsJsonObject(configurationComponent.getName());
                configurationComponent.setX(pos.get("x").getAsInt());
                configurationComponent.setY(pos.get("y").getAsInt());
                configurationComponent.setOpened(pos.get("open").getAsBoolean());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadPanel(JsonObject json, String name, CategoryComponent cat) {
        if (!json.has(name)) {
            return;
        }

        JsonObject pos = json.getAsJsonObject(name);
        cat.setX(pos.get("x").getAsInt());
        cat.setY(pos.get("y").getAsInt());
        cat.setOpened(pos.get("open").getAsBoolean());
    }
}
