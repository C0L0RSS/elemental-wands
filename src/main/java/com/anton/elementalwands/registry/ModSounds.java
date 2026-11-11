package com.anton.elementalwands.registry;

import com.anton.elementalwands.ElementalWandsMod;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Every sound the mod plays, named for the moment it belongs to rather than
 * for the audio behind it. {@code assets/elementalwands/sounds.json} decides
 * what each event actually plays, so audio can be replaced without touching
 * gameplay code. Do not play vanilla {@code SoundEvents} directly; add an
 * event here and map it in {@code sounds.json}.
 *
 * <p>Ids follow {@code spell.<element>.<spell>.<moment>},
 * {@code entity.<mob>.<action>.<moment>}, {@code cinematic.<scene>.<cue>} and
 * {@code world.<place>.<moment>}. See docs/reference/sound.md.
 */
public final class ModSounds {

    // spell.fire
    public static final SoundEvent SPELL_FIRE_DRAGONS_PYRE_CAST = register("spell.fire.dragons_pyre.cast");
    public static final SoundEvent SPELL_FIRE_FIRE_LEAP_LAND = register("spell.fire.fire_leap.land");
    public static final SoundEvent SPELL_FIRE_FIRE_LEAP_LAUNCH = register("spell.fire.fire_leap.launch");
    public static final SoundEvent SPELL_FIRE_FLAMETHROWER_LOOP = register("spell.fire.flamethrower.loop");
    public static final SoundEvent SPELL_FIRE_FLAMETHROWER_OVERHEAT = register("spell.fire.flamethrower.overheat");
    public static final SoundEvent SPELL_FIRE_FLASHOVER_BLAST = register("spell.fire.flashover.blast");
    public static final SoundEvent SPELL_FIRE_FLASHOVER_CAST = register("spell.fire.flashover.cast");
    public static final SoundEvent SPELL_FIRE_INFERNO_WAVE_CAST = register("spell.fire.inferno_wave.cast");
    public static final SoundEvent SPELL_FIRE_INFERNO_WAVE_HIT = register("spell.fire.inferno_wave.hit");
    public static final SoundEvent SPELL_FIRE_METEOR_CAST = register("spell.fire.meteor.cast");
    public static final SoundEvent SPELL_FIRE_METEOR_IMPACT_BLAST = register("spell.fire.meteor.impact_blast");
    public static final SoundEvent SPELL_FIRE_METEOR_IMPACT_BOOM = register("spell.fire.meteor.impact_boom");

    // spell.nature
    public static final SoundEvent SPELL_NATURE_OVERGROWTH_AWAKEN_ROAR = register("spell.nature.overgrowth.awaken_roar");
    public static final SoundEvent SPELL_NATURE_OVERGROWTH_AWAKEN_SPROUT = register("spell.nature.overgrowth.awaken_sprout");
    public static final SoundEvent SPELL_NATURE_OVERGROWTH_BREAK = register("spell.nature.overgrowth.break");
    public static final SoundEvent SPELL_NATURE_OVERGROWTH_CAST = register("spell.nature.overgrowth.cast");
    public static final SoundEvent SPELL_NATURE_OVERGROWTH_EMPOWER = register("spell.nature.overgrowth.empower");
    public static final SoundEvent SPELL_NATURE_OVERGROWTH_END = register("spell.nature.overgrowth.end");
    public static final SoundEvent SPELL_NATURE_OVERGROWTH_GROW_CANOPY = register("spell.nature.overgrowth.grow_canopy");
    public static final SoundEvent SPELL_NATURE_OVERGROWTH_GROW_STAGE = register("spell.nature.overgrowth.grow_stage");
    public static final SoundEvent SPELL_NATURE_OVERGROWTH_HURT = register("spell.nature.overgrowth.hurt");
    public static final SoundEvent SPELL_NATURE_SEED_CAST = register("spell.nature.seed.cast");
    public static final SoundEvent SPELL_NATURE_SEED_END = register("spell.nature.seed.end");
    public static final SoundEvent SPELL_NATURE_SEED_FAIL = register("spell.nature.seed.fail");
    public static final SoundEvent SPELL_NATURE_SEED_GROW = register("spell.nature.seed.grow");
    public static final SoundEvent SPELL_NATURE_SEED_HARVEST = register("spell.nature.seed.harvest");
    public static final SoundEvent SPELL_NATURE_SEED_PLANT = register("spell.nature.seed.plant");
    public static final SoundEvent SPELL_NATURE_SPRINGBLOOM_CAST = register("spell.nature.springbloom.cast");
    public static final SoundEvent SPELL_NATURE_SPRINGBLOOM_LAND = register("spell.nature.springbloom.land");
    public static final SoundEvent SPELL_NATURE_SPRINGBLOOM_LAUNCH = register("spell.nature.springbloom.launch");
    public static final SoundEvent SPELL_NATURE_TENDRIL_BLOOM_BLOOM = register("spell.nature.tendril_bloom.bloom");
    public static final SoundEvent SPELL_NATURE_TENDRIL_BLOOM_CAST = register("spell.nature.tendril_bloom.cast");
    public static final SoundEvent SPELL_NATURE_TENDRIL_BLOOM_GROW = register("spell.nature.tendril_bloom.grow");
    public static final SoundEvent SPELL_NATURE_TENDRIL_BLOOM_KNOT_END = register("spell.nature.tendril_bloom.knot_end");
    public static final SoundEvent SPELL_NATURE_TENDRIL_BLOOM_LAUNCH = register("spell.nature.tendril_bloom.launch");
    public static final SoundEvent SPELL_NATURE_THORNBITE_CAST_RUSTLE = register("spell.nature.thornbite.cast_rustle");
    public static final SoundEvent SPELL_NATURE_THORNBITE_CAST_WHIP = register("spell.nature.thornbite.cast_whip");
    public static final SoundEvent SPELL_NATURE_THORNBITE_HIT = register("spell.nature.thornbite.hit");

