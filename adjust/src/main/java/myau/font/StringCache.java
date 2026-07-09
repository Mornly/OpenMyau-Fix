package myau.font;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.WorldRenderer;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.awt.font.GlyphVector;
import java.lang.ref.WeakReference;
import java.text.Bidi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.WeakHashMap;

public class StringCache {
    private static final int BASELINE_OFFSET = 0;
    private static final int UNDERLINE_OFFSET = 1;
    private static final int UNDERLINE_THICKNESS = 1;
    private static final int STRIKETHROUGH_OFFSET = -6;
    private static final int STRIKETHROUGH_THICKNESS = 2;

    private final GlyphCache glyphCache;
    private final int[] colorTable;
    private final WeakHashMap<Key, Entry> stringCache = new WeakHashMap<>();
    private final WeakHashMap<String, Key> weakRefCache = new WeakHashMap<>();
    private final Key lookupKey = new Key();
    private final Glyph[][] digitGlyphs = new Glyph[4][];
    private boolean digitGlyphsReady = false;
    private boolean antiAliasEnabled = false;
    private float renderDivisor = 2.0F;
    private final Thread mainThread;
    public int height;

    static private class Key {
        public String str;

        @Override
        public int hashCode() {
            int code = 0, length = str.length();
            boolean colorCode = false;

            for (int index = 0; index < length; index++) {
                char c = str.charAt(index);
                if (c >= '0' && c <= '9' && !colorCode) {
                    c = '0';
                }
                code = (code * 31) + c;
                colorCode = (c == '§');
            }

            return code;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) return false;
            String other = o.toString();
            int length = str.length();
            if (length != other.length()) return false;
            boolean colorCode = false;
            for (int index = 0; index < length; index++) {
                char c1 = str.charAt(index);
                char c2 = other.charAt(index);
                if (c1 != c2 && (c1 < '0' || c1 > '9' || c2 < '0' || c2 > '9' || colorCode)) {
                    return false;
                }
                colorCode = (c1 == '§');
            }
            return true;
        }

