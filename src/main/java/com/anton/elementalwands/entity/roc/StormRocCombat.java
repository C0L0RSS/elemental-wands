package com.anton.elementalwands.entity.roc;

import com.anton.elementalwands.summit.StormSummit;
import com.anton.elementalwands.summit.StormSummitLayout;
import java.util.*;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.boss.*;
import net.minecraft.entity.damage.*;
import net.minecraft.registry.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.Difficulty;
import static com.anton.elementalwands.entity.roc.StormRocRules.*;

/** Telegraph -> committed hit -> opening. All encounter decisions and damage stay on the server. */
final class StormRocCombat {
    private static final RegistryKey<DamageType> DAMAGE=RegistryKey.of(RegistryKeys.DAMAGE_TYPE,Identifier.of("elementalwands","roc_storm"));
    private static final Identifier SLOW=Identifier.of("elementalwands","roc_tornado_drag");
    private static final class Strike {
        final RocEffectEntity effect; int bolts;
        Strike(RocEffectEntity effect){this.effect=effect;}
    }
    private static final class Tornado {
        final RocEffectEntity effect; double angle;
        Tornado(RocEffectEntity effect,double angle){this.effect=effect;this.angle=angle;}
    }
    private record Caught(Tornado tornado,long at) {}
    private static final class Ring {
        final RocEffectEntity effect;final Set<UUID> hit=new HashSet<>();
        Ring(RocEffectEntity effect){this.effect=effect;}
    }
    private final StormRocEntity boss;
    private final Set<UUID> roster=new LinkedHashSet<>();
    private final Map<UUID,Double> threat=new HashMap<>();
    private final Map<UUID,Vec3d> previous=new HashMap<>(),motion=new HashMap<>();
    private final Map<UUID,Long> immunity=new HashMap<>(),footHurt=new HashMap<>(),cawAt=new HashMap<>();
    private final Map<UUID,Caught> caught=new HashMap<>();
    private final Set<UUID> slowed=new HashSet<>();
    private final List<Strike> strikes=new ArrayList<>();
    private final List<Tornado> tornadoes=new ArrayList<>();
    private final List<Ring> rings=new ArrayList<>();
    private final List<RocEffectEntity> effects=new ArrayList<>();
    private final Set<UUID> actionHits=new HashSet<>();
    private final ServerBossBar bar=new ServerBossBar(Text.translatable("entity.elementalwands.storm_roc"),BossBar.Color.YELLOW,BossBar.Style.PROGRESS);
    private BlockPos centre=BlockPos.ORIGIN;
    private Stage stage=Stage.IDLE;
    private Action action,last;
    private long since,nextAction,actionAt,nextSmall,nextLarge,nextAmbient,nextDistant,nextStampede,lastRanged;
    private Vec3d from=Vec3d.ZERO,to=Vec3d.ZERO,mark=Vec3d.ZERO;
    private UUID target;
    private float floor;
    private int perch,round,selection;
    private boolean review,phasePending;
    private RocEffectEntity marker;
    StormRocCombat(StormRocEntity boss){this.boss=boss;}
    Stage stage(){return stage;}
    boolean active(){return stage!=Stage.IDLE&&stage!=Stage.COMPLETE;}
    boolean participant(UUID id){return roster.contains(id);}
    void removeParticipant(UUID id){roster.remove(id);caught.remove(id);immunity.remove(id);clearSlow(id);}
    float healthFloor(){return Math.max(boss.getMaxHealth()*.5f,floor);}
    String status(){return "Storm Roc: "+stage.name().toLowerCase(Locale.ROOT)+(action==null?"":" / "+action.name().toLowerCase(Locale.ROOT))
        +", health "+Math.round(boss.getHealth())+"/"+Math.round(boss.getMaxHealth())+", tornadoes "+tornadoes.size()+", fighters "+roster.size();}
    private ServerWorld world(){return (ServerWorld)boss.getEntityWorld();}
    private long now(){return boss.getEntityWorld().getTime();}
    private Vec3d center(){return new Vec3d(centre.getX()+.5,StormSummit.SURFACE_Y+1.03,centre.getZ()+.5);}
    private List<ServerPlayerEntity> players(){return world().getPlayers(p->boss.eligible(p)&&StormSummit.nearestCentre(p.getEntityPos()).equals(centre));}
    void start(BlockPos centre,List<ServerPlayerEntity> players){
        cancel();this.centre=centre.toImmutable();for(var p:players)roster.add(p.getUuid());
        int health=health(Math.max(1,players.size()));boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(health);boss.setHealth(health);
        floor=health*.875f;phasePending=false;review=false;round=0;perch=0;selection=0;
        boss.setPosition(perch(0));boss.face(center());air();tornado(center().add(-19,0,10));
    }
    void cancel(){
        for(var e:effects)e.discard();effects.clear();strikes.clear();tornadoes.clear();rings.clear();
        for(UUID id:new HashSet<>(slowed))clearSlow(id);
        caught.clear();immunity.clear();footHurt.clear();cawAt.clear();roster.clear();threat.clear();previous.clear();motion.clear();
        bar.clearPlayers();stage=Stage.IDLE;action=null;marker=null;review=false;
    }
    private void clearHazards(){for(var e:effects)e.discard();effects.clear();strikes.clear();tornadoes.clear();rings.clear();caught.clear();for(UUID id:new HashSet<>(slowed))clearSlow(id);}
    void damaged(ServerPlayerEntity player,float amount){
        threat.merge(player.getUuid(),(double)amount,Double::sum);
        if(player.squaredDistanceTo(boss)>28*28)lastRanged=now();
        if(boss.getHealth()<=boss.getMaxHealth()*.5f+.01)phasePending=true;
    }
    void tick(ServerWorld world){
        if(!active())return;
        List<ServerPlayerEntity> players=players();
        if(players.isEmpty()){cancel();return;}
        for(var p:new HashSet<>(bar.getPlayers()))if(!players.contains(p))bar.removePlayer(p);
        for(var p:players){bar.addPlayer(p);Vec3d before=previous.put(p.getUuid(),p.getEntityPos());
            Vec3d delta=before==null?Vec3d.ZERO:p.getEntityPos().subtract(before);motion.put(p.getUuid(),delta.horizontalLengthSquared()>.36?Vec3d.ZERO:delta.multiply(1,0,1));}
        bar.setPercent(Math.clamp(boss.getHealth()/boss.getMaxHealth(),0,1));
        // Distant cosmetic bolts can be outside entity-ticking chunks; their owner still
        // expires them on schedule instead of letting an unticked flash repeat forever.
        effects.removeIf(e->{if(!e.isRemoved()&&e.effectTicks(0)>e.duration())e.discard();return e.isRemoved();});
        if(!review&&boss.getHealth()<=boss.getMaxHealth()*.75f&&tornadoes.size()<2)tornado(center().add(19,0,-12));
        tickStrikes(players);tickTornadoes(players);tickRings(players);caw(players);
        if(stage==Stage.STUCK||stage==Stage.BRAWL)groundSpells();
        if(boss.getHealth()<=boss.getMaxHealth()*.5f+.01&&stage!=Stage.TAKEOFF)takeoff(players);
        long t=now()-since;
        switch(stage){
            case AIR -> {
                storm(players);
                int cycle=(int)(t%120);
                if(cycle==75){from=boss.getEntityPos();perch=(perch+1)%StormSummitLayout.PERCHES.length;to=perch(perch);boss.pose("flight");}
                if(cycle>=75){double f=Math.min(1,(cycle-75)/44.0);boss.setPosition(from.lerp(to,smooth(f)).add(0,Math.sin(f*Math.PI)*12,0));boss.face(to);}
                if(cycle==0){boss.setPosition(perch(perch));boss.face(center());boss.pose("perched");}
                if(t>=airDuration())dive(chooseLanding());
            }
            case DIVE -> {
                if(!review)storm(players);
                double f=Math.clamp(t/(double)DIVE_TICKS,0,1);
                Vec3d apex=mark.add(0,64,0);
                boss.setPosition(f<.6?from.lerp(apex,smooth(f/.6)):apex.lerp(to,smooth((f-.6)/.4)));
                if(t>=DIVE_TICKS){
                    boss.setPosition(to);if(marker!=null)marker.discard();
                    for(var p:players)if(horizontal(p.getEntityPos(),mark)<=DIVE_RADIUS+p.getWidth()*.5&&p.getY()<mark.y+26)hurt(p,DIVE_DAMAGE);
                    ring(mark,70);sound(mark,SoundEvents.ENTITY_GENERIC_EXPLODE.value(),3,.55f);
                    // Phase One's sky stops striking at impact; permanent tornadoes remain.
                    for(var s:strikes)s.effect.discard();strikes.clear();
                    enter(Stage.STUCK,"stuck");
                }
            }
            case STUCK -> {feet(players);if(t>=STUCK_TICKS){enter(Stage.BRAWL,"hunched");nextAction=now()+12;}}
            case BRAWL -> {
                if(boss.getHealth()<=floor+.01||boss.getHealth()<=boss.getMaxHealth()*.5f+.01){takeoff(players);break;}
                if(action!=null)tickAction(players);else if(now()>=nextAction&&!review)chooseAction(players);
            }
            case TAKEOFF -> {
                if(t==24)for(var p:players)if(horizontal(p.getEntityPos(),boss.getEntityPos())<35)pushSafe(p,p.getEntityPos().subtract(boss.getEntityPos()),1.8,.38);
                double f=Math.clamp((t-24)/26.0,0,1);boss.setPosition(from.lerp(to,smooth(f)).add(0,Math.sin(f*Math.PI)*12,0));
                if(t>=TAKEOFF_TICKS){
                    if(phasePending||boss.getHealth()<=boss.getMaxHealth()*.5f+.01){clearHazards();bar.clearPlayers();stage=Stage.COMPLETE;boss.pose("perched");boss.face(center());}
                    else{floor=Math.max(boss.getMaxHealth()*.5f,floor-boss.getMaxHealth()*.125f);round++;air();}
                }
            }
            default -> {}
        }
    }
    private void enter(Stage stage,String pose){this.stage=stage;since=now();action=null;boss.pose(pose);}
    private void air(){enter(Stage.AIR,"perched");nextSmall=now()+25;nextLarge=now()+80;nextAmbient=now()+35;nextDistant=now()+100;}
    private int airDuration(){return world().getDifficulty()==Difficulty.HARD?300:world().getDifficulty()==Difficulty.EASY?400:AIR_TICKS;}
    private Vec3d perch(int n){int[] p=StormSummitLayout.PERCHES[n];return new Vec3d(centre.getX()+p[0]+.5,StormSummit.SURFACE_Y+p[1],centre.getZ()+p[2]+.5);}
    private static double smooth(double f){return f*f*(3-2*f);}
    private static double horizontal(Vec3d a,Vec3d b){return a.subtract(b).horizontalLength();}
    private Vec3d onFloor(Vec3d v){return new Vec3d(v.x,center().y,v.z);}
    private Vec3d clamp(Vec3d v,double radius){Vec3d d=v.subtract(center()).multiply(1,0,1);return center().add(d.lengthSquared()>radius*radius?d.normalize().multiply(radius):d);}
    private Vec3d interior(Vec3d point,double margin){
        Vec3d d=point.subtract(center());return clamp(point,Math.max(1,StormSummit.rim(d.x,d.z)-margin));
    }
    private void sound(Vec3d pos,SoundEvent sound,float volume,float pitch){world().playSound(null,pos.x,pos.y,pos.z,sound,SoundCategory.HOSTILE,volume,pitch);}
    private boolean hurt(ServerPlayerEntity p,float raw){return boss.eligible(p)&&p.damage(world(),new DamageSource(world().getRegistryManager().getOrThrow(RegistryKeys.DAMAGE_TYPE).getOrThrow(DAMAGE),boss),raw);}

