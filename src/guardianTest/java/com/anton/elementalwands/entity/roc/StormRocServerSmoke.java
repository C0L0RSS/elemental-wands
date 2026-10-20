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
    /** A quick head attack that only puts the Roc in its brawl: it sweeps where its target first stood, so it can't follow them into a later check. */
    private static final StormRocRules.Action SETUP=StormRocRules.Action.SWING_LEFT;
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
    private final Set<UUID> chainMarkers = new HashSet<>();
    private final Set<UUID> brawlStrikes = new HashSet<>();
    private Vec3d lastMark, lastBody;
    private RocEffectEntity chainMark, live, aimedAtSlam;
    private int markerAt;
    private StormRocEntity managed;

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
        if (++tick > 5000) throw new AssertionError("Roc server fixture timeout at " + step);
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
            boss.testAction(target,SETUP);
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
                    target.setHealth(200);dodge.setHealth(200);boss.testAction(target,SETUP);strike=boss.testStrike(target.getEntityPos(),false);
                    target.setPosition(53.5,101,.5);}
            }
            case 2 -> {
                if(elapsed==35){require(target.getHealth()==200,"Marked strike tracked a dodging player");checks.add("Locked strike aim and moving dodge");boss.testAction(target,SETUP);
                    RocEffectEntity a=boss.testStrike(target.getEntityPos(),false),b=boss.testStrike(target.getEntityPos(),true);
                    require(a!=null&&b!=null&&a.getEntityPos().subtract(b.getEntityPos()).horizontalLength()>=Math.max(a.radius(),b.radius())+.04,"Lightning circle centers stacked inside one another");
                    require(!boss.damage(world,outsider.getDamageSources().playerAttack(outsider),100),"Outsider damaged Roc");
                    checks.add("Lightning circle centers separated and outsider cannot damage Roc");next();boss.stopFight();}
            }
            case 3 -> {
                if(elapsed==5){
                    require(effects().isEmpty(),"Stop left Roc effects alive");require(target.getHealth()==200,"Cancelled strike still damaged");checks.add("Cancellation clears effects and delayed damage");
                    boss.startFight(CENTRE,List.of(target,dodge));boss.testAction(target,SETUP);
                    target.setPosition(-10,101,.5);target.setHealth(200);target.setVelocity(Vec3d.ZERO);tornado=boss.testTornado(target.getEntityPos());
                }
                if(elapsed==6+StormRocRules.TORNADO_HOLD/2)require(target.getVelocity().y>.5&&target.getHealth()==200,"Tornado did not catch and whirl its challenger up unhurt: "+target.getVelocity());
                if(elapsed==8+StormRocRules.TORNADO_HOLD){
                    damage(200-target.getHealth(),6,"Tornado catch release diamond damage");
                    require(target.getVelocity().y>0&&target.getVelocity().multiply(1,0,1).dotProduct(CENTRE.toCenterPos().subtract(target.getEntityPos()))>0,"Tornado release did not throw inward");
                    target.setPosition(tornado.getEntityPos().x+StormRocRules.TORNADO_RADIUS*.6,101,tornado.getEntityPos().z);target.setVelocity(Vec3d.ZERO);
                }
                if(elapsed==10+StormRocRules.TORNADO_HOLD)require(target.getAttributeValue(EntityAttributes.MOVEMENT_SPEED)<.08,"Tornado winds did not slow challenger");
                if(elapsed==18+StormRocRules.TORNADO_HOLD){target.setPosition(tornado.getEntityPos().x,101,tornado.getEntityPos().z);target.setVelocity(Vec3d.ZERO);}
                if(elapsed==19+StormRocRules.TORNADO_HOLD){require(Math.abs(target.getVelocity().y)<.01,"Tornado immediately recaught/juggled released challenger");boss.stopFight();}
                if(elapsed==21+StormRocRules.TORNADO_HOLD){
                    require(Math.abs(target.getAttributeValue(EntityAttributes.MOVEMENT_SPEED)-.1)<.0001,"Tornado slow survived cancellation");
                    require(effects().isEmpty(),"Tornado survived cancellation");checks.add("Tornado catch and whirl, timed release, inward throw, winds slowing, recatch immunity and cleanup");
                    target.setPosition(30.5,101,.5);target.setVelocity(Vec3d.ZERO);target.setHealth(200);
                    boss.startFight(CENTRE,List.of(target,dodge));boss.setHealth(boss.getMaxHealth()*.74f);
                }
                if(elapsed==23+StormRocRules.TORNADO_HOLD){require(effects().stream().filter(e->e.kind()==RocEffectEntity.Kind.TORNADO).count()==2,"75-percent health did not add second tornado");checks.add("Second tornado unlocks below75percent health");
                    boss.stopFight();target.setHealth(200);dodge.setHealth(200);boss.startFight(CENTRE,List.of(target,dodge));
                }
                // A fresh air stretch: on its spire it rears into its call, then calls a large strike on everyone.
                int call=24+StormRocRules.TORNADO_HOLD+StormRocRules.CALL_AT;
                if(elapsed==call)require(boss.poseName().equals("call"),"Roc did not rear into its call: "+boss.poseName());
                if(elapsed==call+StormRocRules.CALL_VOLLEY+1){
                    require(effects().stream().anyMatch(e->e.kind()==RocEffectEntity.Kind.CALL_BOLT),"Call raised no bolt into the clouds");
                    require(effects().stream().filter(e->e.kind()==RocEffectEntity.Kind.LARGE_STRIKE).count()>=2,"Call did not strike every challenger");
                    require(boss.lookTarget()==target.getId()||boss.lookTarget()==dodge.getId(),"Roc does not look at the player it strikes");
                    checks.add("Spire call: rears up, bolt into the clouds, a large strike at each challenger, and it watches its mark");
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
                    require(!boss.phaseTwo()&&boss.stage()!=StormRocRules.Stage.CHARGE,"First landing ended entire phase");checks.add("Landing health quota triggers takeoff safely");
                    boss.testAction(target,SETUP);boss.setHealth(boss.getMaxHealth()*.5f);next();
                }
            }
            case 6 -> {
                if(elapsed==1)require(boss.stage()==StormRocRules.Stage.TAKEOFF,"Phase gate skipped the takeoff animation");
                if(elapsed>StormRocRules.TAKEOFF_TICKS+5){require(boss.stage()==StormRocRules.Stage.CHARGE&&!boss.fightComplete(),"Half-health takeoff did not begin the phase change: "+boss.status());require(effects().stream().allMatch(e->e.kind()==RocEffectEntity.Kind.TORNADO),"Phase change retained damaging effects");checks.add("Half health takeoff begins the phase change with only tornadoes left");
                    boss.discard();
                    target.setPosition(45.5,135,.5);dodge.setPosition(-45.5,135,.5);
                    target.setHealth(200);dodge.setHealth(200);
                    boss=new StormRocEntity(ModEntities.STORM_ROC,world);boss.startFight(CENTRE,List.of(target,dodge));world.spawnEntity(boss);
                    require(boss.skipToPhaseTwo()&&boss.stage()==StormRocRules.Stage.CHARGE,"Skip did not directly start CHARGE");
                    damage(boss.getHealth(),boss.getMaxHealth()*.5f,"Skip half health");
                    float health=boss.getHealth();require(!boss.damage(world,target.getDamageSources().playerAttack(target),100),"CHARGE accepted damage");
                    require(boss.getHealth()==health,"CHARGE lost health");step=21;elapsed=0;}
            }
            case 7 -> {
                if(elapsed==180){
                    target.onTeleportationDone();dodge.onTeleportationDone();
                    require(StormSummit.nearestCentre(target.getEntityPos()).equals(StormSummit.centre(0,1)),"Authored lifecycle slot did not finish building");
                    for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)world.setChunkForced(64+x,z,false);
                    String result=StormSummitManager.start(target);require(result.startsWith("Started the fight with 2"),"Sealed start failed: "+result);
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
                    String start=StormSummitManager.start(dodge);require(start.startsWith("Started the fight with 1"),"Retry after wipe failed: "+start);
                    require(StormSummitManager.reset(dodge).contains("reset"),"Operator reset failed");
                    require(!StormRocEncounter.activeAt(StormSummit.centre(0,1)),"Reset retained fight");
                    require(StormSummitManager.start(dodge).startsWith("Started the fight with 1"),"Second retry start failed");
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
                    boss.testAction(dodge,SETUP);boss.setHealth(boss.getMaxHealth()*.5f);next();
                }
            }
            case 10 -> {
                if(elapsed==StormRocRules.TAKEOFF_TICKS+StormRocRules.CHARGE_TICKS+4){
                    require(boss.phaseTwo(),"Managed encounter did not enter phase two");boss.setHealth(1.005f);
                }
                if(elapsed>StormRocRules.TAKEOFF_TICKS+StormRocRules.CHARGE_TICKS+8){
                    require(!StormRocEncounter.activeAt(StormSummit.centre(0,1)),"Phase completion did not end managed encounter");
                    require(dodge.interactionManager.getGameMode()==GameMode.SURVIVAL,"Phase completion retained spectator mode");
                    require(world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(880,70,-160,1180,260,160),e->!e.isRemoved()).isEmpty(),"Phase completion awarded loot");
                    checks.add("Reset, retry and managed phase completion restore players without victory loot");
                    StormSummitManager.enter(target,1);StormSummitManager.enter(outsider,1);target.onTeleportationDone();outsider.onTeleportationDone();dodge.onTeleportationDone();
                    target.setHealth(target.getMaxHealth());dodge.setHealth(dodge.getMaxHealth());outsider.setHealth(outsider.getMaxHealth());
                    require(StormSummitManager.start(dodge).startsWith("Started the fight with 3"),"Death/disconnect retry failed");
                    next();
                }
            }
            case 11 -> {
                if(elapsed==3){
                    target.experienceLevel=17;target.totalExperience=400;target.experienceProgress=.25f;
                    // The realm death is a lethal tornado throw: eliminating the challenger edits the Roc's
                    // caught list while it is still releasing them.
                    managed=managedBoss(StormSummit.centre(0,1));require(managed!=null,"Managed boss missing before tornado death");
                    tornado=managed.testTornado(target.getEntityPos());
                    target.setPosition(tornado.getX(),101,tornado.getZ());target.setVelocity(Vec3d.ZERO);
                }
                if(elapsed==20)target.setHealth(2);
                if(elapsed>3&&target.isAlive()){if(elapsed>70)throw new AssertionError("Tornado throw never killed the caught challenger");return;}
                if(elapsed>3){
                    require(managed.fighting()&&!managed.isRemoved(),"Lethal tornado throw broke the managed Roc: "+managed.status());
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
                        case BITE,SWING_LEFT,SWING_RIGHT -> boss.modelToWorld(StormRocPose.socket(action.pose,action==StormRocRules.Action.BITE?action.impact:action.impact+StormRocRules.SWING_SWEEP/2,"beak"));
                        case SCREAM -> boss.modelToWorld(StormRocPose.socket(action.pose,action.impact,"beak")).add(Vec3d.fromPolar(0,boss.getYaw()).multiply(20));
                        case WING -> new Vec3d(12.5,101,.5);
                        case POUNCE -> new Vec3d(.5,101,30.5);
                        case STAMPEDE -> new Vec3d(.5,101,45.5);
                    };
                    // The bite's prey stands 6 blocks beyond its bill's reach: it must leap to bite there.
                    if(action==StormRocRules.Action.BITE)point=point.add(Vec3d.fromPolar(0,boss.getYaw()).multiply(6));
                    target.setPosition(point.x,101,point.z);outsider.setPosition(point.x,101,point.z);
                    if(action==StormRocRules.Action.SWING_LEFT||action==StormRocRules.Action.SWING_RIGHT||action==StormRocRules.Action.WING)dodge.setPosition(point.x,135,point.z);
                    // The bite's burst reaches past its target: a player 4.5 blocks to the side is caught too.
                    if(action==StormRocRules.Action.BITE){Vec3d side=Vec3d.fromPolar(0,boss.getYaw()+90).multiply(4.5);dodge.setPosition(point.x+side.x,101,point.z+side.z);dodge.setVelocity(Vec3d.ZERO);}
                }
                int hitAt=action==StormRocRules.Action.SWING_LEFT||action==StormRocRules.Action.SWING_RIGHT?action.impact+StormRocRules.SWING_SWEEP+1:action==StormRocRules.Action.STAMPEDE?55
                        :action==StormRocRules.Action.BITE?StormRocRules.BITE_LEAP_IMPACT+1
                        // The Thunderclap's ring rolls out from the clap and reaches its target 12 blocks off later.
                        :action==StormRocRules.Action.WING?action.impact+12:action.impact+1;
                // Its bite's prey stands out of reach, so it plays the longer leap.
                int done=action==StormRocRules.Action.BITE?StormRocRules.BITE_LEAP_DURATION:action.duration;
                if(elapsed==hitAt){
                    float expected=switch(action){case SWING_LEFT,SWING_RIGHT->10;case BITE,SCREAM,STAMPEDE->16;case WING->6;case POUNCE->20;};
                    damage(200-target.getHealth(),expected,action+" diamond hit damage");
                    if(action==StormRocRules.Action.STAMPEDE)require(boss.getZ()>40,"Stampede did not reach its locked ranged target: "+boss.getEntityPos());
                    if(action==StormRocRules.Action.BITE){
                        require(dodge.getHealth()<200,"Thunder Bite burst missed a player beside its target");
                        require(target.getVelocity().horizontalLengthSquared()>.01,"Thunder Bite did not throw its target back");
                        require(boss.poseName().equals("bite_leap"),"Thunder Bite did not switch to its leap: "+boss.poseName());
                        require(boss.getEntityPos().subtract(CENTRE.toCenterPos()).horizontalLength()>5,"Thunder Bite did not leap to its prey: "+boss.getEntityPos());
                        checks.add("Thunder Bite leaps to prey beyond its reach, bursts over everyone near its bite and throws them back");
                    }
                    else require(dodge.getHealth()==200,action+" hit distant dodger");
                    require(outsider.getHealth()==200,action+" hit outsider");
                    if(action==StormRocRules.Action.SCREAM)require(target.getVelocity().horizontalLengthSquared()>.01,"Storm Scream did not shove its target back along its line");
                }
                // The sweep's crackle wave rolls out over the front: a player on the rock 19 blocks
                // ahead, beyond the bill, is caught by it. The dodger moves there off its spot above
                // the sweep as the sweep ends, before the wave (from 10 blocks) has passed under it.
                if(action.swing()&&elapsed==action.impact+StormRocRules.SWING_SWEEP){Vec3d ahead=CENTRE.toCenterPos().add(Vec3d.fromPolar(0,boss.getYaw()).multiply(19));dodge.setPosition(ahead.x,101,ahead.z);dodge.setVelocity(Vec3d.ZERO);}
                if(action.swing()&&elapsed==hitAt+10){
                    require(dodge.getHealth()<200,action+" crackle wave missed a grounded player ahead");
                    checks.add(action+" crackle wave rolls out over the front and catches a grounded player");
                }
                // The landing ring reaches a crushed player two ticks later; it must not add to the crush.
                if(action==StormRocRules.Action.POUNCE&&elapsed==action.impact+7){
                    damage(200-target.getHealth(),20,"Pounce ring stacked on its crush");
                    checks.add("Crushed player takes the pounce once, not crush plus ring");
                }
                if(elapsed==done+3){
                    checks.add(action+" hit-frame damage, dodge and outsider exclusion");
                    if(++melee>=StormRocRules.Action.values().length){target.setPosition(30.5,101,.5);target.setHealth(200);target.setVelocity(Vec3d.ZERO);boss.stopFight();boss.startFight(CENTRE,List.of(target,dodge));boss.testAction(target,SETUP);target.setPosition(.5,101,.5);dodge.setPosition(20.5,104,.5);dodge.setHealth(200);outsider.setPosition(.5,101,.5);outsider.setHealth(200);boss.testDive(new Vec3d(.5,101.03,.5));step=16;elapsed=0;}
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
                    boss.testAction(target,StormRocRules.Action.WING);boss.setPosition(45.5,101.03,.5);
                }
                if(elapsed==StormRocRules.Action.WING.impact+13){
                    damage(200-target.getHealth(),6,"Wing hit at rim");Vec3d impulse=target.getVelocity().multiply(1,0,1);
                    require(impulse.dotProduct(CENTRE.toCenterPos().subtract(target.getEntityPos()))>0,"Wing knocked rim player toward the edge");
                    require(StormSummit.beyondRim(CENTRE,target.getEntityPos().add(impulse.multiply(12)))<=-8,"Wing impulse projected outside interior safety margin");
                    require(outsider.getHealth()==200,"Rim wing hit outsider");require(dodge.getHealth()==200,"Rim wing hit elevated dodger");checks.add("Rim wing impulse points inward and stops within interior margin");
                }
                if(elapsed==StormRocRules.Action.WING.impact+15){target.setPosition(30.5,101,.5);target.setHealth(200);target.setVelocity(Vec3d.ZERO);dodge.setPosition(30.5,101,12.5);boss.stopFight();boss.startFight(CENTRE,List.of(target,dodge));boss.testAction(target,SETUP);step=18;elapsed=0;}
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
                    boss.stopFight();boss.startFight(CENTRE,List.of(target,dodge));boss.testAction(target,SETUP);step=19;elapsed=0;
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
                    boss.stopFight();boss.startFight(CENTRE,List.of(target,dodge));boss.testAction(target,SETUP);step=20;elapsed=0;
                }
            }
            case 20 -> {
                if(elapsed==1)placeFootFire(target,false);
                if(elapsed==5){
                    require(boss.getHealth()<flowerHealth,"Actual owned foot coals dealt no Roc damage");
                    require(Math.abs(WandProgression.get(target,WizardAffinity.FIRE).xp()-flowerXp-(flowerHealth-boss.getHealth()))<.01,"Actual foot coals did not credit canonical health XP");
                    TemporaryBlockManager.restoreTemporaryBlocks(world,firePlacement);boss.stopFight();boss.startFight(CENTRE,List.of(target,dodge));boss.testAction(target,SETUP);placeFootFire(target,true);
                }
                if(elapsed==9){
                    require(boss.getHealth()<flowerHealth,"Actual owned foot flame dealt no Roc damage");
                    require(boss.isOnFire()&&SpellCombat.burnOwner(boss,world.getDamageSources().onFire())==target,"Actual placed Inferno flame did not ignite canonical boss with caster owner");
                    require(Math.abs(WandProgression.get(target,WizardAffinity.FIRE).xp()-flowerXp-(flowerHealth-boss.getHealth()))<.01,"Actual foot flame did not credit canonical health XP");
                    TemporaryBlockManager.restoreTemporaryBlocks(world,firePlacement);boss.extinguish();boss.stopFight();boss.startFight(CENTRE,List.of(target,dodge));boss.testAction(target,SETUP);placeFootFire(outsider,false);
                }
                if(elapsed==13){
                    require(boss.getHealth()==flowerHealth&&WandProgression.get(outsider,WizardAffinity.FIRE).xp()==flowerXp,"Nonparticipant foot coals damaged/rewarded Roc");
                    TemporaryBlockManager.restoreTemporaryBlocks(world,firePlacement);placeFootFire(outsider,true);
                }
                if(elapsed==17){
                    require(boss.getHealth()==flowerHealth&&!boss.isOnFire()&&WandProgression.get(outsider,WizardAffinity.FIRE).xp()==flowerXp,"Nonparticipant foot flame damaged/ignited/rewarded Roc");
                    TemporaryBlockManager.restoreTemporaryBlocks(world,firePlacement);checks.add("Actual owned foot coals/flame damage and credit XP/burn owner; outsider ground fire ignored");
                    boss.stopFight();boss.startFight(CENTRE,List.of(target,dodge));boss.testAction(target,SETUP);step=4;elapsed=0;
                }
            }
            case 21 -> {
                if(elapsed==39)require(!boss.charged(),"Roc charged before tick40");
                if(elapsed==40)require(boss.charged(),"Roc not charged at tick40");
                if(elapsed==90)require(effects().stream().anyMatch(e->e.kind()==RocEffectEntity.Kind.CHARGE_BOLT),"Charge bolt missing at tick90");
                if(elapsed==StormRocRules.CHARGE_TICKS+2){
                    require(boss.phaseTwo()&&boss.stage()==StormRocRules.Stage.AIR,"CHARGE did not finish in phase-two AIR: "+boss.status());
                    damage(boss.combat().healthFloor(),boss.getMaxHealth()*.375f,"Phase-two first landing floor");
                    var ts=effects().stream().filter(e->e.kind()==RocEffectEntity.Kind.TORNADO).toList();
                    require(ts.size()==3,"Phase two did not have three tornadoes");
                    require(effects().stream().anyMatch(e->e.kind()==RocEffectEntity.Kind.STORM&&flatDistance(e.getEntityPos(),CENTRE.toCenterPos())<1),"Phase two has no storm marker at the summit centre");
                    for(int i=0;i<ts.size();i++){require(ts.get(i).radius()==7,"Phase-two tornado radius");for(int j=0;j<i;j++)require(flatDistance(ts.get(i).getEntityPos(),ts.get(j).getEntityPos())>=18,"Phase-two tornado spacing below18");}
                    checks.add("Skip starts invulnerable CHARGE, tick40 charged flag, tick90 bolt, phase-two AIR, .375 landing floor and three radius7 separated tornadoes");
                    boss.testChain();step=22;elapsed=0;
                }
            }
            case 22 -> {
                var marks=effects().stream().filter(e->e.kind()==RocEffectEntity.Kind.DIVE_MARKER).toList();
                for(var m:marks)if(chainMarkers.add(m.getUuid())){
                    int n=chainMarkers.size();int expected=n==1?20:68+(n-2)*32;
                    require(elapsed==expected,"Chain marker"+n+" at tick"+elapsed+" expected"+expected);
                    require(m.radius()==8&&m.warningTicks()==(n==1?40:24),"Chain marker radius/warning");
                    if(lastMark!=null)require(flatDistance(lastMark,m.getEntityPos())<=25.01,"Chain hop exceeded25");
                    if(n<4)require(StormSummit.beyondRim(CENTRE,m.getEntityPos())<=-13+.001,"Chain mark lacked rim margin");
                    else require(flatDistance(CENTRE.toCenterPos(),m.getEntityPos())<=24.01,"Final chain mark outside24");
                    lastMark=m.getEntityPos();chainMark=m;markerAt=elapsed;
                    if(n==2){target.setPosition(lastMark.x,lastMark.y+1,lastMark.z);target.setVelocity(Vec3d.ZERO);target.setHealth(200);before=200;}
                }
                if(elapsed==59)aimedAtSlam=boss.testStrike(new Vec3d(40,101,15),false);
                if(elapsed==60){require(aimedAtSlam!=null&&aimedAtSlam.isRemoved(),"First slam retained aimed strike");}
                if(chainMark!=null){
                    int due=markerAt+chainMark.warningTicks();
                    if(elapsed<due)require(!chainMark.isRemoved(),"Chain marker ended before slam");
                    if(elapsed==due){
                        require(chainMark.isRemoved(),"Chain marker survived slam");
                        // The body hops no further than its circle: the bill is 26 blocks ahead of the feet.
                        if(lastBody!=null)require(flatDistance(lastBody,boss.getEntityPos())<=25.01,"Chain body hop exceeded25: "+flatDistance(lastBody,boss.getEntityPos()));
                        lastBody=boss.getEntityPos();
                        require(effects().stream().anyMatch(e->e.kind()==RocEffectEntity.Kind.SHOCKWAVE&&e.radius()==26&&flatDistance(e.getEntityPos(),lastMark)<.01&&e.effectTicks(0)==0),"Slam did not emit radius26 ring");
                        if(chainMarkers.size()==2){damage(before-target.getHealth(),12,"Later chain slam diamond damage");target.setPosition(55,135,0);target.setVelocity(Vec3d.ZERO);}
                    }
                }
                if(elapsed==156){require(chainMarkers.size()==4&&boss.stage()==StormRocRules.Stage.STUCK,"Fourth slam did not leave STUCK with exactly4 markers");checks.add("Four chain markers, launch/warnings/pauses, hop caps, rim margins, later12HP slam, four jumpable rings and aimed-strike cleanup");step=23;elapsed=0;}
            }
            case 23 -> {
                if(boss.stage()==StormRocRules.Stage.BRAWL){
                    for(var e:effects())if((e.kind()==RocEffectEntity.Kind.SMALL_STRIKE||e.kind()==RocEffectEntity.Kind.LARGE_STRIKE)&&e.effectTicks(0)==0){require(e.radius()==3.5||e.radius()==7,"Brawl random strike radius");brawlStrikes.add(e.getUuid());}
                }
                if(elapsed==165){
                    require(boss.stage()==StormRocRules.Stage.BRAWL&&!brawlStrikes.isEmpty(),"No random strikes during following BRAWL");checks.add("Random phase-two strikes continue during BRAWL");
                    target.setPosition(45.5,101,.5);dodge.setPosition(45.5,101,20.5);beginStrike(false);require(strike.radius()==3.5,"Phase-two small radius");step=24;elapsed=0;
                }
            }
            case 24 -> {
                if(elapsed==19)require(target.getHealth()==before,"Phase-two small hit before warning");
                if(elapsed==21)damage(before-target.getHealth(),10,"Phase-two small first bolt diamond damage");
                if(elapsed==35){target.setPosition(45.5,101,.5);beginStrike(true);require(strike.radius()==7,"Phase-two large radius");step=25;elapsed=0;}
            }
            case 25 -> {
                if(elapsed==29)require(target.getHealth()==before,"Phase-two large hit before warning");
                if(elapsed==31)damage(before-target.getHealth(),18,"Phase-two large first bolt diamond damage");
                if(elapsed==49)require(effects().stream().noneMatch(e->e.kind()==RocEffectEntity.Kind.LIVE_GROUND),"Live ground appeared before third bolt");
                if(elapsed==50){
                    live=effects().stream().filter(e->e.kind()==RocEffectEntity.Kind.LIVE_GROUND).findFirst().orElseThrow(()->new AssertionError("Third large bolt did not leave LIVE_GROUND"));
                    require(live.radius()==7&&live.getEntityPos().distanceTo(strike.getEntityPos())<.01,"Live ground differs from large circle");
                    target.setHealth(200);dodge.setHealth(200);outsider.setHealth(200);
                    dodge.setPosition(live.getX()+10,101,live.getZ());outsider.setPosition(live.getX(),101,live.getZ());step=26;elapsed=0;
                }
            }
            case 26 -> {
                if(elapsed<=50){int pulses=elapsed/10;damage(200-target.getHealth(),pulses,"Live ground grounded pulse damage");require(dodge.getHealth()==200,"Live ground hit outside player");require(outsider.getHealth()==200,"Live ground hit outsider");}
                if(elapsed==52){require(live.isRemoved(),"Live ground survived52ticks");checks.add("Phase-two small10HP/large18HP and radii; third bolt leaves matching LIVE_GROUND, five1HP grounded pulses, outside/outsider exclusion and expiry");
                    // A separate field checks the airborne case without sacrificing the full grounded pulse run.
                    boss.testAction(target,SETUP);target.setPosition(45.5,104,.5);strike=boss.testStrike(target.getEntityPos(),true);step=27;elapsed=0;}
            }
            case 27 -> {
                if(elapsed==51){target.setHealth(200);target.setOnGround(false);}
                if(elapsed==102){require(target.getHealth()==200,"Live ground hit airborne player");
                    // Small bolt first, then a large one inside the stacking window: the large still costs its full 18.
                    boss.testAction(target,SETUP);target.setPosition(45.5,101,.5);target.setVelocity(Vec3d.ZERO);target.setOnGround(true);
                    target.setHealth(200);target.timeUntilRegen=0;
                    strike=boss.testStrike(new Vec3d(52.3,101,.5),true);require(strike!=null&&strike.radius()==7,"Stacking large strike missing");step=29;elapsed=0;}
            }
            case 29 -> {
                if(elapsed==6){RocEffectEntity small=boss.testStrike(target.getEntityPos(),false);
                    require(small!=null&&flatDistance(small.getEntityPos(),target.getEntityPos())<=small.radius(),"Stacking small strike does not cover target");}
                if(elapsed==27)damage(200-target.getHealth(),10,"Small bolt before the large one");
                if(elapsed==31){damage(200-target.getHealth(),18,"Large bolt after a small one stacks to the large's full cost");
                    // Large first, then a small one inside the window: the small adds nothing.
                    boss.testAction(target,SETUP);target.setHealth(200);target.timeUntilRegen=0;
                    strike=boss.testStrike(new Vec3d(52.3,101,.5),true);step=30;elapsed=0;}
            }
            case 30 -> {
                if(elapsed==12)boss.testStrike(target.getEntityPos(),false);
                if(elapsed==31)damage(200-target.getHealth(),18,"Large bolt");
                if(elapsed==33){damage(200-target.getHealth(),18,"Small bolt inside the large's window adds nothing");
                    checks.add("Stacked hits compare costs after armor: small-then-large and large-then-small both cost the large's 18");
                    boss.setHealth(1.005f);step=28;elapsed=0;}
            }
            case 28 -> {
                require(boss.stage()==StormRocRules.Stage.COMPLETE&&boss.defeated()&&boss.fightComplete(),"Phase-two health threshold did not defeat Roc");require(effects().isEmpty(),"Defeat left effects alive");checks.add("Airborne live-ground exclusion;1.005HP defeats on next tick, COMPLETE/defeated/fightComplete and all effects cleared");
                boss.discard();target.setHealth(200);dodge.setHealth(200);
                for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++){world.getChunk(64+x,z);world.setChunkForced(64+x,z,true);}
                StormSummitManager.enter(target,1);StormSummitManager.enter(dodge,1);step=7;elapsed=0;
            }
            default -> throw new AssertionError("Unknown fixture stage");
        }
    }
    private static double flatDistance(Vec3d a,Vec3d b){return a.subtract(b).horizontalLength();}
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
    private void beginStrike(boolean large){target.setHealth(200);target.timeUntilRegen=0;dodge.setHealth(200);outsider.setHealth(200);boss.testAction(target,SETUP);before=target.getHealth();target.setVelocity(Vec3d.ZERO);velocity=target.getVelocity();strike=boss.testStrike(target.getEntityPos(),large);require(strike!=null,"testStrike did not create effect");}
    private void answerChecks(){
        var wing=List.of(StormRocRules.Action.WING);
        require(StormRocCombat.answers(2,1,true,null).equals(wing),"Target between the Roc's feet had no answer");
        require(StormRocCombat.answers(6,1,true,null).equals(wing),"Target under the Roc's neck had no answer: "+StormRocCombat.answers(6,1,true,null));
        require(StormRocCombat.answers(6,1,true,StormRocRules.Action.WING).equals(List.of(StormRocRules.Action.POUNCE)),"Target staying under the neck was not pounced");
        require(StormRocCombat.answers(5,-1,true,null).equals(wing),"Target behind the Roc drew no wing wave");
        var front=StormRocCombat.answers(12.5,1,true,null);
        require(front.containsAll(List.of(StormRocRules.Action.BITE,StormRocRules.Action.SWING_LEFT,StormRocRules.Action.SCREAM)),"Target in bill reach lacked head attacks: "+front);
        require(!StormRocCombat.answers(7.5,1,true,null).contains(StormRocRules.Action.BITE),"Bite offered where its bill cannot reach");
        require(!StormRocCombat.answers(12.5,1,true,StormRocRules.Action.BITE).contains(StormRocRules.Action.BITE),"Brawl repeated its last attack");
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
