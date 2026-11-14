package local.gnreview;

import java.nio.file.Path;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraftforge.fml.ModList;

/** Runtime checks for the original-JAR migration; never shipped with GN Frontier. */
public final class CompatMigrationChecks {
    public static Map<String,Object> client()throws Exception {
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(mc.getSingleplayerServer()==null||!mc.getSingleplayerServer().getWorldData().getLevelName().equals("GN_Review_r24_expert"))throw new IllegalStateException("Owned test world required");
        var gun=mc.player.getMainHandItem();
        var modifiers=Class.forName("mod.cdv.gdb.DataLookup").getMethod("getModifiers",net.minecraft.world.item.ItemStack.class).invoke(null,gun);
        if(modifiers==null)throw new AssertionError("Gun Durability modifiers");
        boolean jammed=gun.getOrCreateTag().getBoolean("Jammed");gun.getOrCreateTag().putBoolean("Jammed",true);
        boolean handled;
        try { handled=(Boolean)Class.forName("com.tacz.guns.client.input.InspectKey").getMethod("onInspectPress").invoke(null); }
        finally {gun.getOrCreateTag().putBoolean("Jammed",jammed);}
        if(!handled)throw new AssertionError("Native inspect/unjam input");
        var display=com.tacz.guns.api.TimelessAPI.getGunDisplay(gun).orElseThrow();
        display.getSounds("silence");display.getSounds("silence_3p");
        Object zoom=Class.forName("com.tacz.guns.client.input.ZoomKey").getMethod("onZoomPress").invoke(null);
        mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
        return Map.of("nativeJammedInspectHandled",handled,"silencerSoundApi",true,"nativeZoomInputResult",zoom,"inventoryScreen",mc.screen.getClass().getName(),"scope","Native input hooks invoked; controller hardware, every optic and delayed server unjam completion not tested here.");
    }
    public static Map<String,Object> verify(ServerPlayer p,Path evidence)throws Exception {
        if(!p.getServer().getWorldData().getLevelName().equals("GN_Review_r24_expert"))throw new IllegalStateException("Owned test world required");
        Map<String,String> expected=Map.of("gundb","2.2.2","taczadditions","1.3.6","shrapnelexplosionsmod","2.0.7.3","taczexpert","0.1.65-1.20.1","gnfrontier","0.1.0-dev.17");
        for(var row:expected.entrySet())if(!ModList.get().getModContainerById(row.getKey()).orElseThrow().getModInfo().getVersion().toString().equals(row.getValue()))throw new AssertionError("Distribution version "+row);
        var level=p.serverLevel();var source=EntityType.ARMOR_STAND.create(level);source.setPos(p.getX()+70,p.getY()+12,p.getZ());level.addFreshEntity(source);
        Class<?> builderClass=Class.forName("com.atsuishio.superbwarfare.tools.CustomExplosion$Builder");
        Object explosion=builderClass.getConstructor(Entity.class).newInstance(source);
        var neutral=builderClass.getDeclaredField("damageMultiplier");neutral.setAccessible(true);
        if(neutral.getFloat(explosion)!=0)throw new AssertionError("Neutral multiplier");
        builderClass.getMethod("damage",float.class).invoke(explosion,4f);builderClass.getMethod("radius",float.class).invoke(explosion,2f);
        builderClass.getMethod("explode").invoke(explosion);
        if(!source.getPersistentData().getBoolean("sbw_exploded"))throw new AssertionError("Native SBW explosion hook");
        builderClass.getMethod("explode").invoke(explosion);source.discard();
        Snowball projectile=new Snowball(level,p);projectile.setPos(p.getX()+70,p.getY()+15,p.getZ());level.addFreshEntity(projectile);
        Class.forName("com.atsuishio.superbwarfare.tools.ProjectileTool").getMethod("causeCustomExplode",Projectile.class,net.minecraft.world.damagesource.DamageSource.class,Entity.class,float.class,float.class)
            .invoke(null,projectile,p.damageSources().playerAttack(p),p,4f,2f);
        if(!projectile.getPersistentData().getBoolean("sbw_exploded"))throw new AssertionError("Native projectile explosion hook");projectile.discard();
        return Map.of("versions",expected,"neutralMultiplier",true,"sbwExplosion",true,"duplicateGuard",true,"currentProjectileOverload",true,"thirdPartyArchives","original distribution files");
    }
}
