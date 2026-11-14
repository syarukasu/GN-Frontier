package local.gnfrontier.core;

import java.util.*;
import local.gnfrontier.WorldState;
import net.minecraft.nbt.*;

/** Real SavedData/NBT and mission accounting; does not pretend to be a running server test. */
public final class CampaignVerification {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void reject(Runnable op){checks++;try{op.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Invalid mission accepted");}
    public static void main(String[] args) {
        UUID player=UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        for(int rotation=0;rotation<Campaign.MISSIONS.size();rotation++)for(int slot=0;slot<3;slot++) {
            final int selected=slot;
            Campaign.State state=new Campaign.State();Campaign.refresh(state,player,0);state.rotation=rotation;
            Campaign.Mission mission=Campaign.mission(state,slot);String token=Campaign.token(state,slot);
            Economy.State account=new Economy.State();account.balance=1000;
            reject(()->Campaign.claim(state,account,selected,token,100));
            check(!Campaign.credit(state,mission.kind(),UUID.randomUUID()),"Kills before acceptance do not count");
            reject(()->Campaign.accept(state,selected,token+"forged"));Campaign.accept(state,slot,token);
            CompoundTag before=state.save();Economy.State capped=account.copy();capped.balance=Economy.MAX_MONEY;
            UUID target=UUID.nameUUIDFromBytes(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if(mission.kind()!=Campaign.Kind.DELIVERY) {
                reject(()->Campaign.claim(state,account,selected,token,100));
                check(Campaign.credit(state,mission.kind(),target),"Accepted target counted");
                check(!Campaign.credit(state,mission.kind(),target),"Same vehicle UUID cannot count twice");
            } else reject(()->Campaign.claim(state,account,selected,token,mission.count()-1));
            before=state.save();reject(()->Campaign.claim(state,capped,selected,token,mission.count()));
            check(state.save().equals(before),"Money cap failure leaves claim state unchanged");
            Economy.State after=Campaign.claim(state,account,slot,token,mission.count());
            check(after.balance==1000+mission.reward() && account.balance==1000,"Exact independent reward");
            reject(()->Campaign.claim(state,after,selected,token,mission.count()));
            Campaign.State reload=Campaign.State.load(state.save());check(reload.save().equals(state.save()),"Mission state survives actual NBT");
            reject(()->Campaign.claim(reload,after,selected,token,mission.count()));
            Campaign.refresh(reload,player,Economy.DAY_TICKS);reject(()->Campaign.accept(reload,selected,token));
            check(reload.accepted==0 && reload.claimed==0 && reload.targets.isEmpty(),"Expiry clears identity and progress");
            Campaign.accept(reload,slot,Campaign.token(reload,slot));Campaign.refresh(reload,player,0);
            check(reload.day==1 && reload.accepted==(1<<slot),"Clock rewind cannot reset contract");
        }
        Campaign.State s=new Campaign.State();Campaign.refresh(s,player,0);s.jamUntil=500;
        Economy.State a=new Economy.State();a.balance=1000;
        check(Campaign.signalLost(s,a,499) && !Campaign.signalLost(s,a,500),"Interference expiry boundary");
        a.alert[0]=100;check(Campaign.signalLost(s,a,500),"Military interference independent of random interruption");
        s.satellite=true;s.uplinkUntil=140;check(!Campaign.signalLost(s,a,100),"Powered satellite resolves both signal causes");
        check(Economy.prepareTrade(a,Economy.offer("scrap"),Economy.Direction.BUY,1,10,0,64,true).after().balance==920,"Satellite flag applied to real trade planner");
        s.noahComplete=true;s.noahDimension="ad_astra:mars";s.milestones.add("create:encased_fan");
        WorldState world=new WorldState();world.players.put(player,a);world.campaigns.put(player,s);world.terraformedPlanets.addAll(Set.of("ad_astra:mars","ad_astra:glacio"));
        world.emission.putInt("nextEmission",1100);CompoundTag saved=world.save(new CompoundTag());
        check(WorldState.load(saved).save(new CompoundTag()).equals(saved),"Campaign, both planets, emission snapshot and economy round-trip together");
        CompoundTag legacy=saved.copy();legacy.remove("terraformedPlanets");legacy.remove("emission");legacy.getList("players",Tag.TAG_COMPOUND).getCompound(0).remove("campaign");
        WorldState old=WorldState.load(legacy);check(old.players.get(player).balance==1000 && old.campaign(player).day==-1,"dev2 accounts migrate without reset");
        check(Campaign.raidTier(29)==0 && Campaign.raidTier(30)==1 && Campaign.raidTier(60)==2 && Campaign.raidTier(85)==3,"Raid boundaries");
        System.out.println("GN_CAMPAIGN_LOGIC_PASS checks="+checks+" runtimeServer=false");
    }
}