    // spell.space
    public static final SoundEvent SPELL_SPACE_ASTRAL_DOUBLE_CAST = register("spell.space.astral_double.cast");
    public static final SoundEvent SPELL_SPACE_ASTRAL_DOUBLE_CHIME = register("spell.space.astral_double.chime");
    public static final SoundEvent SPELL_SPACE_ASTRAL_DOUBLE_TELEPORT = register("spell.space.astral_double.teleport");
    public static final SoundEvent SPELL_SPACE_BLINK_RIFT_CAST = register("spell.space.blink_rift.cast");
    public static final SoundEvent SPELL_SPACE_BLINK_RIFT_FAIL = register("spell.space.blink_rift.fail");
    public static final SoundEvent SPELL_SPACE_GRAVITY_WELL_CAST = register("spell.space.gravity_well.cast");
    public static final SoundEvent SPELL_SPACE_GRAVITY_WELL_COLLAPSE_BURST = register("spell.space.gravity_well.collapse_burst");
    public static final SoundEvent SPELL_SPACE_GRAVITY_WELL_COLLAPSE_CHARGE = register("spell.space.gravity_well.collapse_charge");
    public static final SoundEvent SPELL_SPACE_GRAVITY_WELL_LAND_CRACK = register("spell.space.gravity_well.land_crack");
    public static final SoundEvent SPELL_SPACE_GRAVITY_WELL_LAND_HUM = register("spell.space.gravity_well.land_hum");
    public static final SoundEvent SPELL_SPACE_HOLLOW_PURPLE_CHARGE = register("spell.space.hollow_purple.charge");
    public static final SoundEvent SPELL_SPACE_HOLLOW_PURPLE_CHARGE_CHIME = register("spell.space.hollow_purple.charge_chime");
    public static final SoundEvent SPELL_SPACE_HOLLOW_PURPLE_CHARGE_PEAK = register("spell.space.hollow_purple.charge_peak");
    public static final SoundEvent SPELL_SPACE_HOLLOW_PURPLE_COLLAPSE_BOOM = register("spell.space.hollow_purple.collapse_boom");
    public static final SoundEvent SPELL_SPACE_HOLLOW_PURPLE_COLLAPSE_IMPLODE = register("spell.space.hollow_purple.collapse_implode");
    public static final SoundEvent SPELL_SPACE_HOLLOW_PURPLE_LAUNCH_BOOM = register("spell.space.hollow_purple.launch_boom");
    public static final SoundEvent SPELL_SPACE_HOLLOW_PURPLE_LAUNCH_RELEASE = register("spell.space.hollow_purple.launch_release");
    public static final SoundEvent SPELL_SPACE_HOLLOW_PURPLE_LOOP = register("spell.space.hollow_purple.loop");
    public static final SoundEvent SPELL_SPACE_SINGULARITY_BOLT_CAST_HUM = register("spell.space.singularity_bolt.cast_hum");
    public static final SoundEvent SPELL_SPACE_SINGULARITY_BOLT_CAST_SPARK = register("spell.space.singularity_bolt.cast_spark");
    public static final SoundEvent SPELL_SPACE_SINGULARITY_BOLT_IMPACT_BURST = register("spell.space.singularity_bolt.impact_burst");
    public static final SoundEvent SPELL_SPACE_SINGULARITY_BOLT_IMPACT_POP = register("spell.space.singularity_bolt.impact_pop");

    // spell.stone
    public static final SoundEvent SPELL_STONE_FAULTLINE_CAST = register("spell.stone.faultline.cast");
    public static final SoundEvent SPELL_STONE_FAULTLINE_ERUPT = register("spell.stone.faultline.erupt");
    public static final SoundEvent SPELL_STONE_GATHERED_MASS_GATHER = register("spell.stone.gathered_mass.gather");
    public static final SoundEvent SPELL_STONE_GATHERED_MASS_IMPACT = register("spell.stone.gathered_mass.impact");
    public static final SoundEvent SPELL_STONE_GATHERED_MASS_LAUNCH = register("spell.stone.gathered_mass.launch");
    public static final SoundEvent SPELL_STONE_JUGGERNAUT_CAST = register("spell.stone.juggernaut.cast");
    public static final SoundEvent SPELL_STONE_JUGGERNAUT_FULL_SPEED = register("spell.stone.juggernaut.full_speed");
    public static final SoundEvent SPELL_STONE_JUGGERNAUT_IMPACT = register("spell.stone.juggernaut.impact");
    public static final SoundEvent SPELL_STONE_JUGGERNAUT_STEP = register("spell.stone.juggernaut.step");
    public static final SoundEvent SPELL_STONE_STONE_WALL_BREAK_BY_GUARDIAN = register("spell.stone.stone_wall.break_by_guardian");
    public static final SoundEvent SPELL_STONE_STONE_WALL_CAST = register("spell.stone.stone_wall.cast");
    public static final SoundEvent SPELL_STONE_STONE_WALL_SETTLE = register("spell.stone.stone_wall.settle");
    public static final SoundEvent SPELL_STONE_STONE_WALL_SHATTER = register("spell.stone.stone_wall.shatter");
    public static final SoundEvent SPELL_STONE_STONE_WALL_STRESS = register("spell.stone.stone_wall.stress");
    public static final SoundEvent SPELL_STONE_TITAN_DOME_CAST = register("spell.stone.titan_dome.cast");
    public static final SoundEvent SPELL_STONE_TITAN_DOME_COLLAPSE_BOOM = register("spell.stone.titan_dome.collapse_boom");
    public static final SoundEvent SPELL_STONE_TITAN_DOME_COLLAPSE_CRACK = register("spell.stone.titan_dome.collapse_crack");
    public static final SoundEvent SPELL_STONE_TITAN_DOME_FORM = register("spell.stone.titan_dome.form");
    public static final SoundEvent SPELL_STONE_TITAN_DOME_FORMED = register("spell.stone.titan_dome.formed");
    public static final SoundEvent SPELL_STONE_TITAN_DOME_REPAIR = register("spell.stone.titan_dome.repair");

