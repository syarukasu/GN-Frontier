package local.gnreview;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import com.mojang.brigadier.context.CommandContext;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import local.gnfrontier.*;
import local.gnfrontier.core.*;
import net.minecraft.commands.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Test-only mod, never installed into the user's instance. Own disposable world only. */
@Mod("gnreview")
public final class VideoChecks {
    private static final List<String> passed=new ArrayList<>();
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);passed.add(name);}
    private static void close(float actual,float expected,String name){check(Math.abs(actual-expected)<.0001,name+"="+actual);}
    public VideoChecks(){MinecraftForge.EVENT_BUS.addListener(this::commands);}
    private void commands(RegisterCommandsEvent event){event.getDispatcher().register(Commands.literal("gnreview").requires(s->s.hasPermission(2)).executes(this::run));}
    private static ResourceLocation id(String name){return new ResourceLocation(name);}
    private static ItemStack item(String name){var value=ForgeRegistries.ITEMS.getValue(id(name));if(value==null||value==Items.AIR)throw new AssertionError("Missing item "+name);return new ItemStack(value);}
    private static final class Menu extends AbstractContainerMenu {
        Menu(){super(null,0);}public ItemStack quickMoveStack(Player p,int slot){return ItemStack.EMPTY;}public boolean stillValid(Player p){return true;}
    }
    private static CraftingContainer grid(int w,int h,String... inputs){
        var grid=new TransientCraftingContainer(new Menu(),w,h);for(int i=0;i<inputs.length;i++)if(!inputs[i].isEmpty())grid.setItem(i,item(inputs[i]));return grid;
    }
    private static CraftingRecipe craft(ServerLevel level,CraftingContainer grid,String result,int count){
        var recipe=level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,level).orElseThrow(()->new AssertionError("No recipe "+result));
        var made=recipe.assemble(grid,level.registryAccess());check(made.is(item(result).getItem())&&made.getCount()==count,"craft "+result+" x"+count);return recipe;
    }
    private static void recipes(ServerLevel level){
        String r="gnfrontier:rough_iron_ingot",w="tfmg:copper_wire",s="minecraft:stick",scrap="gnfrontier:metal_scrap";
        craft(level,grid(2,2,r,r,"minecraft:oak_planks","minecraft:oak_planks"),"minecraft:crafting_table",1);
        craft(level,grid(3,3,r,r,r,"",w,"","",s,""),"gnfrontier:rough_iron_pickaxe",1);
        check(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid(3,3,r,r,r,"",w,"","",w,""),level).isEmpty(),"old pickaxe rejected");
        craft(level,grid(3,2,r,r,r,"","",w),"create:hand_crank",1);
        craft(level,grid(3,3,scrap,scrap,scrap,scrap,scrap,scrap,scrap,scrap,scrap),"gnfrontier:scrap_bundle",1);
        check(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid(2,2,scrap,scrap,scrap,scrap),level).isEmpty(),"handwork bypass absent");
        craft(level,grid(2,2,"survival_instinct:cloth","",s,""),"gnfrontier:unlit_torch",1);
        var torch=grid(2,2,"gnfrontier:unlit_torch","minecraft:flint_and_steel","","");
        var recipe=craft(level,torch,"minecraft:torch",4);
        torch.getItem(1).setHoverName(net.minecraft.network.chat.Component.literal("kept marker"));
        torch.getItem(1).setDamageValue(7);var remainder=recipe.getRemainingItems(torch).get(1);
        check(remainder.is(Items.FLINT_AND_STEEL)&&remainder.getDamageValue()==8&&remainder.hasCustomHoverName(),"lighter remainder NBT and wear");
        torch.getItem(1).setDamageValue(torch.getItem(1).getMaxDamage()-1);
        check(recipe.getRemainingItems(torch).get(1).isEmpty(),"lighter breaks on final use");
        check(level.getRecipeManager().byKey(id("gnfrontier:rough_iron_fan")).isPresent(),"Create blasting route present");
        for(String forbidden:List.of("minecraft:iron_pickaxe","minecraft:diamond_helmet","minecraft:wooden_sword")) {
            check(level.getRecipeManager().getRecipes().stream().noneMatch(x->x.getResultItem(level.registryAccess()).is(item(forbidden).getItem())),"vanilla recipe absent "+forbidden);
        }
        check(level.getRecipeManager().byKey(id("gnfrontier:aluminum_chunk_recycling")).isPresent(),"new recycling registered");
    }
    private static void guns(){
        for(String name:List.of("sharps","mp18","sks","win1894","colt1873")) {
            var index=TimelessAPI.getCommonGunIndex(id("hamster:"+name)).orElseThrow();var data=index.getGunData();var bullet=index.getBulletData();
            close(bullet.getExtraDamage().getHeadShotMultiplier(),1.5f,name+" head multiplier");
            if(!name.equals("sharps"))close(bullet.getExtraDamage().getArmorIgnore(),.4f,name+" armor");
            if(name.equals("sks"))close(bullet.getDamageAmount(),15,name+" damage");
            if(name.equals("sharps"))close(bullet.getDamageAmount(),40,name+" damage");
            if(name.equals("win1894"))check(data.getAmmoAmount()==8,"Win magazine 8 plus native chamber");
            check(data.isCanCrawl(),name+" crawling enabled");
        }
    }
    private static void bed(ServerPlayer p,ServerLevel level,BlockPos base){
        var w=WorldState.get(level);w.players.put(p.getUUID(),new Economy.State());w.respawns.remove(p.getUUID());
        BlockPos foot=base,head=base.east();
        level.setBlock(foot.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(head.below(),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(foot,Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST).setValue(BedBlock.PART,BedPart.FOOT),2);
        level.setBlock(head,Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST).setValue(BedBlock.PART,BedPart.HEAD),3);
        check(RespawnRules.registerBed(p,level,foot),"first bed registers immediately");
        check(RespawnRules.registerBed(p,level,head),"same bed re-register allowed");
        BlockPos f2=foot.south(8),h2=f2.east();
        level.setBlock(f2.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(h2.below(),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(f2,Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST).setValue(BedBlock.PART,BedPart.FOOT),2);
        level.setBlock(h2,Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST).setValue(BedBlock.PART,BedPart.HEAD),3);
        check(!RespawnRules.registerBed(p,level,f2),"bed relocation cooldown enforced");
        check(RespawnRules.withinArea(level,Vec3.atCenterOf(head).add(4,0,0)),"bed 5m protection active");
        level.setBlock(head,Blocks.AIR.defaultBlockState(),3);
        check(w.account(p.getUUID()).spawnDimension.isEmpty(),"bed removal mixin clears registration");
        check(!RespawnRules.registeredBed(level,head),"destroyed bed not retained");
        var dirty=ForgeRegistries.BLOCKS.getValue(id("doomsday_decoration:bed"));
        check(dirty!=null && dirty.defaultBlockState().is(Frontier.UNHYGIENIC_BEDS),"dirty bed tag registered");
        level.setBlock(f2,dirty.defaultBlockState(),3);
        var event=new PlayerInteractEvent.RightClickBlock(p,InteractionHand.MAIN_HAND,f2,new BlockHitResult(Vec3.atCenterOf(f2),Direction.UP,f2,false));
        MinecraftForge.EVENT_BUS.post(event);check(event.isCanceled()&&event.getCancellationResult()==InteractionResult.FAIL,"dirty bed right-click blocked");
    }
    private static void gate(ServerPlayer p,ServerLevel level){
        var world=WorldState.get(level);world.campaigns.put(p.getUUID(),new Campaign.State());p.getInventory().clearContent();p.getInventory().add(new ItemStack(Frontier.PDA.get()));
        var account=world.account(p.getUUID());account.balance=10000;Arrays.fill(account.traderReputation,100);Arrays.fill(account.reputation,100);
        check(!IndustrialProgress.canBuy(p,"mask"),"mask purchase steel gate closed");
        long money=account.balance;boolean rejected=false;
        try{TradeService.trade(p,"mask",Economy.Direction.BUY,1);}catch(IllegalArgumentException expected){rejected=expected.getMessage().equals("STEEL REQUIRED");}
        check(rejected&&account.balance==money,"forged buy does not deduct money");
        p.getInventory().add(item("tfmg:steel_ingot"));check(IndustrialProgress.canBuy(p,"mask"),"steel acquisition opens gate");
        p.getInventory().clearContent();check(IndustrialProgress.canBuy(p,"mask"),"steel milestone persists after use");
        var restored=Campaign.State.load(world.campaign(p.getUUID()).save());check(restored.milestones.contains("gnfrontier:steel_acquired"),"steel milestone survives save");
    }
    private static void rod(ServerPlayer p,ServerLevel level,BlockPos base)throws Exception {
        p.getInventory().clearContent();p.setGameMode(GameType.SURVIVAL);p.teleportTo(level,base.getX()+.5,base.getY(),base.getZ()+.5,0,0);
        var w=WorldState.get(level);w.respawns.remove(p.getUUID());w.account(p.getUUID()).spawnDimension="";
        Zombie zombie=new Zombie(level);zombie.moveTo(base.getX()+8.5,base.getY(),base.getZ()+.5,90,0);zombie.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.FISHING_ROD));level.addFreshEntity(zombie);zombie.setTarget(p);
        try{
            check(zombie.goalSelector.getAvailableGoals().stream().anyMatch(x->x.getGoal() instanceof RodZombieGoal),"rod goal attached by spawn event");
            net.minecraft.world.entity.ai.goal.Goal goal=new RodZombieGoal(zombie);check(goal.canUse(),"rod goal sees exposed target");
            var protection=new RespawnPolicy.State();protection.beginGrace(level.getGameTime(),200);w.respawns.put(p.getUUID(),protection);
            check(!goal.canUse(),"rod goal obeys respawn grace");w.respawns.remove(p.getUUID());
            BlockPos wall=base.east(4);level.setBlock(wall,Blocks.STONE.defaultBlockState(),3);level.setBlock(wall.above(),Blocks.STONE.defaultBlockState(),3);
            check(!goal.canUse(),"rod does not cast through solid wall");level.setBlock(wall,Blocks.AIR.defaultBlockState(),3);level.setBlock(wall.above(),Blocks.AIR.defaultBlockState(),3);
            ScavengerHook hook=new ScavengerHook(zombie);level.addFreshEntity(hook);
            var hit=Arrays.stream(ScavengerHook.class.getDeclaredMethods()).filter(m->Arrays.equals(m.getParameterTypes(),new Class<?>[]{EntityHitResult.class})).findFirst().orElseThrow();hit.setAccessible(true);hit.invoke(hook,new EntityHitResult(p));p.setDeltaMovement(Vec3.ZERO);
            for(int i=0;i<8;i++)((Entity)hook).tick();check(p.getDeltaMovement().x>.1,"hook pulls toward zombie");check(((Entity)hook).isRemoved(),"hook expires after reel");
        }finally{zombie.discard();p.setGameMode(GameType.CREATIVE);}
    }
    private int run(CommandContext<CommandSourceStack> context){
        var server=context.getSource().getServer();
        if(!server.getWorldData().getLevelName().equals("GN_Review_r11_20261112"))throw new IllegalStateException("Owned disposable review world required");
        JsonObject report=new JsonObject();passed.clear();
        try{
            ServerPlayer p=server.getPlayerList().getPlayers().get(0);ServerLevel level=p.serverLevel();WorldState.get(level).enabled=true;
            recipes(level);guns();
            BlockPos base=new BlockPos(p.getBlockX()+30,140,p.getBlockZ());level.getChunkAt(base);
            for(int x=-2;x<=12;x++)for(int z=-2;z<=12;z++)for(int y=-1;y<=3;y++)level.setBlock(base.offset(x,y,z),y==-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            p.teleportTo(level,base.getX()+.5,base.getY(),base.getZ()+.5,0,0);p.setGameMode(GameType.SURVIVAL);
            bed(p,level,base.south(5));gate(p,level);rod(p,level,base);
            p.setGameMode(GameType.CREATIVE);p.getInventory().clearContent();
            check(!MilitarySites.near(level,new BlockPos(29000000,100,29000000)),"rotor site check does not force distant chunks");
            report.addProperty("status","PASS");
        }catch(Throwable error){report.addProperty("status","FAIL");report.addProperty("error",error.toString());error.printStackTrace();}
        JsonArray checks=new JsonArray();passed.forEach(checks::add);report.add("checks",checks);report.addProperty("count",passed.size());
        try{Files.writeString(Path.of("r11-runtime-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception ex){throw new RuntimeException(ex);}
        server.getPlayerList().getPlayers().forEach(p->p.setGameMode(GameType.CREATIVE));
        System.out.println("GN_R11_RUNTIME "+report.get("status")+" checks="+passed.size());return report.get("status").getAsString().equals("PASS")?1:0;
    }
}
