package com.anton.elementalwands.client.blockink;

import com.anton.elementalwands.entity.firstwizard.spell.BlinkRiftSpell;
import com.anton.elementalwands.entity.firstwizard.spell.FireballSpell;
import java.util.Map;

/**
 * Which drawer draws each kind of the First Wizard's effect entities: one line per kind. A spell's server class names
 * its kinds as constants; add its drawers here. A kind with no drawer is not drawn.
 */
public final class InkDrawers {
    private InkDrawers() {}

    static final Map<String, InkDrawer> BY_KIND = Map.ofEntries(
            Map.entry(FireballSpell.KIND, FireballFx::ball),
            Map.entry(FireballSpell.GROUND_KIND, FireballFx::ground),
            Map.entry(com.anton.elementalwands.entity.firstwizard.spell.MeteorSpell.KIND, MeteorFx::ball),
            Map.entry(com.anton.elementalwands.entity.firstwizard.spell.MeteorSpell.MARK_KIND, MeteorFx::mark),
            Map.entry(com.anton.elementalwands.entity.firstwizard.spell.MeteorSpell.GROUND_KIND, FireballFx::ground),
            Map.entry(com.anton.elementalwands.entity.firstwizard.spell.GravityWellSpell.KIND, GravityWellFx::orb),
            Map.entry(com.anton.elementalwands.entity.firstwizard.spell.GravityWellSpell.WELL_KIND, GravityWellFx::well),
            Map.entry(com.anton.elementalwands.entity.firstwizard.spell.SkyShearSpell.FORM_KIND, SkyShearFx::form),
            Map.entry(com.anton.elementalwands.entity.firstwizard.spell.SkyShearSpell.KIND, SkyShearFx::flight),
            Map.entry(com.anton.elementalwands.entity.firstwizard.spell.WaylayDashSpell.KIND, WaylayDashFx::draw),
            Map.entry(com.anton.elementalwands.entity.firstwizard.spell.StoneWallSpell.KIND, StoneFx::wall),
            Map.entry(com.anton.elementalwands.entity.firstwizard.spell.FaultlineSpell.KIND, StoneFx::faultline),
            Map.entry(com.anton.elementalwands.entity.firstwizard.spell.ThornbiteSpell.KIND, ThornbiteFx::draw),
            Map.entry(com.anton.elementalwands.entity.firstwizard.spell.SentryBloomSpell.BUD_KIND, SentryBloomFx::bud),
            Map.entry(com.anton.elementalwands.entity.firstwizard.spell.SentryBloomSpell.GROUND_KIND, SentryBloomFx::ground),
            Map.entry(com.anton.elementalwands.entity.firstwizard.spell.SentryBloomSpell.SEED_KIND, SentryBloomFx::seed),
            Map.entry(BlinkRiftSpell.POOL_KIND, BlinkRiftFx::pool),
            Map.entry(BlinkRiftSpell.DEPART_KIND, BlinkRiftFx::depart),
            // Phase two: the change's ring, the tendrils' marks, the flame, the finisher.
            Map.entry(com.anton.elementalwands.entity.firstwizard.spell.PhaseChange.RING_KIND, InkMovesFx::phaseRing),
            Map.entry(com.anton.elementalwands.entity.firstwizard.spell.SweepSpell.KIND, InkMovesFx::sweep),
            Map.entry(com.anton.elementalwands.entity.firstwizard.spell.SlamSpell.KIND, InkMovesFx::slam),
            Map.entry(com.anton.elementalwands.entity.firstwizard.spell.LeapSpell.KIND, InkMovesFx::leap),
            Map.entry(com.anton.elementalwands.entity.firstwizard.spell.FlameSpell.KIND, InkMovesFx::flame),
            Map.entry(com.anton.elementalwands.entity.firstwizard.spell.HollowPurpleSpell.KIND, HollowPurpleFx::orb),
            Map.entry(com.anton.elementalwands.entity.firstwizard.spell.HollowPurpleSpell.LANE_KIND, HollowPurpleFx::lane));

    static InkDrawer get(String kind) { return BY_KIND.get(kind); }
}