    // Lightning. Markers persist through every full-strength repeat; nothing authorizes a hit client-side.
    private void storm(List<ServerPlayerEntity> players){
        if(now()>=nextSmall){aimed(players,false);nextSmall=now()+40;}
        if(now()>=nextLarge){aimed(players,true);nextLarge=now()+120;}
        if(now()>=nextAmbient){double a=boss.getRandom().nextDouble()*Math.PI*2,r=Math.sqrt(boss.getRandom().nextDouble())*49;strike(center().add(Math.cos(a)*r,0,Math.sin(a)*r),boss.getRandom().nextInt(4)==0);nextAmbient=now()+30;}
        if(now()>=nextDistant){double a=boss.getRandom().nextDouble()*Math.PI*2;effect(RocEffectEntity.Kind.DISTANT_BOLT,center().add(Math.cos(a)*140,-30,Math.sin(a)*140),3,0,6);nextDistant=now()+100;}
    }
    private void aimed(List<ServerPlayerEntity> players,boolean large){
        // Pressure caps at four simultaneous targets; larger parties rotate fairly through that budget.
        int count=Math.min(4,players.size());
        for(int i=0;i<count;i++){
            var p=players.get(Math.floorMod(selection++,players.size()));Vec3d spot=p.getEntityPos();
            if((selection+round)%2==0)spot=spot.add(motion.getOrDefault(p.getUuid(),Vec3d.ZERO).multiply((large?30:20)*.7));
            strike(spot,large);
        }
    }
    RocEffectEntity strike(Vec3d point,boolean large){
        if(!active())return null;
        double radius=large?5:2.5;Vec3d candidate=onFloor(point);
        // A relocated centre must be outside every live circle; leave partly overlapping edges.
        for(int attempt=0;attempt<160;attempt++){
            if(attempt>0){double a=attempt*2.3999632297,r=.8*Math.sqrt(attempt);candidate=onFloor(point.add(Math.cos(a)*r,0,Math.sin(a)*r));}
            if(StormSummit.beyondRim(centre,candidate)>-1)continue;
            boolean clear=true;
            for(var s:strikes)if(!s.effect.isRemoved()&&horizontal(candidate,s.effect.getEntityPos())<Math.max(radius,s.effect.radius())+.05){clear=false;break;}
            if(!clear)continue;
            int warning=large?30:20;
            var e=effect(large?RocEffectEntity.Kind.LARGE_STRIKE:RocEffectEntity.Kind.SMALL_STRIKE,candidate,radius,warning,warning+(large?20:10)+6);
            strikes.add(new Strike(e));sound(candidate,SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE,1.7f,large?.55f:1.1f);return e;
        }
        return null; // Do not stack an unavoidable strike if the bounded search fills up.
    }
    private void tickStrikes(List<ServerPlayerEntity> players){
        for(var it=strikes.iterator();it.hasNext();){var s=it.next();var e=s.effect;if(e.isRemoved()){it.remove();continue;}
            boolean large=e.kind()==RocEffectEntity.Kind.LARGE_STRIKE;int bolts=large?3:2;
            if(s.bolts<bolts&&e.effectTicks(0)>=e.warningTicks()+s.bolts*10){
                s.bolts++;sound(e.getEntityPos(),SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT,large?2.3f:1.4f,large?.65f:1.15f);
                for(var p:players)if(horizontal(p.getEntityPos(),e.getEntityPos())<=e.radius()+p.getWidth()*.5&&p.getY()>=center().y-4&&p.getY()<center().y+110)hurt(p,large?LARGE_DAMAGE:SMALL_DAMAGE);
            }
        }
    }
    private RocEffectEntity effect(RocEffectEntity.Kind kind,Vec3d position,double radius,int warning,int duration){var e=RocEffectEntity.spawn(boss,kind,position,radius,warning,duration);effects.add(e);return e;}

