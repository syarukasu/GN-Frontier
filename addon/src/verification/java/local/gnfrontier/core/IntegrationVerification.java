package local.gnfrontier.core;

import java.nio.file.*;
import java.lang.reflect.*;
import cpw.mods.jarhandling.SecureJar;
import com.google.gson.*;

public final class IntegrationVerification {
    public static void main(String[] args) throws Exception {
        Class<?> contextClass = Class.forName("dev.latvian.mods.rhino.Context");
        Class<?> scriptable = Class.forName("dev.latvian.mods.rhino.Scriptable");
        Object context = contextClass.getMethod("enter").invoke(null);
        Object scope = contextClass.getMethod("initStandardObjects").invoke(context);
        Method evaluate = contextClass.getMethod("evaluateString", scriptable, String.class, String.class, int.class, Object.class);
        String harness = "var added = []; var removed = []; var ServerEvents = {recipes: function(callback) { callback({remove:function(f){removed.push(f);},custom:function(r){return {id:function(id){added.push({id:id,recipe:r});}};}});}};";
        evaluate.invoke(context, scope, harness, "verification-harness", 1, null);
        evaluate.invoke(context, scope, Files.readString(Path.of(args[0])), "gn_frontier_recipes.js", 1, null);
        String json = evaluate.invoke(context, scope, "JSON.stringify({added:added, removed:removed})", "verification-result", 1, null).toString();
        JsonObject result = JsonParser.parseString(json).getAsJsonObject();
        if (result.getAsJsonArray("added").size() != 35 || result.getAsJsonArray("removed").size() != 62) throw new AssertionError("Unexpected recipe operation count");
        if(result.getAsJsonArray("added").asList().stream().anyMatch(e->e.toString().contains("gnfrontier:pda")))throw new AssertionError("Retired PDA must not be craftable");
        Path output=args.length>3?Path.of(args[3]):Path.of("../evidence");
        Files.writeString(output.resolve("rhino-recipes.json"), json);
        System.out.println("GN_RHINO_PASS engine=installed-2001.2.3-build.10 recipes=35 removes=62 eventAdapter=mock runtimeKubeJS=false retiredPdaRecipe=false");

        Class<?> profileClass = Class.forName("mcjty.lostcities.config.LostCityProfile");
        Object profile = profileClass.getConstructor(String.class, String.class).newInstance("gn_azzz15flat_wide", Files.readString(Path.of(args[1])));
        if (!profileClass.getMethod("getWorldStyle").invoke(profile).equals("testworld")
            || profileClass.getField("CITY_MINRADIUS").getInt(profile) != 1500
            || profileClass.getField("CITY_MAXRADIUS").getInt(profile) != 2000
            || Math.abs(profileClass.getField("CITY_CHANCE").getDouble(profile) - 0.00005) > 1e-12
            || profileClass.getField("BUILDING_MAXFLOORS").getInt(profile) != 31) throw new AssertionError("Profile ignored supplied values");
        Object normalized = profileClass.getMethod("toJson", boolean.class).invoke(profile, false);
        Files.writeString(output.resolve("lostcities-normalized-profile.json"), normalized.toString());
        System.out.println("GN_LOSTCITIES_PROFILE_PASS parser=installed-7.5.7 worldGenerated=false");

        SecureJar jar = SecureJar.from(Path.of(args[2]));
        if (!jar.getPackages().contains("local.gnfrontier") || !Files.isRegularFile(jar.getPath("META-INF/mods.toml"))) throw new AssertionError("Invalid loader-facing JAR structure");
        System.out.println("GN_SECUREJAR_PASS name=" + jar.name() + " packages=" + jar.getPackages().size());
        JsonArray nbtVariants = JsonParser.parseString(Files.readString(Path.of("../evidence/loot-nbt-variants.json"))).getAsJsonArray();
        for (JsonElement element : nbtVariants) {
            var tag = net.minecraft.nbt.TagParser.parseTag(element.getAsString());
            if (tag.isEmpty()) throw new AssertionError("Empty loot variant NBT");
            if (tag.contains("GunId")) {
                if (tag.getBoolean("HasBulletInBarrel") || tag.getInt("GunCurrentAmmoCount") != 0) throw new AssertionError("Unplanned preloaded gun");
                if (!java.util.Set.of("SEMI", "AUTO").contains(tag.getString("GunFireMode"))) throw new AssertionError("Invalid gun fire mode");
            }
        }
        System.out.println("GN_LOOT_NBT_PASS variants=" + nbtVariants.size() + " parser=Minecraft-TagParser runtimeContainerTest=false");
    }
}
