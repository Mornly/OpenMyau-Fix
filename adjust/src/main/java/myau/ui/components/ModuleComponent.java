package myau.ui.components;

import myau.Myau;
import myau.module.Module;
import myau.property.Property;
import myau.property.properties.*;
import myau.ui.ClickGui;
import myau.ui.Component;
import myau.ui.dataset.impl.FloatSlider;
import myau.ui.dataset.impl.IntSlider;
import myau.ui.dataset.impl.PercentageSlider;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.BasicStroke;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

public class ModuleComponent implements Component {
    private static ResourceLocation eyeIcon;
    private static ResourceLocation eyeOffIcon;

    public Module mod;
    public CategoryComponent category;
    public int offsetY;
    public boolean panelExpand;

    private final ArrayList<Component> settings;
    private final ArrayList<String> searchTerms;
    private boolean hovered;
    private float hoverAlpha;
    private float enabledAlpha;
    private float hiddenAlpha;
    private float openProgress;

    public ModuleComponent(Module mod, CategoryComponent category, int offsetY) {
        this.mod = mod;
        this.category = category;
        this.offsetY = offsetY;
        this.settings = new ArrayList<>();
        this.searchTerms = new ArrayList<>();
        this.panelExpand = false;
        this.searchTerms.add(mod.getName().toLowerCase());

        int y = offsetY + CategoryComponent.MODULE_HEIGHT;
        if (!Myau.propertyManager.properties.get(mod.getClass()).isEmpty()) {
            for (Property<?> baseProperty : Myau.propertyManager.properties.get(mod.getClass())) {
                this.searchTerms.add(baseProperty.getName().replace("-", " ").toLowerCase());
                if (baseProperty instanceof ModeProperty) {
                    for (String mode : ((ModeProperty) baseProperty).getModes()) {
                        this.searchTerms.add(mode.replace("_", " ").toLowerCase());
                    }
                }
                if (baseProperty instanceof BooleanProperty) {
                    CheckBoxComponent c = new CheckBoxComponent((BooleanProperty) baseProperty, this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof FloatProperty) {
                    SliderComponent c = new SliderComponent(new FloatSlider((FloatProperty) baseProperty), this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof IntProperty) {
                    SliderComponent c = new SliderComponent(new IntSlider((IntProperty) baseProperty), this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof PercentProperty) {
                    SliderComponent c = new SliderComponent(new PercentageSlider((PercentProperty) baseProperty), this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof ModeProperty) {
                    ModeComponent c = new ModeComponent((ModeProperty) baseProperty, this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof ColorProperty) {
                    ColorSliderComponent c = new ColorSliderComponent((ColorProperty) baseProperty, this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof TextProperty) {
                    TextComponent c = new TextComponent((TextProperty) baseProperty, this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                }
            }
        }

    }

    public void setComponentStartAt(int newOffsetY) {
        this.offsetY = newOffsetY;
        int y = this.offsetY + CategoryComponent.MODULE_HEIGHT;

        for (Component c : this.settings) {
            c.setComponentStartAt(y);
            if (c.isVisible()) {
                y += c.getHeight();
            }
        }
    }

    public void draw(AtomicInteger offset) {
        if (!hasVisibleSettings()) {
            this.panelExpand = false;
        }

        int baseX = this.category.getX();
        int baseY = this.category.getY() + this.offsetY;
        int width = this.category.getWidth();
        FontRenderer fr = ClickGui.getInstance().getCurrentRenderer();

        hoverAlpha = ClickGui.animate(hoverAlpha, hovered ? 1.0F : 0.0F, 0.22F);
        enabledAlpha = ClickGui.animate(enabledAlpha, this.mod.isEnabled() ? 1.0F : 0.0F, 0.18F);
        hiddenAlpha = ClickGui.animate(hiddenAlpha, this.mod.isHidden() ? 1.0F : 0.0F, 0.18F);
        updateOpenProgress();

        int baseAlpha = 38 + (int) (hoverAlpha * 18.0F);
        Gui.drawRect(baseX, baseY, baseX + width, baseY + CategoryComponent.MODULE_HEIGHT, new Color(0, 0, 0, ClickGui.alpha(baseAlpha)).getRGB());
        boolean canExpand = hasVisibleSettings();
        int controlsWidth = (canExpand ? 13 : 0) + (hoverAlpha > 0.02F ? 19 : 0);
        String name = this.mod.getName();
        int textColor = ClickGui.blendColor(
                new Color(122, 122, 122, 255),
                new Color(232, 232, 232, 255),
                Math.max(enabledAlpha, hoverAlpha * 0.7F)
        );
        ClickGui.drawText(fr, name, baseX + 1, baseY + 2, textColor);

        if (hoverAlpha > 0.02F) {
            int iconAlpha = (int) (hoverAlpha * 220.0F);
            drawEyeIcon(baseX + width - (canExpand ? 34 : 20), baseY + 2, this.mod.isHidden(), iconAlpha);
        }

        if (canExpand) {
            int signColor = new Color(170, 170, 170, 165 + (int) (hoverAlpha * 70.0F)).getRGB();
            ClickGui.drawText(fr, this.panelExpand ? "-" : "+", baseX + width - 10, baseY + 2, signColor);
        }

        int visibleSettingsHeight = getAnimatedSettingsHeight();
        if (visibleSettingsHeight > 0 && !this.settings.isEmpty()) {
            int settingOffset = offset.get();
            int settingsTop = baseY + CategoryComponent.MODULE_HEIGHT;
            int settingsBottom = settingsTop + visibleSettingsHeight;
            int clipTop = Math.max(settingsTop, this.category.getBodyClipTop());
            int clipBottom = Math.min(settingsBottom, this.category.getBodyClipBottom());
            if (clipBottom > clipTop) {
                applyScissor(getSettingX(), clipTop, getSettingWidth(), clipBottom - clipTop);
                for (Component c : this.settings) {
                    if (c.isVisible()) {
                        c.draw(new AtomicInteger(settingOffset));
                        settingOffset += c.getHeight();
                    }
                }
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
        }
    }

    public int getHeight() {
        return CategoryComponent.MODULE_HEIGHT + getAnimatedSettingsHeight();
    }

    public void update(int mousePosX, int mousePosY) {
        hovered = isHovered(mousePosX, mousePosY);
        if (!panelExpand) {
            return;
        }
        if (!this.settings.isEmpty()) {
            for (Component c : this.settings) {
                if (c.isVisible()) {
                    c.update(mousePosX, mousePosY);
                }
            }
        }
    }

    public void mouseDown(int x, int y, int button) {
        boolean onTitle = x >= this.category.getX() && x <= this.category.getX() + this.category.getWidth()
                && y >= this.category.getY() + this.offsetY && y <= this.category.getY() + this.offsetY + CategoryComponent.MODULE_HEIGHT;
        if (onTitle) {
            if (button == 0 && isEyeHovered(x, y)) {
                this.mod.setHidden(!this.mod.isHidden());
            } else if (hasVisibleSettings() && isExpandHovered(x, y) && (button == 0 || button == 1)) {
                this.panelExpand = !this.panelExpand;
                this.category.updateAllOffsets();
            } else if (button == 0) {
                this.mod.toggle();
            } else if (button == 1 && hasVisibleSettings()) {
                this.panelExpand = !this.panelExpand;
                this.category.updateAllOffsets();
            }
            return;
        }

        if (!panelExpand) {
            return;
        }
        for (Component c : this.settings) {
            if (c.isVisible()) {
                c.mouseDown(x, y, button);
            }
        }
    }

    public void mouseReleased(int x, int y, int button) {
        if (!panelExpand) {
            return;
        }
        for (Component c : this.settings) {
            if (c.isVisible()) {
                c.mouseReleased(x, y, button);
            }
        }
    }

    public void keyTyped(char chatTyped, int keyCode) {
        if (!panelExpand) {
            return;
        }
        for (Component c : this.settings) {
            if (c.isVisible()) {
                c.keyTyped(chatTyped, keyCode);
            }
        }
    }

    public boolean isHovered(int x, int y) {
        return x > this.category.getX() && x < this.category.getX() + this.category.getWidth()
                && y > this.category.getY() + this.offsetY && y < this.category.getY() + CategoryComponent.MODULE_HEIGHT + this.offsetY;
    }

    private boolean hasVisibleSettings() {
        for (Component c : this.settings) {
            if (c.isVisible()) {
                return true;
            }
        }
        return false;
    }

    public int getSettingX() {
        return this.category.getX() + 1;
    }

    public int getSettingWidth() {
        return Math.max(1, this.category.getWidth() - 2);
    }

    private int getSettingsHeight() {
        int h = 0;
        for (Component c : this.settings) {
            if (c.isVisible()) {
                h += c.getHeight();
            }
        }
        return h;
    }

    private int getAnimatedSettingsHeight() {
        if (!hasVisibleSettings()) {
            return 0;
        }
        int fullHeight = getSettingsHeight();
        if (openProgress <= 0.015F) {
            return 0;
        }
        if (openProgress >= 0.985F) {
            return fullHeight;
        }
        return Math.min(fullHeight, (int) Math.ceil(fullHeight * openProgress));
    }

    private void updateOpenProgress() {
        float target = this.panelExpand && hasVisibleSettings() ? 1.0F : 0.0F;
        openProgress = ClickGui.animate(openProgress, target, 0.24F);
        if (target == 1.0F && openProgress > 0.985F) {
            openProgress = 1.0F;
        } else if (target == 0.0F && openProgress < 0.015F) {
            openProgress = 0.0F;
        }
    }

    private void applyScissor(int x, int y, int width, int height) {
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        double scale = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (x * scale), (int) ((sr.getScaledHeight() - y - height) * scale), (int) (width * scale), (int) (height * scale));
    }

    private boolean isExpandHovered(int mouseX, int mouseY) {
        int baseX = this.category.getX();
        int baseY = this.category.getY() + this.offsetY;
        int width = this.category.getWidth();
        return mouseX >= baseX + width - 16 && mouseX <= baseX + width && mouseY >= baseY && mouseY <= baseY + CategoryComponent.MODULE_HEIGHT;
    }

    private boolean isEyeHovered(int mouseX, int mouseY) {
        int baseX = this.category.getX();
        int baseY = this.category.getY() + this.offsetY;
        int width = this.category.getWidth();
        int iconX = baseX + width - (hasVisibleSettings() ? 34 : 20);
        return mouseX >= iconX && mouseX <= iconX + 12 && mouseY >= baseY + 1 && mouseY <= baseY + CategoryComponent.MODULE_HEIGHT - 1;
    }

    private void drawEyeIcon(int x, int y, boolean hidden, int alpha) {
        ensureEyeIcons();
        RenderUtil.drawImage(hidden ? eyeOffIcon : eyeIcon, x, y, 12, 12, new Color(230, 230, 230, ClickGui.alpha(alpha)).getRGB());
    }

    private static void ensureEyeIcons() {
        if (eyeIcon != null && eyeOffIcon != null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        eyeIcon = mc.getTextureManager().getDynamicTextureLocation("myau_clickgui_eye", new DynamicTexture(loadEyeImage(false)));
        eyeOffIcon = mc.getTextureManager().getDynamicTextureLocation("myau_clickgui_eye_off", new DynamicTexture(loadEyeImage(true)));
    }

    private static BufferedImage loadEyeImage(boolean hidden) {
        String path = hidden ? "/assets/myau/textures/gui/eye_off.png" : "/assets/myau/textures/gui/eye.png";
        try (InputStream input = ModuleComponent.class.getResourceAsStream(path)) {
            if (input != null) {
                return ImageIO.read(input);
            }
        } catch (Exception ignored) {
        }

        return createEyeImage(hidden);
    }

    private static BufferedImage createEyeImage(boolean hidden) {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(3.0F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawArc(4, 8, 24, 16, 0, 180);
        g.drawArc(4, 8, 24, 16, 180, 180);
        g.fillOval(12, 12, 8, 8);
        if (hidden) {
            g.setStroke(new BasicStroke(3.2F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(6, 25, 26, 7);
        }
        g.dispose();
        return image;
    }

    public boolean matchesSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            return true;
        }
        String q = query.toLowerCase();
        for (String term : searchTerms) {
            if (term.contains(q)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isVisible() {
        return true;
    }
}
