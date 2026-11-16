package local.gnfrontier;

import net.minecraft.nbt.CompoundTag;

/** Personal search knowledge; the items themselves always belong to the shared corpse. */
public final class CorpseSearch {
    private long revealed=63L;
    private int pending=-1, elapsed;
    public boolean known(int slot){return slot>=0&&slot<54&&(revealed&(1L<<slot))!=0;}
    public long revealed(){return revealed;}
    public int pending(){return pending;}
    public int elapsed(){return elapsed;}
    public static int start(int section){return section==0?6:10;}
    public static int end(int section){return section==0?10:54;}
    public int next(int section){for(int i=start(section);i<end(section);i++)if(!known(i))return i;return -1;}
    public void focus(int slot){if(pending!=slot){pending=slot;elapsed=0;}}
    public boolean advance(int duration){if(pending<6||pending>=54)return false;if(++elapsed<duration)return false;revealed|=1L<<pending;pending=-1;elapsed=0;return true;}
    public CompoundTag save(){var tag=new CompoundTag();tag.putLong("Known",revealed);tag.putInt("Pending",pending);tag.putInt("Elapsed",elapsed);return tag;}
    public static CorpseSearch load(CompoundTag tag){var state=new CorpseSearch();state.revealed=tag.getLong("Known")|63L;state.pending=tag.getInt("Pending");state.elapsed=Math.max(0,tag.getInt("Elapsed"));if(state.pending<6||state.pending>=54||state.known(state.pending)){state.pending=-1;state.elapsed=0;}return state;}
}
