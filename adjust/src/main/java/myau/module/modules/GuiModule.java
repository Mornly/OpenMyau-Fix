package myau.module.modules;

import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.ui.ClickGui;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

public class GuiModule extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private ClickGui clickGui;

    public final BooleanProperty modernFont = new BooleanProperty("Modern-Font", false);
    public final BooleanProperty blur = new BooleanProperty("Blur", false);

    public GuiModule() {
        super("ClickGui", false);
        setKey(Keyboard.KEY_RSHIFT);
    }

    @Override
    public void onEnabled() {
        setEnabled(false);
        if(clickGui == null){
            clickGui = new ClickGui();
        }
        mc.displayGuiScreen(clickGui);
    }
}
