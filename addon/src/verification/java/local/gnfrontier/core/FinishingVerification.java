package local.gnfrontier.core;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import local.gnfrontier.GunTuning;
import mcjty.lostcities.worldgen.lost.regassets.*;
import mcjty.lostcities.config.LostCityProfile;
import mcjty.lostcities.worldgen.lost.regassets.data.*;

/** Offline regression checks only. No client, server, world or entity is started. */
public final class FinishingVerification {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void close(float a,float b,String why){check(Math.abs(a-b)<.0001,why+": "+a+" / "+b);}
    private static JsonElement json(Path p)throws Exception{return JsonParser.parseString(Files.readString(p));}
    public static void main(String[] args)throws Exception {
        Path revision=Path.of(args[0]),resources=Path.of("src/main/resources");
        HypoxiaState h=new HypoxiaState();
        for(int i=0;i<7;i++){h.step(true,false);check(!h.collapsed,"Warning phase before collapse");}
        h.step(true,false);check(h.collapsed,"Eight seconds of oxygen debt collapses");
        for(int i=0;i<4;i++){h.step(false,false);check(h.collapsed,"No early wake");}
        h.step(true,true);check(h.collapsed&&h.safeSeconds==0,"Interrupted breath resets recovery");
        for(int i=0;i<4;i++)h.step(false,false);
        check(h.collapsed,"Recovery needs continuous air even after debt clears");
        h.step(false,false);check(!h.collapsed&&h.debt==0,"Five safe seconds recovers");
        for(int i=0;i<300;i++)h.step(true,true);
        check(h.debt==30&&h.collapsed,"Long immersion saturates safely");
        Campaign.State c=new Campaign.State();c.satellite=true;c.deterrence=true;
        check(!Campaign.satelliteOnline(c,0)&&!Campaign.deterrenceOnline(c,0),"Legacy flags without power do not activate");
        c.satellitesLaunched=2;c.uplinkUntil=140;c.deterrenceUntil=140;
        for(long t:new long[]{100,120,140})check(Campaign.satelliteOnline(c,t)&&Campaign.deterrenceOnline(c,t),"Fresh power leases");
        check(!Campaign.satelliteOnline(c,141)&&!Campaign.deterrenceOnline(c,141),"Power expiry");
        check(!Campaign.satelliteOnline(c,0),"Clock rewind cannot make a distant lease permanent");
        Campaign.State reload=Campaign.State.load(c.save());check(reload.save().equals(c.save()),"Power/launch state NBT round trip");
        Economy.State account=new Economy.State();
        for(int reputation:new int[]{-100,0,24,25,59,60,100}){
            account.reputation[0]=reputation;account.reputation[1]=0;
            check(Campaign.militaryRank(account)==(reputation>=60?3:reputation>=25?2:1),"Rank threshold "+reputation);
        }
        var manifest=json(revision.resolve("generated-data.json")).getAsJsonObject();
        var recipes=manifest.getAsJsonObject("recipes");int guns=0,vehicles=0;
        for(var entry:manifest.getAsJsonArray("finishing_rank_recipes")){
            var gate=entry.getAsJsonObject();var recipe=recipes.getAsJsonObject(gate.get("recipe").getAsString());
            int rank=gate.get("rank").getAsInt();String permit="gnfrontier:military_permit_"+new String[]{"i","ii","iii"}[rank-1];
            if(gate.has("gun")){guns++;check(recipe.getAsJsonArray("materials").asList().stream().anyMatch(e->e.getAsJsonObject().getAsJsonObject("item").has("item")&&permit.equals(e.getAsJsonObject().getAsJsonObject("item").get("item").getAsString())&&e.getAsJsonObject().get("count").getAsInt()==1),"Native gun permit");}
            else {vehicles++;check(recipe.getAsJsonArray("inputs").asList().stream().anyMatch(e->e.getAsString().equals(permit)),"Native vehicle permit");}
        }
        check(guns==70&&vehicles==44,"All intended factory recipes gated");
        var g=CommonAssetsManager.GSON;
        GunData sharps=g.fromJson(Files.readString(revision.resolve("sharps_data-source.json")),GunData.class);
        var bullet=sharps.getBulletData();close(bullet.getDamageAmount(),45,"Installed Sharps baseline");
        float armor=bullet.getExtraDamage().getArmorIgnore();int ammo=sharps.getAmmoAmount();
        GunTuning.tuneSharps(bullet);close(bullet.getDamageAmount(),40,"Sharps base forty");
        close(bullet.getExtraDamage().getDamageAdjust().getFirst().getDamage()*bullet.getExtraDamage().getHeadShotMultiplier(),60,"Near headshot sixty");
        close(bullet.getExtraDamage().getDamageAdjust().get(1).getDamage(),34*40f/45,"Preserved falloff ratio");
        close(bullet.getExtraDamage().getArmorIgnore(),armor,"Armor behavior unchanged");check(sharps.getAmmoAmount()==ammo,"Capacity unchanged");
        String once=g.toJson(bullet);GunTuning.tuneSharps(bullet);check(g.toJson(bullet).equals(once),"Reload tuning is idempotent");
        GunData mp18=g.fromJson(Files.readString(revision.resolve("mp18_data-source.json")),GunData.class);
        GunData win=g.fromJson(Files.readString(revision.resolve("win1894_data-source.json")),GunData.class);
        GunTuning.tuneMp18(mp18.getBulletData());
        close(mp18.getBulletData().getExtraDamage().getArmorIgnore(),.4f,"MP18 armor bypass matches observation");
        close(mp18.getBulletData().getExtraDamage().getHeadShotMultiplier(),1.5f,"MP18 head multiplier matches observation");
        close(mp18.getWeight(),4.2f,"MP18 weight remains original");
        check(win.getAmmoAmount()==8,"Win1894 magazine eight, separate native chamber");
        var p=new LostCityProfile("gn_azzz15flat_finished",Files.readString(Path.of(args[1])));
        check(p.getWorldStyle().equals("gnfrontier:ruined_city")&&p.CITY_MINRADIUS==1500&&p.CITY_MAXRADIUS==2000&&Math.abs(p.CITY_CHANCE-.00005)<1e-12,"Finished profile parsed by installed LC");
        for(String city:List.of("commercial_core","residential_border")){
            var parsed=CityStyleRE.CODEC.parse(JsonOps.INSTANCE,json(resources.resolve("data/gnfrontier/lostcities/citystyles/"+city+".json")));
            check(parsed.error().isEmpty(),"City style codec: "+parsed.error());
        }
        // Biome/WorldSettings codecs require Forge's transformed registries and belong to the excluded launch check.
        // Decode the independent codecs here; verify biome references and schema against installed assets in Python.
        var world=json(resources.resolve("data/gnfrontier/lostcities/worldstyles/ruined_city.json")).getAsJsonObject();
        check(MultiSettings.CODEC.parse(JsonOps.INSTANCE,world.get("multisettings")).error().isEmpty(),"Multi settings codec");
        check(PartSelector.CODEC.parse(JsonOps.INSTANCE,world.get("parts")).error().isEmpty(),"Road selector codec");
        var report=new JsonObject();report.addProperty("checks",checks);report.addProperty("gun_factory_rank_gates",guns);report.addProperty("vehicle_factory_rank_gates",vehicles);report.addProperty("sharps_damage",bullet.getDamageAmount());report.addProperty("sharps_near_headshot",60);report.addProperty("runtime_verified",false);
        report.addProperty("world_style_registry_decode",false);
        Files.writeString((args.length>2?Path.of(args[2]):revision).resolve("logic-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println("GN_FINISHING_PASS checks="+checks+" runtime=false");
    }
}
