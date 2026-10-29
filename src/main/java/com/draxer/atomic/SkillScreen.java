package com.draxer.atomic;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Árbol de habilidades (tecla K): las mejoras a la izquierda (clic para subir de nivel) y la
 * lista de hechizos a la derecha (clic para elegirlo).
 */
public final class SkillScreen extends Screen {
    private static final int W = 170;
    private static final int H = 24;

    public SkillScreen() {
        super(Component.translatable("gui.draxatomic.skills.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int left() {
        return this.width / 2 - W - 8;
    }

    private int right() {
        return this.width / 2 + 8;
    }

    private static final int TOP = 44;

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        MagicState.Upgrade[] ups = MagicState.Upgrade.values();
        for (int i = 0; i < ups.length; i++) {
            int y = TOP + i * (H + 4);
            if (mx >= left() && mx < left() + W && my >= y && my < y + H) {
                ClientPacketDistributor.sendToServer(new MagicPayloads.Action(MagicPayloads.BUY, i));
                return true;
            }
        }
        MagicState.Spell[] spells = MagicState.Spell.values();
        for (int i = 0; i < spells.length; i++) {
            int y = TOP + i * 14;
            if (mx >= right() && mx < right() + W && my >= y && my < y + 12) {
                ClientPacketDistributor.sendToServer(new MagicPayloads.Action(MagicPayloads.SELECT, i));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        MagicPayloads.Sync s = MagicPayloads.Sync.latest;
        graphics.fill(0, 0, this.width, this.height, 0xD0100818);
        graphics.centeredText(this.font, this.title, this.width / 2, 10, 0xFFD9B8FF);
        graphics.centeredText(this.font, Component.translatable("gui.draxatomic.skills.points", s.points(), s.xp(), 500),
                this.width / 2, 24, 0xFFFFD54A);
        // Mejoras
        MagicState.Upgrade[] ups = MagicState.Upgrade.values();
        List<Component> tip = new ArrayList<>();
        for (int i = 0; i < ups.length; i++) {
            MagicState.Upgrade u = ups[i];
            int x = left();
            int y = TOP + i * (H + 4);
            int lvl = s.level(u);
            boolean maxed = lvl >= u.max();
            boolean afford = !maxed && s.points() >= lvl + 1;
            boolean hover = mouseX >= x && mouseX < x + W && mouseY >= y && mouseY < y + H;
            int border = maxed ? 0xFF4CD964 : afford ? 0xFFB07CFF : 0xFF4A4060;
            graphics.fill(x - 1, y - 1, x + W + 1, y + H + 1, border);
            graphics.fill(x, y, x + W, y + H, hover ? 0xFF2A2038 : 0xFF1A1424);
            graphics.text(this.font, Component.translatable("upgrade.draxatomic." + u.id()), x + 4, y + 3, 0xFFE8E0F0);
            StringBuilder pips = new StringBuilder();
            for (int k = 0; k < u.max(); k++) pips.append(k < lvl ? "◆" : "◇");
            graphics.text(this.font, Component.literal(pips.toString()), x + 4, y + 13, 0xFFB07CFF);
            Component costLine = maxed ? Component.translatable("gui.draxatomic.skills.max")
                    : Component.translatable("gui.draxatomic.skills.cost", lvl + 1);
            graphics.text(this.font, costLine, x + W - 4 - this.font.width(costLine), y + 13, maxed ? 0xFF4CD964 : afford ? 0xFFFFD54A : 0xFF8A8098);
            if (hover) tip.add(Component.translatable("upgrade.draxatomic." + u.id() + ".desc"));
        }
        // Hechizos
        graphics.text(this.font, Component.translatable("gui.draxatomic.skills.spells"), right(), TOP - 12, 0xFFD9B8FF);
        MagicState.Spell[] spells = MagicState.Spell.values();
        for (int i = 0; i < spells.length; i++) {
            MagicState.Spell sp = spells[i];
            int y = TOP + i * 14;
            boolean selected = s.spell() == i;
            boolean hover = mouseX >= right() && mouseX < right() + W && mouseY >= y && mouseY < y + 12;
            if (selected || hover) graphics.fill(right() - 2, y - 1, right() + W, y + 11, selected ? 0x806A2BCF : 0x40FFFFFF);
            Component label = Component.literal(sp.toggle() ? "◈ " : "✦ ").append(Component.translatable("spell.draxatomic." + sp.id()));
            graphics.text(this.font, label, right(), y + 1, s.on(sp) ? 0xFFB8F5C4 : 0xFFE8E0F0);
            if (hover) tip.add(Component.translatable("spell.draxatomic." + sp.id() + ".desc"));
        }
        graphics.centeredText(this.font, Component.translatable("gui.draxatomic.skills.keys"), this.width / 2, this.height - 14, 0xFF8A8098);
        if (!tip.isEmpty()) {
            int x = Math.min(mouseX + 10, this.width - 230);
            int y = Math.min(mouseY + 10, this.height - 20);
            graphics.fill(x - 3, y - 3, x + 225, y + 12, 0xF0080A0C);
            graphics.text(this.font, tip.get(0), x, y, 0xFFD0D6DC);
        }
    }
}