    // spell.wind
    public static final SoundEvent SPELL_WIND_GALE_DAGGERS_LAUNCH = register("spell.wind.gale_daggers.launch");
    public static final SoundEvent SPELL_WIND_GALE_DAGGERS_PREPARE = register("spell.wind.gale_daggers.prepare");
    public static final SoundEvent SPELL_WIND_SKY_SHEAR_CAST = register("spell.wind.sky_shear.cast");
    public static final SoundEvent SPELL_WIND_SKY_SHEAR_HIT = register("spell.wind.sky_shear.hit");
    public static final SoundEvent SPELL_WIND_UPDRAFT_CAST = register("spell.wind.updraft.cast");
    public static final SoundEvent SPELL_WIND_WAYLAY_DASH_CAST = register("spell.wind.waylay_dash.cast");
    public static final SoundEvent SPELL_WIND_ZEPHYR_STRIKE_CAST = register("spell.wind.zephyr_strike.cast");
    public static final SoundEvent SPELL_WIND_ZEPHYR_STRIKE_LAND = register("spell.wind.zephyr_strike.land");

    // entity.guardian
    public static final SoundEvent ENTITY_GUARDIAN_ATTACK_WINDUP = register("entity.guardian.attack.windup");
    public static final SoundEvent ENTITY_GUARDIAN_BEAM_CHARGE = register("entity.guardian.beam.charge");
    public static final SoundEvent ENTITY_GUARDIAN_BEAM_LAUNCH = register("entity.guardian.beam.launch");
    public static final SoundEvent ENTITY_GUARDIAN_FAN_CHARGE = register("entity.guardian.fan.charge");
    public static final SoundEvent ENTITY_GUARDIAN_FAN_LAUNCH = register("entity.guardian.fan.launch");
    public static final SoundEvent ENTITY_GUARDIAN_GROUND_IMPACT = register("entity.guardian.ground_impact");
    public static final SoundEvent ENTITY_GUARDIAN_LEAP_LAND_BOOM = register("entity.guardian.leap.land_boom");
    public static final SoundEvent ENTITY_GUARDIAN_LEAP_LAUNCH = register("entity.guardian.leap.launch");
    public static final SoundEvent ENTITY_GUARDIAN_LEAP_WINDUP = register("entity.guardian.leap.windup");
    public static final SoundEvent ENTITY_GUARDIAN_PULSE_BLAST_BOOM = register("entity.guardian.pulse.blast_boom");
    public static final SoundEvent ENTITY_GUARDIAN_PULSE_BLAST_SONIC = register("entity.guardian.pulse.blast_sonic");
    public static final SoundEvent ENTITY_GUARDIAN_PULSE_CHARGE = register("entity.guardian.pulse.charge");
    public static final SoundEvent ENTITY_GUARDIAN_PULSE_LOOP = register("entity.guardian.pulse.loop");
    public static final SoundEvent ENTITY_GUARDIAN_REHEARSAL_IMPACT = register("entity.guardian.rehearsal.impact");
    public static final SoundEvent ENTITY_GUARDIAN_ROCK_IMPACT = register("entity.guardian.rock.impact");
    public static final SoundEvent ENTITY_GUARDIAN_SHELL_CRACK = register("entity.guardian.shell.crack");
    public static final SoundEvent ENTITY_GUARDIAN_SHELL_BREAK_BURST_BOOM = register("entity.guardian.shell_break.burst_boom");
    public static final SoundEvent ENTITY_GUARDIAN_SHELL_BREAK_BURST_CRACK = register("entity.guardian.shell_break.burst_crack");
    public static final SoundEvent ENTITY_GUARDIAN_SHELL_BREAK_LOOP = register("entity.guardian.shell_break.loop");
    public static final SoundEvent ENTITY_GUARDIAN_SHELL_BREAK_WINDUP = register("entity.guardian.shell_break.windup");
    public static final SoundEvent ENTITY_GUARDIAN_STEP = register("entity.guardian.step");
    public static final SoundEvent ENTITY_GUARDIAN_THROW_LAUNCH = register("entity.guardian.throw.launch");
    public static final SoundEvent ENTITY_GUARDIAN_VOICE_ROAR = register("entity.guardian.voice.roar");
    public static final SoundEvent ENTITY_GUARDIAN_VOLLEY_CHARGE = register("entity.guardian.volley.charge");
    public static final SoundEvent ENTITY_GUARDIAN_VOLLEY_LAUNCH = register("entity.guardian.volley.launch");

    // entity.hollow_archer
    public static final SoundEvent ENTITY_HOLLOW_ARCHER_AMBIENT = register("entity.hollow_archer.ambient");
    public static final SoundEvent ENTITY_HOLLOW_ARCHER_DEATH = register("entity.hollow_archer.death");
    public static final SoundEvent ENTITY_HOLLOW_ARCHER_HURT = register("entity.hollow_archer.hurt");
    public static final SoundEvent ENTITY_HOLLOW_ARCHER_SHOT_LAUNCH = register("entity.hollow_archer.shot.launch");
    public static final SoundEvent ENTITY_HOLLOW_ARCHER_SHOT_WINDUP = register("entity.hollow_archer.shot.windup");
    public static final SoundEvent ENTITY_HOLLOW_ARCHER_STEP = register("entity.hollow_archer.step");

