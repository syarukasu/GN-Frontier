package local.gnfrontier.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import net.minecraft.locale.Language;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pack-authored Japanese wins over general translation and generated mod packs. */
@Mixin(value=ClientLanguage.class,remap=false)
public abstract class JapanesePackPriorityMixin {
    @Shadow @Final @Mutable private Map<String,String> f_118910_;

    @Inject(method="m_264420_",at=@At("RETURN"))
    private static void gnfrontier$finishReviewedJapanese(ResourceManager manager,List<String> languages,boolean bidirectional,
            CallbackInfoReturnable<ClientLanguage> callback){
        if(languages.isEmpty()||!languages.get(languages.size()-1).equals("ja_jp"))return;
        JapanesePackPriorityMixin result=(JapanesePackPriorityMixin)(Object)callback.getReturnValue();
        Map<String,String> merged=new HashMap<>(result.f_118910_);
        // Oculus appends its bundled strings directly after every language file. Apply
        // the pack's Japanese once more after that pass, without changing its JAR.
        for(String namespace:manager.getNamespaces()){
            ResourceLocation id=new ResourceLocation(namespace,"lang/ja_jp.json");
            for(Resource resource:manager.getResourceStack(id)){
                if(!resource.sourcePackId().toLowerCase(Locale.ROOT).contains("kubejs"))continue;
                try(var stream=resource.open()){
                    Language.loadFromJson(stream,merged::put);
                }catch(IOException|RuntimeException error){
                    com.mojang.logging.LogUtils.getLogger().warn("Could not load reviewed Japanese resource {}",id,error);
                }
                break;
            }
        }
        result.f_118910_=Map.copyOf(merged);
    }

    @Redirect(method="m_264420_", at=@At(value="INVOKE", target="Lnet/minecraft/server/packs/resources/ResourceManager;m_213829_(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/List;"))
    private static List<Resource> gnfrontier$preferReviewedJapanese(ResourceManager manager,ResourceLocation id){
        List<Resource> stack=manager.getResourceStack(id);
        if(!id.getPath().equals("lang/ja_jp.json"))return stack;
        int custom=-1;
        for(int i=0;i<stack.size();i++){
            String pack=stack.get(i).sourcePackId();
            if(custom<0&&pack.toLowerCase(Locale.ROOT).contains("kubejs"))custom=i;
        }
        if(custom<0||custom==stack.size()-1)return stack;
        // Keep all other packs in their original order and never reorder textures or other languages.
        List<Resource> ordered=new ArrayList<>(stack);
        Resource reviewed=ordered.remove(custom);
        ordered.add(reviewed);
        return ordered;
    }
}
