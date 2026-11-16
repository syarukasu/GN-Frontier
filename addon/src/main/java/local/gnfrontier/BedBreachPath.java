package local.gnfrontier;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.BedBlock;

/** Bounded standing-space search. Prefer fewer broken blocks, then a shorter route. */
public final class BedBreachPath {
    private static final int MAX_NODES=8192, BREAK_COST=10000;
    private static final int[] HEIGHT_STEPS={0,1,-1};
    private static final Map<ServerLevel,Budget> BUDGETS=new WeakHashMap<>();
    private static final class Budget {long tick=Long.MIN_VALUE;int used;}
    private record Node(BlockPos pos,Node previous,int cost){}
    /** Only one synchronous search owns this snapshot. Never reused across ticks or edits. */
    static final class SearchSpace {
        private record Standing(List<BlockPos> blocks){}
        private final Mob mob;
        private final ServerLevel level;
        private final int height;
        private final Map<BlockPos,Standing> standing=new HashMap<>();
        SearchSpace(Mob mob){this.mob=mob;level=(ServerLevel)mob.level();height=(int)Math.ceil(mob.getBbHeight());}
        List<BlockPos> blockers(BlockPos from,BlockPos to){
            var base=standing.computeIfAbsent(to,pos->new Standing(BedBreachPath.blockers(mob,pos,pos))).blocks();
            if(base==null||to.getY()<=from.getY())return base;
            BlockPos extra=from.above(height);
            if(extra.getX()==to.getX()&&extra.getZ()==to.getZ()&&extra.getY()>=to.getY()&&extra.getY()<to.getY()+height)return base;
            if(!level.hasChunkAt(extra)||!level.getFluidState(extra).isEmpty())return null;
            var state=level.getBlockState(extra);
            if(state.getCollisionShape(level,extra).isEmpty())return base;
            if(state.getBlock() instanceof BedBlock||state.getDestroySpeed(level,extra)<0||!state.canEntityDestroy(level,extra,mob))return null;
            if(base.isEmpty())return List.of(extra);
            var result=new ArrayList<>(base);result.add(extra);return result;
        }
    }
    private BedBreachPath(){}
    public static List<BlockPos> blockers(Mob mob,BlockPos from,BlockPos to) {
        ServerLevel level=(ServerLevel)mob.level();
        if(!level.hasChunkAt(to)||!level.getWorldBorder().isWithinBounds(to)
                ||to.getY()<=level.getMinBuildHeight()||to.getY()+Math.ceil(mob.getBbHeight())>=level.getMaxBuildHeight())return null;
        BlockPos floor=to.below();
        if(!level.hasChunkAt(floor)||!level.getBlockState(floor).isFaceSturdy(level,floor,Direction.UP))return null;
        Set<BlockPos> body=new LinkedHashSet<>();int height=(int)Math.ceil(mob.getBbHeight());
        for(int dy=0;dy<height;dy++)body.add(to.above(dy));
        if(to.getY()>from.getY())body.add(from.above(height));
        List<BlockPos> blocked=new ArrayList<>();
        for(BlockPos pos:body) {
            if(!level.hasChunkAt(pos)||!level.getFluidState(pos).isEmpty())return null;
            var state=level.getBlockState(pos);
            if(state.getCollisionShape(level,pos).isEmpty())continue;
            if(state.getBlock() instanceof BedBlock||state.getDestroySpeed(level,pos)<0||!state.canEntityDestroy(level,pos,mob))return null;
            blocked.add(pos.immutable());
        }
        return blocked;
    }
    public static List<BlockPos> find(Mob mob,BlockPos bed,int range) {
        ServerLevel level=(ServerLevel)mob.level();Budget budget=BUDGETS.computeIfAbsent(level,k->new Budget());
        long tick=level.getGameTime();if(budget.tick!=tick){budget.tick=tick;budget.used=0;}
        if(budget.used++>=2||mob.getBbWidth()>1||mob.getBbHeight()>2.5)return List.of();
        return search(mob,bed,range);
    }
    static List<BlockPos> search(Mob mob,BlockPos bed,int range){
        SearchSpace space=new SearchSpace(mob);
        BlockPos origin=mob.blockPosition();
        Comparator<Node> order=Comparator.comparingInt(Node::cost).thenComparingInt(n->distance(n.pos,bed)).thenComparingLong(n->n.pos.asLong());
        PriorityQueue<Node> open=new PriorityQueue<>(order);Map<BlockPos,Node> best=new HashMap<>();
        Node first=new Node(origin,null,0);open.add(first);best.put(origin,first);int visited=0;
        while(!open.isEmpty()&&visited++<MAX_NODES) {
            Node node=open.remove();if(best.get(node.pos)!=node)continue;
            if(Math.abs(node.pos.getY()-bed.getY())<=1&&Math.abs(node.pos.getX()-bed.getX())+Math.abs(node.pos.getZ()-bed.getZ())<=1) {
                List<BlockPos> result=new ArrayList<>();for(Node at=node;at.previous!=null;at=at.previous)result.add(at.pos);
                Collections.reverse(result);return result;
            }
            for(Direction direction:Direction.Plane.HORIZONTAL)for(int dy:HEIGHT_STEPS) {
                BlockPos next=node.pos.relative(direction).above(dy);
                if(next.distSqr(origin)>(double)range*range||next.distSqr(bed)>(double)(range+4)*(range+4))continue;
                Node old=best.get(next);if(old!=null&&old.cost<=node.cost+1)continue;
                List<BlockPos> blocks=space.blockers(node.pos,next);if(blocks==null)continue;
                int cost=node.cost+1+blocks.size()*BREAK_COST;
                if(old!=null&&old.cost<=cost)continue;
                Node candidate=new Node(next,node,cost);best.put(next,candidate);open.add(candidate);
            }
        }
        return List.of();
    }
    private static int distance(BlockPos a,BlockPos b){return Math.abs(a.getX()-b.getX())+Math.abs(a.getY()-b.getY())+Math.abs(a.getZ()-b.getZ());}
}
