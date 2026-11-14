package local.gnfrontier.core;

import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.lang.reflect.Method;
import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class GameplayDataVerification {
    private static Object value(AnnotationNode a,String key){if(a.values==null)return null;for(int i=0;i<a.values.size();i+=2)if(a.values.get(i).equals(key))return a.values.get(i+1);return null;}
    private static ClassNode read(byte[] b){ClassNode c=new ClassNode();new ClassReader(b).accept(c,ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);return c;}
    public static void main(String[] args)throws Exception {
        Path jar=Path.of(args[0]),mods=Path.of(args[1]);List<Path> upstream=new ArrayList<>();
        try(var stream=Files.list(mods)){stream.filter(p->p.toString().endsWith(".jar")).forEach(upstream::add);}upstream.add(Path.of(args[2]));
        // Validate new optional targets against staged upstream JARs before deployment.
        for(int i=6;i<args.length;i++)upstream.add(Path.of(args[i]));
        Map<String,byte[]> targets=new HashMap<>();List<ClassNode> mixins=new ArrayList<>();
        try(ZipFile z=new ZipFile(jar.toFile())) {
            for(var entries=z.entries();entries.hasMoreElements();) {
                var e=entries.nextElement();if(!e.getName().contains("/mixin/")||!e.getName().endsWith(".class"))continue;
                ClassNode c=read(z.getInputStream(e).readAllBytes());mixins.add(c);
                for(var ann:c.invisibleAnnotations)if(ann.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
                    Object names=value(ann,"targets"),types=value(ann,"value");
                    if(names instanceof List<?> list)for(Object name:list)targets.put(name.toString().replace('.','/')+".class",null);
                    if(types instanceof List<?> list)for(Object type:list)targets.put(((Type)type).getInternalName()+".class",null);
                }
            }
        }
        for(Path path:upstream)try(ZipFile z=new ZipFile(path.toFile())){for(String name:targets.keySet()){var e=z.getEntry(name);if(e!=null)targets.put(name,z.getInputStream(e).readAllBytes());}}
        int injections=0;
        for(ClassNode mixin:mixins) {
            List<String> targetNames=new ArrayList<>();
            for(var ann:mixin.invisibleAnnotations)if(ann.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
                Object names=value(ann,"targets"),types=value(ann,"value");
                if(names instanceof List<?> list)for(Object name:list)targetNames.add(name.toString().replace('.','/')+".class");
                if(types instanceof List<?> list)for(Object type:list)targetNames.add(((Type)type).getInternalName()+".class");
            }
            if(targetNames.isEmpty())throw new AssertionError("Mixin without target "+mixin.name);
            for(String target:targetNames){
            if(targets.get(target)==null)throw new AssertionError("Missing mixin target "+target);
            ClassNode runtime=read(targets.get(target));
            for(MethodNode method:mixin.methods)if(method.visibleAnnotations!=null)for(var ann:method.visibleAnnotations)if(ann.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) {
                for(Object name:(List<?>)value(ann,"method")) {
                    String selector=name.toString();List<MethodNode> found=runtime.methods.stream().filter(m->selector.contains("(")?selector.equals(m.name+m.desc):selector.equals(m.name)).toList();
                    if(found.size()!=1)throw new AssertionError(mixin.name+" -> "+target+" ambiguous/missing "+selector);
                    if((found.get(0).access&Opcodes.ACC_STATIC)!=(method.access&Opcodes.ACC_STATIC))throw new AssertionError("Static mismatch "+selector);
                    injections++;
                }
            }
            }
        }
        if(injections<9)throw new AssertionError("Expected all runtime injection points, found "+injections);
        System.out.println("GN_MIXIN_TARGETS_PASS injections="+injections+" bytecodeTransformation=false");
        Class<?> contextClass=Class.forName("dev.latvian.mods.rhino.Context"),scriptable=Class.forName("dev.latvian.mods.rhino.Scriptable");
        Object context=contextClass.getMethod("enter").invoke(null),scope=contextClass.getMethod("initStandardObjects").invoke(context);
        Method eval=contextClass.getMethod("evaluateString",scriptable,String.class,String.class,int.class,Object.class);
        eval.invoke(context,scope,"var added=[],removed=[];var ServerEvents={recipes:function(cb){cb({remove:function(f){removed.push(f);},custom:function(r){return{id:function(id){added.push({id:id,recipe:r});}};}});}};","harness",1,null);
        eval.invoke(context,scope,Files.readString(Path.of(args[3])),"r3-progression",1,null);
        JsonObject out=JsonParser.parseString(eval.invoke(context,scope,"JSON.stringify({added:added,removed:removed})","out",1,null).toString()).getAsJsonObject();
        JsonObject manifest=JsonParser.parseString(Files.readString(Path.of(args[4]))).getAsJsonObject();
        if(out.getAsJsonArray("added").size()!=manifest.getAsJsonObject("recipes").size() || out.getAsJsonArray("removed").size()!=manifest.getAsJsonArray("removals").size())throw new AssertionError("Progression script operations lost");
        Set<String> unique=new HashSet<>();for(var element:out.getAsJsonArray("added")) {
            var recipe=element.getAsJsonObject();String id=recipe.get("id").getAsString();if(!unique.add(id)||!recipe.get("recipe").equals(manifest.getAsJsonObject("recipes").get(id)))throw new AssertionError("Recipe mismatch "+id);
        }
        Files.writeString((args.length>5?Path.of(args[5]):Path.of(args[4]).getParent()).resolve("rhino-progression.json"),out.toString());
        System.out.println("GN_R3_RHINO_PASS recipes="+unique.size()+" removes="+out.getAsJsonArray("removed").size()+" gameRegistry=false");
        for(var element:manifest.getAsJsonArray("car_templates")) {
            String id=element.getAsJsonObject().get("id").getAsString();Path source=Path.of("src/main/resources/data/gnfrontier/structures/"+id.substring(id.indexOf(':')+1)+".nbt");
            var nbt=net.minecraft.nbt.NbtIo.readCompressed(source.toFile());
            if(nbt.getList("blocks",10).size()!=3 || nbt.getList("palette",10).size()!=3)throw new AssertionError("A complete vehicle needs front, rear and connector: "+id);
            var size=nbt.getList("size",3);for(var b:nbt.getList("blocks",10)) {
                var pos=((net.minecraft.nbt.CompoundTag)b).getList("pos",3);for(int i=0;i<3;i++)if(pos.getInt(i)<0 || pos.getInt(i)>=size.getInt(i))throw new AssertionError("Template bounds "+id);
            }
        }
        System.out.println("GN_CAR_TEMPLATE_NBT_PASS templates="+manifest.getAsJsonArray("car_templates").size()+" worldPlaced=false");
    }
}
