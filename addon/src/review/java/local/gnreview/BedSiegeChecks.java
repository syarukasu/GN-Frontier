package local.gnreview;

import java.nio.file.Path;
import java.util.*;
import local.gnfrontier.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.ForgeRegistries;

/** Natural server ticks in owned corridors; never loaded into the user's instance. */
public final class BedSiegeChecks {
    private static void owned(ServerPlayer p){if(!p.getServer().getWorldData().getLevelName().equals("GN_Review_r28_beds"))throw new IllegalStateException("Owned world required");}
    private static void check(boolean ok,String detail){if(!ok)throw new AssertionError(detail);}
    private static void command(ServerPlayer p,String... commands){for(String c:commands)p.getServer().getCommands().performPrefixedCommand(p.getServer().createCommandSourceStack(),c);}
    private static BlockPos bed(ServerLevel level,BlockPos head,boolean registered,boolean white){
        var block=white?Blocks.WHITE_BED:Blocks.RED_BED;
        level.setBlock(head.relative(Direction.WEST),block.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST).setValue(BedBlock.PART,BedPart.FOOT),3);
        level.setBlock(head,block.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST).setValue(BedBlock.PART,BedPart.HEAD),3);
        if(registered){var data=WorldState.get(level);var owner=data.account(UUID.randomUUID());owner.spawnDimension=level.dimension().location().toString();owner.spawnPosition=head.asLong();owner.respawnChangedAt=level.getGameTime();((net.minecraft.world.level.saveddata.SavedData)data).setDirty();}
        check(RespawnRules.intactBed(level,head),"Fixture bed intact");return head;
    }
    private static Mob mob(ServerLevel level,String id,BlockPos pos){
        Mob mob=(Mob)Objects.requireNonNull(ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(id))).create(level);
        check(mob!=null,"Fixture entity "+id);mob.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,-90,0);
        mob.getPersistentData().putBoolean("sewv:companion_rolled",true);mob.setPersistenceRequired();mob.setInvulnerable(true);
        mob.getPersistentData().putBoolean("gn_r28_fixture",true);level.addFreshEntity(mob);return mob;
    }
    public static Map<String,Object> start(ServerPlayer p,Path evidence)throws Exception{
        owned(p);command(p,"gamemode creative @p","gamerule doMobSpawning false","gamerule doDaylightCycle false","gamerule mobGriefing true","time set 18000","weather clear");
        p.setGameMode(GameType.CREATIVE);p.teleportTo(p.serverLevel(),56.5,244,27.5,0,30);
        ServerLevel level=p.serverLevel();WorldState data=WorldState.get(level);data.enabled=true;data.players.clear();data.respawns.clear();((net.minecraft.world.level.saveddata.SavedData)data).setDirty();
        PrototypeConfig.RAIDS.set(false);PrototypeConfig.DAY_ZOMBIES.set(false);
        List<Entity> remove=new ArrayList<>();for(Entity e:level.getAllEntities())if(e instanceof Mob&&e.getY()>235)remove.add(e);remove.forEach(Entity::discard);
        String[] kinds={"simpleenemymod:ruunit","simpleenemymod:usunit","simpleenemymod:pmcunit","minecraft:pillager","minecraft:zombie","simpleenemymod:ruunit","simpleenemymod:pmcunit","simpleenemymod:usunit"};
        ListTag cases=new ListTag();
        for(int i=0;i<kinds.length;i++){
            int x=i%3*48,z=i/3*48;
            for(int cx=x>>4;cx<=(x+17)>>4;cx++)for(int cz=z>>4;cz<=(z+5)>>4;cz++)level.setChunkForced(cx,cz,true);
            for(int dx=0;dx<=17;dx++)for(int dz=0;dz<=5;dz++)for(int dy=239;dy<=243;dy++){
                boolean shell=dy==239||dy==243||dx==0||dx==17||dz==0||dz==5;
                level.setBlock(new BlockPos(x+dx,dy,z+dz),(shell?Blocks.BEDROCK:Blocks.AIR).defaultBlockState(),3);
            }
            boolean wall=i!=5;
            if(wall)for(int dz=1;dz<5;dz++)for(int dy=240;dy<=242;dy++)level.setBlock(new BlockPos(x+10,dy,z+dz),(i==6?Blocks.BEDROCK:Blocks.OAK_PLANKS).defaultBlockState(),3);
            BlockPos head=bed(level,new BlockPos(x+14,240,z+2),true,true);
            Mob actor=mob(level,kinds[i],new BlockPos(x+2,240,z+2));
            if(i==3)RegisteredBedGoal.markBandit((Pillager)actor);
            if(i==4)actor.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));
            check(actor.goalSelector.getAvailableGoals().stream().anyMatch(g->g.getGoal() instanceof RegisteredBedGoal),"Bed goal installed for "+kinds[i]);
            CompoundTag row=new CompoundTag();row.putInt("case",i);row.putString("kind",kinds[i]);row.putUUID("actor",actor.getUUID());row.putLong("bed",head.asLong());row.putInt("x",x);row.putInt("z",z);row.putBoolean("expectDestroyed",i<6);row.put("events",new ListTag());cases.add(row);
        }
        p.getPersistentData().put("gn_r28_cases",cases);p.getPersistentData().putLong("gn_r28_start",level.getGameTime());p.getPersistentData().putBoolean("gn_r28_active",true);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,true,LivingDestroyBlockEvent.class,e->{
            if(!p.getPersistentData().getBoolean("gn_r28_active"))return;
            for(Tag tag:p.getPersistentData().getList("gn_r28_cases",10)){
                CompoundTag row=(CompoundTag)tag;if(!row.getUUID("actor").equals(e.getEntity().getUUID()))continue;
                if(row.getInt("case")==7)e.setCanceled(true);
                CompoundTag event=new CompoundTag();event.putLong("pos",e.getPos().asLong());event.putBoolean("canceled",e.isCanceled());event.putBoolean("bed",level.getBlockState(e.getPos()).getBlock() instanceof BedBlock);
                event.putDouble("reach",e.getEntity().getEyePosition().distanceTo(Vec3.atCenterOf(e.getPos())));event.putString("block",String.valueOf(ForgeRegistries.BLOCKS.getKey(level.getBlockState(e.getPos()).getBlock())));
                row.getList("events",10).add(event);
            }
        });
        return Map.of("cases",8,"naturalServerTicks",true,"range",PrototypeConfig.BED_ATTACK_RANGE.get(),"wallInsideShelter",true,"bedDestroyTicks",PrototypeConfig.BED_BREAK_TICKS.get(),"aiTargetsOrTicksForced",false);
    }
    public static Map<String,Object> status(ServerPlayer p,Path evidence)throws Exception{
        owned(p);List<Map<String,Object>> result=new ArrayList<>();
        for(Tag tag:p.getPersistentData().getList("gn_r28_cases",10)){
            CompoundTag row=(CompoundTag)tag;Mob actor=(Mob)p.serverLevel().getEntity(row.getUUID("actor"));BlockPos head=BlockPos.of(row.getLong("bed"));
            result.add(Map.of("case",row.getInt("case"),"kind",row.getString("kind"),"bedIntact",RespawnRules.intactBed(p.serverLevel(),head),"registered",RespawnRules.registeredBed(p.serverLevel(),head),"position",actor==null?"missing":actor.position().toString(),"bedGoalRunning",actor!=null&&RegisteredBedGoal.running(actor),"breakEvents",row.getList("events",10).toString()));
            if(actor!=null){Map<String,Object> diag=new LinkedHashMap<>();diag.put("case",row.getInt("case"));diag.put("navigation",actor.getNavigation().getClass().getName());diag.put("navigationDone",actor.getNavigation().isDone());diag.put("moveControl",actor.getMoveControl().getClass().getName());diag.put("moveHasWanted",actor.getMoveControl().hasWanted());diag.put("speed",actor.getSpeed());diag.put("goals",actor.goalSelector.getRunningGoals().map(g->g.getGoal().getClass().getName()).toList());for(var wrapped:actor.goalSelector.getAvailableGoals())if(wrapped.getGoal() instanceof RegisteredBedGoal goal)for(String field:List.of("route","cursor","stalled","elapsed","path")){var f=RegisteredBedGoal.class.getDeclaredField(field);f.setAccessible(true);diag.put(field,String.valueOf(f.get(goal)));}result.add(diag);}
        }
        return Map.of("elapsedTicks",p.serverLevel().getGameTime()-p.getPersistentData().getLong("gn_r28_start"),"cases",result);
    }
    public static Map<String,Object> verify(ServerPlayer p,Path evidence)throws Exception{
        owned(p);ServerLevel level=p.serverLevel();
        for(Tag tag:p.getPersistentData().getList("gn_r28_cases",10)){
            CompoundTag row=(CompoundTag)tag;int index=row.getInt("case");BlockPos head=BlockPos.of(row.getLong("bed"));boolean expected=row.getBoolean("expectDestroyed");
            check(RespawnRules.intactBed(level,head)!=expected,"Case "+index+" bed outcome");
            check(RespawnRules.registeredBed(level,head)!=expected,"Case "+index+" registration outcome");
            int broken=0,beds=0;
            for(Tag t:row.getList("events",10)){
                CompoundTag event=(CompoundTag)t;if(event.getBoolean("canceled"))continue;
                check(event.getDouble("reach")<2.9,"Direct reach for case "+index);
                if(event.getBoolean("bed")){beds++;continue;}
                BlockPos block=BlockPos.of(event.getLong("pos"));
                check(block.getX()==row.getInt("x")+10&&block.getZ()>=row.getInt("z")+1&&block.getZ()<=row.getInt("z")+4,"Only corridor wall destroyed");broken++;
            }
            check(beds==(expected?1:0),"Exactly one direct bed break for case "+index);
            check(broken==(index<5?2:0),"Minimum two-block doorway, or no collateral, for case "+index+": "+broken);
            if(index==7)check(!row.getList("events",10).isEmpty(),"Forge cancellation was exercised");
        }
        var data=WorldState.get(level);data.enabled=false;
        Mob plain=mob(level,"minecraft:pillager",new BlockPos(165,240,2));
        try{check(!((net.minecraft.world.entity.ai.goal.Goal)new RegisteredBedGoal(plain)).canUse(),"Inactive world guard");}finally{data.enabled=true;plain.discard();}
        p.getPersistentData().putBoolean("gn_r28_active",false);
        Map<String,Object> result=new LinkedHashMap<>(status(p,evidence));result.put("status","PASS");result.put("scope","Eight natural-tick corridors: east/west/PMC/marked Bandits/pickaxe zombie, open route, unbreakable wall, Forge veto. Only the necessary two-block doorway may be broken.");return result;
    }
    public static Map<String,Object> explosives(ServerPlayer p,Path evidence)throws Exception{
        owned(p);ServerLevel level=p.serverLevel();List<String> passed=new ArrayList<>();
        ListTag explosionCases=new ListTag();p.getPersistentData().put("gn_r28_explosions",explosionCases);
        for(int kind=0;kind<3;kind++){
            int x=200+kind*24,z=0;for(int cx=x>>4;cx<=(x+10)>>4;cx++)level.setChunkForced(cx,0,true);
            for(int dx=-4;dx<=10;dx++)for(int dz=0;dz<=10;dz++)for(int y=239;y<=244;y++)level.setBlock(new BlockPos(x+dx,y,z+dz),(y==239?Blocks.BEDROCK:Blocks.AIR).defaultBlockState(),3);
            BlockPos head=bed(level,new BlockPos(x,240,z+4),true,true),ordinary=bed(level,new BlockPos(x+3,240,z+4),false,false);
            CompoundTag row=new CompoundTag();row.putLong("head",head.asLong());row.putLong("ordinary",ordinary.asLong());row.putInt("kind",kind);explosionCases.add(row);
            if(kind==0)level.explode(null,x+1.5,241,z+4.5,6,false,Level.ExplosionInteraction.TNT);
            else if(kind==1){
                Entity source=EntityType.ARMOR_STAND.create(level);source.setPos(x+1.5,241,z+4.5);level.addFreshEntity(source);
                Class<?> c=Class.forName("com.atsuishio.superbwarfare.tools.CustomExplosion$Builder");Object b=c.getConstructor(Entity.class).newInstance(source);
                c.getMethod("radius",float.class).invoke(b,6f);c.getMethod("damage",float.class).invoke(b,0f);c.getMethod("destroyBlock",boolean.class).invoke(b,true);c.getMethod("explode").invoke(b);source.discard();
            }else{
                Projectile grenade=(Projectile)ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("taczexpert:grenade")).create(level);grenade.setOwner(p);grenade.setPos(x+1.5,241,z+4.5);
                Class<?> c=Class.forName("com.ori.taczplus.grenade.GrenadeEffects");
                for(var method:c.getDeclaredMethods())if(method.getName().equals("frag")){method.setAccessible(true);method.invoke(null,level,grenade);break;}grenade.discard();
            }
            check(RespawnRules.intactBed(level,head)&&RespawnRules.registeredBed(level,head),"Registered bed survives explosion kind "+kind);
            check(level.getEntitiesOfClass(ItemEntity.class,new AABB(head).inflate(10),e->e.getItem().is(Items.WHITE_BED)).isEmpty(),"No duplicate protected-bed drops");
            level.getBlockState(head).onBlockExploded(level,head,new Explosion(level,null,head.getX(),head.getY(),head.getZ(),4,false,Explosion.BlockInteraction.DESTROY));
            check(RespawnRules.intactBed(level,head),"Custom block explosion callback guard");passed.add(List.of("vanilla","Superb Warfare","TaCZ Expert").get(kind));
        }
        return Map.of("status","SCHEDULED","immediateRegisteredBedSurvives",passed,"deferredCraterCheckRequired",true);
    }
    public static Map<String,Object> explosionVerify(ServerPlayer p,Path evidence){
        owned(p);List<Map<String,Object>> rows=new ArrayList<>();
        for(Tag t:p.getPersistentData().getList("gn_r28_explosions",10)){CompoundTag row=(CompoundTag)t;BlockPos head=BlockPos.of(row.getLong("head")),ordinary=BlockPos.of(row.getLong("ordinary"));int kind=row.getInt("kind");
            check(RespawnRules.intactBed(p.serverLevel(),head)&&RespawnRules.registeredBed(p.serverLevel(),head),"Registered bed survives deferred explosion "+kind);
            if(kind<2)check(!RespawnRules.intactBed(p.serverLevel(),ordinary),"Control bed destroyed after deferred explosion "+kind);
            rows.add(Map.of("kind",kind,"registeredIntact",true,"controlDestroyed",!RespawnRules.intactBed(p.serverLevel(),ordinary)));
        }
        check(rows.size()==3,"Three explosive types exercised");return Map.of("status","PASS","cases",rows);
    }
    public static Map<String,Object> kickClient(Path evidence)throws Exception{
        var mc=net.minecraft.client.Minecraft.getInstance();check(mc.player!=null&&mc.screen==null,"Owned first-person world active");
        var field=local.gnfrontier.client.KickClient.class.getDeclaredField("KEY");field.setAccessible(true);var key=(net.minecraft.client.KeyMapping)field.get(null);
        net.minecraft.client.KeyMapping.click(key.getKey());final boolean[] done={false};final long started=System.currentTimeMillis();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,false,net.minecraftforge.event.TickEvent.RenderTickEvent.class,e->{
            if(done[0]||e.phase!=net.minecraftforge.event.TickEvent.Phase.END||System.currentTimeMillis()-started>15000)return;
            if(local.gnfrontier.client.KickClient.strength(mc.player.getId(),e.renderTickTime)<.7f)return;
            done[0]=true;
            try(var shot=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){shot.writeToFile(evidence.resolve("window-kick-native.png"));}catch(Exception ex){throw new RuntimeException(ex);}
        });return Map.of("registeredKey",key.getName(),"input","KeyMapping click; normal client tick and network","capture","window-kick-native.png");
    }
    public static Map<String,Object> kickSetup(ServerPlayer p,Path evidence){
        owned(p);p.teleportTo(p.serverLevel(),178.5,240,3.5,-90,0);p.setGameMode(GameType.CREATIVE);
        for(int x=175;x<=184;x++)for(int z=0;z<=6;z++)p.serverLevel().setBlock(new BlockPos(x,239,z),Blocks.BEDROCK.defaultBlockState(),3);
        p.serverLevel().setBlock(new BlockPos(180,241,3),Blocks.GLASS_PANE.defaultBlockState(),3);
        return Map.of("glass",true,"position",p.position().toString(),"kickKeyExpected","key.gnfrontier.kick");
    }
    public static Map<String,Object> kickVerify(ServerPlayer p,Path evidence){owned(p);check(p.serverLevel().getBlockState(new BlockPos(180,241,3)).isAir(),"Window kick removed pane");return Map.of("status","PASS","windowRemoved",true);}
}
