package local.gnfrontier.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.WeakHashMap;
import local.gnfrontier.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** Reuses the defeated unit's original model, uniform and worn equipment without a new texture. */
public final class FallenSoldierRenderer extends EntityRenderer<FallenSoldier> {
    private final WeakHashMap<FallenSoldier,LivingEntity> bodies=new WeakHashMap<>();
    private final WeakHashMap<LivingEntity,AnimationState> nativeDeathPoses=new WeakHashMap<>();
    public FallenSoldierRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=.45f;}
    @Override public ResourceLocation getTextureLocation(FallenSoldier corpse){return new ResourceLocation("minecraft","textures/entity/steve.png");}
    @Override public void render(FallenSoldier corpse,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){
        LivingEntity body=bodies.computeIfAbsent(corpse,c->{
            var type=EntityType.byString(c.appearance().getString("id")).orElse(null);if(type==null)return null;
            var entity=type.create(c.level());if(!(entity instanceof LivingEntity living))return null;
            living.load(c.appearance().copy());living.setCustomNameVisible(false);living.setCustomName(null);
            living.deathTime=0;living.hurtTime=0;living.setHealth(living.getMaxHealth());
            AnimationState deathPose=freezeNativeDeathPose(living);
            if(deathPose!=null)nativeDeathPoses.put(living,deathPose);
            return living;
        });
        if(body!=null){
            for(int slot=0;slot<NpcInventory.EQUIPMENT.length;slot++){
                ItemStack visible=corpse.visibleEquipment(slot);
                if(!ItemStack.matches(body.getItemBySlot(NpcInventory.EQUIPMENT[slot]),visible))body.setItemSlot(NpcInventory.EQUIPMENT[slot],visible.copy());
            }
            body.setPos(corpse.position());body.setYRot(0);body.yRotO=0;body.yBodyRot=body.yBodyRotO=0;body.yHeadRot=body.yHeadRotO=0;
            poses.pushPose();
            try{
                poses.mulPose(Axis.YP.rotationDegrees(-yaw));
                if(!nativeDeathPoses.containsKey(body)){poses.translate(0,.32,.9);poses.mulPose(Axis.XP.rotationDegrees(-90));}
                EntityRenderer<? super LivingEntity> renderer=entityRenderDispatcher.getRenderer(body);
                if(!CorpseBodyLayers.render(corpse,body,renderer,poses,buffers,light))renderer.render(body,0,0,poses,buffers,light);
                // Native render layers also initialize animation state on the first real render.
                // Restore the completed state afterwards so subsequent frames stay at rest.
                AnimationState death=nativeDeathPoses.get(body);
                if(death!=null){death.start(0);death.updateTime(20,1);}
            }finally{poses.popPose();}
        }
        super.render(corpse,yaw,partial,poses,buffers,light);
    }
    public static void bind(IEventBus bus){
        bus.addListener((EntityRenderersEvent.RegisterRenderers event)->event.registerEntityRenderer(Frontier.FALLEN_SOLDIER.get(),FallenSoldierRenderer::new));
        bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event)->event.enqueueWork(()->net.minecraft.client.gui.screens.MenuScreens.register(Frontier.CORPSE_MENU.get(),CorpseScreen::new)));
    }
    /** Simple Enemy/Combined Arms already supply a complete fallen pose, including relaxed arms. */
    @SuppressWarnings("unchecked")
    private AnimationState freezeNativeDeathPose(LivingEntity body){
        try{
            Object value=body.getClass().getField("deathAnimationState").get(body);
            if(!(value instanceof AnimationState state))return null;
            // Keep the native manager on its death layer rather than idle/weapon animations.
            body.setHealth(0);
            // Native model setup initializes its animation manager and can reset AnimationState.
            // Initialize first, then hold its one-second death animation at the completed frame.
            var renderer=entityRenderDispatcher.getRenderer(body);
            if(renderer instanceof LivingEntityRenderer<?,?> living)
                ((net.minecraft.client.model.EntityModel<LivingEntity>)living.getModel()).setupAnim(body,0,0,0,0,0);
            body.tickCount=20;state.start(0);state.updateTime(20,1);return state;
        }catch(ReflectiveOperationException ignored){return null;}
    }
}
