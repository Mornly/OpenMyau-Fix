package myau.property.properties;

import com.google.gson.JsonObject;
import myau.property.Property;

import java.util.function.BooleanSupplier;

public class ColorProperty extends Property<Integer> {
    public ColorProperty(String name, Integer color) {
        this(name, color, null);
    }

    public ColorProperty(String name, Integer color, BooleanSupplier check) {
        super(name, color, rgb -> true, check);
    }

    @Override
    public String getValuePrompt() {
        return "RGB";
    }

    @Override
    public String formatValue() {
        String hex = String.format("%06X", this.getValue() & 0xFFFFFF);
        return String.format("&c%s&a%s&9%s", hex.substring(0, 2), hex.substring(2, 4), hex.substring(4, 6));
    }

    @Override
    public boolean parseString(String string) {
        try {
            String clean = string.replace("#", "").trim();
            if (clean.length() == 6) {
                return this.setValue(Integer.parseInt(clean, 16) | 0xFF000000);
            } else if (clean.length() == 8) {
                return this.setValue(Integer.parseUnsignedInt(clean, 16));
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean read(JsonObject jsonObject) {
        try {
            String val = jsonObject.get(this.getName()).getAsString();
            return this.parseString(val);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void write(JsonObject jsonObject) {
        jsonObject.addProperty(this.getName(), String.format("%08X", this.getValue()));
    }
}