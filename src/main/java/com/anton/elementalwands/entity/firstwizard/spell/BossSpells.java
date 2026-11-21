package com.anton.elementalwands.entity.firstwizard.spell;

import com.anton.elementalwands.entity.firstwizard.FirstWizardRules.Move;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every implemented spell of the First Wizard, one line each. A move in the table without a line here is unavailable:
 * the chooser never picks it and {@code /ew wizard move} says it is not built. Nothing here may touch a registry when
 * the class loads; the contract check reads this list without starting Minecraft.
 */
public final class BossSpells {
    private BossSpells() {}

    public static final List<BossSpell> ALL = List.of(
            new FireballSpell(),
            new MeteorSpell(),
            new GravityWellSpell(),
            new SkyShearSpell(),
            new WaylayDashSpell(),
            new StoneWallSpell(),
            new FaultlineSpell(),
            new ThornbiteSpell(),
            new SentryBloomSpell(),
            new BlinkRiftSpell(),
            // Phase two's ink moves.
            new SlamSpell(),
            new SweepSpell(),
            new LeapSpell(),
            new FlameSpell(),
            new HollowPurpleSpell());

    private static final Map<Move, BossSpell> BY_MOVE = new EnumMap<>(Move.class);
    private static final Set<Move> AVAILABLE;

    static {
        for (BossSpell spell : ALL)
            if (BY_MOVE.put(spell.move(), spell) != null) throw new IllegalStateException("Two spells for " + spell.move());
        AVAILABLE = Collections.unmodifiableSet(BY_MOVE.isEmpty() ? EnumSet.noneOf(Move.class) : EnumSet.copyOf(BY_MOVE.keySet()));
    }

    /** The spell for a move, or null when it is not built. */
    public static BossSpell get(Move move) { return BY_MOVE.get(move); }

    /** The moves that have a spell. */
    public static Set<Move> available() { return AVAILABLE; }
}