    // entity.hollow_brute
    public static final SoundEvent ENTITY_HOLLOW_BRUTE_AMBIENT = register("entity.hollow_brute.ambient");
    public static final SoundEvent ENTITY_HOLLOW_BRUTE_DEATH = register("entity.hollow_brute.death");
    public static final SoundEvent ENTITY_HOLLOW_BRUTE_HURT = register("entity.hollow_brute.hurt");
    public static final SoundEvent ENTITY_HOLLOW_BRUTE_SLAM_IMPACT = register("entity.hollow_brute.slam.impact");
    public static final SoundEvent ENTITY_HOLLOW_BRUTE_SLAM_WINDUP = register("entity.hollow_brute.slam.windup");
    public static final SoundEvent ENTITY_HOLLOW_BRUTE_STEP = register("entity.hollow_brute.step");

    // entity.hollow_crawler
    public static final SoundEvent ENTITY_HOLLOW_CRAWLER_AMBIENT = register("entity.hollow_crawler.ambient");
    public static final SoundEvent ENTITY_HOLLOW_CRAWLER_DEATH = register("entity.hollow_crawler.death");
    public static final SoundEvent ENTITY_HOLLOW_CRAWLER_HURT = register("entity.hollow_crawler.hurt");
    public static final SoundEvent ENTITY_HOLLOW_CRAWLER_RAKE_WINDUP = register("entity.hollow_crawler.rake.windup");
    public static final SoundEvent ENTITY_HOLLOW_CRAWLER_STEP = register("entity.hollow_crawler.step");

    // entity.hollow_undead
    public static final SoundEvent ENTITY_HOLLOW_UNDEAD_RISE_SOIL = register("entity.hollow_undead.rise.soil");
    public static final SoundEvent ENTITY_HOLLOW_UNDEAD_RISE_SOUL = register("entity.hollow_undead.rise.soul");

