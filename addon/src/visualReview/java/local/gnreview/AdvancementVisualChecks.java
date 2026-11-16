package local.gnreview;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import com.google.gson.GsonBuilder;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** Uses the real advancement manager, earned-event packets and game framebuffer. */
public final class AdvancementVisualChecks {
    private static final String BACKUP="gn_advancement_visual_review";
    private static final List<String> EXAMPLES=List.of("hamster:gun_archive/makarov", "immersive_weathering:immersive_weathering/bark_off", "creatingspace:reach_earth_orbit", "superbwarfare:main/vehicle_assembling", "hamster:gun_archive/bergman1896");
    private static void owned(ServerPlayer p){if(!p.getServer().getWorldData().getLevelName().equals("GN_Review_r39_creation"))throw new IllegalStateException("Owned world required");}
    private static Advancement advancement(ServerPlayer p,String id){return Objects.requireNonNull(p.getServer().getAdvancements().getAdvancement(new ResourceLocation(id)),id);}
    public static Map<String,Object> server(ServerPlayer p,String action,Properties settings){
        owned(p);var manager=p.getAdvancements();
        if(action.equals("prepare")){
            if(p.getPersistentData().contains(BACKUP))throw new IllegalStateException("Restore previous review first");
            var n=new CompoundTag();var progress=new CompoundTag();
            for(var a:p.getServer().getAdvancements().getAllAdvancements()){
                ListTag done=new ListTag();for(String criterion:manager.getOrStartProgress(a).getCompletedCriteria())done.add(StringTag.valueOf(criterion));
                progress.put(a.getId().toString(),done);
            }
            n.put("progress",progress);n.put("inventory",p.getInventory().save(new ListTag()));
            n.putInt("selected",p.getInventory().selected);n.putInt("xpLevel",p.experienceLevel);n.putInt("xpTotal",p.totalExperience);n.putFloat("xpProgress",p.experienceProgress);
            n.putBoolean("invulnerable",p.isInvulnerable());n.putBoolean("gravity",p.isNoGravity());n.putDouble("x",p.getX());n.putDouble("y",p.getY());n.putDouble("z",p.getZ());n.putFloat("yaw",p.getYRot());n.putFloat("pitch",p.getXRot());
            p.getPersistentData().put(BACKUP,n);p.setInvulnerable(true);p.setNoGravity(true);p.teleportTo(-282.5,73,-6.5);p.setYRot(180);p.setXRot(0);
            return Map.of("progressSnapshot",progress.size(),"worldReused",true);
        }
        if(action.equals("restore")){
            var n=p.getPersistentData().getCompound(BACKUP);if(!n.contains("progress"))throw new IllegalStateException("No backup");
            var before=n.getCompound("progress");int revoked=0;
            for(var a:p.getServer().getAdvancements().getAllAdvancements()){
                var expected=before.getList(a.getId().toString(),Tag.TAG_STRING);Set<String> keep=new HashSet<>();expected.forEach(t->keep.add(t.getAsString()));
                for(String criterion:completed(p,a))if(!keep.contains(criterion)){manager.revoke(a,criterion);revoked++;}
                if(!new HashSet<>(completed(p,a)).equals(keep))throw new AssertionError("Progress differs: "+a.getId());
            }
            p.getInventory().clearContent();p.getInventory().load(n.getList("inventory",Tag.TAG_COMPOUND));p.getInventory().selected=n.getInt("selected");
            p.experienceLevel=n.getInt("xpLevel");p.totalExperience=n.getInt("xpTotal");p.experienceProgress=n.getFloat("xpProgress");
            p.setInvulnerable(n.getBoolean("invulnerable"));p.setNoGravity(n.getBoolean("gravity"));p.teleportTo(n.getDouble("x"),n.getDouble("y"),n.getDouble("z"));p.setYRot(n.getFloat("yaw"));p.setXRot(n.getFloat("pitch"));
            if(!n.getList("inventory",Tag.TAG_COMPOUND).equals(p.getInventory().save(new ListTag())))throw new AssertionError("Inventory differs");
            p.getPersistentData().remove(BACKUP);p.getInventory().setChanged();p.containerMenu.broadcastChanges();manager.save();
            return Map.of("restored",true,"inventoryAndAdvancementsExact",true,"revokedReviewCriteria",revoked);
        }
        throw new IllegalArgumentException(action);
    }
    private static void write(Path path,Object data)throws Exception{
        if(Files.exists(path))throw new IllegalArgumentException("Fresh evidence required");
        Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(data));
    }
    public static Map<String,Object> client(String action,Properties settings)throws Exception{
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();if(mc.player==null||server==null||!server.getWorldData().getLevelName().equals("GN_Review_r39_creation"))throw new IllegalStateException("Owned client required");
        if(!mc.getLanguageManager().getSelected().equals("ja_jp"))throw new IllegalStateException("Japanese required");
        if(action.equals("audit")){
            List<Map<String,Object>> rows=new ArrayList<>();
            for(var a:server.getAdvancements().getAllAdvancements()){
                var d=a.getDisplay();if(d==null)continue;
                rows.add(Map.of("id",a.getId().toString(),"title",d.getTitle().getString(),"description",d.getDescription().getString(),"titleJson",Component.Serializer.toJson(d.getTitle()),"descriptionJson",Component.Serializer.toJson(d.getDescription()),"toast",d.shouldShowToast(),"chat",d.shouldAnnounceChat()));
            }
            rows.sort(Comparator.comparing(row->String.valueOf(row.get("id"))));
            return Map.of("language","ja_jp","registeredVisibleAdvancements",rows.size(),"rows",rows,"clientPosition",List.of(mc.player.getX(),mc.player.getY(),mc.player.getZ()));
        }
        if(action.equals("toast")){
            int index=Integer.parseInt(settings.getProperty("pdaPage"));String id=EXAMPLES.get(index);
            var player=server.getPlayerList().getPlayers().get(0);var a=advancement(player,id);
            if(!player.getPersistentData().contains(BACKUP))throw new IllegalStateException("Backup required");
            boolean alreadyEarned=managerComplete(player,a);
            mc.setScreen(null);mc.options.hideGui=false;mc.getToasts().clear();
            Path json=Path.of(settings.getProperty("path"));Path png=json.resolveSibling(json.getFileName().toString().replace(".json",".png"));
            if(Files.exists(png))throw new IllegalArgumentException("Fresh image required");
            long[] firstFrame={0};int[] frames={0};AtomicBoolean captured=new AtomicBoolean();
            Consumer<TickEvent.RenderTickEvent> listener=event->{
                if(event.phase!=TickEvent.Phase.END||captured.get())return;
                if(firstFrame[0]==0)firstFrame[0]=System.nanoTime();frames[0]++;
                if(frames[0]<4||System.nanoTime()-firstFrame[0]<1_200_000_000L)return;
                if(!captured.compareAndSet(false,true))return;
                try(var frame=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
                    frame.writeToFile(png);write(json.resolveSibling(png.getFileName()+".proof.json"),Map.of("source","actual Minecraft framebuffer","advancement",id,"title",a.getDisplay().getTitle().getString(),"description",a.getDisplay().getDescription().getString(),"earned",managerComplete(player,a),"trigger",alreadyEarned?"native preview of previously earned advancement":index==4?"real gun acquisition inventory criterion":"controlled server award","screen",mc.screen==null?"world":mc.screen.getClass().getName(),"renderedFrames",frames[0]));
                }catch(Exception error){throw new RuntimeException(error);}
                finally{MinecraftForge.EVENT_BUS.unregister(thisListenerHolder.get());}
            };
            // The observer only lives until this one frame is saved.
            thisListenerHolder.set(listener);MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,false,TickEvent.RenderTickEvent.class,listener);
            if(alreadyEarned)mc.getToasts().addToast(new net.minecraft.client.gui.components.toasts.AdvancementToast(a));
            else server.execute(()->{
                if(index==4){
                    ItemStack gun=new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz","modern_kinetic_gun")));
                    gun.getOrCreateTag().putString("GunId","hamster:bergman1896");gun.getOrCreateTag().putString("GunFireMode","SEMI");
                    player.getInventory().add(gun);player.getInventory().setChanged();player.containerMenu.broadcastChanges();
                    net.minecraft.advancements.CriteriaTriggers.INVENTORY_CHANGED.trigger(player,player.getInventory(),gun);
                }else for(String criterion:a.getCriteria().keySet())player.getAdvancements().award(a,criterion);
            });
            return Map.of("registeredAdvancement",id,"alreadyEarned",alreadyEarned,"capture",png.toString());
        }
        if(action.equals("reload")){mc.reloadResourcePacks();return Map.of("reloadRequested",true);}
        throw new IllegalArgumentException(action);
    }
    private static final java.util.concurrent.atomic.AtomicReference<Object> thisListenerHolder=new java.util.concurrent.atomic.AtomicReference<>();
    private static List<String> completed(ServerPlayer p,Advancement a){List<String> result=new ArrayList<>();p.getAdvancements().getOrStartProgress(a).getCompletedCriteria().forEach(result::add);return result;}
    private static boolean managerComplete(ServerPlayer p,Advancement a){return p.getAdvancements().getOrStartProgress(a).isDone();}
}
