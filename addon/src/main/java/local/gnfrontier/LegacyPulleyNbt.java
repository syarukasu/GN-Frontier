package local.gnfrontier;

import net.minecraft.nbt.*;
import java.util.UUID;

/** Repair only legacy rope-pulley encodings; valid vanilla NBT is left intact. */
public final class LegacyPulleyNbt {
    private LegacyPulleyNbt() {}
    public static CompoundTag repair(CompoundTag input) {
        CompoundTag result=input;
        if(input.contains("Owner")&&!input.hasUUID("Owner")) {
            result=input.copy();UUID owner=readLegacyOwner(input.get("Owner"));
            if(owner==null)result.remove("Owner");else result.putUUID("Owner",owner);
        }
        // ChaosZPack's exported float/byte suffixes were quoted in JSON. Decode only
        // the fields that PulleyBlockEntity itself reads, without touching other tags.
        for(String key:new String[]{"Offset","Speed","Running","Waiting"}) {
            if(!input.contains(key,Tag.TAG_STRING))continue;
            String value=input.getString(key);
            boolean floatField=key.equals("Offset")||key.equals("Speed");
            if(floatField&&value.matches("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)[fF]")) {
                float parsed;
                try{parsed=Float.parseFloat(value.substring(0,value.length()-1));}catch(NumberFormatException ex){continue;}
                if(!Float.isFinite(parsed))continue;
                if(result==input)result=input.copy();result.putFloat(key,parsed);
            } else if(!floatField&&(value.equals("0b")||value.equals("1b")||value.equals("0B")||value.equals("1B"))) {
                if(result==input)result=input.copy();result.putBoolean(key,value.charAt(0)=='1');
            }
        }
        return result;
    }
    private static UUID readLegacyOwner(Tag tag) {
        if(tag instanceof StringTag s) {
            try{UUID id=UUID.fromString(s.getAsString());return id.toString().equalsIgnoreCase(s.getAsString())?id:null;}catch(IllegalArgumentException ignored){return null;}
        }
        if(!(tag instanceof ListTag list))return null;
        int offset=list.size()==5&&list.get(0) instanceof StringTag&&list.getString(0).equals("I")?1:0;
        if(list.size()-offset!=4)return null;
        int[] words=new int[4];
        for(int i=0;i<4;i++) {
            Tag word=list.get(i+offset);
            if(word instanceof IntTag n)words[i]=n.getAsInt();
            else if(word instanceof StringTag s&&s.getAsString().matches("-?[0-9]+")) {
                try{words[i]=Integer.parseInt(s.getAsString());}catch(NumberFormatException ex){return null;}
            } else return null;
        }
        return new UUID(((long)words[0]<<32)|(words[1]&0xffffffffL),((long)words[2]<<32)|(words[3]&0xffffffffL));
    }
}
