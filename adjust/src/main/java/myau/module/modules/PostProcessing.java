package myau.module.modules;

import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;

public class PostProcessing extends Module {
    public static PostProcessing instance;

    public final BooleanProperty blur = new BooleanProperty("Blur", true);
    public final FloatProperty blurStrength = new FloatProperty("BlurStrength", 10f, 1f, 30f);
    public final BooleanProperty bloom = new BooleanProperty("Bloom", false);
    public final FloatProperty bloomStrength = new FloatProperty("BloomStrength", 5f, 1f, 20f);
    public final FloatProperty bloomOffset = new FloatProperty("BloomOffset", 3f, 1f, 10f);
    public final FloatProperty bloomIterations = new FloatProperty("BloomIterations", 5f, 1f, 10f);

    public PostProcessing() {
        super("PostProcessing", false, false);
        instance = this;
    }

    public static boolean isBlurEnabled() {
        return instance != null && instance.isEnabled() && instance.blur.getValue();
    }

    public static float getBlurStrength() {
        return instance != null ? instance.blurStrength.getValue() : 0f;
    }

    public static boolean isBloomEnabled() {
        return instance != null && instance.isEnabled() && instance.bloom.getValue();
    }

    public static int getBloomIterations() {
        return instance != null ? instance.bloomIterations.getValue().intValue() : 1;
    }

    public static int getBloomOffset() {
        return instance != null ? instance.bloomOffset.getValue().intValue() : 1;
    }
}