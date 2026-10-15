package com.anton.elementalwands.entity.roc;

import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.util.SpellCombat;
import com.anton.elementalwands.util.NatureCombat;
import com.anton.elementalwands.util.SeedlingManager;
import com.anton.elementalwands.util.TemporaryBlockManager;
import com.anton.elementalwands.registry.ModSpellBlocks;
import com.anton.elementalwands.util.WandProgression;
import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.summit.StormSummit;
import com.anton.elementalwands.summit.StormSummitManager;
import com.anton.elementalwands.summit.StormRocEncounter;
import net.minecraft.world.GameMode;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;

/** Dedicated, real-damage scripted phase-one regression; never a balance approval. */
public final class StormRocServerSmoke implements ModInitializer {
    private int tick, step, elapsed, melee;
    private StormRocEntity boss;
    private ServerPlayerEntity target, dodge, outsider;
    private ServerWorld world;
    private RocEffectEntity strike, tornado;
    private RocBodyPartEntity head;
    private BlockPos flower;
    private double flowerXp;
    private float flowerHealth;
    private BlockPos fireCell;
    private TemporaryBlockManager.TemporaryPlacement firePlacement;
    private float before;
    private Vec3d velocity;
    private final List<String> checks = new ArrayList<>();
    private static final BlockPos CENTRE = new BlockPos(0, StormSummit.SURFACE_Y, 0);
    private static final Box AREA = new Box(-160, 50, -160, 160, 260, 160);
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { run(server); }
            catch (Throwable error) {
                error.printStackTrace();
                try { Files.writeString(Path.of("ROC_SERVER_FAILED.txt"), "step=" + step + " elapsed=" + elapsed + "\n" + error + "\n"); } catch (Exception ignored) {}
                server.stop(false);
            }
        });
    }
    private void run(MinecraftServer server) throws Exception {
        if (++tick > 2500) throw new AssertionError("Roc server fixture timeout at " + step);
        if (tick == 25) {
            world = server.getWorld(StormSummit.WORLD);
            require(world != null, "Summit dimension missing");
            for (int x=-8;x<=8;x++) for(int z=-8;z<=8;z++){world.getChunk(x,z);world.setChunkForced(x,z,true);}
            // Mechanics fixture owns a minimal flat floor; the native fixture uses the authored summit.
            for(int x=-65;x<=65;x++)for(int z=-65;z<=65;z++)world.setBlockState(new BlockPos(x,100,z),net.minecraft.block.Blocks.STONE.getDefaultState());
            target=player(server,"RocTarget",45.5,101,.5);dodge=player(server,"RocDodge",45.5,101,12.5);outsider=player(server,"RocOutsider",45.5,101,.5);
            for(var p:List.of(target,dodge,outsider)){
                p.setLoaded(true);p.onTeleportationDone();p.setNoGravity(true);p.getHungerManager().setFoodLevel(10);
                p.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(200);p.setHealth(200);
                p.equipStack(EquipmentSlot.HEAD,Items.DIAMOND_HELMET.getDefaultStack());p.equipStack(EquipmentSlot.CHEST,Items.DIAMOND_CHESTPLATE.getDefaultStack());
                p.equipStack(EquipmentSlot.LEGS,Items.DIAMOND_LEGGINGS.getDefaultStack());p.equipStack(EquipmentSlot.FEET,Items.DIAMOND_BOOTS.getDefaultStack());
                // Dummy connections do not run the real handler's equipment-refresh tick.
                p.getAttributeInstance(EntityAttributes.ARMOR).setBaseValue(20);p.getAttributeInstance(EntityAttributes.ARMOR_TOUGHNESS).setBaseValue(8);
                require(p.getArmor()==20&&Math.abs(p.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS)-8)<.001,"Dummy diamond armor attributes were not installed");
            }
            boss=new StormRocEntity(ModEntities.STORM_ROC,world);boss.startFight(CENTRE,List.of(target,dodge));world.spawnEntity(boss);
            boss.testAction(target,StormRocRules.Action.PECK);
            beginStrike(false);return;
        }
        if(boss==null)return;
        elapsed++;
        switch(step){
            case 0 -> {
                if(elapsed==1)answerChecks();
                if(elapsed==19)require(target.getHealth()==before,"Small strike hit before warning elapsed");
                if(elapsed==23){damage(before-target.getHealth(),8,"Small first bolt diamond damage");require(target.getVelocity().lengthSquared()<1e-6,"Small bolt pushed its target");}
                if(elapsed==34){damage(before-target.getHealth(),16,"Small repeat bolt damage");require(dodge.getHealth()==200,"Small strike hit distant dodger");require(outsider.getHealth()==200,"Small strike hit outsider");checks.add("Small warning, repeat damage, diamond armor, dodge, outsider and no push");next();beginStrike(true);}
            }
            case 1 -> {
                if(elapsed==29)require(target.getHealth()==before,"Large strike hit before warning elapsed");
                if(elapsed==33)damage(before-target.getHealth(),16,"Large first bolt diamond damage");
                if(elapsed==54){damage(before-target.getHealth(),48,"Large triple bolt damage");checks.add("Large warning and three full repeated bolts");next();
                    target.setHealth(200);dodge.setHealth(200);boss.testAction(target,StormRocRules.Action.PECK);strike=boss.testStrike(target.getEntityPos(),false);
                    target.setPosition(53.5,101,.5);}
            }
            case 2 -> {
                if(elapsed==35){require(target.getHealth()==200,"Marked strike tracked a dodging player");checks.add("Locked strike aim and moving dodge");boss.testAction(target,StormRocRules.Action.PECK);
                    RocEffectEntity a=boss.testStrike(target.getEntityPos(),false),b=boss.testStrike(target.getEntityPos(),true);
                    require(a!=null&&b!=null&&a.getEntityPos().subtract(b.getEntityPos()).horizontalLength()>=Math.max(a.radius(),b.radius())+.04,"Lightning circle centers stacked inside one another");
                    require(!boss.damage(world,outsider.getDamageSources().playerAttack(outsider),100),"Outsider damaged Roc");
                    checks.add("Lightning circle centers separated and outsider cannot damage Roc");next();boss.stopFight();}
            }
            case 3 -> {
                if(elapsed==5){
                    require(effects().isEmpty(),"Stop left Roc effects alive");require(target.getHealth()==200,"Cancelled strike still damaged");checks.add("Cancellation clears effects and delayed damage");
                    boss.startFight(CENTRE,List.of(target,dodge));boss.testAction(target,StormRocRules.Action.PECK);
                    target.setPosition(-10,101,.5);target.setHealth(200);target.setVelocity(Vec3d.ZERO);tornado=boss.testTornado(target.getEntityPos());
                }
                if(elapsed==8)require(target.getAttributeValue(EntityAttributes.MOVEMENT_SPEED)<.08,"Tornado core did not slow challenger");
                if(elapsed==30){
                    damage(200-target.getHealth(),6,"Tornado catch release diamond damage");
                    require(target.getVelocity().y>0&&target.getVelocity().multiply(1,0,1).dotProduct(CENTRE.toCenterPos().subtract(target.getEntityPos()))>0,"Tornado release did not throw inward");
                    target.setPosition(35,101,.5);target.setVelocity(Vec3d.ZERO);
                }
                if(elapsed==40){target.setPosition(tornado.getEntityPos().x,101,tornado.getEntityPos().z);target.setVelocity(Vec3d.ZERO);}
                if(elapsed==41){require(Math.abs(target.getVelocity().y)<.01,"Tornado immediately recaught/juggled released challenger");boss.stopFight();}
                if(elapsed==43){
                    require(Math.abs(target.getAttributeValue(EntityAttributes.MOVEMENT_SPEED)-.1)<.0001,"Tornado slow survived cancellation");
                    require(effects().isEmpty(),"Tornado survived cancellation");checks.add("Tornado core slowing, timed catch/release, inward throw, recatch immunity and cleanup");
                    target.setPosition(30.5,101,.5);target.setVelocity(Vec3d.ZERO);target.setHealth(200);
                    boss.startFight(CENTRE,List.of(target,dodge));boss.setHealth(boss.getMaxHealth()*.74f);
                }
                if(elapsed==45){require(effects().stream().filter(e->e.kind()==RocEffectEntity.Kind.TORNADO).count()==2,"75-percent health did not add second tornado");checks.add("Second tornado unlocks below75percent health");
                    boss.stopFight();boss.startFight(CENTRE,List.of(target,dodge));step=15;elapsed=0;
                }
            }
            case 4 -> {
                if(elapsed==220){
                    require(boss.combat().stage()==StormRocRules.Stage.BRAWL,"Ground window ended on a timer without health loss: "+boss.status());
                    checks.add("Ground vulnerability remains until health quota");
                    float maximum=boss.getMaxHealth();
                    boss.timeUntilRegen=0;boss.damage(world,target.getDamageSources().playerAttack(target),100000);
                    damage(boss.getHealth(),(float)(maximum*(1-StormRocRules.LANDING_SHARE)),"Landing quota clamps large damage");
                    require(boss.isAlive(),"Large grounded hit killed phase-one boss");
                    require(!boss.damage(world,dodge.getDamageSources().playerAttack(dodge),25),"New caster attack at landing health floor was accepted without health loss");
                    checks.add("Landing health floor rejects distinct new caster instead of accepting zero-health damage");next();
                }
            }
            case 5 -> {
                if(elapsed==2)require(boss.combat().stage()==StormRocRules.Stage.TAKEOFF,"Landing quota did not trigger takeoff: "+boss.status());
                if(elapsed>StormRocRules.TAKEOFF_TICKS+5){
                    require(!boss.phaseOneComplete(),"First landing ended entire phase");checks.add("Landing health quota triggers takeoff safely");
                    boss.testAction(target,StormRocRules.Action.PECK);boss.setHealth(boss.getMaxHealth()*.5f);next();
                }
            }
            case 6 -> {
                if(elapsed==1)require(!boss.phaseOneComplete(),"Phase gate skipped the takeoff animation");
                if(elapsed>StormRocRules.TAKEOFF_TICKS+5){require(boss.phaseOneComplete(),"Half-health phase did not complete after takeoff: "+boss.status());require(effects().isEmpty(),"Phase completion retained damaging effects");checks.add("Half health takeoff and safe phase completion");
                    boss.discard();
                    target.setHealth(200);dodge.setHealth(200);
                    for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++){world.getChunk(64+x,z);world.setChunkForced(64+x,z,true);}
                    StormSummitManager.enter(target,1);StormSummitManager.enter(dodge,1);next();}
            }
            case 7 -> {
                if(elapsed==180){
                    target.onTeleportationDone();dodge.onTeleportationDone();
                    require(StormSummit.nearestCentre(target.getEntityPos()).equals(StormSummit.centre(0,1)),"Authored lifecycle slot did not finish building");
                    for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)world.setChunkForced(64+x,z,false);
                    String result=StormSummitManager.start(target);require(result.startsWith("Started phase one with 2"),"Sealed start failed: "+result);
                    require(!StormRocEncounter.canTeleport(outsider,world,target.getEntityPos()),"Outsider could teleport into sealed fight");
                    require(!StormRocEncounter.canTeleport(target,server.getOverworld(),new Vec3d(0,100,0)),"Participant could escape dimension");
                    require(!StormRocEncounter.canTeleport(target,world,new Vec3d(0,101,0)),"Participant could escape slot");
                    require(StormRocEncounter.canTeleport(target,world,new Vec3d(1024,140,-90)),"Encounter blocked a legal spire approach");
                    next();
                }
            }
            case 8 -> {
                if(elapsed==20){
                    var active=world.getEntitiesByClass(StormRocEntity.class,new Box(880,70,-160,1180,260,160),e->e.fighting()&&!e.isRemoved()).stream().findFirst().orElseThrow(()->new AssertionError("Transient fight ticket loaded no Roc"));
                    require(active.age>=15,"Transient fight ticket did not keep perched boss ticking: age"+active.age);
                    for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)require(!world.getForcedChunks().contains(net.minecraft.util.math.ChunkPos.toLong(64+x,z)),"Managed fight persisted a FORCED chunk");
                    checks.add("Transient encounter ticket ticks perched boss without persistent forced chunks");
                    target.setPosition(1104,90,.5);
                }
                if(elapsed==23){
                    require(target.isSpectator(),"Fallen challenger did not spectate");require(!StormRocEncounter.canCast(target),"Defeated challenger could cast");
                    require(target.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.DIAMOND_CHESTPLATE),"Fall lost belongings");
                    require(StormSummitManager.enter(target,1).contains("Leave your current fight"),"Defeated challenger could rejoin via enter");
                    String left=StormSummitManager.leave(target);require(!StormSummitManager.inRealm(target)&&!target.isSpectator(),"Explicit leave did not restore mode/home: "+left);
                    dodge.setPosition(1104,90,.5);next();
                }
            }
            case 9 -> {
                if(elapsed==3){
                    require(!StormRocEncounter.activeAt(StormSummit.centre(0,1)),"Wipe retained active fight");
                    require(dodge.interactionManager.getGameMode()==GameMode.SURVIVAL,"Wipe retained spectator mode");
                    checks.add("Authored summit sealed roster, legal spire movement, containment, fall defeat, belongings, leave and wipe");
                    String start=StormSummitManager.start(dodge);require(start.startsWith("Started phase one with 1"),"Retry after wipe failed: "+start);
                    require(StormSummitManager.reset(dodge).contains("reset"),"Operator reset failed");
                    require(!StormRocEncounter.activeAt(StormSummit.centre(0,1)),"Reset retained fight");
                    require(StormSummitManager.start(dodge).startsWith("Started phase one with 1"),"Second retry start failed");
                }
                if(elapsed>=5){
                    dodge.onTeleportationDone();
                    StormRocEntity reference=managedBoss(StormSummit.centre(0,1));
                    require(StormRocEncounter.activeAt(StormSummit.centre(0,1))&&reference!=null&&reference.fighting()&&reference.isAlive(),"Retry manager lost its active boss: "+retryDiagnostics(reference));
                    var found=world.getEntitiesByClass(StormRocEntity.class,new Box(880,70,-160,1180,260,160),e->e==reference&&e.fighting()&&!e.isRemoved()).stream().findFirst();
                    if(found.isEmpty()){
                        if(elapsed<23)return;
                        throw new AssertionError("Retry active boss not queryable within20ticks: "+retryDiagnostics(reference));
                    }
                    boss=found.get();
                    boss.testAction(dodge,StormRocRules.Action.PECK);boss.setHealth(boss.getMaxHealth()*.5f);next();
                }
            }
            case 10 -> {
                if(elapsed>StormRocRules.TAKEOFF_TICKS+8){
                    require(!StormRocEncounter.activeAt(StormSummit.centre(0,1)),"Phase completion did not end managed encounter");
                    require(dodge.interactionManager.getGameMode()==GameMode.SURVIVAL,"Phase completion retained spectator mode");
                    require(world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(880,70,-160,1180,260,160),e->!e.isRemoved()).isEmpty(),"Phase completion awarded loot");
                    checks.add("Reset, retry and managed phase completion restore players without victory loot");
                    StormSummitManager.enter(target,1);StormSummitManager.enter(outsider,1);target.onTeleportationDone();outsider.onTeleportationDone();dodge.onTeleportationDone();
                    target.setHealth(target.getMaxHealth());dodge.setHealth(dodge.getMaxHealth());outsider.setHealth(outsider.getMaxHealth());
                    require(StormSummitManager.start(dodge).startsWith("Started phase one with 3"),"Death/disconnect retry failed");
                    next();
                }
            }
            case 11 -> {
                if(elapsed==3){
                    target.experienceLevel=17;target.totalExperience=400;target.experienceProgress=.25f;
                    target.damage(world,target.getDamageSources().outOfWorld(),100000);require(!target.isAlive(),"Scripted realm death failed");
                    var handler=target.networkHandler;UUID id=target.getUuid();
                    handler.player=server.getPlayerManager().respawnPlayer(target,false,net.minecraft.entity.Entity.RemovalReason.KILLED);
                    target=server.getPlayerManager().getPlayer(id);target.setLoaded(true);target.onTeleportationDone();next();
                }
            }
            case 12 -> {
                if(elapsed==3){
                    require(target.isSpectator()&&StormSummitManager.inRealm(target),"Defeated respawn did not return as spectator");
                    require(target.experienceLevel==17&&target.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.DIAMOND_CHESTPLATE),"Realm death lost inventory/experience");
                    require(!StormRocEncounter.canCast(target),"Respawned player rejoined fight");
                    ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(dodge.networkHandler,server);next();
                }
            }
            case 13 -> {
                if(elapsed==3){
                    require(dodge.isSpectator()&&!StormRocEncounter.canCast(dodge),"Disconnect allowed roster readmission");
                    require(StormRocEncounter.activeAt(StormSummit.centre(0,1)),"One disconnect wiped remaining challenger");
                    ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(outsider.networkHandler,server);next();
                }
            }
            case 14 -> {
                if(elapsed==3){
                    require(!StormRocEncounter.activeAt(StormSummit.centre(0,1)),"All disconnected/defeated roster did not end encounter");
                    require(!target.isSpectator()&&!dodge.isSpectator()&&!outsider.isSpectator(),"Disconnect wipe failed mode restoration");
                    for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)require(!world.getForcedChunks().contains(net.minecraft.util.math.ChunkPos.toLong(64+x,z)),"Encounter cleanup left persisted forced chunks");
                    checks.add("Real realm death/respawn keeps belongings and experience, sealed respawn, disconnect exclusion and all-defeated cleanup");
                    Files.writeString(Path.of("ROC_SERVER_PASSED.txt"),String.join("\n",checks)+"\n");server.stop(false);
                }
            }
            case 15 -> {
                var action=StormRocRules.Action.values()[melee];
                if(elapsed==1){
                    target.setPosition(.5,101,action==StormRocRules.Action.STAMPEDE?45.5:30.5);target.setHealth(200);target.timeUntilRegen=0;target.setVelocity(Vec3d.ZERO);
                    dodge.setPosition(50.5,101,45.5);dodge.setHealth(200);outsider.setHealth(200);
                    boss.testAction(target,action);
                    Vec3d point=switch(action){
                        case PECK,SWING_LEFT,SWING_RIGHT -> boss.modelToWorld(StormRocPose.socket(action.pose,action==StormRocRules.Action.PECK?12:20,"beak"));
                        case SCREAM -> boss.modelToWorld(StormRocPose.socket(action.pose,24,"beak")).add(Vec3d.fromPolar(0,boss.getYaw()).multiply(20));
                        case WING -> new Vec3d(20.5,101,.5);
                        case POUNCE -> new Vec3d(.5,101,30.5);
                        case STAMPEDE -> new Vec3d(.5,101,45.5);
                    };
                    target.setPosition(point.x,101,point.z);outsider.setPosition(point.x,101,point.z);
                    if(action==StormRocRules.Action.SWING_LEFT||action==StormRocRules.Action.SWING_RIGHT||action==StormRocRules.Action.WING)dodge.setPosition(point.x,135,point.z);
                }
                int hitAt=action==StormRocRules.Action.SWING_LEFT||action==StormRocRules.Action.SWING_RIGHT?25:action==StormRocRules.Action.STAMPEDE?55:action.impact+1;
                if(elapsed==hitAt){
                    float expected=switch(action){case PECK,SWING_LEFT,SWING_RIGHT->10;case SCREAM,STAMPEDE->16;case WING->4;case POUNCE->20;};
                    damage(200-target.getHealth(),expected,action+" diamond hit damage");
                    if(action==StormRocRules.Action.STAMPEDE)require(boss.getZ()>40,"Stampede did not reach its locked ranged target: "+boss.getEntityPos());
                    require(dodge.getHealth()==200,action+" hit distant dodger");require(outsider.getHealth()==200,action+" hit outsider");
                    if(action==StormRocRules.Action.PECK||action==StormRocRules.Action.SCREAM)require(target.getVelocity().lengthSquared()<.0001,action+" unexpectedly pushed target");
                }
                // The landing ring reaches a crushed player two ticks later; it must not add to the crush.
                if(action==StormRocRules.Action.POUNCE&&elapsed==action.impact+7){
                    damage(200-target.getHealth(),20,"Pounce ring stacked on its crush");
                    checks.add("Crushed player takes the pounce once, not crush plus ring");
                }
                if(elapsed==action.duration+3){
                    checks.add(action+" hit-frame damage, dodge and outsider exclusion");
                    if(++melee>=StormRocRules.Action.values().length){target.setPosition(30.5,101,.5);target.setHealth(200);target.setVelocity(Vec3d.ZERO);boss.stopFight();boss.startFight(CENTRE,List.of(target,dodge));boss.testAction(target,StormRocRules.Action.PECK);target.setPosition(.5,101,.5);dodge.setPosition(20.5,104,.5);dodge.setHealth(200);outsider.setPosition(.5,101,.5);outsider.setHealth(200);boss.testDive(new Vec3d(.5,101.03,.5));step=16;elapsed=0;}
                    else elapsed=0;
                }
            }
            case 16 -> {
                if(elapsed==59)require(target.getHealth()==200,"Dive hit before committed dive timeline");
                if(elapsed==60){
                    damage(200-target.getHealth(),35,"Dive direct hit against diamond");require(boss.combat().stage()==StormRocRules.Stage.STUCK,"Dive did not open stuck vulnerability");
                    target.setPosition(20.5,101,.5);target.setVelocity(Vec3d.ZERO);outsider.setPosition(20.5,101,.5);
                }
                if(elapsed==80){
                    damage(200-target.getHealth(),41,"Dive plus one grounded shockwave hit");require(dodge.getHealth()==200,"Shockwave hit a jumping player");require(outsider.getHealth()==200,"Dive/shockwave hit outsider");
                    require(target.getVelocity().lengthSquared()<.0001,"Dive shockwave pushed player");checks.add("Committed dive timing, lethal diamond direct hit, stuck opening, jumpable single shockwave and no push");
                }
                if(elapsed==82){
                    boss.stopFight();target.setPosition(30.5,101,.5);target.setHealth(200);target.setVelocity(Vec3d.ZERO);dodge.setPosition(30.5,101,12.5);
                    boss.startFight(CENTRE,List.of(target,dodge));step=17;elapsed=0;
                }
            }
            case 17 -> {
                if(elapsed==1){
                    target.setPosition(58.5,101,.5);target.setHealth(200);target.setVelocity(Vec3d.ZERO);dodge.setPosition(58.5,135,.5);dodge.setHealth(200);outsider.setPosition(58.5,101,.5);outsider.setHealth(200);
                    boss.testAction(target,StormRocRules.Action.WING);boss.setPosition(35.5,101.03,.5);
                }
                if(elapsed==21){
                    damage(200-target.getHealth(),4,"Wing hit at rim");Vec3d impulse=target.getVelocity().multiply(1,0,1);
                    require(impulse.dotProduct(CENTRE.toCenterPos().subtract(target.getEntityPos()))>0,"Wing knocked rim player toward the edge");
                    require(StormSummit.beyondRim(CENTRE,target.getEntityPos().add(impulse.multiply(12)))<=-8,"Wing impulse projected outside interior safety margin");
                    require(outsider.getHealth()==200,"Rim wing hit outsider");require(dodge.getHealth()==200,"Rim wing hit elevated dodger");checks.add("Rim wing impulse points inward and stops within interior margin");
                }
                if(elapsed==23){target.setPosition(30.5,101,.5);target.setHealth(200);target.setVelocity(Vec3d.ZERO);dodge.setPosition(30.5,101,12.5);boss.stopFight();boss.startFight(CENTRE,List.of(target,dodge));boss.testAction(target,StormRocRules.Action.PECK);step=18;elapsed=0;}
            }
            case 18 -> {
                if(elapsed==1){
                    head=world.getEntitiesByClass(RocBodyPartEntity.class,AREA,p->p.owner()==boss&&!p.isRemoved()).stream().findFirst().orElseThrow(()->new AssertionError("No animated Roc part for canonical damage regression"));
                    var type=world.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE).getOrThrow(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE,net.minecraft.util.Identifier.of("elementalwands","roc_storm")));
                    var source=new net.minecraft.entity.damage.DamageSource(type,target,target);
                    float health=boss.getHealth();double xp=WandProgression.get(target,WizardAffinity.WIND).xp();
                    require(SpellCombat.damage(boss,world,source,20,target,WizardAffinity.WIND),"Owned AoE missed canonical body");
                    require(!SpellCombat.damage(head,world,source,20,target,WizardAffinity.WIND),"Same owned AoE multiplied damage via part");
                    damage(health-boss.getHealth(),20,"Body/part AoE counts once");require(Math.abs(WandProgression.get(target,WizardAffinity.WIND).xp()-xp-20)<.01,"Body/part AoE rewarded XP more than once");
                    double secondXp=WandProgression.get(dodge,WizardAffinity.WIND).xp();
                    require(SpellCombat.damage(head,world,new net.minecraft.entity.damage.DamageSource(type,dodge,dodge),20,dodge,WizardAffinity.WIND),"Second eligible player was swallowed by first player's hurt window");
                    damage(health-boss.getHealth(),40,"Two distinct owners same-tick damage");require(Math.abs(WandProgression.get(dodge,WizardAffinity.WIND).xp()-secondXp-20)<.01,"Second owner's XP was not credited exactly once");
                    checks.add("Canonical body/part bypass-cooldown AoE deduplicates damage and XP; distinct eligible owners both count");
                }
                if(elapsed==2){
                    var type=world.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE).getOrThrow(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE,net.minecraft.util.Identifier.of("elementalwands","roc_storm")));
                    var source=new net.minecraft.entity.damage.DamageSource(type,target,target);
                    float health=boss.getHealth();double xp=WandProgression.get(target,WizardAffinity.WIND).xp();
                    require(SpellCombat.damage(boss,world,source,8,target,WizardAffinity.WIND),"Smaller bypass-cooldown AoE missed body");
                    require(SpellCombat.damage(head,world,source,12,target,WizardAffinity.WIND),"Larger same AoE did not apply its difference");
                    damage(health-boss.getHealth(),12,"Increasing same AoE applies maximum only");require(Math.abs(WandProgression.get(target,WizardAffinity.WIND).xp()-xp-12)<.01,"Increasing same AoE doubled XP");
                    checks.add("Same-owner same-source AoE8then12 deals12total and rewards12XP");
                }
                if(elapsed==13){
                    float health=boss.getHealth();double xp=WandProgression.get(target,WizardAffinity.NATURE).xp();
                    float thorns=NatureCombat.thornContact(world,head,target.getUuid());
                    require(thorns>0&&boss.getHealth()<health,"Anonymous owned Nature thorns could not damage canonical Roc part");
                    require(Math.abs(WandProgression.get(target,WizardAffinity.NATURE).xp()-xp-(health-boss.getHealth()))<.01,"Nature part contact XP differed from canonical health loss");
                    require(NatureCombat.thornContact(world,boss,target.getUuid())==0,"Same Nature contact counted again through body after part");
                    checks.add("Owned anonymous Nature thorns damage canonical Roc and reward actual health XP");
                }
                if(elapsed==24){
                    SpellCombat.ignite(head,target,2);require(boss.isOnFire()&&SpellCombat.burnOwner(boss,world.getDamageSources().onFire())==target,"Igniting part did not attribute canonical boss burn");
                    SpellCombat.trackBurn(head,dodge,40);require(SpellCombat.burnOwner(boss,world.getDamageSources().onFire())==dodge,"Tracking part burn did not replace canonical owner");
                    float health=boss.getHealth();double xp=WandProgression.get(dodge,WizardAffinity.FIRE).xp();
                    require(boss.damage(world,world.getDamageSources().onFire(),4),"Attributed canonical burn was rejected");
                    damage(health-boss.getHealth(),4,"Canonical burn health damage");require(Math.abs(WandProgression.get(dodge,WizardAffinity.FIRE).xp()-xp-4)<.01,"Canonical burn XP owner/count wrong");boss.extinguish();
                    checks.add("Ignite/track on part maps canonical burn owner, health and XP");
                }
                if(elapsed==36){
                    float health=boss.getHealth();double xp=WandProgression.get(target,WizardAffinity.FIRE).xp();
                    require(SpellCombat.damage(head,world,world.getDamageSources().hotFloor(),12,target,WizardAffinity.FIRE),"Anonymous owned Pyre damage rejected");
                    damage(health-boss.getHealth(),12,"Owned Pyre canonical health");require(Math.abs(WandProgression.get(target,WizardAffinity.FIRE).xp()-xp-12)<.01,"Owned Pyre XP incorrect");
                }
                if(elapsed==48){
                    float health=boss.getHealth();double xp=WandProgression.get(target,WizardAffinity.FIRE).xp();
                    require(SpellCombat.damage(head,world,world.getDamageSources().inFire(),10,target,WizardAffinity.FIRE),"Anonymous owned Inferno damage rejected");
                    damage(health-boss.getHealth(),10,"Owned Inferno canonical health");require(Math.abs(WandProgression.get(target,WizardAffinity.FIRE).xp()-xp-10)<.01,"Owned Inferno XP incorrect");
                    checks.add("Owned anonymous Pyre/Inferno sources retain damage and canonical XP");
                    boss.stopFight();boss.startFight(CENTRE,List.of(target,dodge));boss.testAction(target,StormRocRules.Action.PECK);step=19;elapsed=0;
                }
            }
            case 19 -> {
                if(elapsed==1){
                    Vec3d body=boss.socket("body");BlockPos floor=BlockPos.ofFloored(body.x,100,body.z);flower=floor.up();
                    require(NatureCombat.standsIn(boss,Set.of(flower)),"Roc whole-body floor footprint did not overlap planted thicket cell");
                    Box patch=new Box(floor.getX()-4,floor.getY()-.5,floor.getZ()-4,floor.getX()+5,floor.getY()+3.5,floor.getZ()+5);
                    require(world.getEntitiesByClass(StormRocEntity.class,patch,e->e==boss).contains(boss),"Real Seedling ground query could not reach Roc body volume");
                    flowerHealth=boss.getHealth();flowerXp=WandProgression.get(target,WizardAffinity.NATURE).xp();
                    require(SeedlingManager.tryPlantSeedling(world,target,new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(floor).add(0,.5,0),Direction.UP,floor,false)),"Could not plant real fixture thicket beneath Roc body");
                }
                if(elapsed==5){
                    require(boss.getHealth()<flowerHealth,"Real placed Seedling tick dealt no Roc thorn damage");
                    require(Math.abs(WandProgression.get(target,WizardAffinity.NATURE).xp()-flowerXp-(flowerHealth-boss.getHealth()))<.01,"Real planted thicket did not credit canonical health XP");
                    require(SeedlingManager.destroySeedlingAtAnchor(world,flower),"Real planted fixture thicket failed cleanup");
                    checks.add("Actual planted Seedling ground query/footprint reaches Roc and ticked thorns reward canonical damage XP");
                    boss.stopFight();boss.startFight(CENTRE,List.of(target,dodge));boss.testAction(target,StormRocRules.Action.PECK);step=20;elapsed=0;
                }
            }
            case 20 -> {
                if(elapsed==1)placeFootFire(target,false);
                if(elapsed==5){
                    require(boss.getHealth()<flowerHealth,"Actual owned foot coals dealt no Roc damage");
                    require(Math.abs(WandProgression.get(target,WizardAffinity.FIRE).xp()-flowerXp-(flowerHealth-boss.getHealth()))<.01,"Actual foot coals did not credit canonical health XP");
                    TemporaryBlockManager.restoreTemporaryBlocks(world,firePlacement);boss.stopFight();boss.startFight(CENTRE,List.of(target,dodge));boss.testAction(target,StormRocRules.Action.PECK);placeFootFire(target,true);
                }
                if(elapsed==9){
                    require(boss.getHealth()<flowerHealth,"Actual owned foot flame dealt no Roc damage");
                    require(boss.isOnFire()&&SpellCombat.burnOwner(boss,world.getDamageSources().onFire())==target,"Actual placed Inferno flame did not ignite canonical boss with caster owner");
                    require(Math.abs(WandProgression.get(target,WizardAffinity.FIRE).xp()-flowerXp-(flowerHealth-boss.getHealth()))<.01,"Actual foot flame did not credit canonical health XP");
                    TemporaryBlockManager.restoreTemporaryBlocks(world,firePlacement);boss.extinguish();boss.stopFight();boss.startFight(CENTRE,List.of(target,dodge));boss.testAction(target,StormRocRules.Action.PECK);placeFootFire(outsider,false);
                }
                if(elapsed==13){
                    require(boss.getHealth()==flowerHealth&&WandProgression.get(outsider,WizardAffinity.FIRE).xp()==flowerXp,"Nonparticipant foot coals damaged/rewarded Roc");
                    TemporaryBlockManager.restoreTemporaryBlocks(world,firePlacement);placeFootFire(outsider,true);
                }
                if(elapsed==17){
                    require(boss.getHealth()==flowerHealth&&!boss.isOnFire()&&WandProgression.get(outsider,WizardAffinity.FIRE).xp()==flowerXp,"Nonparticipant foot flame damaged/ignited/rewarded Roc");
                    TemporaryBlockManager.restoreTemporaryBlocks(world,firePlacement);checks.add("Actual owned foot coals/flame damage and credit XP/burn owner; outsider ground fire ignored");
                    boss.stopFight();boss.startFight(CENTRE,List.of(target,dodge));boss.testAction(target,StormRocRules.Action.PECK);step=4;elapsed=0;
                }
            }
            default -> throw new AssertionError("Unknown fixture stage");
        }
    }
    private void placeFootFire(ServerPlayerEntity owner,boolean flame){
        Vec3d foot=boss.socket("left_foot");fireCell=BlockPos.ofFloored(foot.x,101.03,foot.z);
        firePlacement=TemporaryBlockManager.placeTrackedTemporaryBlocks(world,List.of(fireCell),(flame?ModSpellBlocks.INFERNO_FLAME:ModSpellBlocks.PYRE_COALS).getDefaultState(),80,s->s.isAir()||s.isReplaceable(),owner.getUuid());
        require(firePlacement.placedCount()==1&&owner.getUuid().equals(TemporaryBlockManager.casterAt(world,fireCell)),"Actual owned foot fire placement failed");
        flowerHealth=boss.getHealth();flowerXp=WandProgression.get(owner,WizardAffinity.FIRE).xp();
    }
    private StormRocEntity managedBoss(BlockPos centre)throws Exception{
        var registry=StormRocEncounter.class.getDeclaredField("fights");registry.setAccessible(true);
        Object fight=((Map<?,?>)registry.get(null)).get(centre);if(fight==null)return null;
        var field=fight.getClass().getDeclaredField("boss");field.setAccessible(true);return (StormRocEntity)field.get(fight);
    }
    private String retryDiagnostics(StormRocEntity reference){
        String players=world.getPlayers().stream().map(p->p.getName().getString()+" pos="+p.getEntityPos()+" world="+p.getEntityWorld().getRegistryKey().getValue()+" mode="+p.interactionManager.getGameMode()+" alive="+p.isAlive()+" health="+p.getHealth()).toList().toString();
        String rocs=world.getEntitiesByClass(StormRocEntity.class,new Box(512,0,-512,1536,400,512),e->true).stream().map(r->r.getUuid()+" "+r.status()+" pos="+r.getEntityPos()+" bbox="+r.getBoundingBox()+" removed="+r.isRemoved()+" age="+r.age).toList().toString();
        String saved=reference==null?"null":reference.getUuid()+" "+reference.status()+" pos="+reference.getEntityPos()+" bbox="+reference.getBoundingBox()+" removed="+reference.isRemoved()+" age="+reference.age+" participant="+reference.participant(dodge.getUuid())+" worldLookup="+world.getEntity(reference.getUuid());
        return StormSummitManager.status()+"; challenger="+dodge.getEntityPos()+"/"+dodge.getEntityWorld().getRegistryKey().getValue()+"/"+dodge.interactionManager.getGameMode()+"/alive="+dodge.isAlive()+"/health="+dodge.getHealth()+"; allRocs="+rocs+"; managed="+saved+"; worldPlayers="+players;
    }
    private void beginStrike(boolean large){target.setHealth(200);target.timeUntilRegen=0;dodge.setHealth(200);outsider.setHealth(200);boss.testAction(target,StormRocRules.Action.PECK);before=target.getHealth();target.setVelocity(Vec3d.ZERO);velocity=target.getVelocity();strike=boss.testStrike(target.getEntityPos(),large);require(strike!=null,"testStrike did not create effect");}
    private void answerChecks(){
        var wing=List.of(StormRocRules.Action.WING);
        require(StormRocCombat.answers(4,1,true,null).equals(wing),"Target between the Roc's feet had no answer");
        require(StormRocCombat.answers(12,1,true,null).equals(wing),"Target under the Roc's neck had no answer: "+StormRocCombat.answers(12,1,true,null));
        require(StormRocCombat.answers(12,1,true,StormRocRules.Action.WING).equals(List.of(StormRocRules.Action.POUNCE)),"Target staying under the neck was not pounced");
        require(StormRocCombat.answers(10,-1,true,null).equals(wing),"Target behind the Roc drew no wing wave");
        var front=StormRocCombat.answers(25,1,true,null);
        require(front.containsAll(List.of(StormRocRules.Action.PECK,StormRocRules.Action.SWING_LEFT,StormRocRules.Action.SCREAM)),"Target in bill reach lacked head attacks: "+front);
        require(!StormRocCombat.answers(15,1,true,null).contains(StormRocRules.Action.PECK),"Peck offered where its bill cannot reach");
        require(!StormRocCombat.answers(25,1,true,StormRocRules.Action.PECK).contains(StormRocRules.Action.PECK),"Brawl repeated its last attack");
        checks.add("Brawl answers every place to stand: wings and pounce under the neck, head attacks only within reach, no repeats");
    }
    private List<RocEffectEntity> effects(){return world.getEntitiesByClass(RocEffectEntity.class,AREA,e->!e.isRemoved());}
    private void next(){step++;elapsed=0;}
    private static void damage(float actual,float expected,String name){require(Math.abs(actual-expected)<.12,name+": expected "+expected+" got "+actual);}
    private ServerPlayerEntity player(MinecraftServer server,String name,double x,double y,double z)throws Exception{
        var helper=com.anton.elementalwands.arena.GuardianNaveSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,ServerWorld.class,UUID.class,String.class,double.class,double.class,double.class);
        helper.setAccessible(true);return (ServerPlayerEntity)helper.invoke(null,server,world,UUID.randomUUID(),name,x,y,z);
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
