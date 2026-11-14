package local.gnreview;

import java.util.*;
import java.nio.file.*;
import java.lang.reflect.*;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import local.gnfrontier.*;
import local.gnfrontier.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.phys.*;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.registries.ForgeRegistries;

/** Loaded by the bounded review agent only. Never included in the production addon. */
public final class QuestChecks {
    private static final List<String> passed=new ArrayList<>();
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);passed.add(why);}
    private static ItemStack item(String id,int amount){Item value=TradeService.item(id);return new ItemStack(value,amount);}
    private static String inventory(ServerPlayer p){ListTag n=new ListTag();p.getInventory().save(n);return n.toString();}
    private static void owned(ServerPlayer p){if(!p.getServer().getWorldData().getLevelName().equals("GN_Review_r19_quests"))throw new IllegalStateException("Owned review world required");}
    private static void commands(ServerPlayer p,String... commands){for(String s:commands)p.getServer().getCommands().performPrefixedCommand(p.getServer().createCommandSourceStack(),s);}
    private static void setup(ServerPlayer p,int standing) {
        p.getInventory().clearContent();p.getInventory().setItem(0,new ItemStack(Frontier.PDA.get()));
        var world=WorldState.get(p.serverLevel());world.enabled=true;var a=new Economy.State();a.balance=1000;Economy.rewardStanding(a,Economy.Merchant.BLACK,standing);
        world.players.put(p.getUUID(),a);world.campaigns.put(p.getUUID(),new Campaign.State());((net.minecraft.world.level.saveddata.SavedData)world).setDirty();
    }
    private static void supply(ServerPlayer p,MerchantQuests.Quest q,int extra) {
        p.getInventory().clearContent();p.getInventory().setItem(0,new ItemStack(Frontier.PDA.get()));
        int slot=9;for(var required:q.items()){p.getInventory().setItem(slot,item(required.item(),required.count()+extra));slot+=3;}
        // Modified stacks and disabled hotbar cells must not count toward delivery.
        ItemStack named=item(q.items().get(0).item(),3);named.setHoverName(Component.literal("検証用の名前付き物資"));p.getInventory().setItem(2,named);
        p.getInventory().setItem(5,item(q.items().get(0).item(),7));p.setItemSlot(EquipmentSlot.OFFHAND,item(q.items().get(0).item(),9));
        p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();
    }
    private static void rejectUnchanged(ServerPlayer p,Runnable operation,String label) {
        var w=WorldState.get(p.serverLevel());String before=inventory(p);var campaign=w.campaign(p.getUUID()).save();var account=WorldState.encode(w.account(p.getUUID()));
        try{operation.run();throw new AssertionError("Unexpected success: "+label);}catch(IllegalArgumentException expected){}
        check(before.equals(inventory(p))&&campaign.equals(w.campaign(p.getUUID()).save())&&account.equals(WorldState.encode(w.account(p.getUUID()))),label);
    }
    public static Map<String,Object> prepare(ServerPlayer p) {
        owned(p);commands(p,"gamerule doMobSpawning false","gamerule doDaylightCycle false","gamerule doWeatherCycle false","gamemode creative @p","time set 6000","weather clear","fill 0 239 0 16 239 16 minecraft:smooth_stone","fill 0 240 0 16 248 16 minecraft:air","tp @p 8.5 240 8.5");
        setup(p,10);supply(p,MerchantQuestData.catalog().get(0),1);FrontierNetwork.send(p,"");
        return Map.of("catalogCount",MerchantQuestData.catalog().size(),"firstToken",MerchantQuestData.catalog().get(0).token(),"startingBalance",1000,"standing",10);
    }
    public static Map<String,Object> verifyUi(ServerPlayer p) {
        owned(p);var w=WorldState.get(p.serverLevel());var first=MerchantQuestData.catalog().get(0);
        check(w.campaign(p.getUUID()).merchantCompleted.contains(first.id()),"Actual PDA packet completed the first contract");
        check(w.account(p.getUUID()).balance==1300,"PDA delivery paid exactly 300");
        for(var required:first.items())check(TradeService.count(p,required.item())==1,"PDA consumed exact quantity of "+required.item());
        check(p.getInventory().getItem(5).getCount()==7&&p.getOffhandItem().getCount()==9&&p.getInventory().getItem(2).hasCustomHoverName(),"Named, offhand and disabled-slot goods preserved");
        rejectUnchanged(p,()->TradeService.claimSpecial(p,first.token()),"Replayed client claim leaves all data unchanged");
        return Map.of("passed",List.copyOf(passed));
    }
    public static Map<String,Object> transactions(ServerPlayer p)throws Exception {
        owned(p);passed.clear();setup(p,0);var catalog=MerchantQuestData.catalog();var first=catalog.get(0);
        rejectUnchanged(p,()->TradeService.acceptSpecial(p,first.token()),"Standing gate enforced by the server");
        setup(p,10);rejectUnchanged(p,()->TradeService.acceptSpecial(p,catalog.get(1).token()),"Prerequisite gate enforced by the server");
        rejectUnchanged(p,()->TradeService.acceptSpecial(p,"black_contact@999"),"Stale definition token rejected");
        var w=WorldState.get(p.serverLevel());long rewards=0;
        for(var q:catalog) {
            supply(p,q,1);rejectUnchanged(p,()->TradeService.claimSpecial(p,q.token()),"Acceptance required: "+q.id());
            TradeService.acceptSpecial(p,q.token());Campaign.refresh(w.campaign(p.getUUID()),p.getUUID(),24000L*(2+catalog.indexOf(q)));
            check(w.campaign(p.getUUID()).merchantAccepted.contains(q.id()),"Daily refresh preserves acceptance: "+q.id());
            int lastSlot=9+(q.items().size()-1)*3;ItemStack full=p.getInventory().getItem(lastSlot).copy();p.getInventory().setItem(lastSlot,ItemStack.EMPTY);
            rejectUnchanged(p,()->TradeService.claimSpecial(p,q.token()),"Missing last ingredient is atomic: "+q.id());p.getInventory().setItem(lastSlot,full);
            long money=w.account(p.getUUID()).balance;w.account(p.getUUID()).balance=Economy.MAX_MONEY;
            rejectUnchanged(p,()->TradeService.claimSpecial(p,q.token()),"Balance cap is atomic: "+q.id());w.account(p.getUUID()).balance=money;
            TradeService.claimSpecial(p,q.token());rewards+=q.reward();
            check(w.account(p.getUUID()).balance==1000+rewards,"Exact campaign reward: "+q.id());
            for(var required:q.items())check(TradeService.count(p,required.item())==1,"Exact material remainder: "+q.id()+"/"+required.item());
            rejectUnchanged(p,()->TradeService.claimSpecial(p,q.token()),"Duplicate completion rejected: "+q.id());
        }
        ServerPlayer peer=FakePlayerFactory.get(p.serverLevel(),new GameProfile(UUID.fromString("b9a78820-21df-4e8d-b5cd-0123456789ab"),"GN_QuestPeer"));
        setup(peer,10);supply(peer,first,1);long before=w.account(p.getUUID()).balance;String beforeInventory=inventory(p);
        TradeService.acceptSpecial(peer,first.token());TradeService.claimSpecial(peer,first.token());
        check(w.account(peer.getUUID()).balance==1300&&w.campaign(peer.getUUID()).merchantCompleted.size()==1,"Second server player owns independent rewards and progress");
        check(w.account(p.getUUID()).balance==before&&inventory(p).equals(beforeInventory)&&w.campaign(p.getUUID()).merchantCompleted.size()==8,"Second player did not change first player state");
        var data=((net.minecraft.world.level.saveddata.SavedData)w).save(new CompoundTag());var loaded=WorldState.load(data);
        check(loaded.campaign(p.getUUID()).merchantCompleted.size()==8&&loaded.campaign(peer.getUUID()).merchantCompleted.size()==1,"Both player records survive SavedData load");
        // Integrated servers do not register the dedicated-server save-all command.
        ((net.minecraft.world.level.saveddata.SavedData)w).setDirty();p.getServer().overworld().getDataStorage().save();
        Path file=p.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/gnfrontier.dat");
        var disk=WorldState.load(NbtIo.readCompressed(file.toFile()).getCompound("data"));
        check(disk.campaign(p.getUUID()).merchantCompleted.size()==8&&disk.account(p.getUUID()).balance==12600,"Actual compressed world save retains all eight rewards");
        FrontierNetwork.send(p,"");return Map.of("passed",List.copyOf(passed),"contracts",8,"totalRewards",rewards,"twoServerPlayerRecords",true,"twoConnectedClients",false,"savedFile",file.toString());
    }
    public static Map<String,Object> overlay()throws Exception {
        var mc=Minecraft.getInstance();if(mc.level==null)throw new IllegalStateException("Review world required");var original=mc.hitResult;
        Class<?> type=Class.forName("com.simibubi.create.content.equipment.goggles.GoggleOverlayRenderer");
        var method=type.getMethod("renderOverlay",ForgeGui.class,GuiGraphics.class,float.class,int.class,int.class);
        var hover=type.getField("hoverTicks");var last=type.getField("lastHovered");GuiGraphics graphics=new GuiGraphics(mc,mc.renderBuffers().bufferSource());
        try {
            for(HitResult hit:new HitResult[]{new EntityHitResult(mc.player),null}) {
                mc.hitResult=hit;hover.setInt(null,77);last.set(null,mc.player.blockPosition());
                method.invoke(null,(ForgeGui)mc.gui,graphics,0f,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
                check(hover.getInt(null)==0&&last.get(null)==null,"Non-block overlay path returns without cast and resets hover state");
            }
            BlockPos pos=mc.player.blockPosition().below();mc.hitResult=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);hover.setInt(null,0);
            method.invoke(null,(ForgeGui)mc.gui,graphics,0f,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
            // Ordinary terrain has no goggles tooltip: Create records its position,
            // then resets hoverTicks when no tooltip is available.
            check(pos.equals(last.get(null)),"Valid block overlay still runs the original Create path");
            graphics.flush();
        } finally {mc.hitResult=original;}
        return Map.of("entityHitPassed",true,"nullHitPassed",true,"blockHitPassed",true,"nativeHoverResetPreserved",true);
    }
    public static Map<String,Object> worldgen(ServerPlayer p)throws Exception {
        owned(p);var level=p.serverLevel();List<ChunkAccess> chunks=new ArrayList<>();for(int z=-1;z<=1;z++)for(int x=-1;x<=1;x++)chunks.add(level.getChunk(x,z));
        WorldGenRegion region=new WorldGenRegion(level,chunks,ChunkStatus.FEATURES,0);BlockPos outside=new BlockPos(24,240,8),inside=new BlockPos(8,240,8);
        var old=level.getBlockState(outside);LogUtils.getLogger().info("GN_R19_TFMG_FIXTURE_BEGIN");
        check(!WorldgenBounds.setBlock(region,outside,net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK.defaultBlockState(),2),"Outside region write rejected before native write");
        check(level.getBlockState(outside).equals(old),"Rejected write leaves destination unchanged");
        Class<?> type=Class.forName("com.drmangotea.tfmg.worldgen.deposits.OilDepositFeature");Object feature=type.getConstructor(com.mojang.serialization.Codec.class).newInstance(NoneFeatureConfiguration.CODEC);
        var place=type.getMethod("placeDeposit",BlockPos.class,WorldGenLevel.class,RandomSource.class);
        place.invoke(feature,outside,region,RandomSource.create(41));check(level.getBlockState(outside).equals(old),"Transformed TFMG deposit respects generation region");
        place.invoke(feature,inside,region,RandomSource.create(42));
        check(ForgeRegistries.BLOCKS.getKey(level.getBlockState(inside).getBlock()).equals(new ResourceLocation("tfmg:oil_deposit")),"TFMG oil deposit still generates in the legal region");
        var well=(Feature<NoneFeatureConfiguration>)Class.forName("com.drmangotea.tfmg.worldgen.deposits.OilWellFeature").getConstructor(com.mojang.serialization.Codec.class).newInstance(NoneFeatureConfiguration.CODEC);
        well.place(new FeaturePlaceContext<>(Optional.empty(),region,level.getChunkSource().getGenerator(),RandomSource.create(43),outside, NoneFeatureConfiguration.INSTANCE));
        check(level.getBlockState(outside).equals(old),"Transformed TFMG oil well respects generation region");
        LogUtils.getLogger().info("GN_R19_TFMG_FIXTURE_END");
        return Map.of("oilDepositGuard",true,"oilWellGuard",true,"validDepositGenerated",true,"outsideUnchanged",true);
    }
}