    // Tornadoes are persistent drifting obstacles, never homing missiles. Shared immunity prevents juggling.
    RocEffectEntity tornado(Vec3d point){
        Vec3d spot=clamp(point,SAFE_RADIUS);
        for(int i=0;i<64;i++){
            boolean clear=true;for(var t:tornadoes)if(horizontal(spot,t.effect.getEntityPos())<15)clear=false;
            if(clear)break;double a=i*2.3999632297;spot=center().add(Math.cos(a)*27,0,Math.sin(a)*27);
        }
        var e=effect(RocEffectEntity.Kind.TORNADO,spot,TORNADO_RADIUS,0,Integer.MAX_VALUE);tornadoes.add(new Tornado(e,boss.getRandom().nextDouble()*Math.PI*2));return e;
    }
    private void tickTornadoes(List<ServerPlayerEntity> players){
        Set<UUID> currentlySlow=new HashSet<>();
        for(var t:tornadoes){
            if(t.effect.isRemoved())continue;
            Vec3d pos=t.effect.getEntityPos();Vec3d next=pos.add(Math.cos(t.angle)*.026,0,Math.sin(t.angle)*.026);
            boolean blocked=horizontal(next,center())>SAFE_RADIUS;
            for(var other:tornadoes)if(other!=t&&horizontal(next,other.effect.getEntityPos())<14)blocked=true;
            if(blocked)t.angle+=1.1;else t.effect.setPosition(next);
            for(var p:players){
                double distance=horizontal(p.getEntityPos(),t.effect.getEntityPos());
                if(distance>TORNADO_RADIUS||p.getY()>center().y+20)continue;
                currentlySlow.add(p.getUuid());slow(p,Math.clamp(.15+.45*(1-distance/TORNADO_RADIUS),.15,.6));
                if(caught.containsKey(p.getUuid()))continue;
                if(distance<TORNADO_CORE&&now()>=immunity.getOrDefault(p.getUuid(),0L)){caught.put(p.getUuid(),new Caught(t,now()));continue;}
                Vec3d inward=t.effect.getEntityPos().subtract(p.getEntityPos()).multiply(1,0,1).normalize();
                double pull=p.isOnGround()?.012:.065;
                p.addVelocity(inward.x*pull,0,inward.z*pull);p.velocityModified=true;
                if(now()%30==0)hurt(p,8);
            }
        }
        for(UUID id:new HashSet<>(slowed))if(!currentlySlow.contains(id))clearSlow(id);
        for(var it=caught.entrySet().iterator();it.hasNext();){var entry=it.next();var p=world().getServer().getPlayerManager().getPlayer(entry.getKey());var hold=entry.getValue();
            if(p==null||!boss.eligible(p)||horizontal(p.getEntityPos(),hold.tornado.effect.getEntityPos())>TORNADO_RADIUS*1.5){immunity.put(entry.getKey(),now()+60);it.remove();continue;}
            long age=now()-hold.at;
            if(age>=24){hurt(p,TORNADO_DAMAGE);pushSafe(p,center().subtract(p.getEntityPos()),2.2,.55);immunity.put(entry.getKey(),now()+60);it.remove();continue;}
            Vec3d d=p.getEntityPos().subtract(hold.tornado.effect.getEntityPos()).multiply(1,0,1).normalize();
            Vec3d orbit=hold.tornado.effect.getEntityPos().add(d.x*.8-d.z*.45,Math.min(5,1+age*.18),d.z*.8+d.x*.45);
            Vec3d v=orbit.subtract(p.getEntityPos()).multiply(.32);if(v.lengthSquared()>1)v=v.normalize();
            p.setVelocity(v);p.velocityModified=true;p.fallDistance=0;
        }
    }
    private void slow(ServerPlayerEntity p,double amount){var attr=p.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);if(attr==null)return;
        attr.removeModifier(SLOW);attr.addTemporaryModifier(new EntityAttributeModifier(SLOW,-amount,EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));slowed.add(p.getUuid());}
    private void clearSlow(UUID id){var p=world().getServer().getPlayerManager().getPlayer(id);if(p!=null){var a=p.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);if(a!=null)a.removeModifier(SLOW);}slowed.remove(id);}
    private void caw(List<ServerPlayerEntity> players){
        if(stage!=Stage.AIR)return;
        for(var p:players){if(p.getY()<center().y+8||now()<cawAt.getOrDefault(p.getUuid(),0L))continue;
            for(int i=0;i<StormSummitLayout.PERCHES.length;i++)if(horizontal(p.getEntityPos(),perch(i))<17){
                hurt(p,LARGE_DAMAGE);Vec3d v=center().subtract(p.getEntityPos()).multiply(1,0,1).normalize().multiply(2.8);p.setVelocity(v.x,.55,v.z);p.velocityModified=true;
                cawAt.put(p.getUuid(),now()+40);sound(p.getEntityPos(),SoundEvents.ENTITY_PHANTOM_SWOOP,2,.5f);break;
            }
        }
    }
    // Every authored shove stops inside an interior margin. Voluntary movement can still fall.
    private void pushSafe(ServerPlayerEntity p,Vec3d direction,double strength,double up){
        Vec3d d=direction.multiply(1,0,1).normalize();Vec3d projected=p.getEntityPos().add(d.multiply(strength*12));
        double dx=projected.x-center().x,dz=projected.z-center().z;double radius=StormSummit.rim(dx,dz)-9;
        Vec3d safe=clamp(projected,radius);Vec3d impulse=safe.subtract(p.getEntityPos()).multiply(1/12.0,0,1/12.0);
        // A player already near the edge can only receive an inward impulse.
        if(StormSummit.beyondRim(centre,p.getEntityPos())>-10)impulse=center().subtract(p.getEntityPos()).multiply(1,0,1).normalize().multiply(Math.min(strength,1.4));
        p.setVelocity(impulse.x,up,impulse.z);p.velocityModified=true;p.fallDistance=0;
    }

    // The fixed dive area never tracks a player or covers a tornado.
    private Vec3d chooseLanding(){
        Vec3d best=center();double bestClear=-1;
        for(int i=0;i<80;i++){double a=i*2.3999632297;Vec3d point=center().add(Math.cos(a)*Math.sqrt(i/80.0)*24,0,Math.sin(a)*Math.sqrt(i/80.0)*24);
            double nearest=999;for(var t:tornadoes)nearest=Math.min(nearest,horizontal(point,t.effect.getEntityPos()));
            if(nearest>bestClear){best=point;bestClear=nearest;}if(nearest>DIVE_RADIUS+TORNADO_RADIUS+2)return point;}
        // Two spaced small tornadoes always leave a safe centre patch, but search the whole interior if needed.
        return best;
    }
    void dive(Vec3d point){
        mark=clamp(point,24);
        for(var tornado:tornadoes)if(horizontal(mark,tornado.effect.getEntityPos())<DIVE_RADIUS+TORNADO_RADIUS+2){mark=chooseLanding();break;}
        from=boss.getEntityPos();boss.face(mark);enter(Stage.DIVE,"dive");
        Vec3d bill=StormRocPose.socket("stuck",0,"beak");Vec3d offset=boss.modelToWorld(bill).subtract(boss.getEntityPos());
        to=mark.subtract(offset.x,0,offset.z);to=new Vec3d(to.x,center().y,to.z);
        marker=effect(RocEffectEntity.Kind.DIVE_MARKER,mark,DIVE_RADIUS,DIVE_TICKS,DIVE_TICKS+2);
        sound(mark,SoundEvents.ENTITY_PHANTOM_AMBIENT,3,.45f);
    }
    private void ring(Vec3d mark,double radius){var e=effect(RocEffectEntity.Kind.SHOCKWAVE,mark,radius,0,(int)Math.ceil(radius/SHOCK_SPEED)+2);rings.add(new Ring(e));}
    private void tickRings(List<ServerPlayerEntity> players){
        for(var it=rings.iterator();it.hasNext();){var ring=it.next();if(ring.effect.isRemoved()){it.remove();continue;}
            double radius=ring.effect.effectTicks(0)*SHOCK_SPEED,previous=Math.max(0,radius-SHOCK_SPEED);
            for(var p:players){if(ring.hit.contains(p.getUuid()))continue;double distance=horizontal(p.getEntityPos(),ring.effect.getEntityPos());
                if(distance+p.getWidth()*.5<previous-.6||distance-p.getWidth()*.5>radius+.6)continue;
                ring.hit.add(p.getUuid()); // A jumped ring does not hit again while its trailing smoke passes.
                if(p.getY()<ring.effect.getY()+SHOCK_HEIGHT&&p.getY()+p.getHeight()>ring.effect.getY()-.4)hurt(p,RING_DAMAGE);
            }
        }
    }
    /** Authored/no-clip movement skips vanilla stepping callbacks. Preserve owned ground
     * spell contact explicitly at the actual feet, without allowing ambient world damage. */
    private void groundSpells(){
        if(action==Action.POUNCE&&now()-actionAt>=24&&now()-actionAt<44)return;
        for(String side:List.of("left_foot","right_foot")){
            Vec3d foot=boss.socket(side);
            if(foot.y>center().y+1.8)continue;
            BlockPos air=BlockPos.ofFloored(foot.x,center().y,foot.z);
            for(BlockPos pos:List.of(air,air.down())){
                var state=world().getBlockState(pos);
                boolean flame=state.isOf(com.anton.elementalwands.registry.ModSpellBlocks.INFERNO_FLAME);
                boolean coals=state.isOf(com.anton.elementalwands.registry.ModSpellBlocks.PYRE_COALS);
                if(!flame&&!coals)continue;
                UUID owner=com.anton.elementalwands.util.TemporaryBlockManager.casterAt(world(),pos);
                var player=owner==null?null:world().getServer().getPlayerManager().getPlayer(owner);
                if(player==null||!boss.eligible(player))continue;
                com.anton.elementalwands.util.SpellCombat.damage(boss,world(),flame?world().getDamageSources().inFire():world().getDamageSources().hotFloor(),
                        1,player,com.anton.elementalwands.data.WizardAffinity.FIRE);
                if(flame)com.anton.elementalwands.util.SpellCombat.ignite(boss,player,8);
            }
        }
    }
    private void feet(List<ServerPlayerEntity> players){
        for(var p:players){if(now()<footHurt.getOrDefault(p.getUuid(),0L))continue;
            for(String side:List.of("left_foot","right_foot")){Vec3d foot=boss.socket(side);
                if(horizontal(p.getEntityPos(),foot)<3.3&&p.getY()<foot.y+4&&p.getY()+p.getHeight()>foot.y-1){hurt(p,14);footHurt.put(p.getUuid(),now()+20);break;}}
        }
    }
    private void takeoff(List<ServerPlayerEntity> players){phasePending|=boss.getHealth()<=boss.getMaxHealth()*.5f+.01;from=boss.getEntityPos();to=perch((perch+1)%6);perch=(perch+1)%6;enter(Stage.TAKEOFF,"takeoff");}

    // Ground brawl: position selects its answer; aim locks before every committed attack.
    private void chooseAction(List<ServerPlayerEntity> players){
        var p=players.stream().max(Comparator.comparingDouble(v->threat.getOrDefault(v.getUuid(),0d)+boss.getRandom().nextDouble()*5)).orElse(players.get(0));
        double distance=horizontal(p.getEntityPos(),boss.getEntityPos());Vec3d forward=Vec3d.fromPolar(0,boss.getYaw());Vec3d relative=p.getEntityPos().subtract(boss.getEntityPos());
        double facing=relative.multiply(1,0,1).normalize().dotProduct(forward);
        Action chosen;
        if(distance>27&&now()>=nextStampede&&now()-lastRanged<160)chosen=Action.STAMPEDE;
        else if(last!=Action.POUNCE&&(distance>35||++selection%5==0))chosen=Action.POUNCE;
        else if(facing<.2&&distance<24&&last!=Action.WING)chosen=Action.WING;
        else if(horizontal(p.getEntityPos(),boss.socket("head"))<10&&last!=Action.PECK)chosen=Action.PECK;
        else if(distance<24&&last!=Action.SWING_LEFT&&last!=Action.SWING_RIGHT)chosen=relative.crossProduct(forward).y>0?Action.SWING_LEFT:Action.SWING_RIGHT;
        else chosen=Action.SCREAM;
        begin(p,chosen);
    }
    private void begin(ServerPlayerEntity p,Action chosen){
        action=chosen;actionAt=now();target=p.getUuid();actionHits.clear();boss.face(p.getEntityPos());boss.pose(chosen.pose);from=boss.getEntityPos();
        to=interior(p.getEntityPos(),10);mark=to;
        if(chosen==Action.STAMPEDE){nextStampede=now()+160;Vec3d d=to.subtract(from).multiply(1,0,1);if(d.lengthSquared()<16)to=interior(from.add(Vec3d.fromPolar(0,boss.getYaw()).multiply(20)),10);}
    }
    private void tickAction(List<ServerPlayerEntity> players){
        int t=(int)(now()-actionAt);var p=world().getServer().getPlayerManager().getPlayer(target);
        if((action==Action.SCREAM&&t<18||action==Action.STAMPEDE&&t<20)&&p!=null&&boss.eligible(p)){
            boss.face(p.getEntityPos());if(action==Action.STAMPEDE)to=interior(p.getEntityPos(),10);
        }
        switch(action){
            case PECK -> {if(t==action.impact)hitCircle(players,boss.socket("beak"),6.2,PECK_DAMAGE,9);}
            case SWING_LEFT,SWING_RIGHT -> {
                // Resolve the swept arc across several frames, once per victim, along the actual bill.
                if(t>=16&&t<=23)for(var victim:players)if(!actionHits.contains(victim.getUuid())&&horizontal(victim.getEntityPos(),boss.socket("beak"))<6.5
                        &&victim.getY()<boss.socket("beak").y+3&&victim.getY()+victim.getHeight()>boss.socket("beak").y-3){
                    if(hurt(victim,PECK_DAMAGE)){actionHits.add(victim.getUuid());Vec3d side=Vec3d.fromPolar(0,boss.getYaw()+(action==Action.SWING_LEFT?90:-90));pushSafe(victim,side,.65,.16);}}
            }
            case SCREAM -> {if(t==24){Vec3d origin=boss.socket("beak");var e=effect(RocEffectEntity.Kind.SCREAM,new Vec3d(origin.x,center().y+1,origin.z),2.5,0,10);e.setYaw(boss.getYaw());
                Vec3d forward=Vec3d.fromPolar(0,boss.getYaw());for(var victim:players){Vec3d d=victim.getEntityPos().subtract(origin);double along=d.dotProduct(forward);double side=Math.abs(d.x*forward.z-d.z*forward.x);
                    if(along>=-1&&along<=110&&side<=2.5+victim.getWidth()*.5&&victim.getY()<center().y+12)hurt(victim,SCREAM_DAMAGE);}
                sound(origin,SoundEvents.ENTITY_PHANTOM_DEATH,3,.4f);}}
            case WING -> {if(t==20){effect(RocEffectEntity.Kind.SHOCKWAVE,boss.getEntityPos(),26,0,22);for(var victim:players)if(horizontal(victim.getEntityPos(),boss.getEntityPos())<26&&victim.getY()<center().y+18
                        &&victim.getY()+victim.getHeight()>center().y){hurt(victim,WING_DAMAGE);pushSafe(victim,victim.getEntityPos().subtract(boss.getEntityPos()),1.4,.3);}}}
            case POUNCE -> {
                if(t==24)marker=effect(RocEffectEntity.Kind.DIVE_MARKER,to,6,20,22);
                if(t>=24&&t<=44){double f=(t-24)/20.0;boss.setPosition(from.lerp(to,f).add(0,Math.sin(f*Math.PI)*14,0));}
                if(t==44){boss.setPosition(to);hitCircle(players,to,6,POUNCE_DAMAGE,10);ring(to,26);sound(to,SoundEvents.ENTITY_GENERIC_EXPLODE.value(),2,.65f);}
            }
            case STAMPEDE -> {
                if(t>=24&&t<=54){Vec3d old=boss.getEntityPos();double f=(t-24)/30.0;boss.setPosition(from.lerp(to,f));
                    for(var victim:players)if(!actionHits.contains(victim.getUuid())&&distanceToSegment(victim.getEntityPos(),old,boss.getEntityPos())<3.3&&victim.getY()<center().y+10){
                        if(hurt(victim,SCREAM_DAMAGE)){actionHits.add(victim.getUuid());pushSafe(victim,Vec3d.fromPolar(0,boss.getYaw()+90),.8,.2);}}}
                if(t>54){double f=Math.min(1,(t-54)/30.0);Vec3d slide=to.subtract(from).multiply(1,0,1).normalize().multiply(4*(1-(1-f)*(1-f)));boss.setPosition(interior(to.add(slide),9));}
            }
        }
        if(t>=action.duration){last=action;action=null;boss.pose("hunched");
            int party=Math.min(4,players.size());nextAction=now()+Math.max(6,18-(party-1)*2+(world().getDifficulty()==Difficulty.EASY?8:world().getDifficulty()==Difficulty.HARD?-5:0));}
    }
    private void hitCircle(List<ServerPlayerEntity> players,Vec3d point,double radius,float damage,double height){for(var p:players)if(horizontal(p.getEntityPos(),point)<=radius&&p.getY()<center().y+height)hurt(p,damage);}
    private static double distanceToSegment(Vec3d p,Vec3d a,Vec3d b){Vec3d d=b.subtract(a).multiply(1,0,1);double l=d.lengthSquared();double t=l==0?0:Math.clamp(p.subtract(a).dotProduct(d)/l,0,1);return horizontal(p,a.add(d.multiply(t)));}
    void rehearse(ServerPlayerEntity player,Action action){
        if(!active())start(StormSummit.nearestCentre(player.getEntityPos()),List.of(player));
        clearHazards();review=true;boss.setPosition(center());enter(Stage.BRAWL,"hunched");floor=Math.max(boss.getMaxHealth()*.5f,boss.getHealth()-boss.getMaxHealth()*.125f);
        begin(player,action);
    }
}
