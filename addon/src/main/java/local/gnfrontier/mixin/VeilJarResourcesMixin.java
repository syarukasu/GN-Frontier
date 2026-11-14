package local.gnfrontier.mixin;

import foundry.veil.ext.PackResourcesExtension;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.zip.ZipInputStream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraftforge.resource.PathPackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Nested archives expose ZIP entries, not the host directory layout expected by Veil. */
@Pseudo
@Mixin(targets="foundry.veil.impl.resource.VeilResourceManagerImpl",remap=false)
public abstract class VeilJarResourcesMixin {
    @Unique private static boolean gn$reportedNestedResources;

    @Redirect(method="loadPack",at=@At(value="INVOKE",
        target="Lfoundry/veil/ext/PackResourcesExtension;veil$listResources(Lfoundry/veil/ext/PackResourcesExtension$PackResourceConsumer;)V",remap=false),remap=false)
    private void gn$enumerateNestedResources(PackResourcesExtension extension,PackResourcesExtension.PackResourceConsumer consumer) {
        if(extension instanceof PathPackResources pack
            &&pack.getSource().getFileSystem().getClass().getName().equals("net.minecraftforge.jarjar.nio.pathfs.PathFileSystem")) {
            try(ZipInputStream zip=new ZipInputStream(Files.newInputStream(pack.getSource()))) {
                for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) {
                    if(entry.isDirectory())continue;
                    String[] key=entry.getName().split("/",3);
                    if(key.length!=3)continue;
                    PackType type=key[0].equals("assets")?PackType.CLIENT_RESOURCES:key[0].equals("data")?PackType.SERVER_DATA:null;
                    if(type==null)continue;
                    ResourceLocation id=ResourceLocation.tryParse(key[1]+":"+key[2]);
                    if(id!=null)consumer.accept(type,id,null,null,null);
                }
            } catch(IOException ex){throw new UncheckedIOException("Cannot enumerate nested Veil resource pack "+pack.packId(),ex);}
            if(!gn$reportedNestedResources) {
                gn$reportedNestedResources=true;
                org.slf4j.LoggerFactory.getLogger("GNFrontier").info("Veil nested-JAR resource scan completed using validated archive entries");
            }
        } else extension.veil$listResources(consumer);
    }
}
