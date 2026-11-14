package local.gnfrontier.core;

import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;
import cpw.mods.jarhandling.SecureJar;
import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class ShrapnelVerification {
    private static ClassNode node(ZipFile jar,String path)throws Exception{ClassNode n=new ClassNode();new ClassReader(jar.getInputStream(jar.getEntry(path))).accept(n,0);return n;}
    public static void main(String[] args)throws Exception {
        Path source=Path.of(args[0]),patched=Path.of(args[1]),sbw=Path.of(args[2]);
        try(var original=new ZipFile(source.toFile());var jar=new JarFile(patched.toFile());var nativeJar=new ZipFile(sbw.toFile())) {
            Set<String> expected=Set.of("META-INF/MANIFEST.MF","META-INF/mods.toml","com/cyberbutcher/shrapnelexplosionsmod/mixin/SBWEntitiesExplosionMixin.class","com/cyberbutcher/shrapnelexplosionsmod/mixin/SBWCancelVanillaExplosionMixin.class");
            Set<String> changed=new HashSet<>();if(original.size()!=jar.size())throw new AssertionError("Changed entry count");
            for(var e:original.stream().toList())if(!Arrays.equals(original.getInputStream(e).readAllBytes(),jar.getInputStream(jar.getEntry(e.getName())).readAllBytes()))changed.add(e.getName());
            if(!changed.equals(expected))throw new AssertionError("Unreviewed patch changes: "+changed);
            if(!"2.0.7.3-gncompat.1".equals(jar.getManifest().getMainAttributes().getValue("Implementation-Version")))throw new AssertionError("Manifest version");
            String metadata=new String(jar.getInputStream(jar.getEntry("META-INF/mods.toml")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            if(!metadata.contains("version=\"2.0.7.3-gncompat.1\""))throw new AssertionError("Forge metadata version");
            var config=JsonParser.parseString(new String(jar.getInputStream(jar.getEntry("shrapnelexplosionsmod.mixins.json")).readAllBytes())).getAsJsonObject();
            if(config.getAsJsonArray("mixins").size()!=6)throw new AssertionError("An original mixin was disabled");
            ClassNode mixin=node(jar,"com/cyberbutcher/shrapnelexplosionsmod/mixin/SBWEntitiesExplosionMixin.class"),builder=node(nativeJar,"com/atsuishio/superbwarfare/tools/CustomExplosion$Builder.class");
            int unique=0,shadow=0;
            for(FieldNode f:mixin.fields)for(AnnotationNode a:f.visibleAnnotations==null?List.<AnnotationNode>of():f.visibleAnnotations) {
                if(a.desc.endsWith("/Unique;")&&f.name.equals("damageMultiplier")&&f.desc.equals("F")&&f.value==null)unique++;
                if(a.desc.endsWith("/Shadow;")){shadow++;if(builder.fields.stream().noneMatch(x->x.name.equals(f.name)&&x.desc.equals(f.desc)))throw new AssertionError("Unresolved shadow: "+f.name);}
            }
            if(unique!=1||shadow!=8)throw new AssertionError("Unexpected shadow declarations");
            ClassNode redirect=node(jar,"com/cyberbutcher/shrapnelexplosionsmod/mixin/SBWCancelVanillaExplosionMixin.class"),projectile=node(nativeJar,"com/atsuishio/superbwarfare/tools/ProjectileTool.class");int targets=0;
            for(MethodNode method:redirect.methods)for(AnnotationNode a:method.visibleAnnotations==null?List.<AnnotationNode>of():method.visibleAnnotations)if(a.desc.endsWith("/Redirect;"))for(int i=0;i<a.values.size();i+=2)if(a.values.get(i).equals("method"))for(Object value:(List<?>)a.values.get(i+1)) {
                String target=value.toString();if(projectile.methods.stream().noneMatch(m->(m.name+m.desc).equals(target)))throw new AssertionError("Unresolved redirect: "+target);targets++;
            }
            if(targets!=1)throw new AssertionError("Redirect count");
        }
        SecureJar secure=SecureJar.from(patched);if(secure.name().isBlank()||secure.getPackages().isEmpty())throw new AssertionError("SecureJar failed");
        System.out.println("GN_SHRAPNEL_BINARY_PASS changedEntries=4 retainedMixins=6 resolvedShadows=8 resolvedRedirects=1 secureJar="+secure.name());
    }
}
