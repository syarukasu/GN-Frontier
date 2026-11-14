package local.gnfrontier;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;

public final class ScavengerHook extends ThrowableProjectile {
    private static final EntityDataAccessor<Integer> OWNER=SynchedEntityData.defineId(ScavengerHook.class,EntityDataSerializers.INT);
    private ServerPlayer hooked;
    private int reelTicks;
    public ScavengerHook(EntityType<? extends ScavengerHook> type,Level level){super(type,level);}
    public ScavengerHook(LivingEntity owner){
        super(Frontier.SCAVENGER_HOOK.get(),owner,owner.level());
        entityData.set(OWNER,owner.getId());
    }
    @Override protected void defineSynchedData(){entityData.define(OWNER,0);}
    public Entity visibleOwner(){return level().getEntity(entityData.get(OWNER));}
    @Override protected float getGravity(){return .01f;}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    @Override protected boolean canHitEntity(Entity entity){return entity instanceof ServerPlayer && super.canHitEntity(entity);}
    @Override protected void onHitEntity(EntityHitResult hit){
        if(!level().isClientSide && hit.getEntity() instanceof ServerPlayer player){
            if(player.isCreative() || player.isSpectator() || RespawnRules.protectedFromPull(player)){discard();return;}
            hooked=player;setDeltaMovement(Vec3.ZERO);setNoGravity(true);
        }
    }
    @Override protected void onHitBlock(BlockHitResult hit){if(!level().isClientSide)discard();}
    @Override public void tick(){
        if(hooked==null)super.tick();else {baseTick();setPos(hooked.getBoundingBox().getCenter());}
        if(level().isClientSide)return;
        if(!(getOwner() instanceof LivingEntity owner) || !owner.isAlive() || tickCount>50
                || !owner.getMainHandItem().is(Items.FISHING_ROD) || !RespawnRules.enabled(level())){discard();return;}
        if(hooked==null)return;
        if(!hooked.isAlive() || hooked.isCreative() || hooked.isSpectator() || RespawnRules.protectedFromPull(hooked)
                || hooked.level()!=level() || owner.distanceToSqr(hooked)>Math.pow(PrototypeConfig.ROD_RANGE.get()+2,2)){
            discard();return;
        }
        if(level().clip(new ClipContext(owner.getEyePosition(),hooked.getEyePosition(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner)).getType()!=HitResult.Type.MISS){discard();return;}
        if(++reelTicks<8)return;
        Vec3 toward=owner.position().subtract(hooked.position());
        double horizontal=Math.sqrt(toward.x*toward.x+toward.z*toward.z);
        if(horizontal>.1){
            // Vanilla knockback dispatches Forge's cancelable event and honors knockback resistance.
            hooked.knockback(PrototypeConfig.ROD_PULL.get(),-toward.x,-toward.z);
            hooked.hurtMarked=true;
            hooked.connection.send(new ClientboundSetEntityMotionPacket(hooked));
        }
        level().playSound(null,owner.blockPosition(),SoundEvents.FISHING_BOBBER_RETRIEVE,SoundSource.HOSTILE,.8f,.9f);
        discard();
    }
}
