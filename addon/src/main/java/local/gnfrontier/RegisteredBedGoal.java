package local.gnfrontier;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

/** Approach a registered bed, clear only the selected route, then break it in reach. */
public final class RegisteredBedGoal extends Goal {
    private record Permission(Entity actor,BlockPos pos){}
    private static final ThreadLocal<Permission> BREAK_CHECK=new ThreadLocal<>();
    private final Mob mob;
    private BlockPos target,cracking;
    private Path path;
    private List<BlockPos> route=List.of();
    private int cursor,elapsed,breaking,stalled;
    private long retryAt;
    public RegisteredBedGoal(Mob mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    public static boolean human(Mob mob){return FactionHostility.force(mob)||(mob instanceof Pillager&&mob.getPersistentData().getBoolean("gnfrontier_bandit"));}
    public static void install(Mob mob){
        if(!(mob instanceof Zombie)&&!human(mob))return;
        if(mob.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal() instanceof RegisteredBedGoal))mob.goalSelector.addGoal(0,new RegisteredBedGoal(mob));
    }
    public static void markBandit(Pillager mob){mob.getPersistentData().putBoolean("gnfrontier_bandit",true);install(mob);}
    public static boolean running(Mob mob){return mob.goalSelector.getRunningGoals().anyMatch(g->g.getGoal() instanceof RegisteredBedGoal);}
    public static boolean checkingBed(Entity actor){Permission p=BREAK_CHECK.get();return p!=null&&p.actor==actor;}
    public static boolean checkingBreak(Entity actor,BlockPos pos){Permission p=BREAK_CHECK.get();return p!=null&&p.actor==actor&&p.pos.equals(pos);}
    private boolean eligible(){
        if(!(mob.level() instanceof ServerLevel level)||!RespawnRules.enabled(level)||!mob.isAlive()||mob.isPassenger()||!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING))return false;
        if(human(mob))return PrototypeConfig.HUMAN_BED_ATTACKS.get();
        return mob instanceof Zombie&&PrototypeConfig.BREAK_BLOCKS.get()&&(mob.getMainHandItem().getItem() instanceof PickaxeItem||(EnhancedAiCompat.present()&&mob.getOffhandItem().getItem() instanceof PickaxeItem));
    }
    @Override public boolean canUse(){
        if(!eligible()||mob.level().getGameTime()<retryAt)return false;
        retryAt=mob.level().getGameTime()+60+mob.getRandom().nextInt(40);
        ServerLevel level=(ServerLevel)mob.level();path=null;route=List.of();
        List<BlockPos> beds=RespawnRules.nearbyRegisteredBeds(level,mob.blockPosition(),PrototypeConfig.BED_ATTACK_RANGE.get(),mob);
        for(BlockPos bed:beds){
            target=bed;if(!griefAllowed(bed))continue;
            if(inReach(bed,true))return true;
            Path candidate=mob.getNavigation().createPath(bed,1);
            if(candidate!=null&&candidate.canReach()){path=candidate;return true;}
        }
        if(PrototypeConfig.BED_APPROACH_BREAKING.get())for(BlockPos bed:beds){
            target=bed;if(!griefAllowed(bed))continue;
            route=BedBreachPath.find(mob,bed,PrototypeConfig.BED_ATTACK_RANGE.get());
            if(!route.isEmpty())return true;
        }
        target=null;return false;
    }
    @Override public boolean canContinueToUse(){return eligible()&&target!=null&&elapsed<1800&&target.distSqr(mob.blockPosition())<=Math.pow(PrototypeConfig.BED_ATTACK_RANGE.get()+4,2)&&RespawnRules.attackableBed((ServerLevel)mob.level(),target,mob);}
    @Override public void start(){elapsed=breaking=cursor=stalled=0;if(path!=null)mob.getNavigation().moveTo(path,1);}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    private boolean inReach(BlockPos pos,boolean bed){
        Vec3 point=Vec3.atLowerCornerOf(pos).add(.5,bed?.4:.5,.5);
        if(mob.getEyePosition().distanceToSqr(point)>2.8*2.8)return false;
        var hit=mob.level().clip(new ClipContext(mob.getEyePosition(),point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mob));
        return hit.getType()==HitResult.Type.BLOCK&&(bed?pos.equals(RespawnRules.bedHead((ServerLevel)mob.level(),hit.getBlockPos())):pos.equals(hit.getBlockPos()));
    }
    private boolean griefAllowed(BlockPos pos){
        if(!eligible()||target==null||!RespawnRules.attackableBed((ServerLevel)mob.level(),target,mob))return false;
        BREAK_CHECK.set(new Permission(mob,pos));
        try{return ForgeEventFactory.getMobGriefingEvent(mob.level(),mob);}finally{BREAK_CHECK.remove();}
    }
    private void clearCracks(){if(cracking!=null)((ServerLevel)mob.level()).destroyBlockProgress(mob.getId(),cracking,-1);cracking=null;breaking=0;}
    private void breakBlock(BlockPos pos,boolean bed){
        if(!inReach(pos,bed)||!griefAllowed(pos)){clearCracks();return;}
        ServerLevel level=(ServerLevel)mob.level();var state=level.getBlockState(pos);
        if(!pos.equals(cracking)){clearCracks();cracking=pos.immutable();}
        int duration=bed?PrototypeConfig.BED_BREAK_TICKS.get():(int)Math.max(20,Math.min(200,state.getDestroySpeed(level,pos)*30));
        mob.getNavigation().stop();mob.getLookControl().setLookAt(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
        if(breaking%10==0)mob.swing(InteractionHand.MAIN_HAND);
        level.destroyBlockProgress(mob.getId(),pos,Math.min(9,breaking*10/duration));
        if(++breaking<duration)return;
        BREAK_CHECK.set(new Permission(mob,pos));
        try{if(state.canEntityDestroy(level,pos,mob)&&ForgeEventFactory.onEntityDestroyBlock(mob,pos,state))level.destroyBlock(pos,!bed,mob);else elapsed=1800;}
        finally{BREAK_CHECK.remove();}
        clearCracks();stalled=0;if(bed)elapsed=1800;
    }
    @Override public void tick(){
        elapsed++;
        if(!canContinueToUse()){clearCracks();return;}
        if(inReach(target,true)){breakBlock(target,true);return;}
        if(path!=null){
            clearCracks();if(!mob.getNavigation().isDone())return;path=null;
            if(PrototypeConfig.BED_APPROACH_BREAKING.get())route=BedBreachPath.find(mob,target,PrototypeConfig.BED_ATTACK_RANGE.get());cursor=0;
        }
        while(cursor<route.size()&&mob.position().distanceToSqr(Vec3.atBottomCenterOf(route.get(cursor)))<.45){cursor++;stalled=0;}
        if(cursor>=route.size()){clearCracks();elapsed=1800;return;}
        BlockPos next=route.get(cursor);List<BlockPos> blocks=BedBreachPath.blockers(mob,mob.blockPosition(),next);
        if(blocks==null){clearCracks();elapsed=1800;return;}
        if(!blocks.isEmpty()){
            if(!PrototypeConfig.BED_APPROACH_BREAKING.get()){clearCracks();elapsed=1800;return;}
            BlockPos block=blocks.stream().filter(p->inReach(p,false)).min(Comparator.comparingDouble(p->mob.getEyePosition().distanceToSqr(Vec3.atCenterOf(p)))).orElse(null);
            if(block!=null){breakBlock(block,false);return;}
            clearCracks();
        }else clearCracks();
        mob.getLookControl().setLookAt(next.getX()+.5,next.getY()+.5,next.getZ()+.5);
        // Some tactical navigators discard one-block paths as already reached.
        // This route has already checked body clearance and floor support. Use
        // ordinary movement/collision towards its adjacent waypoint, never teleport.
        mob.getNavigation().stop();
        mob.getMoveControl().setWantedPosition(next.getX()+.5,next.getY(),next.getZ()+.5,1);
        if(next.getY()>mob.getY()+.5)mob.getJumpControl().jump();
        stalled++;
        if(stalled>100)elapsed=1800;
    }
    @Override public void stop(){clearCracks();mob.getNavigation().stop();target=null;path=null;route=List.of();retryAt=mob.level().getGameTime()+80;}
}