    // entity.necromancer
    public static final SoundEvent ENTITY_NECROMANCER_AMBUSH_IMPACT = register("entity.necromancer.ambush.impact");
    public static final SoundEvent ENTITY_NECROMANCER_AMBUSH_WARN = register("entity.necromancer.ambush.warn");
    public static final SoundEvent ENTITY_NECROMANCER_AMBUSH_WINDUP = register("entity.necromancer.ambush.windup");
    public static final SoundEvent ENTITY_NECROMANCER_BLINK_WARN = register("entity.necromancer.blink.warn");
    public static final SoundEvent ENTITY_NECROMANCER_COLLAPSE_IMPACT = register("entity.necromancer.collapse.impact");
    public static final SoundEvent ENTITY_NECROMANCER_COLLAPSE_SOULS = register("entity.necromancer.collapse.souls");
    public static final SoundEvent ENTITY_NECROMANCER_DEFLECT_HIT = register("entity.necromancer.deflect.hit");
    public static final SoundEvent ENTITY_NECROMANCER_DIVE_IMPACT_BOOM = register("entity.necromancer.dive.impact_boom");
    public static final SoundEvent ENTITY_NECROMANCER_DIVE_IMPACT_EMERGE = register("entity.necromancer.dive.impact_emerge");
    public static final SoundEvent ENTITY_NECROMANCER_DIVE_SINK = register("entity.necromancer.dive.sink");
    public static final SoundEvent ENTITY_NECROMANCER_DIVE_TUNNEL_LOOP = register("entity.necromancer.dive.tunnel_loop");
    public static final SoundEvent ENTITY_NECROMANCER_DIVE_WARN = register("entity.necromancer.dive.warn");
    public static final SoundEvent ENTITY_NECROMANCER_DIVE_WARN_LOOP = register("entity.necromancer.dive.warn_loop");
    public static final SoundEvent ENTITY_NECROMANCER_DRAIN_END = register("entity.necromancer.drain.end");
    public static final SoundEvent ENTITY_NECROMANCER_DRAIN_WINDUP = register("entity.necromancer.drain.windup");
    public static final SoundEvent ENTITY_NECROMANCER_DRAIN_WINDUP_INHALE = register("entity.necromancer.drain.windup_inhale");
    public static final SoundEvent ENTITY_NECROMANCER_ENGAGE_CALL = register("entity.necromancer.engage.call");
    public static final SoundEvent ENTITY_NECROMANCER_FREED_SOUL_BLINK_ARRIVE = register("entity.necromancer.freed_soul.blink_arrive");
    public static final SoundEvent ENTITY_NECROMANCER_FREED_SOUL_BLINK_WARN = register("entity.necromancer.freed_soul.blink_warn");
    public static final SoundEvent ENTITY_NECROMANCER_FREED_SOUL_BOLT_WINDUP = register("entity.necromancer.freed_soul.bolt_windup");
    public static final SoundEvent ENTITY_NECROMANCER_FREED_SOUL_HANDS_WINDUP = register("entity.necromancer.freed_soul.hands_windup");
    public static final SoundEvent ENTITY_NECROMANCER_FREED_SOUL_KNOCKDOWN = register("entity.necromancer.freed_soul.knockdown");
    public static final SoundEvent ENTITY_NECROMANCER_GRAB_HIT = register("entity.necromancer.grab.hit");
    public static final SoundEvent ENTITY_NECROMANCER_GRAB_IMPACT = register("entity.necromancer.grab.impact");
    public static final SoundEvent ENTITY_NECROMANCER_GRAB_MISS = register("entity.necromancer.grab.miss");
    public static final SoundEvent ENTITY_NECROMANCER_HANDS_CLAW_IMPACT = register("entity.necromancer.hands.claw_impact");
    public static final SoundEvent ENTITY_NECROMANCER_HANDS_IMPACT = register("entity.necromancer.hands.impact");
    public static final SoundEvent ENTITY_NECROMANCER_HANDS_WINDUP = register("entity.necromancer.hands.windup");
    public static final SoundEvent ENTITY_NECROMANCER_HARVEST_ABSORB = register("entity.necromancer.harvest.absorb");
    public static final SoundEvent ENTITY_NECROMANCER_HARVEST_RAISE = register("entity.necromancer.harvest.raise");
    public static final SoundEvent ENTITY_NECROMANCER_HARVEST_SCREAM_ROAR = register("entity.necromancer.harvest.scream_roar");
    public static final SoundEvent ENTITY_NECROMANCER_HARVEST_SCREAM_SHRIEK = register("entity.necromancer.harvest.scream_shriek");
    public static final SoundEvent ENTITY_NECROMANCER_HARVEST_SCREAM_WAIL = register("entity.necromancer.harvest.scream_wail");
    public static final SoundEvent ENTITY_NECROMANCER_HARVEST_SOUL_DESTROYED = register("entity.necromancer.harvest.soul_destroyed");
    public static final SoundEvent ENTITY_NECROMANCER_HARVEST_SPOT_WARN = register("entity.necromancer.harvest.spot_warn");
    public static final SoundEvent ENTITY_NECROMANCER_RAIN_IMPACT_BOOM = register("entity.necromancer.rain.impact_boom");
    public static final SoundEvent ENTITY_NECROMANCER_RAIN_IMPACT_SOULS = register("entity.necromancer.rain.impact_souls");
    public static final SoundEvent ENTITY_NECROMANCER_RAIN_LAUNCH = register("entity.necromancer.rain.launch");
    public static final SoundEvent ENTITY_NECROMANCER_RUSH_BITE = register("entity.necromancer.rush.bite");
    public static final SoundEvent ENTITY_NECROMANCER_RUSH_LAUNCH = register("entity.necromancer.rush.launch");
    public static final SoundEvent ENTITY_NECROMANCER_RUSH_STEP = register("entity.necromancer.rush.step");
    public static final SoundEvent ENTITY_NECROMANCER_RUSH_THROW = register("entity.necromancer.rush.throw");
    public static final SoundEvent ENTITY_NECROMANCER_RUSH_WINDUP_CLAWS = register("entity.necromancer.rush.windup_claws");
    public static final SoundEvent ENTITY_NECROMANCER_SHIFT_WARN = register("entity.necromancer.shift.warn");
    public static final SoundEvent ENTITY_NECROMANCER_SIEGE_BEGIN_CALL = register("entity.necromancer.siege.begin_call");
    public static final SoundEvent ENTITY_NECROMANCER_SIEGE_CRASH_IMPACT = register("entity.necromancer.siege.crash_impact");
    public static final SoundEvent ENTITY_NECROMANCER_SIEGE_CRASH_SOULS = register("entity.necromancer.siege.crash_souls");
    public static final SoundEvent ENTITY_NECROMANCER_SIEGE_LANDING_WARN = register("entity.necromancer.siege.landing_warn");
    public static final SoundEvent ENTITY_NECROMANCER_SIEGE_PERCH_WARN = register("entity.necromancer.siege.perch_warn");
    public static final SoundEvent ENTITY_NECROMANCER_SIEGE_WAVE_CALL = register("entity.necromancer.siege.wave_call");
    public static final SoundEvent ENTITY_NECROMANCER_SOUL_BOLT_DOUBLE_MARKED = register("entity.necromancer.soul_bolt.double_marked");
    public static final SoundEvent ENTITY_NECROMANCER_SOUL_BOLT_IMPACT = register("entity.necromancer.soul_bolt.impact");
    public static final SoundEvent ENTITY_NECROMANCER_SOUL_BOLT_LAUNCH = register("entity.necromancer.soul_bolt.launch");
    public static final SoundEvent ENTITY_NECROMANCER_SOUL_BOLT_WINDUP = register("entity.necromancer.soul_bolt.windup");
    public static final SoundEvent ENTITY_NECROMANCER_SOUL_BOLT_WINDUP_KINDLE = register("entity.necromancer.soul_bolt.windup_kindle");
    public static final SoundEvent ENTITY_NECROMANCER_SPLIT_CHARGE = register("entity.necromancer.split.charge");
    public static final SoundEvent ENTITY_NECROMANCER_SPLIT_RELEASE_SHRIEK = register("entity.necromancer.split.release_shriek");
    public static final SoundEvent ENTITY_NECROMANCER_SPLIT_RELEASE_SOULS = register("entity.necromancer.split.release_souls");
    public static final SoundEvent ENTITY_NECROMANCER_STAGGER_HIT = register("entity.necromancer.stagger.hit");
    public static final SoundEvent ENTITY_NECROMANCER_STEP = register("entity.necromancer.step");
    public static final SoundEvent ENTITY_NECROMANCER_SWIPE_SWING = register("entity.necromancer.swipe.swing");
    public static final SoundEvent ENTITY_NECROMANCER_TELEPORT_ARRIVE = register("entity.necromancer.teleport.arrive");
    public static final SoundEvent ENTITY_NECROMANCER_TELEPORT_DEPART = register("entity.necromancer.teleport.depart");
    public static final SoundEvent ENTITY_NECROMANCER_VOICE_GROWL = register("entity.necromancer.voice.growl");
    public static final SoundEvent ENTITY_NECROMANCER_VOICE_RATTLE = register("entity.necromancer.voice.rattle");
    public static final SoundEvent ENTITY_NECROMANCER_VOICE_ROAR = register("entity.necromancer.voice.roar");
    public static final SoundEvent ENTITY_NECROMANCER_VOICE_SNARL = register("entity.necromancer.voice.snarl");

