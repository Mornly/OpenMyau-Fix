package myau.ui.components;

import myau.ui.ClickGui;
import myau.ui.Component;
import myau.ui.callback.GuiInput;
import myau.ui.dataset.Slider;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.atomic.AtomicInteger;

public class SliderComponent implements Component {
    private final Slider slider;
    private final ModuleComponent parentModule;
    private int offsetY;
    private int x;
    private int y;
    private boolean dragging = false;
    private double sliderWidth;
    private long increment = 0;
    private long decrement = 0;
    private boolean hovered;
    private float hoverAlpha;

    public SliderComponent(Slider slider, ModuleComponent parentModule, int offsetY) {
        this.slider = slider;
        this.parentModule = parentModule;
        this.x = parentModule.category.getX();
        this.y = parentModule.category.getY() + parentModule.offsetY;
        this.offsetY = offsetY;
    }

    public void draw(AtomicInteger offset) {
        FontRenderer fr = ClickGui.getInstance().getCurrentRenderer();
        int baseX = this.parentModule.getSettingX();
        int baseY = this.parentModule.category.getY() + this.offsetY;
        int width = this.parentModule.getSettingWidth();
        int accent = ClickGui.getHudColor((long) (baseX + baseY + offset.get()) * 18L);
        int trackX = baseX + 8;
        int trackY = baseY + 14;
        int trackWidth = width - 16;

        updateSliderWidth(trackWidth);
        hoverAlpha = ClickGui.animate(hoverAlpha, hovered ? 1.0F : 0.0F, 0.22F);
        Gui.drawRect(baseX, baseY, baseX + width, baseY + getHeight(), new Color(0, 0, 0, 50 + (int) (hoverAlpha * 32.0F)).getRGB());
        String label = ClickGui.trimToChars(this.slider.getName(), 15) + ": " + ClickGui.trimToChars(this.slider.getValueString(), 15);
        ClickGui.drawText(fr, label, baseX + 8, baseY + 2, new Color(215, 215, 215).getRGB());

        Gui.drawRect(trackX, trackY, trackX + trackWidth, trackY + 3, new Color(38, 38, 38, 210).getRGB());
        Gui.drawRect(trackX, trackY, trackX + (int) this.sliderWidth, trackY + 3, accent);
        Gui.drawRect(trackX + (int) this.sliderWidth - 1, trackY - 1, trackX + (int) this.sliderWidth + 1, trackY + 4, new Color(235, 235, 235).getRGB());
    }

    public void setComponentStartAt(int newOffsetY) {
        this.offsetY = newOffsetY;
    }

    @Override
    public int getHeight() {
        return 20;
    }

    public void update(int mousePosX, int mousePosY) {
        this.y = this.parentModule.category.getY() + this.offsetY;
        this.x = this.parentModule.getSettingX();
        this.hovered = mousePosX >= this.x && mousePosX <= this.x + this.parentModule.getSettingWidth()
                && mousePosY >= this.y && mousePosY <= this.y + getHeight();

        int trackX = this.x + 8;
        int trackWidth = this.parentModule.getSettingWidth() - 16;
        updateSliderWidth(trackWidth);

        double d = Math.min(trackWidth, Math.max(0, mousePosX - trackX));
        if (this.dragging) {
            if (d == 0.0D) {
                this.slider.setValue(this.slider.getMin());
            } else {
                double rawValue = d / (double) trackWidth * (this.slider.getMax() - this.slider.getMin()) + this.slider.getMin();
                double increment = this.slider.getIncrement();
                if (increment > 0) {
                    rawValue = Math.round(rawValue / increment) * increment;
                }
                double n = roundToPrecision(rawValue, 2);
                n = Math.max(this.slider.getMin(), Math.min(this.slider.getMax(), n));
                this.slider.setValue(n);
            }
        }
        if (this.increment != 0 && this.increment < System.currentTimeMillis()) {
            this.increment = System.currentTimeMillis() + 50;
            this.slider.stepping(true);
        }
        if (this.decrement != 0 && this.decrement < System.currentTimeMillis()) {
            this.decrement = System.currentTimeMillis() + 50;
            this.slider.stepping(false);
        }
    }

    private void updateSliderWidth(int trackWidth) {
        double range = this.slider.getMax() - this.slider.getMin();
        if (range <= 0.0D) {
            this.sliderWidth = 0.0D;
            return;
        }
        this.sliderWidth = trackWidth * (this.slider.getInput() - this.slider.getMin()) / range;
        this.sliderWidth = Math.max(0.0D, Math.min(trackWidth, this.sliderWidth));
    }

    private static double roundToPrecision(double v, int precision) {
        if (precision < 0) {
            return 0.0D;
        }
        BigDecimal bd = new BigDecimal(v);
        bd = bd.setScale(precision, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public void mouseDown(int x, int y, int button) {
        if (this.isTextHovered(x, y) && button == 0 && this.parentModule.panelExpand) {
            GuiInput.prompt(slider.getName().replace("-", " "), slider.getValueString(), slider::setValueString, ClickGui.getInstance());
            return;
        }

        if (this.isLeftHalfHovered(x, y) && this.parentModule.panelExpand) {
            if (button == 0) {
                this.dragging = true;
            } else if (button == 1 && this.decrement == 0) {
                this.decrement = System.currentTimeMillis() + 500;
                this.slider.stepping(false);
            }
        }

        if (this.isRightHalfHovered(x, y) && this.parentModule.panelExpand) {
            if (button == 0) {
                this.dragging = true;
            } else if (button == 1 && this.increment == 0) {
                this.increment = System.currentTimeMillis() + 500;
                this.slider.stepping(true);
            }
        }
    }

    public void mouseReleased(int x, int y, int button) {
        this.dragging = false;
        this.increment = 0;
        this.decrement = 0;
    }

    @Override
    public void keyTyped(char chatTyped, int keyCode) {
    }

    public boolean isTextHovered(int x, int y) {
        return x >= this.x && x <= this.x + this.parentModule.getSettingWidth() && y >= this.y && y <= this.y + 12;
    }

    public boolean isLeftHalfHovered(int x, int y) {
        return x >= this.x && x <= this.x + this.parentModule.getSettingWidth() / 2 + 1 && y > this.y + 12 && y <= this.y + getHeight();
    }

    public boolean isRightHalfHovered(int x, int y) {
        return x >= this.x + this.parentModule.getSettingWidth() / 2 && x <= this.x + this.parentModule.getSettingWidth() && y > this.y + 12 && y <= this.y + getHeight();
    }

    @Override
    public boolean isVisible() {
        return slider.isVisible();
    }
}