        @Override
        public String toString() {
            return str;
        }
    }

    static private class Entry {
        public WeakReference<Key> keyRef;
        public int advance;
        public Glyph[] glyphs;
        public ColorCode[] colors;
        public boolean specialRender;
    }

    static private class ColorCode implements Comparable<Integer> {
        public static final byte UNDERLINE = 1;
        public static final byte STRIKETHROUGH = 2;
        public int stringIndex;
        public int stripIndex;
        public byte colorCode;
        public byte fontStyle;
        public byte renderStyle;

        @Override
        public int compareTo(Integer i) {
            return Integer.compare(stringIndex, i);
        }
    }

    static private class Glyph implements Comparable<Glyph> {
        public int stringIndex;
        public GlyphCache.Entry texture;
        public int x;
        public int y;
        public int advance;

        @Override
        public int compareTo(Glyph o) {
            return Integer.compare(stringIndex, o.stringIndex);
        }
    }

    public StringCache(int[] colors) {
        mainThread = Thread.currentThread();
        glyphCache = new GlyphCache();
        colorTable = colors;
        cacheDightGlyphs();
    }

    public void setDefaultFont(Font font, int fontSize, boolean antiAlias) {
        setDefaultFont(font, fontSize, antiAlias, 1);
    }

    public void setDefaultFont(Font font, int fontSize, boolean antiAlias, int qualityScale) {
        renderDivisor = 2.0F * Math.max(1, qualityScale);
        glyphCache.setDefaultFont(font, fontSize, antiAlias);
        antiAliasEnabled = antiAlias;
        height = 0;
        weakRefCache.clear();
        stringCache.clear();
        cacheDightGlyphs();
    }

    private void cacheDightGlyphs() {
        digitGlyphsReady = false;
        digitGlyphs[Font.PLAIN] = cacheString("0123456789").glyphs;
        digitGlyphs[Font.BOLD] = cacheString("§l0123456789").glyphs;
        digitGlyphs[Font.ITALIC] = cacheString("§o0123456789").glyphs;
        digitGlyphs[Font.BOLD | Font.ITALIC] = cacheString("§l§o0123456789").glyphs;
        digitGlyphsReady = true;
    }

    public int renderString(String str, float startX, float startY, int initialColor, boolean shadowFlag) {
        if (str == null || str.isEmpty()) return 0;
        GlStateManager.disableBlend();
        startX = Math.round(startX);
        startY = Math.round(startY);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);

        Entry entry = cacheString(str);
        startY += BASELINE_OFFSET;
        int color = initialColor;
        GlStateManager.color((color >> 16 & 0xff) / 255F, (color >> 8 & 0xff) / 255F, (color & 0xff) / 255F);

        if (antiAliasEnabled) {
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        worldRenderer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        int fontStyle = Font.PLAIN;

        for (int glyphIndex = 0, colorIndex = 0; glyphIndex < entry.glyphs.length; glyphIndex++) {
            while (colorIndex < entry.colors.length && entry.glyphs[glyphIndex].stringIndex >= entry.colors[colorIndex].stringIndex) {
                color = applyColorCode(entry.colors[colorIndex].colorCode, initialColor, shadowFlag);
                fontStyle = entry.colors[colorIndex].fontStyle;
                colorIndex++;
            }

            Glyph glyph = entry.glyphs[glyphIndex];
            GlyphCache.Entry texture = glyph.texture;
            if (texture == null) continue;
            int glyphX = glyph.x;

            char c = str.charAt(glyph.stringIndex);
            if (c >= '0' && c <= '9' && digitGlyphs[fontStyle] != null) {
                int oldWidth = texture.width - (texture.offsetX * 2);
                Glyph digitGlyph = digitGlyphs[fontStyle][c - '0'];
                if (digitGlyph != null && digitGlyph.texture != null) {
                    texture = digitGlyph.texture;
                } else continue;
                int newWidth = texture.width - (texture.offsetX * 2);
                glyphX += (oldWidth - newWidth) >> 1;
            }
            if (texture.height > this.height) this.height = texture.height;

            GlStateManager.bindTexture(texture.textureName);
            float x1 = startX + (glyphX - texture.offsetX) / renderDivisor;
            float x2 = startX + (glyphX - texture.offsetX + texture.width) / renderDivisor;
            float y1 = startY + (glyph.y - texture.offsetY) / renderDivisor + this.height / renderDivisor;
            float y2 = startY + (glyph.y - texture.offsetY + texture.height) / renderDivisor + this.height / renderDivisor;

            int a = color >> 24 & 0xff;
            int r = color >> 16 & 0xff;
            int g = color >> 8 & 0xff;
            int b = color & 0xff;

            worldRenderer.pos(x1, y1, 0).tex(texture.u1, texture.v1).color(r, g, b, a).endVertex();
            worldRenderer.pos(x1, y2, 0).tex(texture.u1, texture.v2).color(r, g, b, a).endVertex();
            worldRenderer.pos(x2, y2, 0).tex(texture.u2, texture.v2).color(r, g, b, a).endVertex();
            worldRenderer.pos(x2, y1, 0).tex(texture.u2, texture.v1).color(r, g, b, a).endVertex();
        }
        tessellator.draw();

        if (entry.specialRender) {
            int renderStyle = 0;
            GlStateManager.disableTexture2D();
            worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
            for (int glyphIndex = 0, colorIndex = 0; glyphIndex < entry.glyphs.length; glyphIndex++) {
                while (colorIndex < entry.colors.length && entry.glyphs[glyphIndex].stringIndex >= entry.colors[colorIndex].stringIndex) {
                    applyColorCode(entry.colors[colorIndex].colorCode, initialColor, shadowFlag);
                    renderStyle = entry.colors[colorIndex].renderStyle;
                    colorIndex++;
                }
                Glyph glyph = entry.glyphs[glyphIndex];
                int glyphSpace = glyph.advance - glyph.texture.width;

                if ((renderStyle & ColorCode.UNDERLINE) != 0) {
                    float x1 = startX + (glyph.x - glyphSpace) / renderDivisor;
                    float x2 = startX + (glyph.x + glyph.advance) / renderDivisor;
                    float y1 = startY + (height / renderDivisor) + 1;
                    float y2 = startY + ((height + UNDERLINE_THICKNESS) / renderDivisor) + 1;
                    worldRenderer.pos(x1, y1, 0).color(0, 0, 0, 255).endVertex();
                    worldRenderer.pos(x1, y2, 0).color(0, 0, 0, 255).endVertex();
                    worldRenderer.pos(x2, y2, 0).color(0, 0, 0, 255).endVertex();
                    worldRenderer.pos(x2, y1, 0).color(0, 0, 0, 255).endVertex();
                }
                if ((renderStyle & ColorCode.STRIKETHROUGH) != 0) {
                    float x1 = startX + (glyph.x - glyphSpace) / renderDivisor;
                    float x2 = startX + (glyph.x + glyph.advance) / renderDivisor;
                    float y1 = startY + (STRIKETHROUGH_OFFSET + height) / renderDivisor;
                    float y2 = startY + (STRIKETHROUGH_OFFSET + STRIKETHROUGH_THICKNESS + height) / renderDivisor;
                    worldRenderer.pos(x1, y1, 0).color(0, 0, 0, 255).endVertex();
                    worldRenderer.pos(x1, y2, 0).color(0, 0, 0, 255).endVertex();
                    worldRenderer.pos(x2, y2, 0).color(0, 0, 0, 255).endVertex();
                    worldRenderer.pos(x2, y1, 0).color(0, 0, 0, 255).endVertex();
                }
            }
            tessellator.draw();
            GlStateManager.enableTexture2D();
        }
        GlStateManager.enableBlend();
        return (int) (startX + entry.advance / renderDivisor);
    }

    public int getStringWidth(String str) {
        if (str == null || str.isEmpty()) return 0;
        return Math.round(cacheString(str).advance / renderDivisor);
    }

    public int getHeight() {
        return Math.round(height / renderDivisor);
    }

    private int sizeString(String str, float width, boolean breakAtSpaces) {
        if (str == null || str.isEmpty()) return 0;
        width *= renderDivisor;
        Glyph[] glyphs = cacheString(str).glyphs;
        int wsIndex = -1, advance = 0, index = 0;
        while (index < glyphs.length && advance <= width) {
            if (breakAtSpaces) {
                char c = str.charAt(glyphs[index].stringIndex);
                if (c == ' ' || c == '\n') { wsIndex = index; if (c == '\n') break; }
            }
            advance += glyphs[index].advance;
            index++;
        }
        if (index < glyphs.length && wsIndex != -1 && wsIndex < index) index = wsIndex;
        return index < glyphs.length ? glyphs[index].stringIndex : str.length();
    }

    public int sizeStringToWidth(String str, int width) {
        return sizeString(str, width, true);
    }

    public String trimStringToWidth(String str, float width, boolean reverse) {
        int length = sizeString(str, width, false);
        str = str.substring(0, length);
        if (reverse) str = new StringBuilder(str).reverse().toString();
        return str;
    }

    private int applyColorCode(int colorCode, int color, boolean shadowFlag) {
        if (colorCode != -1) {
            colorCode = shadowFlag ? colorCode + 16 : colorCode;
            color = colorTable[colorCode] & 0xffffff | color & 0xff000000;
        }
        return color;
    }

    private Entry cacheString(String str) {
        Key key;
        Entry entry = null;
        if (mainThread == Thread.currentThread()) {
            lookupKey.str = str;
            entry = stringCache.get(lookupKey);
        }
        if (entry == null) {
            char[] text = str.toCharArray();
            entry = new Entry();
            int length = stripColorCodes(entry, str, text);
            List<Glyph> glyphList = new ArrayList<>();
            entry.advance = layoutBidiString(glyphList, text, length, entry.colors);
            entry.glyphs = new Glyph[glyphList.size()];
            entry.glyphs = glyphList.toArray(entry.glyphs);
            Arrays.sort(entry.glyphs);
            int colorIndex = 0, shift = 0;
            for (int glyphIndex = 0; glyphIndex < entry.glyphs.length; glyphIndex++) {
                Glyph glyph = entry.glyphs[glyphIndex];
                while (colorIndex < entry.colors.length && glyph.stringIndex + shift >= entry.colors[colorIndex].stringIndex) {
                    shift += 2;
                    colorIndex++;
                }
                glyph.stringIndex += shift;
            }
            if (mainThread == Thread.currentThread()) {
                key = new Key();
                key.str = str;
                entry.keyRef = new WeakReference<>(key);
                stringCache.put(key, entry);
            }
        }
        if (mainThread == Thread.currentThread()) {
            Key oldKey = entry.keyRef.get();
            if (oldKey != null) weakRefCache.put(str, oldKey);
            lookupKey.str = null;
        }
        return entry;
    }

    private int stripColorCodes(Entry cacheEntry, String str, char[] text) {
        List<ColorCode> colorList = new ArrayList<>();
        int start = 0, shift = 0, next;
        byte fontStyle = Font.PLAIN, renderStyle = 0, colorCode = -1;
        while ((next = str.indexOf('§', start)) != -1 && next + 1 < str.length()) {
            System.arraycopy(text, next - shift + 2, text, next - shift, text.length - next - 2);
            int code = "0123456789abcdefklmnor".indexOf(Character.toLowerCase(str.charAt(next + 1)));
            switch (code) {
                case 17: fontStyle |= Font.BOLD; break;
                case 18: renderStyle |= ColorCode.STRIKETHROUGH; cacheEntry.specialRender = true; break;
                case 19: renderStyle |= ColorCode.UNDERLINE; cacheEntry.specialRender = true; break;
                case 20: fontStyle |= Font.ITALIC; break;
                case 21: fontStyle = Font.PLAIN; renderStyle = 0; colorCode = -1; break;
                default: if (code >= 0 && code <= 15) { colorCode = (byte) code; fontStyle = Font.PLAIN; renderStyle = 0; } break;
            }
            ColorCode entry = new ColorCode();
            entry.stringIndex = next;
            entry.stripIndex = next - shift;
            entry.colorCode = colorCode;
            entry.fontStyle = fontStyle;
            entry.renderStyle = renderStyle;
            colorList.add(entry);
            start = next + 2;
            shift += 2;
        }
        cacheEntry.colors = new ColorCode[colorList.size()];
        cacheEntry.colors = colorList.toArray(cacheEntry.colors);
        return text.length - shift;
    }

    private int layoutBidiString(List<Glyph> glyphList, char[] text, int limit, ColorCode[] colors) {
        int advance = 0;
        if (Bidi.requiresBidi(text, 0, limit)) {
            Bidi bidi = new Bidi(text, 0, null, 0, limit, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
            if (bidi.isRightToLeft()) return layoutStyle(glyphList, text, 0, limit, Font.LAYOUT_RIGHT_TO_LEFT, advance, colors);
            else {
                int runCount = bidi.getRunCount();
                byte[] levels = new byte[runCount];
                Integer[] ranges = new Integer[runCount];
                for (int i = 0; i < runCount; i++) { levels[i] = (byte) bidi.getRunLevel(i); ranges[i] = i; }
                Bidi.reorderVisually(levels, 0, ranges, 0, runCount);
                for (int i = 0; i < runCount; i++) {
                    int logicalIndex = ranges[i];
                    int layoutFlag = (bidi.getRunLevel(logicalIndex) & 1) == 1 ? Font.LAYOUT_RIGHT_TO_LEFT : Font.LAYOUT_LEFT_TO_RIGHT;
                    advance = layoutStyle(glyphList, text, bidi.getRunStart(logicalIndex), bidi.getRunLimit(logicalIndex), layoutFlag, advance, colors);
                }
            }
        } else advance = layoutStyle(glyphList, text, 0, limit, Font.LAYOUT_LEFT_TO_RIGHT, advance, colors);
        return advance;
    }

    private int layoutStyle(List<Glyph> glyphList, char[] text, int start, int limit, int layoutFlags, int advance, ColorCode[] colors) {
        int currentFontStyle = Font.PLAIN;
        int colorIndex = Arrays.binarySearch(colors, start);
        if (colorIndex < 0) colorIndex = -colorIndex - 2;
        while (start < limit) {
            int next = limit;
            while (colorIndex >= 0 && colorIndex < colors.length - 1 && colors[colorIndex].stripIndex == colors[colorIndex+1].stripIndex) colorIndex++;
            if (colorIndex >= 0 && colorIndex < colors.length) currentFontStyle = colors[colorIndex].fontStyle;
            while (++colorIndex < colors.length) {
                if (colors[colorIndex].fontStyle != currentFontStyle) { next = colors[colorIndex].stripIndex; break; }
            }
            advance = layoutString(glyphList, text, start, next, layoutFlags, advance, currentFontStyle);
            start = next;
        }
        return advance;
    }

    private int layoutString(List<Glyph> glyphList, char[] text, int start, int limit, int layoutFlags, int advance, int style) {
        if (digitGlyphsReady) for (int i = start; i < limit; i++) if (text[i] >= '0' && text[i] <= '9') text[i] = '0';
        Font font = glyphCache.lookupFont(text, start, limit, style);
        while (start < limit) {
            Font font2 = glyphCache.lookupFont(text, start, limit, style);
            int next;
            if (font != font2) {
                next = start + 1;
                advance = layoutFont(glyphList, text, start, next, layoutFlags, advance, font2);
            } else {
                next = font.canDisplayUpTo(text, start, limit);
                if (next == -1) next = limit;
                if (next == start) next++;
                advance = layoutFont(glyphList, text, start, next, layoutFlags, advance, font);
            }
            start = next;
        }
        return advance;
    }

    private int layoutFont(List<Glyph> glyphList, char[] text, int start, int limit, int layoutFlags, int advance, Font font) {
        if (mainThread == Thread.currentThread()) glyphCache.cacheGlyphs(font, text, start, limit, layoutFlags);
        GlyphVector vector = glyphCache.layoutGlyphVector(font, text, start, limit, layoutFlags);
        Glyph glyph = null;
        int numGlyphs = vector.getNumGlyphs();
        for (int i = 0; i < numGlyphs; i++) {
            Point position = vector.getGlyphPixelBounds(i, null, advance, 0).getLocation();
            if (glyph != null) glyph.advance = position.x - glyph.x;
            glyph = new Glyph();
            glyph.stringIndex = start + vector.getGlyphCharIndex(i);
            glyph.texture = glyphCache.lookupGlyph(font, vector.getGlyphCode(i));
            glyph.x = position.x;
            glyph.y = position.y;
            glyphList.add(glyph);
        }
        advance += (int) vector.getGlyphPosition(numGlyphs).getX();
        if (glyph != null) glyph.advance = advance - glyph.x;
        return advance;
    }
}
