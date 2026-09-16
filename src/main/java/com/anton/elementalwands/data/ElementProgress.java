package com.anton.elementalwands.data;

import java.util.List;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Permanent ownership and currency for one element, saved with the player. */
public record ElementProgress(long flux, List<String> spells) {
    public static final ElementProgress EMPTY = new ElementProgress(0, List.of());
    public static final Codec<ElementProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("flux").forGetter(ElementProgress::flux),
            Codec.STRING.listOf().fieldOf("spells").forGetter(ElementProgress::spells)
    ).apply(i, ElementProgress::new));
    public ElementProgress { flux = Math.max(0, flux); spells = List.copyOf(spells); }
}
