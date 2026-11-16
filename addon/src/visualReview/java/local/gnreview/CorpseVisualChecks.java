package local.gnreview;

import java.util.*;
import java.util.stream.*;
import java.nio.file.*;
import local.gnfrontier.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.registries.ForgeRegistries;

/** Real renderer/menu review in the registered disposable world. No OS input or fake screenshot. */
public final class CorpseVisualChecks {
    public static Map<String,Object> renderers(Path evidence)throws Exception{
        var mc=Minecraft.getInstance();Map<String,Object> result=new LinkedHashMap<>();
        for(var field:net.minecraft.client.renderer.entity.EntityRenderDispatcher.class.getDeclaredFields())if(Map.class.isAssignableFrom(field.getType())){
            field.setAccessible(true);Map<?,?> values=(Map<?,?>)field.get(mc.getEntityRenderDispatcher());
            var own=values.get(Frontier.FALLEN_SOLDIER.get());result.put(field.getName(),Map.of("size",values.size(),"fallenRenderer",own==null?"null":own.getClass().getName()));
            result.put(field.getName()+".gn",values.entrySet().stream().filter(e->e.getKey() instanceof EntityType<?> type&&String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(type)).startsWith("gnfrontier:")).map(e->String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey((EntityType<?>)e.getKey()))+"="+e.getValue().getClass().getName()).toList());
        }
        for(var field:net.minecraft.client.renderer.entity.EntityRenderers.class.getDeclaredFields())if(Map.class.isAssignableFrom(field.getType())){field.setAccessible(true);Map<?,?> map=(Map<?,?>)field.get(null);result.put("providers."+field.getName(),map.entrySet().stream().filter(e->e.getKey() instanceof EntityType<?> type&&String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(type)).startsWith("gnfrontier:")).map(e->String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey((EntityType<?>)e.getKey()))+"="+e.getValue().getClass().getName()).toList());}
        java.util.function.Consumer<net.minecraftforge.event.TickEvent.ClientTickEvent> observer=event->{
            if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||mc.level==null)return;
            List<String> missing=new ArrayList<>();for(Entity entity:mc.level.entitiesForRendering())if(mc.getEntityRenderDispatcher().getRenderer(entity)==null)missing.add(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType())+" "+entity.getClass().getName());
            if(!missing.isEmpty())try{Files.writeString(evidence.resolve("missing-renderers.txt"),String.join("\n",missing));}catch(Exception ignored){}
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,false,net.minecraftforge.event.TickEvent.ClientTickEvent.class,observer);
        return result;
    }
    private static void owned(ServerPlayer p){
        if(!p.getServer().getWorldData().getLevelName().equals("GN_Review_r39_creation"))throw new IllegalStateException("Registered review world required");
    }
    private static Map<String,Object> item(ItemStack stack){
        return Map.of("item",ForgeRegistries.ITEMS.getKey(stack.getItem()).toString(),"name",stack.getHoverName().getString(),"count",stack.getCount(),"nbt",String.valueOf(stack.getTag()));
    }
    private static void cmd(ServerPlayer p,String command){p.getServer().getCommands().performPrefixedCommand(p.getServer().createCommandSourceStack(),command);}
    public static Map<String,Object> server(ServerPlayer p,String action,Properties settings){
        owned(p);var level=p.serverLevel();Map<String,Object> data=new LinkedHashMap<>();
        if(action.equals("pose")){
            for(Mob mob:level.getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(24))){mob.setNoAi(true);mob.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);}
            p.teleportTo(level,-282.5,72,-6.5,180,35);
            return Map.of("position",p.position().toString());
        }
        if(action.equals("terrain")){
            List<Map<String,Object>> cells=new ArrayList<>();
            int cx=Integer.parseInt(settings.getProperty("x",String.valueOf(p.getBlockX()))),cz=Integer.parseInt(settings.getProperty("z",String.valueOf(p.getBlockZ())));
            for(int x=cx-16;x<=cx+16;x+=4)for(int z=cz-16;z<=cz+16;z+=4){int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);cells.add(Map.of("x",x,"y",y,"z",z,"ground",ForgeRegistries.BLOCKS.getKey(level.getBlockState(new BlockPos(x,y-1,z)).getBlock()).toString()));}
            data.put("terrain",cells);data.put("position",List.of(p.getX(),p.getY(),p.getZ()));return data;
        }
        if(action.equals("stage")||action.equals("cleanup")){
            List<Entity> remove=new ArrayList<>();for(var entity:level.getAllEntities())if(entity.getPersistentData().getBoolean("gn_visual_corpse_fixture"))remove.add(entity);
            remove.forEach(Entity::discard);data.put("removedOldFixtures",remove.size());if(action.equals("cleanup"))return data;
            double x=Double.parseDouble(settings.getProperty("x","-400.5")),z=Double.parseDouble(settings.getProperty("z","188.5"));
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,(int)Math.floor(x),(int)Math.floor(z));
            WorldState.get(level).enabled=true;
            cmd(p,"gamerule doMobSpawning false");cmd(p,"gamerule doDaylightCycle false");cmd(p,"time set 6000");cmd(p,"weather clear");
            p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);p.setHealth(p.getMaxHealth());p.getFoodData().setFoodLevel(20);p.getInventory().clearContent();
            p.teleportTo(level,x,y+1,z+4.8,180,28);
            List<Map<String,Object>> rows=new ArrayList<>();
            for(int i=0;i<2;i++){
                String id=i==0?"simpleenemymod:ruunit":"simpleenemymod:usunit";
                Mob mob=(Mob)ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(id)).create(level);
                double px=x+(i==0?-1.6:1.6);int py=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,(int)Math.floor(px),(int)Math.floor(z));
                mob.moveTo(px,py,z,i==0?-20:18,0);mob.getPersistentData().putBoolean("sewv:companion_rolled",true);
                mob.setNoAi(true);mob.setPersistenceRequired();mob.getPersistentData().putBoolean("gn_visual_corpse_fixture",true);
                mob.finalizeSpawn(level,level.getCurrentDifficultyAt(mob.blockPosition()),MobSpawnType.COMMAND,null,null);
                level.addFreshEntity(mob);NpcEquipment.prepare(mob);
                var possessions=NpcEquipment.contents(mob);mob.hurt(level.damageSources().genericKill(),10000);
                var found=level.getEntitiesOfClass(FallenSoldier.class,mob.getBoundingBox().inflate(1));
                if(found.size()!=1)throw new IllegalStateException("Expected one actual death corpse for "+id);
                FallenSoldier body=found.get(0);((Entity)body).getPersistentData().putBoolean("gn_visual_corpse_fixture",true);
                rows.add(Map.of("type",id,"corpseId",((Entity)body).getId(),"position",List.of(px,py,z),"items",possessions.stream().filter(s->!s.isEmpty()).map(CorpseVisualChecks::item).toList()));
            }
            data.put("bodies",rows);data.put("camera",List.of(p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot()));return data;
        }
        if(action.equals("check")){
            List<Map<String,Object>> bodies=new ArrayList<>();for(Entity entity:level.getAllEntities())if(entity instanceof FallenSoldier body&&entity.getPersistentData().getBoolean("gn_visual_corpse_fixture"))
                bodies.add(Map.of("id",entity.getId(),"position",entity.position().toString(),"type",body.appearance().getString("id"),"items",IntStream.range(0,54).mapToObj(body.inventory::getItem).map(CorpseVisualChecks::item).toList()));
            data.put("position",p.position().toString());data.put("bodies",bodies);data.put("playerItems",p.getInventory().items.stream().filter(s->!s.isEmpty()).map(CorpseVisualChecks::item).toList());data.put("menu",p.containerMenu.getClass().getName());return data;
        }
        throw new IllegalArgumentException(action);
    }
    public static Map<String,Object> client(String action){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||!mc.getSingleplayerServer().getWorldData().getLevelName().equals("GN_Review_r39_creation"))throw new IllegalStateException("Owned integrated world required");
        Map<String,Object> data=new LinkedHashMap<>();
        if(action.equals("death-info")||action.startsWith("death-time-")){
            List<Map<String,Object>> rows=new ArrayList<>();
            try{
                for(Entity entity:mc.level.entitiesForRendering())if(entity instanceof FallenSoldier body){
                    var renderer=mc.getEntityRenderDispatcher().getRenderer(entity);var field=renderer.getClass().getDeclaredField("bodies");field.setAccessible(true);
                    var model=(LivingEntity)((Map<?,?>)field.get(renderer)).get(body);if(model==null)continue;
                    var state=(AnimationState)model.getClass().getField("deathAnimationState").get(model);
                    if(action.startsWith("death-time-")){model.tickCount=Integer.parseInt(action.substring(11));state.start(0);}
                    rows.add(Map.of("type",body.appearance().getString("id"),"ticks",model.tickCount,"started",state.isStarted(),"accumulated",state.getAccumulatedTime()));
                }
                var type=Class.forName("net.nekoyuni.SimpleEnemyMod.entity.client.animation.ModAnimationsDefinitions");
                for(String key:List.of("UNIT_DEATH","UNIT_DEATH_BACK")){
                    var def=(net.minecraft.client.animation.AnimationDefinition)type.getField(key).get(null);
                    data.put(key,Map.of("length",def.lengthInSeconds(),"looping",def.looping(),"bones",def.boneAnimations().keySet()));
                }
            }catch(Exception error){throw new IllegalStateException(error);}
            data.put("bodies",rows);return data;
        }
        if(action.equals("entities")){
            List<Map<String,Object>> entities=new ArrayList<>();for(Entity entity:mc.level.entitiesForRendering())if(entity.distanceToSqr(mc.player)<400||entity instanceof FallenSoldier)
                entities.add(Map.of("id",entity.getId(),"type",String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType())),"position",entity.position().toString(),"appearance",entity instanceof FallenSoldier body?body.appearance().getString("id"):""));
            return Map.of("position",mc.player.position().toString(),"entities",entities);
        }
        if(action.equals("view")||action.equals("close")){
            mc.player.closeContainer();mc.setScreen(null);mc.options.hideGui=action.equals("view");
            mc.options.bobView().set(false);mc.options.fov().set(70);mc.options.guiScale().set(3);mc.resizeDisplay();return Map.of("hideGui",mc.options.hideGui);
        }
        if(action.equals("open-east")||action.equals("open-west")){
            mc.options.hideGui=false;String type=action.equals("open-east")?"simpleenemymod:ruunit":"simpleenemymod:usunit";
            FallenSoldier target=null;for(Entity entity:mc.level.entitiesForRendering())if(entity instanceof FallenSoldier body&&body.appearance().getString("id").equals(type)&&entity.distanceToSqr(mc.player)<64){target=body;break;}
            if(target==null)throw new IllegalStateException("Nearby rendered corpse missing: "+type);
            var interaction=mc.gameMode.interact(mc.player,(Entity)target,InteractionHand.MAIN_HAND);
            return Map.of("interaction",interaction.toString(),"target",((Entity)target).getId());
        }
        if(action.equals("take")||action.equals("take-vest")||action.equals("take-helmet")){
            if(!(mc.player.containerMenu instanceof CorpseMenu)||mc.screen==null)throw new IllegalStateException("Corpse menu required");
            int slot=action.equals("take-vest")?4:action.equals("take-helmet")?5:0;
            int id=mc.player.containerMenu.containerId;data.put("before",item(mc.player.containerMenu.getSlot(slot).getItem()));
            mc.gameMode.handleInventoryMouseClick(id,slot,0,ClickType.QUICK_MOVE,mc.player);data.put("click","QUICK_MOVE");return data;
        }
        if(action.equals("rendered")){
            List<Map<String,Object>> bodies=new ArrayList<>();
            for(Entity entity:mc.level.entitiesForRendering())if(entity instanceof FallenSoldier body){
                var renderer=mc.getEntityRenderDispatcher().getRenderer(entity);
                try{
                    var field=renderer.getClass().getDeclaredField("bodies");field.setAccessible(true);
                    var model=(LivingEntity)((Map<?,?>)field.get(renderer)).get(body);
                    var original=model==null?null:mc.getEntityRenderDispatcher().getRenderer(model);
                    bodies.add(Map.of("id",entity.getId(),"type",body.appearance().getString("id"),"originalRenderer",original==null?"":original.getClass().getName(),
                        "originalModel",original instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?,?> living?living.getModel().getClass().getName():"",
                        "synced",IntStream.range(0,6).mapToObj(body::visibleEquipment).map(CorpseVisualChecks::item).toList(),
                        "rendered",model==null?List.of():Arrays.stream(NpcInventory.EQUIPMENT).map(model::getItemBySlot).map(CorpseVisualChecks::item).toList()));
                }catch(Exception error){throw new IllegalStateException(error);}
            }
            return Map.of("bodies",bodies);
        }
        if(action.equals("screen")){
            data.put("screen",mc.screen==null?"null":mc.screen.getClass().getName());data.put("title",mc.screen==null?"":mc.screen.getTitle().getString());
            data.put("container",mc.player.containerMenu.getClass().getName());
            data.put("slots",mc.player.containerMenu.slots.stream().map(s->item(s.getItem())).toList());
            if(mc.player.containerMenu instanceof CorpseMenu menu){data.put("known",IntStream.range(0,54).filter(menu::known).boxed().toList());data.put("section",menu.activeSection());data.put("pending",menu.pendingSlot());data.put("elapsed",menu.elapsed());data.put("duration",menu.duration());}
            data.put("size",List.of(mc.getWindow().getWidth(),mc.getWindow().getHeight()));return data;
        }
        if(action.equals("search-pockets")||action.equals("search-storage")){
            if(!(mc.player.containerMenu instanceof CorpseMenu menu))throw new IllegalStateException("Corpse menu required");
            int section=action.equals("search-pockets")?0:1;mc.gameMode.handleInventoryButtonClick(((net.minecraft.world.inventory.AbstractContainerMenu)menu).containerId,section);return Map.of("section",section,"packetSent",true);
        }
        throw new IllegalArgumentException(action);
    }
}
