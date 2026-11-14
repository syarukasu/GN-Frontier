package local.gnreview;

import java.lang.reflect.Method;
import java.util.*;
import local.gnfrontier.client.ui.FrontierTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;

/** Only loaded by the private review driver; never included in the production MOD. */
public final class LoadingTopicChecks {
    private static Component tip(long time)throws Exception {
        Method method=FrontierTheme.class.getDeclaredMethod("loadingTip",long.class);
        method.setAccessible(true);return (Component)method.invoke(null,time);
    }
    public static Map<String,Object> client(String action,int index)throws Exception {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level!=null||mc.getOverlay()!=null||!mc.gameDirectory.toPath().toRealPath().toString().contains("loading-topics-r27"))
            throw new IllegalStateException("Owned pre-world review client required");
        FrontierTheme.fontsReady=true;
        if(action.equals("preview")) {
            if(index<0||index>=48)throw new IllegalArgumentException("Tip index");
            mc.setScreen(new Preview(tip(index*10000L)));
            return Map.of("index",index,"text",tip(index*10000L).getString(),"fixture",true);
        }
        if(action.equals("native")) {
            mc.setScreen(new GenericDirtMessageScreen(Component.translatable("gui.gnfrontier.loading.world")));
            return Map.of("nativeScreen",mc.screen.getClass().getName());
        }
        if(!action.equals("verify"))throw new IllegalArgumentException(action);
        Set<String> unique=new HashSet<>();List<Map<String,Object>> rows=new ArrayList<>();
        for(int i=0;i<48;i++) {
            Component text=tip(i*10000L);String expected=Component.translatable("gui.gnfrontier.loading.tip."+i).getString();
            if(!text.getString().equals(expected)||!unique.add(expected))throw new AssertionError("Missing or repeated topic "+i);
            if(!tip(i*10000L+9999).getString().equals(expected))throw new AssertionError("Topic changes before ten seconds");
            int widest=0;
            for(int width:new int[]{380,336,256}) {
                int lines=mc.font.split(text,width).size();widest=Math.max(widest,lines);
                if(lines>3)throw new AssertionError("Clipped topic "+i+" at width "+width+": "+lines);
            }
            rows.add(Map.of("index",i,"text",expected,"maxLines",widest));
        }
        if(!tip(480000L).getString().equals(tip(0).getString()))throw new AssertionError("Cycle boundary");
        return Map.of("status","PASS","topics",48,"uniqueTopics",unique.size(),"intervalMillis",10000,
                "fontWidths",List.of(380,336,256),"maxLines",3,"rows",rows,"worldLoaded",false);
    }
    private static final class Preview extends Screen {
        private final Component text;
        Preview(Component text){super(Component.literal("Loading topic review"));this.text=text;}
        @Override public void render(GuiGraphics g,int mx,int my,float delta){
            FrontierTheme.loading(g,width,height,Component.translatable("gui.gnfrontier.loading.world"),.42f,false);
            int bar=Math.min(380,width-64);
            FrontierTheme.paragraph(g,text,(width-bar)/2,height-56,bar,FrontierTheme.MUTED,3);
        }
    }
}
