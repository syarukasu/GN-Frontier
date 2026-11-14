package local.gnfrontier;

import java.util.UUID;
import local.gnfrontier.core.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.*;
import net.minecraftforge.registries.*;

/** Original machines with real Forge Energy accounting and persistent ownership/work. */
public final class Infrastructure {
    public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,Frontier.ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,Frontier.ID);
    public static final RegistryObject<Block> MINING=BLOCKS.register("mining_rig",()->new MachineBlock(0));
    public static final RegistryObject<Block> TERRAFORMER=BLOCKS.register("terraformer",()->new MachineBlock(1));
    public static final RegistryObject<Block> LAUNCHER=BLOCKS.register("orbital_launcher",()->new MachineBlock(2));
    public static final RegistryObject<Block> RELAY=BLOCKS.register("ground_uplink",()->new MachineBlock(3));
    public static final RegistryObject<Block> DETERRENCE=BLOCKS.register("deterrence_station",()->new MachineBlock(4));
    public static final RegistryObject<Item> MINING_ITEM=Frontier.ITEMS.register("mining_rig",()->new BlockItem(MINING.get(),new Item.Properties()));
    public static final RegistryObject<Item> TERRAFORMER_ITEM=Frontier.ITEMS.register("terraformer",()->new BlockItem(TERRAFORMER.get(),new Item.Properties()));
    public static final RegistryObject<Item> LAUNCHER_ITEM=Frontier.ITEMS.register("orbital_launcher",()->new BlockItem(LAUNCHER.get(),new Item.Properties()));
    public static final RegistryObject<Item> RELAY_ITEM=Frontier.ITEMS.register("ground_uplink",()->new BlockItem(RELAY.get(),new Item.Properties()));
    public static final RegistryObject<Item> DETERRENCE_ITEM=Frontier.ITEMS.register("deterrence_station",()->new BlockItem(DETERRENCE.get(),new Item.Properties()));
    public static final RegistryObject<BlockEntityType<Machine>> MACHINE=BLOCK_ENTITIES.register("machine",()->BlockEntityType.Builder.of(Machine::new,MINING.get(),TERRAFORMER.get(),LAUNCHER.get(),RELAY.get(),DETERRENCE.get()).build(null));
    public static final class MachineBlock extends BaseEntityBlock {
        private final int kind;
        MachineBlock(int k){super(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(4));kind=k;}
        @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new Machine(pos,state);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState state,BlockEntityType<T> type){
            return l.isClientSide?null:createTickerHelper(type,MACHINE.get(),Machine::tick);
        }
        @Override public void setPlacedBy(Level l,BlockPos pos,BlockState state,LivingEntity who,ItemStack stack){
            if(who instanceof Player p && l.getBlockEntity(pos) instanceof Machine machine){machine.owner=p.getUUID();machine.setChanged();}
        }
        @Override public InteractionResult use(BlockState state,Level l,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
            if(!l.isClientSide && player instanceof ServerPlayer p && l.getBlockEntity(pos) instanceof Machine machine)machine.interact(p,hand);
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        @Override public void onRemove(BlockState state,Level l,BlockPos pos,BlockState next,boolean moving){
            if(!state.is(next.getBlock()) && l.getBlockEntity(pos) instanceof Machine m && !l.isClientSide){
                if(!m.cpu.isEmpty())Block.popResource(l,pos,new ItemStack(TradeService.item(m.cpu)));
                if(!m.gpu.isEmpty())Block.popResource(l,pos,new ItemStack(TradeService.item(m.gpu)));
                if(m.seeded && (kind==2||kind==4))Block.popResource(l,pos,new ItemStack(kind==2?Frontier.SATELLITE.get():Frontier.DETERRENCE.get()));
                if(kind==2 && m.engine>0)Block.popResource(l,pos,new ItemStack(TradeService.item("ad_astra:steel_engine")));
                if(m.owner!=null && l instanceof ServerLevel sl){var c=WorldState.get(sl).campaign(m.owner);if(kind==3)c.uplinkUntil=0;if(kind==4)c.deterrenceUntil=0;WorldState.get(sl).setDirty();}
            }super.onRemove(state,l,pos,next,moving);
        }
    }
    public static final class Machine extends BlockEntity {
        private UUID owner;
        private long work;
        private boolean seeded,finished;
        private String cpu="",gpu="";
        private int engine,fuel,terrainCursor,greened;
        private final EnergyStorage energy=new EnergyStorage(100000,2000,0) {
            @Override public int receiveEnergy(int amount,boolean simulate){int n=super.receiveEnergy(amount,simulate);if(n>0&&!simulate)setChanged();return n;}
        };
        private LazyOptional<IEnergyStorage> energyCap=LazyOptional.of(()->energy);
        public Machine(BlockPos pos,BlockState state){super(MACHINE.get(),pos,state);}
        @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,Direction side){return cap==ForgeCapabilities.ENERGY?energyCap.cast():super.getCapability(cap,side);}
        @Override public void invalidateCaps(){super.invalidateCaps();energyCap.invalidate();}
        @Override public void reviveCaps(){super.reviveCaps();energyCap=LazyOptional.of(()->energy);}
        @Override protected void saveAdditional(CompoundTag n){super.saveAdditional(n);if(owner!=null)n.putUUID("owner",owner);
            n.putLong("work",work);n.putInt("energy",energy.getEnergyStored());n.putBoolean("seeded",seeded);n.putBoolean("finished",finished);n.putString("cpu",cpu);n.putString("gpu",gpu);
            n.putInt("engine",engine);n.putInt("fuel",fuel);n.putInt("terrainCursor",terrainCursor);n.putInt("greened",greened);}
        @Override public void load(CompoundTag n){super.load(n);owner=n.hasUUID("owner")?n.getUUID("owner"):null;
            work=Math.max(0,n.getLong("work"));energy.deserializeNBT(net.minecraft.nbt.IntTag.valueOf(Math.max(0,Math.min(100000,n.getInt("energy")))));
            seeded=n.getBoolean("seeded");finished=n.getBoolean("finished");cpu=n.getString("cpu");gpu=n.getString("gpu");
            engine=Math.min(1,Math.max(0,n.getInt("engine")));fuel=Math.min(2,Math.max(0,n.getInt("fuel")));terrainCursor=Math.max(0,n.getInt("terrainCursor"));greened=Math.max(0,n.getInt("greened"));}
        private void consume(int amount){energy.deserializeNBT(net.minecraft.nbt.IntTag.valueOf(energy.getEnergyStored()-amount));}
        private void interact(ServerPlayer p,InteractionHand hand){
            if(!WorldState.get(p.serverLevel()).enabled)return;
            if(owner==null)owner=p.getUUID();
            if(!owner.equals(p.getUUID())&&!p.isCreative()){p.sendSystemMessage(Component.literal("所有者だけが設定できます"));return;}
            ItemStack s=p.getItemInHand(hand);String id=ForgeRegistries.ITEMS.getKey(s.getItem()).toString();
            int kind=((MachineBlock)getBlockState().getBlock()).kind;
            if(kind==2) {
                if(!finished && s.is(Frontier.SATELLITE.get())&&!seeded){seeded=true;s.shrink(1);}
                else if(!finished && id.equals("ad_astra:steel_engine")&&engine==0){engine=1;s.shrink(1);}
                else if(!finished && id.equals("tfmg:diesel_bucket")&&fuel<2){fuel++;s.shrink(1);ItemStack bucket=new ItemStack(Items.BUCKET);if(!p.getInventory().add(bucket))p.drop(bucket,false);}
                setChanged();p.sendSystemMessage(Component.literal("衛星打上げ設備：衛星 "+seeded+" / エンジン "+engine+" / 燃料 "+fuel+"/2 / "+work+"/320000 FE。Ad Astra次元・上空開放が必要"));return;
            }
            if(kind==3){p.sendSystemMessage(Component.literal("地上中継器：蓄電 "+energy.getEnergyStored()+" FE / 衛星 "+WorldState.get(p.serverLevel()).campaign(owner).satellitesLaunched+"基 / 消費20 FE/t"));return;}
            if(kind==4){if(s.is(Frontier.DETERRENCE.get())&&!seeded){seeded=true;s.shrink(1);setChanged();}p.sendSystemMessage(Component.literal("戦略抑止基地：制御装置 "+seeded+" / "+energy.getEnergyStored()+" FE / 消費80 FE/t"));return;}
            if(getBlockState().is(TERRAFORMER.get())) {
                if(s.is(Frontier.NOAH.get()) && !seeded && isPlanet(p.serverLevel())){seeded=true;s.shrink(1);setChanged();}
                p.sendSystemMessage(Component.literal("Noah："+(finished?"大気完了・緑化 "+greened+"列 / 走査 "+terrainCursor+"/16641":seeded?"大気改変中":"Noahシードが必要")+" / "+work+" / 2400000 FE / 蓄電 "+energy.getEnergyStored()+" FE"));
            } else {
                boolean isCpu=cpuScore(id)>0,isGpu=gpuScore(id)>0;
                if(isCpu||isGpu){String old=isCpu?cpu:gpu;if(isCpu)cpu=id;else gpu=id;s.shrink(1);
                    if(!old.isEmpty()){ItemStack returned=new ItemStack(TradeService.item(old));if(!p.getInventory().add(returned))p.drop(returned,false);}setChanged();}
                p.sendSystemMessage(Component.literal("マイニング Rank I：CPU "+cpuScore(cpu)+" / GPU "+gpuScore(gpu)+" / "+energy.getEnergyStored()+" FE / 処理 "+work+" tick"));
            }
        }
        private static boolean isPlanet(ServerLevel l){return l.dimension().location().equals(new net.minecraft.resources.ResourceLocation("ad_astra:mars")) || l.dimension().location().equals(new net.minecraft.resources.ResourceLocation("ad_astra:glacio"));}
        private static int cpuScore(String id){return switch(id){case "gnfrontier:cpu_pentium_g4560"->1;case "gnfrontier:cpu_i3_6100"->2;case "gnfrontier:cpu_i5_2500k"->3;default->0;};}
        private static int gpuScore(String id){return switch(id){case "gnfrontier:gpu_8800_gtx"->1;case "gnfrontier:gpu_gtx_480"->2;default->0;};}
        private static void tick(Level level,BlockPos pos,BlockState state,Machine m){
            if(!(level instanceof ServerLevel l) || m.owner==null)return;WorldState w=WorldState.get(l);if(!w.enabled)return;
            int kind=((MachineBlock)state.getBlock()).kind;long now=l.getServer().overworld().getGameTime();Campaign.State campaign=w.campaign(m.owner);
            if(kind==2){
                if(m.finished||!m.seeded||m.engine!=1||m.fuel!=2||m.energy.getEnergyStored()<160||!l.dimension().location().getNamespace().equals("ad_astra")||!l.canSeeSky(pos.above()))return;
                m.consume(160);m.work+=160;m.setChanged();
                if(l.getGameTime()%10==0)l.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,pos.getX()+.5,pos.getY()+1.1,pos.getZ()+.5,5,.2,.1,.2,.04);
                if(m.work>=320000){m.finished=true;m.seeded=false;m.engine=0;m.fuel=0;campaign.satellite=true;campaign.satellitesLaunched++;w.setDirty();
                    l.playSound(null,pos,net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LAUNCH,net.minecraft.sounds.SoundSource.BLOCKS,2,.5f);
                    l.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,pos.getX()+.5,pos.getY()+5,pos.getZ()+.5,100,.5,5,.5,.1);}
                return;
            }
            if(kind==3||kind==4){int cost=kind==3?20:80;if(m.energy.getEnergyStored()<cost || (kind==3?!campaign.satellite:!m.seeded))return;
                m.consume(cost);if(kind==3)campaign.uplinkUntil=now+40;else{campaign.deterrence=true;campaign.deterrenceUntil=now+40;}m.setChanged();w.setDirty();return;}
            if(state.is(TERRAFORMER.get())) {
                if(!m.seeded||!isPlanet(l))return;
                if(m.finished){for(int i=0;i<4 && m.terrainCursor<16641 && m.energy.getEnergyStored()>=100;i++){
                    int result=NoahLandscape.column(l,pos,m.owner,m.terrainCursor);if(result<0)break;
                    m.terrainCursor++;if(result>0){m.consume(100);m.greened++;}m.setChanged();}return;}
                if(m.energy.getEnergyStored()<80)return;
                m.consume(80);m.work+=80;m.setChanged();
                if(m.work>=2400000){m.finished=true;Campaign.State c=w.campaign(m.owner);c.noahComplete=true;c.noahDimension=l.dimension().location().toString();c.noahAnchor=pos.asLong();w.setDirty();
                    w.terraformedPlanets.add(c.noahDimension);AtmosphereOverrides.refresh(l.getServer());l.getServer().getPlayerList().broadcastSystemMessage(Component.literal("Project Noah："+c.noahDimension+" の大気・温度改変が完了しました"),false);}
            } else {
                int cpu=cpuScore(m.cpu),gpu=gpuScore(m.gpu);if(cpu==0||gpu==0)return;int cost=20+10*cpu+20*gpu;
                Economy.State account=w.account(m.owner);long reward=2L*gpu;
                if(account.balance>Economy.MAX_MONEY-reward || m.energy.getEnergyStored()<cost)return;
                m.consume(cost);m.work+=cpu;m.setChanged();
                if(m.work>=1200){m.work-=1200;account.balance+=reward;w.setDirty();}
            }
        }
    }
}
