package local.gnfrontier;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/** Apply once at birth; persistent attributes preserve the profile through dawn and reloads. */
public final class NightZombies {
    public static final String MARKER="gnfrontier_night_zombie_v1";
    public static final UUID SPEED_ID=UUID.fromString("7f149e12-2ecf-4b08-9042-cde85f41553b");
    private NightZombies(){}
    public static boolean nightTime(long dayTime){long time=Math.floorMod(dayTime,24000L);return time>=13000&&time<23000;}
    public static boolean initializeFresh(Zombie zombie,ServerLevel level){
        if(level.dimension()!=Level.OVERWORLD||!PrototypeConfig.NIGHT_ZOMBIES.get()
                ||!nightTime(level.getDayTime())||zombie.getPersistentData().getBoolean(MARKER))return false;
        var health=Objects.requireNonNull(zombie.getAttribute(Attributes.MAX_HEALTH));
        var attack=Objects.requireNonNull(zombie.getAttribute(Attributes.ATTACK_DAMAGE));
        var speed=Objects.requireNonNull(zombie.getAttribute(Attributes.MOVEMENT_SPEED));
        float fraction=zombie.getMaxHealth()>0?zombie.getHealth()/zombie.getMaxHealth():1;
        health.setBaseValue(Math.max(health.getBaseValue(),PrototypeConfig.NIGHT_ZOMBIE_HEALTH.get()));
        attack.setBaseValue(Math.max(attack.getBaseValue(),PrototypeConfig.NIGHT_ZOMBIE_DAMAGE.get()));
        if(speed.getModifier(SPEED_ID)==null&&PrototypeConfig.NIGHT_ZOMBIE_SPEED.get()>1)
            speed.addPermanentModifier(new AttributeModifier(SPEED_ID,"GN Frontier night-born speed",
                PrototypeConfig.NIGHT_ZOMBIE_SPEED.get()-1,AttributeModifier.Operation.MULTIPLY_TOTAL));
        zombie.setHealth(zombie.getMaxHealth()*fraction);
        zombie.getPersistentData().putBoolean(MARKER,true);
        return true;
    }
}