    // entity.storm_roc
    public static final SoundEvent ENTITY_STORM_ROC_BITE_IMPACT_BOOM = register("entity.storm_roc.bite.impact_boom");
    public static final SoundEvent ENTITY_STORM_ROC_BITE_IMPACT_CRACK = register("entity.storm_roc.bite.impact_crack");
    public static final SoundEvent ENTITY_STORM_ROC_CHARGE_BOLT = register("entity.storm_roc.charge.bolt");
    public static final SoundEvent ENTITY_STORM_ROC_DIVE_IMPACT = register("entity.storm_roc.dive.impact");
    public static final SoundEvent ENTITY_STORM_ROC_LIGHTNING_IMPACT = register("entity.storm_roc.lightning.impact");
    public static final SoundEvent ENTITY_STORM_ROC_LIGHTNING_WARN = register("entity.storm_roc.lightning.warn");
    public static final SoundEvent ENTITY_STORM_ROC_POUNCE_IMPACT = register("entity.storm_roc.pounce.impact");
    public static final SoundEvent ENTITY_STORM_ROC_SMASH_IMPACT = register("entity.storm_roc.smash.impact");
    public static final SoundEvent ENTITY_STORM_ROC_SPIRE_CAW_HIT = register("entity.storm_roc.spire_caw.hit");
    public static final SoundEvent ENTITY_STORM_ROC_STAMPEDE_SKID = register("entity.storm_roc.stampede.skid");
    public static final SoundEvent ENTITY_STORM_ROC_STAMPEDE_STEP = register("entity.storm_roc.stampede.step");
    public static final SoundEvent ENTITY_STORM_ROC_STAMPEDE_WINDUP = register("entity.storm_roc.stampede.windup");
    public static final SoundEvent ENTITY_STORM_ROC_STORM_THUNDER = register("entity.storm_roc.storm.thunder");
    public static final SoundEvent ENTITY_STORM_ROC_THUNDERCLAP_IMPACT = register("entity.storm_roc.thunderclap.impact");
    public static final SoundEvent ENTITY_STORM_ROC_TORNADO_CATCH = register("entity.storm_roc.tornado.catch");
    public static final SoundEvent ENTITY_STORM_ROC_TORNADO_FLING = register("entity.storm_roc.tornado.fling");
    public static final SoundEvent ENTITY_STORM_ROC_TORNADO_LAND = register("entity.storm_roc.tornado.land");
    public static final SoundEvent ENTITY_STORM_ROC_TORNADO_LOOP = register("entity.storm_roc.tornado.loop");
    public static final SoundEvent ENTITY_STORM_ROC_TORNADO_WARN = register("entity.storm_roc.tornado.warn");
    public static final SoundEvent ENTITY_STORM_ROC_VOICE_CAW = register("entity.storm_roc.voice.caw");
    public static final SoundEvent ENTITY_STORM_ROC_VOICE_DEATH = register("entity.storm_roc.voice.death");
    public static final SoundEvent ENTITY_STORM_ROC_VOICE_INHALE = register("entity.storm_roc.voice.inhale");
    public static final SoundEvent ENTITY_STORM_ROC_VOICE_SCREAM = register("entity.storm_roc.voice.scream");
    public static final SoundEvent ENTITY_STORM_ROC_VOICE_SNORT = register("entity.storm_roc.voice.snort");
    public static final SoundEvent ENTITY_STORM_ROC_WING_FLAP = register("entity.storm_roc.wing.flap");
    public static final SoundEvent ENTITY_STORM_ROC_WING_SWOOP = register("entity.storm_roc.wing.swoop");

    // cinematic.guardian_intro
    public static final SoundEvent CINEMATIC_GUARDIAN_INTRO_FALL_GUST = register("cinematic.guardian_intro.fall_gust");
    public static final SoundEvent CINEMATIC_GUARDIAN_INTRO_FALL_ROAR = register("cinematic.guardian_intro.fall_roar");
    public static final SoundEvent CINEMATIC_GUARDIAN_INTRO_FLYBY_GUST = register("cinematic.guardian_intro.flyby_gust");
    public static final SoundEvent CINEMATIC_GUARDIAN_INTRO_HURL_GUST = register("cinematic.guardian_intro.hurl_gust");
    public static final SoundEvent CINEMATIC_GUARDIAN_INTRO_HURL_SWING = register("cinematic.guardian_intro.hurl_swing");
    public static final SoundEvent CINEMATIC_GUARDIAN_INTRO_IMPACT_ROAR = register("cinematic.guardian_intro.impact_roar");
    public static final SoundEvent CINEMATIC_GUARDIAN_INTRO_OVERHEAD_RUMBLE = register("cinematic.guardian_intro.overhead_rumble");
    public static final SoundEvent CINEMATIC_GUARDIAN_INTRO_POINT_GROWL = register("cinematic.guardian_intro.point_growl");
    public static final SoundEvent CINEMATIC_GUARDIAN_INTRO_SMASH_BOOM = register("cinematic.guardian_intro.smash_boom");
    public static final SoundEvent CINEMATIC_GUARDIAN_INTRO_SMASH_CRACK = register("cinematic.guardian_intro.smash_crack");
    public static final SoundEvent CINEMATIC_GUARDIAN_INTRO_SMASH_SLAM = register("cinematic.guardian_intro.smash_slam");
    public static final SoundEvent CINEMATIC_GUARDIAN_INTRO_STONE_GRIND = register("cinematic.guardian_intro.stone_grind");
    public static final SoundEvent CINEMATIC_GUARDIAN_INTRO_TEAR_CRACK = register("cinematic.guardian_intro.tear_crack");
    public static final SoundEvent CINEMATIC_GUARDIAN_INTRO_TEAR_RIP = register("cinematic.guardian_intro.tear_rip");
    public static final SoundEvent CINEMATIC_GUARDIAN_INTRO_TOSS_SWING = register("cinematic.guardian_intro.toss_swing");
    public static final SoundEvent CINEMATIC_GUARDIAN_INTRO_ZOMBIE_DEATH = register("cinematic.guardian_intro.zombie_death");
    public static final SoundEvent CINEMATIC_GUARDIAN_INTRO_ZOMBIE_GROAN = register("cinematic.guardian_intro.zombie_groan");
    public static final SoundEvent CINEMATIC_GUARDIAN_INTRO_ZOMBIE_HURT = register("cinematic.guardian_intro.zombie_hurt");
    public static final SoundEvent CINEMATIC_GUARDIAN_INTRO_ZOMBIE_STEP = register("cinematic.guardian_intro.zombie_step");

