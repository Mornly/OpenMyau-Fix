package myau.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class ScoreboardDragger {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static int x = Integer.MIN_VALUE;
    private static int y = Integer.MIN_VALUE;
    private static boolean dragging;
    private static int dragStartX;
    private static int dragStartY;
    private static int dragStartXPos;
    private static int dragStartYPos;
    private static int lastWidth;
    private static int lastHeight;

    private ScoreboardDragger() {
    }

    public static void render(ScoreObjective objective, ScaledResolution sr) {
        if (objective == null || mc.theWorld == null) {
            return;
        }

        FontRenderer font = mc.fontRendererObj;
        List<Score> scores = getVisibleScores(objective);
        if (scores.isEmpty()) {
            return;
        }

        int width = font.getStringWidth(objective.getDisplayName());
        Scoreboard scoreboard = objective.getScoreboard();
        for (Score score : scores) {
            ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
            String line = ScorePlayerTeam.formatPlayerName(team, score.getPlayerName());
            width = Math.max(width, font.getStringWidth(line));
        }

        int height = scores.size() * font.FONT_HEIGHT + font.FONT_HEIGHT + 1;
        updateDrag(sr, width, height, scores.size());

        int left = getX(sr, width);
        int top = getY(sr, scores.size(), font);
        int right = left + width + 2;
        int yLine = top + font.FONT_HEIGHT + 1;
        int index = 0;

        for (Score score : scores) {
            int rowTop = yLine + (scores.size() - 1 - index) * font.FONT_HEIGHT;
            int rowBottom = rowTop + font.FONT_HEIGHT;
            ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
            String line = ScorePlayerTeam.formatPlayerName(team, score.getPlayerName());

            Gui.drawRect(left - 2, rowTop, right, rowBottom, 1342177280);
            font.drawString(line, left, rowTop, 553648127);
            index++;
        }

        Gui.drawRect(left - 2, top, right, top + font.FONT_HEIGHT, 1610612736);
        Gui.drawRect(left - 2, top + font.FONT_HEIGHT, right, top + font.FONT_HEIGHT + 1, 1342177280);
        String title = objective.getDisplayName();
        font.drawString(title, left + width / 2.0F - font.getStringWidth(title) / 2.0F, top, 553648127, false);
    }

    private static List<Score> getVisibleScores(ScoreObjective objective) {
        Collection<Score> sorted = objective.getScoreboard().getSortedScores(objective);
        List<Score> visible = new ArrayList<>();
        for (Score score : sorted) {
            String name = score.getPlayerName();
            if (name != null && !name.startsWith("#")) {
                visible.add(score);
            }
        }
        if (visible.size() <= 15) {
            return visible;
        }
        return new ArrayList<>(visible.subList(visible.size() - 15, visible.size()));
    }

    private static int getX(ScaledResolution sr, int width) {
        if (x == Integer.MIN_VALUE) {
            return sr.getScaledWidth() - width - 3;
        }
        return Math.max(2, Math.min(x, sr.getScaledWidth() - width - 3));
    }

    private static int getY(ScaledResolution sr, int rows, FontRenderer font) {
        if (y == Integer.MIN_VALUE) {
            int rowsHeight = rows * font.FONT_HEIGHT;
            return sr.getScaledHeight() / 2 + rowsHeight / 3 - rowsHeight - font.FONT_HEIGHT - 1;
        }
        return Math.max(2, Math.min(y, sr.getScaledHeight() - lastHeight - 2));
    }

    private static void updateDrag(ScaledResolution sr, int width, int height, int rows) {
        lastWidth = width + 4;
        lastHeight = height;
        if (!(mc.currentScreen instanceof GuiChat)) {
            dragging = false;
            return;
        }

        int mouseX = Mouse.getX() * sr.getScaledWidth() / mc.displayWidth;
        int mouseY = sr.getScaledHeight() - Mouse.getY() * sr.getScaledHeight() / mc.displayHeight - 1;
        int left = getX(sr, width);
        int top = getY(sr, rows, mc.fontRendererObj);

        if (Mouse.isButtonDown(0)) {
            if (!dragging && mouseX >= left - 2 && mouseX <= left - 2 + lastWidth && mouseY >= top && mouseY <= top + height) {
                dragging = true;
                dragStartX = mouseX;
                dragStartY = mouseY;
                dragStartXPos = left;
                dragStartYPos = top;
            }
            if (dragging) {
                x = dragStartXPos + mouseX - dragStartX;
                y = dragStartYPos + mouseY - dragStartY;
            }
        } else {
            dragging = false;
        }
    }
}
