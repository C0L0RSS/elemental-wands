package com.anton.elementalwands.client.blockink;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;

/**
 * Draws one kind of the First Wizard's effect entities, every frame, from the entity's synced clocks alone: nothing
 * drawn decides a hit. Register one per kind in {@link InkDrawers}. A drawer starts in its element's colour and cools
 * to purple, then black ink ({@link InkKit#cool}); it keeps big shapes low or see-through so the copy and the players
 * stay visible.
 */
@FunctionalInterface
public interface InkDrawer {
    void draw(InkFrame frame, FirstWizardEffectEntity effect);
}