    // cinematic.necromancer_intro
    public static final SoundEvent CINEMATIC_NECROMANCER_INTRO_ARCH_CRY = register("cinematic.necromancer_intro.arch_cry");
    public static final SoundEvent CINEMATIC_NECROMANCER_INTRO_ARCH_SOUL = register("cinematic.necromancer_intro.arch_soul");
    public static final SoundEvent CINEMATIC_NECROMANCER_INTRO_BEGIN_MOOD = register("cinematic.necromancer_intro.begin_mood");
    public static final SoundEvent CINEMATIC_NECROMANCER_INTRO_BRAZIER_IGNITE = register("cinematic.necromancer_intro.brazier_ignite");
    public static final SoundEvent CINEMATIC_NECROMANCER_INTRO_CRUMBLE_BONES = register("cinematic.necromancer_intro.crumble_bones");
    public static final SoundEvent CINEMATIC_NECROMANCER_INTRO_CRUMBLE_RATTLE = register("cinematic.necromancer_intro.crumble_rattle");
    public static final SoundEvent CINEMATIC_NECROMANCER_INTRO_EYES_HEARTBEAT = register("cinematic.necromancer_intro.eyes_heartbeat");
    public static final SoundEvent CINEMATIC_NECROMANCER_INTRO_EYES_IGNITE = register("cinematic.necromancer_intro.eyes_ignite");
    public static final SoundEvent CINEMATIC_NECROMANCER_INTRO_LEVEL_ROAR = register("cinematic.necromancer_intro.level_roar");
    public static final SoundEvent CINEMATIC_NECROMANCER_INTRO_PULL_CRY = register("cinematic.necromancer_intro.pull_cry");
    public static final SoundEvent CINEMATIC_NECROMANCER_INTRO_PULL_SHRIEK = register("cinematic.necromancer_intro.pull_shriek");
    public static final SoundEvent CINEMATIC_NECROMANCER_INTRO_PULL_STAFF = register("cinematic.necromancer_intro.pull_staff");
    public static final SoundEvent CINEMATIC_NECROMANCER_INTRO_RIP_DEATH = register("cinematic.necromancer_intro.rip_death");
    public static final SoundEvent CINEMATIC_NECROMANCER_INTRO_RIP_SHRIEK = register("cinematic.necromancer_intro.rip_shriek");
    public static final SoundEvent CINEMATIC_NECROMANCER_INTRO_RIP_SOUL = register("cinematic.necromancer_intro.rip_soul");
    public static final SoundEvent CINEMATIC_NECROMANCER_INTRO_SLAM_FIRE = register("cinematic.necromancer_intro.slam_fire");
    public static final SoundEvent CINEMATIC_NECROMANCER_INTRO_SLAM_FLARE = register("cinematic.necromancer_intro.slam_flare");
    public static final SoundEvent CINEMATIC_NECROMANCER_INTRO_SLAM_IMPACT = register("cinematic.necromancer_intro.slam_impact");
    public static final SoundEvent CINEMATIC_NECROMANCER_INTRO_SOUL_ARRIVE_CHARGE = register("cinematic.necromancer_intro.soul_arrive_charge");
    public static final SoundEvent CINEMATIC_NECROMANCER_INTRO_SOUL_ARRIVE_FLARE = register("cinematic.necromancer_intro.soul_arrive_flare");
    public static final SoundEvent CINEMATIC_NECROMANCER_INTRO_TURN_ROBE = register("cinematic.necromancer_intro.turn_robe");
    public static final SoundEvent CINEMATIC_NECROMANCER_INTRO_VICTIM_GROAN = register("cinematic.necromancer_intro.victim_groan");

    // cinematic.necromancer_transform
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_BONE_CREAKS = register("cinematic.necromancer_transform.bone_creaks");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_BONE_SCRAPES_STONE = register("cinematic.necromancer_transform.bone_scrapes_stone");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_BONES_CRACK = register("cinematic.necromancer_transform.bones_crack");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_BONES_STRETCH_CRACK = register("cinematic.necromancer_transform.bones_stretch_crack");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_BONES_STRETCH_GRIND = register("cinematic.necromancer_transform.bones_stretch_grind");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_BRAZIERS_FLARE = register("cinematic.necromancer_transform.braziers_flare");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_BRAZIERS_GUTTER_OUT = register("cinematic.necromancer_transform.braziers_gutter_out");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_CHOKING = register("cinematic.necromancer_transform.choking");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_CLAW_STRIKES_STONE = register("cinematic.necromancer_transform.claw_strikes_stone");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_CLOTH_TEARS_RIP = register("cinematic.necromancer_transform.cloth_tears_rip");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_CLOTH_TEARS_SHRED = register("cinematic.necromancer_transform.cloth_tears_shred");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_CRASH_BOOM = register("cinematic.necromancer_transform.crash_boom");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_CRASH_IMPACT = register("cinematic.necromancer_transform.crash_impact");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_EYE_LIGHT_GUTTERS = register("cinematic.necromancer_transform.eye_light_gutters");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_EYES_GO_DARK = register("cinematic.necromancer_transform.eyes_go_dark");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_HALVES_RATTLE = register("cinematic.necromancer_transform.halves_rattle");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_HAND_JERKS_UP = register("cinematic.necromancer_transform.hand_jerks_up");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_HAND_SLAMS_DOWN = register("cinematic.necromancer_transform.hand_slams_down");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_HEARTBEAT = register("cinematic.necromancer_transform.heartbeat");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_HISS_OF_SMOKE = register("cinematic.necromancer_transform.hiss_of_smoke");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_HOWL_GROWL = register("cinematic.necromancer_transform.howl_growl");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_HOWL_ROAR = register("cinematic.necromancer_transform.howl_roar");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_RAGS_SLAP_STONE = register("cinematic.necromancer_transform.rags_slap_stone");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_ROBE_TEARS_APART_BOOM = register("cinematic.necromancer_transform.robe_tears_apart_boom");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_ROBE_TEARS_APART_SHRED = register("cinematic.necromancer_transform.robe_tears_apart_shred");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_ROBE_TEARS_APART_SURGE = register("cinematic.necromancer_transform.robe_tears_apart_surge");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_SITS_UP = register("cinematic.necromancer_transform.sits_up");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_SOUL_FIRE_IGNITES_FLARE = register("cinematic.necromancer_transform.soul_fire_ignites_flare");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_SOUL_FIRE_IGNITES_HUM = register("cinematic.necromancer_transform.soul_fire_ignites_hum");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_SOUL_STRIKES_BITE = register("cinematic.necromancer_transform.soul_strikes_bite");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_SOUL_STRIKES_SHRIEK = register("cinematic.necromancer_transform.soul_strikes_shriek");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_SOULS_RUSH_IN_CHARGE = register("cinematic.necromancer_transform.souls_rush_in_charge");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_SOULS_RUSH_IN_SOUL = register("cinematic.necromancer_transform.souls_rush_in_soul");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_SOULS_SHRIEK_FREE_RELEASE = register("cinematic.necromancer_transform.souls_shriek_free_release");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_SOULS_SHRIEK_FREE_SCREAM = register("cinematic.necromancer_transform.souls_shriek_free_scream");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_SOULS_SHRIEK_FREE_SHRILL = register("cinematic.necromancer_transform.souls_shriek_free_shrill");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_SPASM = register("cinematic.necromancer_transform.spasm");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_STAFF_SNAPS_CRACK = register("cinematic.necromancer_transform.staff_snaps_crack");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_STAFF_SNAPS_SPLINTER = register("cinematic.necromancer_transform.staff_snaps_splinter");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_THUD = register("cinematic.necromancer_transform.thud");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_WAILING_STORM_MOOD = register("cinematic.necromancer_transform.wailing_storm_mood");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_WAILING_STORM_WAIL = register("cinematic.necromancer_transform.wailing_storm_wail");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_WHISPERS = register("cinematic.necromancer_transform.whispers");
    public static final SoundEvent CINEMATIC_NECROMANCER_TRANSFORM_WIND_RUSH = register("cinematic.necromancer_transform.wind_rush");

