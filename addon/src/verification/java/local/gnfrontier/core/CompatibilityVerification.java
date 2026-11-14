package local.gnfrontier.core;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import local.gnfrontier.LegacyPulleyNbt;
import mcjty.lostcities.worldgen.lost.regassets.data.PaletteEntry;
import net.minecraft.nbt.*;

/** Real NBT/JSON codec regression checks; runtime mixin behavior is checked separately. */
public final class CompatibilityVerification {
    private static int checks;
    private static void check(boolean pass,String reason){checks++;if(!pass)throw new AssertionError(reason);}
    public static void main(String[] args)throws Exception {
        Path revision=Path.of(args[0]),overlay=Path.of(args[1]);UUID owner=UUID.fromString("4aea9df8-8de2-a308-8d7c-3397050cc06e");
        CompoundTag valid=new CompoundTag();valid.putUUID("Owner",owner);valid.putFloat("Offset",6f);valid.putFloat("Speed",-32f);valid.putBoolean("Running",true);valid.putString("unrelated","keep");
        check(LegacyPulleyNbt.repair(valid)==valid,"Canonical owner and motion are not copied or changed");
        ListTag words=new ListTag();for(int n:valid.getIntArray("Owner"))words.add(IntTag.valueOf(n));
        ListTag strings=new ListTag();strings.add(StringTag.valueOf("I"));for(int n:valid.getIntArray("Owner"))strings.add(StringTag.valueOf(Integer.toString(n)));
        for(Tag legacy:List.of(words,strings,StringTag.valueOf(owner.toString()))) {
            var input=valid.copy();input.put("Owner",legacy);var before=input.copy();var repaired=LegacyPulleyNbt.repair(input);
            check(repaired.getUUID("Owner").equals(owner),"Lossless valid legacy UUID recovery: "+legacy.getType());
            check(repaired.getFloat("Offset")==6f&&repaired.getFloat("Speed")==-32f&&repaired.getBoolean("Running")&&repaired.getString("unrelated").equals("keep"),"Repair preserves motion and unrelated NBT");
            check(input.equals(before),"Repair never mutates pooled source NBT");
            check(LegacyPulleyNbt.repair(repaired)==repaired,"Normalized NBT is idempotent");
        }
        ListTag lost=new ListTag();lost.add(StringTag.valueOf("I"));ListTag decimal=new ListTag();for(int i=0;i<4;i++)decimal.add(DoubleTag.valueOf(.5));
        for(Tag broken:List.of(lost,decimal,new IntArrayTag(new int[]{1,2,3}),StringTag.valueOf("1-1-1-1-1"),StringTag.valueOf("bad UUID"))) {
            var input=valid.copy();input.put("Owner",broken);var before=input.copy();var repaired=LegacyPulleyNbt.repair(input);
            check(!repaired.contains("Owner"),"Unrecoverable optional advancement owner is removed");
            repaired.put("Owner",broken);check(repaired.equals(before)&&input.equals(before),"Malformed owner repair changes no machine data");
        }
        int originals=0,repairedPalettes=0;
        for(Path source:Files.list(revision.resolve("original-parts")).sorted().toList()) {
            var original=JsonParser.parseString(Files.readString(source)).getAsJsonObject();
            var fixed=JsonParser.parseString(Files.readString(overlay.resolve("data/lostcities/lostcities/parts").resolve(source.getFileName()))).getAsJsonObject();
            check(original.get("slices").equals(fixed.get("slices")),"Structure geometry is unchanged");
            var a=original.getAsJsonObject("palette").getAsJsonArray("palette");var b=fixed.getAsJsonObject("palette").getAsJsonArray("palette");check(a.size()==b.size(),"All palette symbols retained");
            for(int i=0;i<a.size();i++) {
                var old=a.get(i).getAsJsonObject();var next=b.get(i).getAsJsonObject();
                if(!old.has("tag")||!old.getAsJsonObject("tag").get("id").getAsString().equals("create:rope_pulley")){check(old.equals(next),"Unrelated palette entry retained");continue;}
                originals++;CompoundTag legacy=CompoundTag.CODEC.parse(JsonOps.INSTANCE,old.get("tag")).getOrThrow(false,s->{throw new AssertionError(s);});
                check(legacy.contains("Owner",Tag.TAG_LIST)&&!legacy.hasUUID("Owner"),"Actual shipped JSON reproduces the invalid UUID list");
                var before=legacy.copy();var normalized=LegacyPulleyNbt.repair(legacy);
                check(!normalized.contains("Owner")&&normalized.getFloat("Offset")==6f&&!normalized.getBoolean("Running"),"Existing legacy machine gets offset and booleans restored");
                check(before.equals(legacy),"Original generated tag is immutable during read repair");
                var parsed=PaletteEntry.CODEC.parse(JsonOps.INSTANCE,next).getOrThrow(false,s->{throw new AssertionError(s);});
                if(next.get("block").getAsString().equals("minecraft:air"))check(parsed.getTag()==null,"Air entry cannot carry a pulley block entity");
                else {var tag=parsed.getTag();check(!tag.contains("Owner")&&tag.getFloat("Offset")==6f&&tag.contains("Offset",Tag.TAG_ANY_NUMERIC)&&!tag.getBoolean("Running"),"Native Lost Cities palette codec accepts corrected machine data");repairedPalettes++;}
            }
        }
        check(originals==2&&repairedPalettes==1,"Both malformed source assets corrected");
        System.out.println("GN_COMPATIBILITY_PASS checks="+checks+" sourceAssets="+originals+" runtimeMixins=false");
    }
}
