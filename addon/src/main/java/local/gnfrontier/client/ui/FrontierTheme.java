package local.gnfrontier.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.*;
import local.gnfrontier.Frontier;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** GN-060/GN-061. Actual pack scenery and code-drawn lettering; no generated artwork. */
public final class FrontierTheme {
    public static final int INK=0xffe7ede8, MUTED=0xff9aaca9, ACCENT=0xffd5ad63;
    public static final ResourceLocation BACKGROUND=Frontier.id("textures/gui/frontier_city.png");
    public static boolean fontsReady;
    private static Language tipLanguage;
    private static int tipCount;
    private static final Map<Character,String[]> GLYPHS=new HashMap<>();
    static {
        String[] rows={
            "A:01110/10001/10001/11111/10001/10001/10001","B:11110/10001/10001/11110/10001/10001/11110",
            "C:01111/10000/10000/10000/10000/10000/01111","D:11110/10001/10001/10001/10001/10001/11110",
            "E:11111/10000/10000/11110/10000/10000/11111","F:11111/10000/10000/11110/10000/10000/10000",
            "G:01111/10000/10000/10111/10001/10001/01111","H:10001/10001/10001/11111/10001/10001/10001",
            "I:11111/00100/00100/00100/00100/00100/11111","J:00111/00010/00010/00010/10010/10010/01100",
            "K:10001/10010/10100/11000/10100/10010/10001","L:10000/10000/10000/10000/10000/10000/11111",
            "M:10001/11011/10101/10101/10001/10001/10001","N:10001/11001/11001/10101/10011/10011/10001",
            "O:01110/10001/10001/10001/10001/10001/01110","P:11110/10001/10001/11110/10000/10000/10000",
            "Q:01110/10001/10001/10001/10101/10010/01101","R:11110/10001/10001/11110/10100/10010/10001",
            "S:01111/10000/10000/01110/00001/00001/11110","T:11111/00100/00100/00100/00100/00100/00100",
            "U:10001/10001/10001/10001/10001/10001/01110","V:10001/10001/10001/10001/10001/01010/00100",
            "W:10001/10001/10001/10101/10101/11011/10001","X:10001/10001/01010/00100/01010/10001/10001",
            "Y:10001/10001/01010/00100/00100/00100/00100","Z:11111/00001/00010/00100/01000/10000/11111",
            "0:01110/10001/10011/10101/11001/10001/01110","1:00100/01100/00100/00100/00100/00100/01110",
            "2:01110/10001/00001/00010/00100/01000/11111","3:11110/00001/00001/01110/00001/00001/11110",
            "4:00010/00110/01010/10010/11111/00010/00010","5:11111/10000/10000/11110/00001/00001/11110",
            "6:01110/10000/10000/11110/10001/10001/01110","7:11111/00001/00010/00100/01000/01000/01000",
            "8:01110/10001/10001/01110/10001/10001/01110","9:01110/10001/10001/01111/00001/00001/01110",
            "%:11001/11010/00010/00100/01000/01011/10011","-:00000/00000/00000/11111/00000/00000/00000",
            "/:00001/00010/00010/00100/01000/01000/10000",".:00000/00000/00000/00000/00000/00110/00110"
        };
        for(String row:rows)GLYPHS.put(row.charAt(0),row.substring(2).split("/"));
        // Slash needs explicit rows because it is also the row separator above.
        GLYPHS.put('/',new String[]{"00001","00010","00010","00100","01000","01000","10000"});
    }
    private FrontierTheme() {}
    public static int pixelWidth(String s,int unit){return Math.max(0,s.length()*6-1)*unit;}
    public static void pixelText(GuiGraphics g,String text,int x,int y,int unit,int colour) {
        for(char c:text.toUpperCase(Locale.ROOT).toCharArray()) {
            String[] rows=GLYPHS.get(c);
            if(rows!=null)for(int r=0;r<7;r++)for(int col=0;col<5;col++)if(rows[r].charAt(col)=='1')
                g.fill(x+col*unit,y+r*unit,x+(col+1)*unit,y+(r+1)*unit,colour);
            x+=unit*6;
        }
    }
    public static void background(GuiGraphics g,int w,int h,boolean loading) {
        g.fill(0,0,w,h,0xff101b1d);
        var mc=Minecraft.getInstance();
        if(mc.getResourceManager().getResource(BACKGROUND).isPresent()) {
            float scale=Math.max(w/1600f,h/900f);
            int dw=(int)Math.ceil(1600*scale),dh=(int)Math.ceil(900*scale);
            g.blit(BACKGROUND,(w-dw)/2,(h-dh)/2,dw,dh,0f,0f,1600,900,1600,900);
        }
        g.fill(0,0,w,h,loading?0xce081315:0x47081114);
        if(!loading)for(int x=0;x<w;x+=8) {
            float strength=Mth.clamp(1f-x/(w*.8f),0,1);
            g.fill(x,0,Math.min(w,x+8),h,((int)(224*strength)<<24)|0x071316);
        }
        g.fillGradient(0,0,w,Math.min(h,90),0x99061013,0x00061013);
        g.fillGradient(0,Math.max(0,h-100),w,h,0x00061013,0xdd061013);
        int margin=Math.max(12,w/32);
        g.fill(margin,20,w-margin,21,0x304f7476);
        g.fill(margin,20,margin+34,21,ACCENT);
    }
    public static void paragraph(GuiGraphics g,Component text,int x,int y,int width,int colour,int maxLines) {
        var font=Minecraft.getInstance().font;int line=0;
        for(var part:font.split(text,width)) {if(line>=maxLines)break;g.drawString(font,part,x,y+line*12,colour,false);line++;}
    }
    private static Component loadingTip(long millis) {
        Language language=Language.getInstance();
        if(language!=tipLanguage) {
            tipCount=0;
            while(language.has("gui.gnfrontier.loading.tip."+tipCount))tipCount++;
            tipLanguage=language;
        }
        if(tipCount==0)return Component.empty();
        int tip=(int)Math.floorMod(millis/10000,tipCount);
        return Component.translatable("gui.gnfrontier.loading.tip."+tip);
    }
    public static void loading(GuiGraphics g,int w,int h,Component stage,float progress,boolean showTips) {
        background(g,w,h,true);
        int unit=w<420?2:3,markY=Math.max(42,h/2-62);
        pixelText(g,"GN FRONTIER",(w-pixelWidth("GN FRONTIER",unit))/2,markY,unit,INK);
        int barWidth=Math.min(380,w-64),x=(w-barWidth)/2,y=h/2+20;
        if(fontsReady) {
            g.drawCenteredString(Minecraft.getInstance().font,stage,w/2,y-24,MUTED);
        } else pixelText(g,"PREPARING RESOURCES",(w-pixelWidth("PREPARING RESOURCES",1))/2,y-22,1,MUTED);
        g.fill(x,y,x+barWidth,y+3,0xff283c3f);
        if(progress>=0) {
            int percent=Mth.clamp((int)(progress*100),0,100);
            g.fill(x,y,x+Math.round(barWidth*Mth.clamp(progress,0,1)),y+3,ACCENT);
            String label=percent+"%";pixelText(g,label,(w-pixelWidth(label,1))/2,y+16,1,INK);
        } else {
            int length=Math.max(16,barWidth/7),range=barWidth-length;
            int start=(int)((Math.sin(Util.getMillis()/850.0)+1)*.5*range);
            g.fill(x+start,y,x+start+length,y+3,ACCENT);
        }
        if(fontsReady&&showTips&&h>=240) {
            paragraph(g,loadingTip(Util.getMillis()),x,h-56,barWidth,MUTED,3);
        }
    }
    public static void reload(GuiGraphics g,Overlay overlay,ReloadInstance reload) {
        Minecraft mc=Minecraft.getInstance();if(mc.getOverlay()!=overlay)return;
        int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        g.flush();
        // Forge's early-display overlay leaves a framebuffer-space projection installed.
        Matrix4f projection=new Matrix4f(RenderSystem.getProjectionMatrix());
        var model=RenderSystem.getModelViewStack();model.pushPose();model.setIdentity();RenderSystem.applyModelViewMatrix();
        g.pose().pushPose();g.pose().setIdentity();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,w,h,0,-1000,1000),VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.disableDepthTest();RenderSystem.setShaderColor(1,1,1,1);
        try {loading(g,w,h,Component.translatable("gui.gnfrontier.loading.resources"),reload.getActualProgress(),false);g.flush();}
        finally {g.pose().popPose();model.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,VertexSorting.ORTHOGRAPHIC_Z);RenderSystem.setShaderColor(1,1,1,1);}
    }
}