    // cinematic.storm_roc_intro
    public static final SoundEvent CINEMATIC_STORM_ROC_INTRO_CAW = register("cinematic.storm_roc_intro.caw");
    public static final SoundEvent CINEMATIC_STORM_ROC_INTRO_CRACKLE = register("cinematic.storm_roc_intro.crackle");
    public static final SoundEvent CINEMATIC_STORM_ROC_INTRO_CRASH = register("cinematic.storm_roc_intro.crash");
    public static final SoundEvent CINEMATIC_STORM_ROC_INTRO_FLING = register("cinematic.storm_roc_intro.fling");
    public static final SoundEvent CINEMATIC_STORM_ROC_INTRO_ROC_LANDS = register("cinematic.storm_roc_intro.roc_lands");
    public static final SoundEvent CINEMATIC_STORM_ROC_INTRO_SCREAM = register("cinematic.storm_roc_intro.scream");
    public static final SoundEvent CINEMATIC_STORM_ROC_INTRO_SNATCH = register("cinematic.storm_roc_intro.snatch");
    public static final SoundEvent CINEMATIC_STORM_ROC_INTRO_STORM_BED = register("cinematic.storm_roc_intro.storm_bed");
    public static final SoundEvent CINEMATIC_STORM_ROC_INTRO_STRUGGLE = register("cinematic.storm_roc_intro.struggle");
    public static final SoundEvent CINEMATIC_STORM_ROC_INTRO_THUNDER = register("cinematic.storm_roc_intro.thunder");
    public static final SoundEvent CINEMATIC_STORM_ROC_INTRO_WAND_STRIKE = register("cinematic.storm_roc_intro.wand_strike");
    public static final SoundEvent CINEMATIC_STORM_ROC_INTRO_WIND = register("cinematic.storm_roc_intro.wind");
    public static final SoundEvent CINEMATIC_STORM_ROC_INTRO_WINGBEAT = register("cinematic.storm_roc_intro.wingbeat");

    // world.church
    public static final SoundEvent WORLD_CHURCH_OFFERING_CHIME = register("world.church.offering_chime");
    public static final SoundEvent WORLD_CHURCH_OFFERING_RESONATE = register("world.church.offering_resonate");
    public static final SoundEvent WORLD_CHURCH_RESTORED_BELL = register("world.church.restored_bell");

    // world.crypt
    public static final SoundEvent WORLD_CRYPT_RITUAL_RUMBLE = register("world.crypt.ritual_rumble");
    public static final SoundEvent WORLD_CRYPT_RITUAL_SOULS = register("world.crypt.ritual_souls");

    // world.guardian_arena
    public static final SoundEvent WORLD_GUARDIAN_ARENA_RITUAL_ACTIVATE = register("world.guardian_arena.ritual_activate");
    public static final SoundEvent WORLD_GUARDIAN_ARENA_RITUAL_CHARGE = register("world.guardian_arena.ritual_charge");

    // world.storm_aerie
    public static final SoundEvent WORLD_STORM_AERIE_TOLL_RESONATE = register("world.storm_aerie.toll_resonate");
    public static final SoundEvent WORLD_STORM_AERIE_TOLL_STRIKE = register("world.storm_aerie.toll_strike");

    private ModSounds() {
    }

    /**
     * Class-load hook. Call once from the common mod initializer so every
     * event is registered before the registries freeze.
     */
    public static void registerAll() {
    }

    /** Registry-entry form, for APIs such as explosions that require one. */
    public static RegistryEntry<SoundEvent> entry(SoundEvent sound) {
        return Registries.SOUND_EVENT.getEntry(sound);
    }

    private static SoundEvent register(String path) {
        Identifier id = Identifier.of(ElementalWandsMod.MOD_ID, path);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }
}
