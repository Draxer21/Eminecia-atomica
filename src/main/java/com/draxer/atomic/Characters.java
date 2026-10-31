package com.draxer.atomic;

import net.minecraft.ChatFormatting;

/** Los personajes con nombre: quiénes son, de qué reino, y de qué color va su nombre. */
public final class Characters {
    private Characters() {}

    public record Character(String id, String kingdom, ChatFormatting color) {}

    public static final Character ALEXIA = new Character("alexia", "midgar", ChatFormatting.WHITE);
    public static final Character IRIS = new Character("iris", "midgar", ChatFormatting.RED);
    public static final Character CLAIRE = new Character("claire", "midgar", ChatFormatting.DARK_RED);
    public static final Character GAMMA = new Character("gamma", "midgar", ChatFormatting.GOLD);
    public static final Character ROSE = new Character("rose", "oriana", ChatFormatting.YELLOW);
    public static final Character RAPHAEL = new Character("raphael", "oriana", ChatFormatting.AQUA);

    public static final Character MARY = new Character("mary", "lawless", ChatFormatting.DARK_RED);
    public static final Character YUKIME = new Character("yukime", "lawless", ChatFormatting.WHITE);
    public static final Character JUGGERNAUT = new Character("juggernaut", "lawless", ChatFormatting.GOLD);

    public static final Character[] ALL = {ALEXIA, IRIS, CLAIRE, GAMMA, ROSE, RAPHAEL, MARY, YUKIME, JUGGERNAUT};

    public static Character byId(String id) {
        for (Character c : ALL) if (c.id().equals(id)) return c;
        return ALEXIA;
    }
}
