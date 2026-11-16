package local.gnperf;

import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;
import local.gnfrontier.*;
import local.gnfrontier.core.CityCentreDraw;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingEvent;

/** Isolated native-world equivalence and paired CPU/allocation measurements. No rendering. */
public final class PerformanceChecks {
    private static volatile double sink;
    private static final Method SEARCH;
    private static final Constructor<?> SPACE;
    private static final Method BLOCKERS;
    static {
        try{
            SEARCH=BedBreachPath.class.getDeclaredMethod("search",Mob.class,BlockPos.class,int.class);SEARCH.setAccessible(true);
            Class<?> type=Class.forName("local.gnfrontier.BedBreachPath$SearchSpace");
            SPACE=type.getDeclaredConstructor(Mob.class);SPACE.setAccessible(true);
            BLOCKERS=type.getDeclaredMethod("blockers",BlockPos.class,BlockPos.class);BLOCKERS.setAccessible(true);
        }catch(ReflectiveOperationException error){throw new ExceptionInInitializerError(error);}
    }
    @SuppressWarnings("unchecked")
    private static List<BlockPos> optimized(Mob mob,BlockPos target,int range){
        try{return (List<BlockPos>)SEARCH.invoke(null,mob,target,range);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    private static final class CachedSpace {
        final Object value;
        CachedSpace(Mob mob){try{value=SPACE.newInstance(mob);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
        @SuppressWarnings("unchecked") List<BlockPos> blockers(BlockPos from,BlockPos to){
            try{return (List<BlockPos>)BLOCKERS.invoke(value,from,to);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        }
    }
    private final List<String> checks=new ArrayList<>();
    private void check(boolean ok,String name){if(!ok)throw new IllegalStateException(name);checks.add(name);}
    private static long allocated(){
        return ((com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean()).getThreadAllocatedBytes(Thread.currentThread().getId());
    }
    private static Map<String,Long> measure(Runnable work){
        long bytes=allocated(),start=System.nanoTime();work.run();
        return Map.of("nanos",System.nanoTime()-start,"allocatedBytes",allocated()-bytes);
    }
    private static Map<String,Long> median(List<Map<String,Long>> values){
        return Map.of("nanos",values.stream().mapToLong(v->v.get("nanos")).sorted().toArray()[values.size()/2],
                      "allocatedBytes",values.stream().mapToLong(v->v.get("allocatedBytes")).sorted().toArray()[values.size()/2]);
    }
    public static Map<String,Object> run(ServerLevel level,Mob issued){return new PerformanceChecks().verify(level,issued);}
    private Map<String,Object> verify(ServerLevel level,Mob issued){
        Map<String,Object> result=new LinkedHashMap<>();
        Random coords=new Random(204);
        int pairs=1_000_000;
        for(int i=0;i<pairs;i++){
            int x=coords.nextInt(),z=coords.nextInt();
            if(Double.doubleToLongBits(CityCentreDraw.draw(x,z))!=Double.doubleToLongBits(new Random(z*797003437L+x*295075153L).nextDouble()))
                throw new IllegalStateException("Normal-city draw differs at "+x+","+z);
        }
        for(int x:new int[]{Integer.MIN_VALUE,-1,0,1,Integer.MAX_VALUE})for(int z:new int[]{Integer.MIN_VALUE,-1,0,1,Integer.MAX_VALUE})
            check(CityCentreDraw.draw(x,z)==new Random(z*797003437L+x*295075153L).nextDouble(),"city draw matches native at edge "+x+","+z);
        check(true,"one million signed and overflow city-centre draws match native bits");
        Runnable nativeDraw=()->{double sum=0;for(int i=0;i<150_000;i++)sum+=new Random((i-75000)*797003437L+(i*3-13)*295075153L).nextDouble();sink=sum;};
        Runnable indexedDraw=()->{double sum=0;for(int i=0;i<150_000;i++)sum+=CityCentreDraw.draw(i*3-13,i-75000);sink=sum;};
        for(int i=0;i<5;i++){nativeDraw.run();indexedDraw.run();}
        List<Map<String,Long>> oldDraw=new ArrayList<>(),newDraw=new ArrayList<>();
        for(int i=0;i<9;i++){if(i%2==0){oldDraw.add(measure(nativeDraw));newDraw.add(measure(indexedDraw));}else{newDraw.add(measure(indexedDraw));oldDraw.add(measure(nativeDraw));}}
        result.put("cityDraw",Map.of("pairsCompared",pairs+25,"drawsPerSample",150_000,"samples",9,"native",median(oldDraw),"optimized",median(newDraw)));
        var contents=NpcEquipment.contents(issued);
        for(int i=0;i<1000;i++)NpcEquipment.tick(new LivingEvent.LivingTickEvent(issued));
        check(same(contents,NpcEquipment.contents(issued)),"issued NPC fast path preserves every item and gun NBT");
        issued.getPersistentData().put(NpcEquipment.SNAPSHOT,new CompoundTag());
        NpcEquipment.tick(new LivingEvent.LivingTickEvent(issued));
        check(!issued.getPersistentData().contains(NpcEquipment.SNAPSHOT),"revived NPC still clears its previous death snapshot");
        issued.getPersistentData().remove(NpcGunQuality.INITIALIZED);
        NpcEquipment.tick(new LivingEvent.LivingTickEvent(issued));
        check(issued.getPersistentData().getBoolean(NpcGunQuality.INITIALIZED)&&same(contents,NpcEquipment.contents(issued)),"unfinished NPC quality initialization still runs exactly once");
        result.put("routes",routes(level));
        result.put("checks",checks);result.put("fpsMeasured",false);result.put("environmentCopied",false);
        return result;
    }
    private static boolean same(List<net.minecraft.world.item.ItemStack> a,List<net.minecraft.world.item.ItemStack> b){
        if(a.size()!=b.size())return false;for(int i=0;i<a.size();i++)if(!net.minecraft.world.item.ItemStack.matches(a.get(i),b.get(i)))return false;return true;
    }
    private Map<String,Object> routes(ServerLevel level){
        var anchor=level.getSharedSpawnPos();
        BlockPos origin=new BlockPos(anchor.getX()+32,level.getMaxBuildHeight()-32,anchor.getZ()+32);
        Map<BlockPos,BlockState> saved=new LinkedHashMap<>();
        Mob mob=EntityType.ZOMBIE.create(level);mob.setNoAi(true);mob.setNoGravity(true);
        try{
            for(int x=0;x<17;x++)for(int z=0;z<17;z++)for(int dy=-1;dy<=3;dy++){
                BlockPos pos=origin.offset(x,dy,z);saved.put(pos,level.getBlockState(pos));
                level.setBlock(pos,(dy==-1?Blocks.STONE:Blocks.AIR).defaultBlockState(),18);
            }
            BlockPos from=origin.offset(2,0,8),target=origin.offset(14,0,8);mob.moveTo(from.getX()+.5,from.getY(),from.getZ()+.5,0,0);
            List<Map<String,Object>> scenarios=new ArrayList<>();
            for(String scenario:List.of("open","detour","breach","unbreakable")){
                for(int x=0;x<17;x++)for(int z=0;z<17;z++)for(int dy=0;dy<=3;dy++)level.setBlock(origin.offset(x,dy,z),Blocks.AIR.defaultBlockState(),18);
                if(!scenario.equals("open"))for(int z=scenario.equals("detour")?2:0;z<(scenario.equals("detour")?15:17);z++)for(int dy=0;dy<3;dy++)
                    level.setBlock(origin.offset(8,dy,z),(scenario.equals("unbreakable")?Blocks.BEDROCK:Blocks.STONE_BRICKS).defaultBlockState(),18);
                List<BlockPos> expected=ReferenceBedBreachPath.search(mob,target,16),actual=optimized(mob,target,16);
                check(expected.equals(actual),"exact native route and tie order retained for "+scenario);
                check(scenario.equals("unbreakable")==actual.isEmpty(),"route reachability retained for "+scenario);
                for(int warm=0;warm<4;warm++){ReferenceBedBreachPath.search(mob,target,16);optimized(mob,target,16);}
                Runnable nativeRoute=()->{sink=ReferenceBedBreachPath.search(mob,target,16).size();};
                Runnable cachedRoute=()->{sink=optimized(mob,target,16).size();};
                List<Map<String,Long>> oldTimes=new ArrayList<>(),newTimes=new ArrayList<>();
                for(int i=0;i<9;i++){if(i%2==0){oldTimes.add(measure(nativeRoute));newTimes.add(measure(cachedRoute));}else{newTimes.add(measure(cachedRoute));oldTimes.add(measure(nativeRoute));}}
                scenarios.add(Map.of("scenario",scenario,"routeLength",actual.size(),"samples",9,"native",median(oldTimes),"optimized",median(newTimes)));
            }
            // Mixed collision, unbreakable, bed and fluid states, including ascending clearance.
            for(int x=2;x<14;x++)for(int z=2;z<14;z++){
                Block block=switch((x*7+z)%11){case 0->Blocks.WATER;case 1->Blocks.BEDROCK;case 2->Blocks.RED_BED;case 3->Blocks.GLASS;case 4->Blocks.STONE;case 5->Blocks.OAK_FENCE;default->Blocks.AIR;};
                level.setBlock(origin.offset(x,0,z),block.defaultBlockState(),18);
            }
            var space=new CachedSpace(mob);int compared=0;
            for(int x=2;x<14;x++)for(int z=2;z<14;z++)for(Direction direction:Direction.Plane.HORIZONTAL)for(int dy:new int[]{-1,0,1}){
                BlockPos to=origin.offset(x,0,z),previous=to.relative(direction).above(dy);
                if(!Objects.equals(BedBreachPath.blockers(mob,previous,to),space.blockers(previous,to)))throw new IllegalStateException("Standing clearance differs at "+to);
                compared++;
            }
            check(true,"all "+compared+" mixed-world standing and step-up checks match uncached native checks");
            BlockPos edited=origin.offset(4,0,4);level.setBlock(edited,Blocks.AIR.defaultBlockState(),18);
            check(new CachedSpace(mob).blockers(edited,edited).isEmpty(),"fresh search accepts newly cleared passage");
            level.setBlock(edited,Blocks.BEDROCK.defaultBlockState(),18);
            check(new CachedSpace(mob).blockers(edited,edited)==null,"next search observes block edits without stale route cache");
            return Map.of("scenarios",scenarios,"mixedTransitionsCompared",compared,"snapshotLifetime","one synchronous route search");
        }finally{saved.forEach((pos,state)->level.setBlock(pos,state,18));mob.discard();}
    }
}
