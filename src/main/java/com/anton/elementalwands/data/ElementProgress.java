package com.anton.elementalwands.data;

import java.util.List;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Permanent ownership, currency and XP for one element. Old saves start at level one. */
public record ElementProgress(long flux, List<String> spells, double xp) {
    public static final ElementProgress EMPTY = new ElementProgress(0, List.of(), 0);
    public ElementProgress(long flux, List<String> spells) { this(flux, spells, 0); }
    public static final Codec<ElementProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("flux").forGetter(ElementProgress::flux),
            Codec.STRING.listOf().fieldOf("spells").forGetter(ElementProgress::spells),
            Codec.DOUBLE.optionalFieldOf("xp", 0.0).forGetter(ElementProgress::xp)
    ).apply(i, ElementProgress::new));
    public ElementProgress { flux=Math.max(0,flux); spells=List.copyOf(spells); xp=ElementLevels.clamp(xp); }
}
