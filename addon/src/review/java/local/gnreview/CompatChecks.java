package local.gnreview;

import java.nio.file.*;
import java.util.*;
import java.lang.reflect.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Test-only runtime fixtures, loaded solely inside the owned review process. */
public final class CompatChecks {
    private static final List<String> passed=new ArrayList<>();
    private static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);passed.add(message);}
    private static Field field(Class<?> cls,String name)throws Exception{Field f=cls.getDeclaredField(name);f.setAccessible(true);return f;}
    private static Class<?> type(String name)throws Exception{return Class.forName(name,true,Minecraft.class.getClassLoader());}
    private static void commands(ServerPlayer p,String... text){for(String t:text)p.getServer().getCommands().performPrefixedCommand(p.getServer().createCommandSourceStack(),t);}
    private static void owned(ServerPlayer p){if(!p.getServer().getWorldData().getLevelName().equals("GN_Review_r20_compat"))throw new IllegalStateException("Owned review world only");}
    public static Map<String,Object> server(ServerPlayer p,Path evidence)throws Exception {
        owned(p);passed.clear();var level=p.serverLevel();LogUtils.getLogger().info("GN_R20_SERVER_FIXTURE_BEGIN");
        commands(p,"gamerule doMobSpawning false","gamerule doDaylightCycle false","gamerule doWeatherCycle false","gamemode creative @p","time set 6000","weather clear");
        // The same seed previously failed at this naturally generated ChaosZPack pulley.
        BlockPos natural=new BlockPos(-276,81,-7);level.getChunk(natural);
        String naturalId=String.valueOf(ForgeRegistries.BLOCKS.getKey(level.getBlockState(natural).getBlock()));
        check(naturalId.equals("create:rope_pulley"),"Known seed regenerated the original failing pulley location");
        BlockEntity naturalEntity=level.getBlockEntity(natural);
        check(naturalEntity!=null,"Naturally generated pulley loaded a block entity");
        CompoundTag naturalSaved=naturalEntity.saveWithFullMetadata();
        check(!naturalSaved.contains("Owner"),"Generated pulley does not attribute advancements to the structure author");
        // Prove the effective resource, not just the workspace file, was overridden.
        int fixedResources=0;
        for(String name:List.of("floodedmalll3ya_2_3_2.json","floodedmalll3ya_2_3_2_parts2.json")) {
            var resource=level.getServer().getResourceManager().getResource(new ResourceLocation("lostcities","lostcities/parts/"+name)).orElseThrow();
            JsonObject part;try(var reader=resource.openAsReader()){part=JsonParser.parseReader(reader).getAsJsonObject();}
            for(var row:part.getAsJsonObject("palette").getAsJsonArray("palette")) {
                var e=row.getAsJsonObject();if(!e.get("char").getAsString().equals("法"))continue;
                if(e.get("block").getAsString().equals("minecraft:air"))check(!e.has("tag"),"Effective air palette has no stale pulley tag");
                else {
                    var tag=CompoundTag.CODEC.parse(JsonOps.INSTANCE,e.get("tag")).getOrThrow(false,s->{throw new AssertionError(s);});
                    check(tag.getFloat("Offset")==6f&&!tag.contains("Owner")&&tag.contains("Offset",Tag.TAG_ANY_NUMERIC),"Effective generated pulley tag is typed correctly");
                }
                fixedResources++;
            }
        }
        check(fixedResources==2,"Both effective structure assets were checked");
        var original=JsonParser.parseString(Files.readString(evidence.resolve("original-parts/floodedmalll3ya_2_3_2.json"))).getAsJsonObject();
        CompoundTag raw=null;
        for(var row:original.getAsJsonObject("palette").getAsJsonArray("palette")) {
            var e=row.getAsJsonObject();if(e.has("tag")&&e.getAsJsonObject("tag").get("id").getAsString().equals("create:rope_pulley"))raw=CompoundTag.CODEC.parse(JsonOps.INSTANCE,e.get("tag")).getOrThrow(false,s->{throw new AssertionError(s);});
        }
        if(raw==null)throw new AssertionError("Original defective fixture missing");
        var before=raw.copy();var state=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("create:rope_pulley")).defaultBlockState();
        var legacy=BlockEntity.loadStatic(new BlockPos(8,245,8),state,raw);
        check(legacy!=null,"Actual native block-entity loader accepts the original malformed tag");
        var saved=legacy.saveWithFullMetadata();
        check(!saved.contains("Owner")&&saved.getFloat("Offset")==6f,"Loaded legacy pulley saves repaired owner and six-block offset");
        check(raw.equals(before),"Read repair did not mutate the input/palette NBT");
        var valid=raw.copy();valid.putUUID("Owner",p.getUUID());valid.putFloat("Offset",3.5f);valid.putFloat("Speed",16f);
        var playerOwned=BlockEntity.loadStatic(new BlockPos(9,245,8),state,valid);check(playerOwned!=null,"Player-owned pulley still loads");
        var playerSaved=playerOwned.saveWithFullMetadata();check(playerSaved.getUUID("Owner").equals(p.getUUID())&&playerSaved.getFloat("Offset")==3.5f&&playerSaved.getFloat("Speed")==16f,"Native round trip preserves valid owner, offset and speed");
        var reloaded=BlockEntity.loadStatic(new BlockPos(8,245,8),state,saved);check(reloaded!=null&&reloaded.saveWithFullMetadata().getFloat("Offset")==6f,"Repaired pulley NBT survives another native reload");
        NbtIo.writeCompressed(saved,evidence.resolve("repaired-pulley.dat").toFile());

        commands(p,"fill -16 239 -16 31 239 31 minecraft:deepslate","fill -16 240 -16 31 244 31 minecraft:air","tp @p 8.5 240 8.5");
        List<ChunkAccess> chunks=new ArrayList<>();for(int z=-1;z<=1;z++)for(int x=-1;x<=1;x++){
            var chunk=level.getChunk(x,z);Heightmap.primeHeightmaps(chunk,EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG));chunks.add(chunk);
        }
        WorldGenRegion region=new WorldGenRegion(level,chunks,ChunkStatus.FEATURES,0);
        Class<?> featureType=type("com.anomalyzone.biome.PsiPathFeature");Object feature=featureType.getConstructor(com.mojang.serialization.Codec.class).newInstance(NoneFeatureConfiguration.CODEC);
        Method stamp=featureType.getDeclaredMethod("stampPath",WorldGenLevel.class,RandomSource.class,int.class,int.class);stamp.setAccessible(true);
        Method pathBlock=featureType.getDeclaredMethod("pathBlock",RandomSource.class);pathBlock.setAccessible(true);
        BlockPos inside=new BlockPos(8,239,8),outside=new BlockPos(24,239,8);var old=level.getBlockState(outside);
        check(region.getHeight(Heightmap.Types.WORLD_SURFACE_WG,24,8)==240,"Fixture uses the actual generation heightmap");
        RandomSource rejected=RandomSource.create(41),reference=RandomSource.create(41);pathBlock.invoke(feature,reference);
        stamp.invoke(feature,region,rejected,24,8);
        check(level.getBlockState(outside).equals(old),"Transformed psi-path rejects the outside write");
        check(rejected.nextLong()==reference.nextLong(),"Rejecting outside writes preserves the original random stream");
        RandomSource accepted=RandomSource.create(42);var expected=pathBlock.invoke(feature,RandomSource.create(42));
        stamp.invoke(feature,region,accepted,8,8);
        check(level.getBlockState(inside).equals(expected)&&!level.getBlockState(inside).equals(Blocks.DEEPSLATE.defaultBlockState()),"Transformed psi-path still places the native selected trail block");
        // Exercise the complete random walk, including writes crossing the cutoff.
        @SuppressWarnings("unchecked") var full=(Feature<NoneFeatureConfiguration>)feature;
        check(full.place(new FeaturePlaceContext<>(Optional.empty(),region,level.getChunkSource().getGenerator(),RandomSource.create(64),new BlockPos(8,240,8),NoneFeatureConfiguration.INSTANCE)),"Complete transformed psi-path placement executes");
        check(level.getBlockState(outside).equals(old),"Complete trail generation keeps the protected outside destination unchanged");
        LogUtils.getLogger().info("GN_R20_SERVER_FIXTURE_END");
        return Map.of("passed",List.copyOf(passed),"naturalPulley",natural.toShortString(),"naturalPulleyOffset",naturalSaved.getFloat("Offset"),"naturalBlockEntityLoaded",true,"effectiveResources",fixedResources,"legacyNbtRepaired",true,"validOwnerPreserved",true,"pathNativePlacement",true,"pathOutsideGuard",true,"rngPreserved",true);
    }
    public static Map<String,Object> lightmap(Path evidence)throws Exception {
        passed.clear();var mc=Minecraft.getInstance();if(mc.level==null)throw new IllegalStateException("Owned loaded review world required");
        var server=mc.getSingleplayerServer();if(server==null||!server.getWorldData().getLevelName().equals("GN_Review_r20_compat"))throw new IllegalStateException("Owned world required");
        Class<?> lightmap=type("com.anomalyzone.EmissionLightmap"),sky=type("com.anomalyzone.SkyHandler"),iris=type("net.irisshaders.iris.Iris");
        Method active=lightmap.getMethod("isActive");Object config=iris.getMethod("getIrisConfig").invoke(null);
        boolean enabled=(boolean)config.getClass().getMethod("areShadersEnabled").invoke(config);
        Method toggle=iris.getMethod("toggleShaders",Minecraft.class,boolean.class);
        var intensity=field(sky,"intensity");float originalIntensity=intensity.getFloat(null);int changed=0;boolean shaderModeSkipped;
        LogUtils.getLogger().info("GN_R20_LIGHTMAP_FIXTURE_BEGIN");
        try {
            shaderModeSkipped=!(boolean)active.invoke(null);
            check(!field(lightmap,"failed").getBoolean(null),"Lookup succeeds with Oculus-added fields");
            long booleans=Arrays.stream(LightTexture.class.getDeclaredFields()).filter(f->f.getType()==boolean.class&&!Modifier.isStatic(f.getModifiers())).count();
            check(booleans>1,"Actual runtime includes the extra boolean fields that broke the heuristic");
            Field dirty=(Field)field(lightmap,"fDirty").get(null),pixelsField=(Field)field(lightmap,"fPixels").get(null),textureField=(Field)field(lightmap,"fTexture").get(null);
            check(dirty.getName().equals("f_109873_")&&pixelsField.getName().equals("f_109871_")&&textureField.getName().equals("f_109870_"),"Lookup selects the exact native lightmap fields");
            if(enabled){check(shaderModeSkipped,"Shader-enabled mode retains AnomalyZone's intended lightmap exclusion");toggle.invoke(null,mc,false);}
            check((boolean)active.invoke(null),"Non-shader mode uses the real lightmap path");
            LightTexture texture=mc.gameRenderer.lightTexture();NativeImage pixels=(NativeImage)pixelsField.get(texture);
            check(pixels.getWidth()==16&&pixels.getHeight()==16&&((DynamicTexture)textureField.get(texture)).getPixels()==pixels,"Selected pixels belong to the actual uploaded light texture");
            intensity.setFloat(null,1f);
            lightmap.getMethod("onRenderTick",TickEvent.RenderTickEvent.class).invoke(null,new TickEvent.RenderTickEvent(TickEvent.Phase.START,0f));
            check(dirty.getBoolean(texture)&&field(lightmap,"pendingPatch").getBoolean(null),"Native emission handler marks the correct texture dirty");
            texture.updateLightTexture(0f);check(!dirty.getBoolean(texture),"Vanilla lightmap refresh clears the selected dirty flag");
            int[] before=new int[256];for(int y=0;y<16;y++)for(int x=0;x<16;x++)before[y*16+x]=pixels.getPixelRGBA(x,y);
            pixels.writeToFile(evidence.resolve("lightmap-before.png"));
            lightmap.getMethod("onFogColor",net.minecraftforge.client.event.ViewportEvent.ComputeFogColor.class).invoke(null,new Object[]{null});
            check(!field(lightmap,"failed").getBoolean(null)&&!field(lightmap,"pendingPatch").getBoolean(null),"Native emission patch and texture upload complete without fallback");
            for(int y=0;y<16;y++)for(int x=0;x<16;x++)if(before[y*16+x]!=pixels.getPixelRGBA(x,y))changed++;
            check(changed>0,"Native emission tint changes actual lightmap pixels");pixels.writeToFile(evidence.resolve("lightmap-emission.png"));
        } finally {
            intensity.setFloat(null,originalIntensity);
            if((boolean)config.getClass().getMethod("areShadersEnabled").invoke(config)!=enabled)toggle.invoke(null,mc,enabled);
            mc.gameRenderer.lightTexture().tick();mc.gameRenderer.lightTexture().updateLightTexture(0f);
        }
        check((boolean)config.getClass().getMethod("areShadersEnabled").invoke(config)==enabled,"Original shader preference restored in the test clone");
        check(!field(lightmap,"failed").getBoolean(null),"No sticky lightmap failure after restoring the shader pipeline");
        LogUtils.getLogger().info("GN_R20_LIGHTMAP_FIXTURE_END");
        return Map.of("passed",List.copyOf(passed),"shaderInitiallyEnabled",enabled,"shaderBranchPreserved",shaderModeSkipped,"changedPixels",changed,"nativeTintApplied",true,"shaderRestored",true);
    }
}
