package local.gnfrontier;
import java.lang.reflect.Field;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.GunIndexPOJO;
import com.tacz.guns.resource.pojo.data.gun.*;

/** Base ammunition only: individual TaCZ rarity/attribute rolls remain intact. */
public final class GunTuning {
    private static void set(Object object,String field,float value){try{Field f=object.getClass().getDeclaredField(field);f.setAccessible(true);f.setFloat(object,value);}catch(ReflectiveOperationException error){throw new IllegalStateException("TaCZ 1.2 tuning field changed: "+field,error);}}
    public static void apply(GunIndexPOJO pojo,CommonGunIndex index){
        if(index==null || pojo.getData()==null)return;
        // TaCZ already owns the key, pose, eye height and movement. GR disables it in its data.
        if(pojo.getData().getNamespace().equals("hamster")) {
            try {Field crawl=GunData.class.getDeclaredField("canCrawl");crawl.setAccessible(true);crawl.setBoolean(index.getGunData(),true);}
            catch(ReflectiveOperationException error){throw new IllegalStateException("TaCZ crawl data changed",error);}
        }
        switch(pojo.getData().toString()){
            case "hamster:sharps_data" -> tuneSharps(index.getBulletData());
            case "hamster:mp18_data" -> tuneMp18(index.getBulletData());
            case "hamster:sks_data" -> tuneSks(index.getBulletData());
            case "hamster:win1894_data", "hamster:colt1873_data" -> tuneMp18(index.getBulletData());
        }
    }
    public static void tuneMp18(BulletData bullet){set(bullet.getExtraDamage(),"armorIgnore",.4f);set(bullet.getExtraDamage(),"headShotMultiplier",1.5f);}
    public static void tuneSks(BulletData bullet){scaleDamage(bullet,15f);tuneMp18(bullet);}
    public static void tuneSharps(BulletData bullet){
        scaleDamage(bullet,40f);set(bullet.getExtraDamage(),"headShotMultiplier",1.5f);
    }
    private static void scaleDamage(BulletData bullet,float damage){
        float old=bullet.getDamageAmount();if(old<=0)throw new IllegalStateException("Invalid base damage");
        float ratio=damage/old;set(bullet,"damageAmount",damage);ExtraDamage extra=bullet.getExtraDamage();
        var original=extra.getDamageAdjust();
        if(original!=null){var adjusted=new java.util.LinkedList<ExtraDamage.DistanceDamagePair>();
            for(var pair:original)adjusted.add(new ExtraDamage.DistanceDamagePair(pair.getDistance(),pair.getDamage()*ratio));
            original.clear();original.addAll(adjusted);}
    }
}
