package local.gncombatreview;

import java.util.*;
import local.gnfrontier.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.registries.ForgeRegistries;

/** Real registered assets, native mining and collision repair; no UI or capture. */
final class PackChecks {
    private final List<String> checks=new ArrayList<>();
    private final Map<String,Object> evidence=new LinkedHashMap<>();
    private void check(boolean ok,String label){if(!ok)throw new IllegalStateException(label);checks.add(label);}
    static Map<String,Object> run(ServerLevel level,ServerPlayer player)throws Exception{return new PackChecks().verify(level,player);}
    private Map<String,Object> verify(ServerLevel level,ServerPlayer player)throws Exception{
        var states=ForgeRegistries.BLOCKS.getValues().stream().map(Block::defaultBlockState).toList();
        int matches=0;for(var state:states)if(BlockEntityTypeLookup.resolve(state)==BlockEntityTypeLookup.scan(state))matches++;
        check(matches==states.size(),"cached block entity resolution matches first registered native result for every block");
        var missing=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("doomsday_decoration:acrate")).defaultBlockState();
        int iterations=3000;long before=System.nanoTime();for(int i=0;i<iterations;i++)BlockEntityTypeLookup.scan(missing);long nativeNs=System.nanoTime()-before;
        before=System.nanoTime();for(int i=0;i<iterations;i++)BlockEntityTypeLookup.resolve(missing);long cachedNs=System.nanoTime()-before;
        check(BlockEntityTypeLookup.resolve(missing)==BlockEntityTypeLookup.scan(missing),"negative block entity cache preserves missing type result");
        evidence.put("blockEntityLookup",Map.of("registeredBlocksChecked",states.size(),"iterations",iterations,"nativeScanNanos",nativeNs,"cachedLookupNanos",cachedNs));
        var untagged=new ArrayList<String>();int vehicles=0;
        var vehicleBlocks=new ArrayList<Block>();
        for(var block:ForgeRegistries.BLOCKS.getValues()){
            Class<?> type=block.getClass();boolean vehicle=false;
            while(type!=null){if(type.getName().equals("com.raiiiden.doomsday_decoration.block.VehicleDecoBlock"))vehicle=true;type=type.getSuperclass();}
            if(vehicle&&ForgeRegistries.BLOCKS.getKey(block).getPath().matches(".*(sedan|jeep|humm|van(?:_|$)|car(?:_|$)|pickup|stationwagon|greywagon|ambulance|forklifttruck|motorcycle|taxi|truck|bus(?:_|$)).*")){vehicles++;vehicleBlocks.add(block);if(!block.defaultBlockState().is(Frontier.SALVAGE_VEHICLES))untagged.add(ForgeRegistries.BLOCKS.getKey(block).toString());}
        }
        evidence.put("untaggedVehicles",untagged);
        // Report missing registrations without losing the rest of the fixture evidence.
        check(untagged.isEmpty(),"all "+vehicles+" native Doomsday vehicle block registrations are salvageable: "+untagged);
        var origin=level.getSharedSpawnPos().offset(0,level.getMaxBuildHeight()-20-level.getSharedSpawnPos().getY(),0);
        Map<BlockPos,BlockState> old=new LinkedHashMap<>();
        for(BlockPos p:BlockPos.betweenClosed(origin.offset(-8,-1,-8),origin.offset(8,5,8)))old.put(p.immutable(),level.getBlockState(p));
        player.getInventory().clearContent();player.setGameMode(GameType.SURVIVAL);player.setPos(origin.getX()+.5,origin.getY(),origin.getZ()-4.5);
        try{
            clear(level,origin);
            var state=VehicleAssemblies.state("green_jeep_1",Direction.NORTH);
            check(state.is(Frontier.SALVAGE_VEHICLES),"intact green jeep shares the salvage vehicle tag");
            player.getInventory().setItem(0,new ItemStack(Frontier.ROUGH_PICKAXE.get()));player.getInventory().selected=0;
            check(player.hasCorrectToolForDrops(state),"rough iron pickaxe can harvest an intact vehicle");
            var totals=new LinkedHashMap<String,Map<String,Integer>>();
            for(String tool:List.of("gnfrontier:rough_iron_pickaxe","create:wrench")){
                clear(level,origin);var item=ForgeRegistries.ITEMS.getValue(new ResourceLocation(tool));player.getInventory().setItem(0,new ItemStack(item));
                level.setBlock(origin,state,3);
                var drops=Block.getDrops(state,level,origin,null,player,player.getMainHandItem());Map<String,Integer> counts=new HashMap<>();
                for(var drop:drops)counts.merge(ForgeRegistries.ITEMS.getKey(drop.getItem()).toString(),drop.getCount(),Integer::sum);
                totals.put(tool,counts);check(counts.getOrDefault("gnfrontier:rough_iron_ingot",0)>0&&counts.getOrDefault("gnfrontier:aluminum_scrap",0)>0,"native vehicle loot gives iron and aluminum using "+tool);
                check(drops.stream().noneMatch(s->s.getItem() instanceof BlockItem),"salvage does not duplicate the vehicle block item for "+tool);
            }
            check(totals.get("create:wrench").get("gnfrontier:rough_iron_ingot")>totals.get("gnfrontier:rough_iron_pickaxe").get("gnfrontier:rough_iron_ingot"),"Create wrench retains its salvage bonus");
            evidence.put("vehicleLoot",totals);
            int variants=0;
            for(var block:vehicleBlocks)for(String tool:List.of("gnfrontier:rough_iron_pickaxe","create:wrench")){
                var drops=Block.getDrops(block.defaultBlockState(),level,origin,null,player,new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(tool))));
                boolean iron=drops.stream().anyMatch(s->s.is(Frontier.ROUGH_IRON.get())&&s.getCount()>0),aluminum=drops.stream().anyMatch(s->s.is(Frontier.ALUMINUM_SCRAP.get())&&s.getCount()>0);
                if(!iron||!aluminum)throw new IllegalStateException("Vehicle material loot missing: "+ForgeRegistries.BLOCKS.getKey(block)+" using "+tool);variants++;
            }
            check(variants==vehicles*2,"all "+vehicles+" vehicle registrations yield iron and aluminum with both pickaxe and wrench");evidence.put("vehicleLootVariantsChecked",variants);
            for(Direction facing:Direction.Plane.HORIZONTAL){
                clear(level,origin);state=VehicleAssemblies.state("green_jeep_1",facing);level.setBlock(origin,state,3);
                var helpers=new ArrayList<BlockPos>();
                for(var p:old.keySet())if(ForgeRegistries.BLOCKS.getKey(level.getBlockState(p).getBlock()).toString().equals("doomsday_decoration:vehicle_collision"))helpers.add(p);
                check(!helpers.isEmpty(),"native vehicle helper geometry exists facing "+facing);
                BlockPos side=helpers.get(0);var collision=level.getBlockState(side).getCollisionShape(level,side);
                check(!collision.isEmpty(),"vehicle body collision is solid away from its anchor facing "+facing);
                for(var p:helpers)level.setBlock(p,Blocks.AIR.defaultBlockState(),2);
                state.randomTick(level,origin,level.random);
                check(!level.getBlockState(side).getCollisionShape(level,side).isEmpty(),"original helper repair restores missing worldgen collision facing "+facing);
            }
            clear(level,origin);player.getInventory().clearContent();
            Class<?> slots=Class.forName("nuparu.tinyinv.world.entity.player.PlayerSlots"),utils=Class.forName("nuparu.tinyinv.world.inventory.SlotUtils");
            var hotbar=slots.getMethod("getHotbarSlots",Player.class);var blocked=utils.getMethod("shouldRemoveSlot",Player.class,int.class);
            check((Integer)hotbar.invoke(null,player)==5,"survival HUD uses exactly five hotbar slots");
            player.setGameMode(GameType.CREATIVE);check((Integer)hotbar.invoke(null,player)==5,"creative HUD also keeps five hotbar slots");player.setGameMode(GameType.SURVIVAL);
            for(int i=0;i<9;i++)check((Boolean)blocked.invoke(null,player,i)==(i>=5),"inventory hotbar slot "+i+" agrees with HUD availability");
            var preserved=new ItemStack(Items.DIAMOND,7);preserved.getOrCreateTag().putString("GNPreserve","hotbar-original");player.getInventory().items.set(7,preserved.copy());HotbarRules.evacuate(player);
            check(player.getInventory().getItem(7).isEmpty()&&player.getInventory().countItem(Items.DIAMOND)==7,"occupied disabled hotbar stack moves without item loss");
            check(player.getInventory().items.stream().anyMatch(s->ItemStack.matches(s,preserved)),"hotbar relocation retains exact custom NBT and quantity");
            player.getInventory().clearContent();for(int i=0;i<36;i++)if(!HotbarRules.blocked(i))player.getInventory().items.set(i,new ItemStack(Items.STONE,64));
            player.getInventory().items.set(8,preserved.copy());HotbarRules.evacuate(player);
            check(player.getInventory().getItem(8).isEmpty()&&player.getPersistentData().getList("gnfrontier_hotbar_return",10).size()==1,"full inventory safely retains displaced items in the player's pending return data");
            player.getInventory().items.set(0,ItemStack.EMPTY);HotbarRules.evacuate(player);HotbarRules.evacuate(player);
            check(ItemStack.matches(player.getInventory().getItem(0),preserved)&&!player.getPersistentData().contains("gnfrontier_hotbar_return"),"freeing one slot returns the preserved item exactly once");
            player.getInventory().clearContent();check(TradeService.hasPda(player),"PDA is available with no terminal item in inventory");
            // Window opening is two blocks high, preserves the wall, and uses a collision-respecting push.
            BlockPos glass=origin.north();level.setBlock(glass,Blocks.GLASS.defaultBlockState(),3);level.setBlock(glass.above(),Blocks.GLASS_PANE.defaultBlockState(),3);level.setBlock(glass.above(2),Blocks.STONE.defaultBlockState(),3);
            player.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+.5);player.setYRot(180);player.setXRot(0);player.setDeltaMovement(Vec3.ZERO);
            WindowKick.kick(player);
            check(level.getBlockState(glass).isAir()&&level.getBlockState(glass.above()).isAir(),"kick opens both lower and upper window cells within two blocks");
            check(level.getBlockState(glass.above(2)).is(Blocks.STONE),"kick keeps solid wall outside the window passage");
            check(player.getDeltaMovement().horizontalDistance()>0&&player.getDeltaMovement().horizontalDistance()<=.21,"kick gives a small forward push without teleporting");
        }finally{
            for(var entry:old.entrySet())level.setBlock(entry.getKey(),entry.getValue(),3);
            player.getInventory().clearContent();player.setDeltaMovement(Vec3.ZERO);
        }
        evidence.put("checks",checks);return evidence;
    }
    private static void clear(ServerLevel level,BlockPos origin){
        for(BlockPos p:BlockPos.betweenClosed(origin.offset(-8,0,-8),origin.offset(8,5,8)))level.setBlock(p,Blocks.AIR.defaultBlockState(),3);
        for(BlockPos p:BlockPos.betweenClosed(origin.offset(-8,-1,-8),origin.offset(8,-1,8)))level.setBlock(p,Blocks.STONE.defaultBlockState(),3);
    }
}
