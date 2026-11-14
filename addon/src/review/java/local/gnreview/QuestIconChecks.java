package local.gnreview;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import local.gnfrontier.client.PdaScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;

/** Checks real item models and renders a hover frame without any OS pointer input. */
public final class QuestIconChecks {
    private static Object field(Object target,String key)throws Exception{Field f=(target instanceof Class<?> c?c:target.getClass()).getDeclaredField(key);f.setAccessible(true);return f.get(target instanceof Class<?>?null:target);}
    public static Map<String,Object> client(String action,Path evidence)throws Exception{
        Minecraft mc=Minecraft.getInstance();
        if(mc.getSingleplayerServer()==null||!mc.getSingleplayerServer().getWorldData().getLevelName().equals("GN_Review_r31_deliveries")||!PdaScreen.isNative(mc.screen)||!PdaScreen.tab(mc.screen).equals("TASKS"))throw new IllegalStateException("Owned PDA quest page required");
        var snapshot=(CompoundTag)field(PdaScreen.class,"snapshot");Method make=PdaScreen.class.getDeclaredMethod("questItem",CompoundTag.class);make.setAccessible(true);
        List<String> ids=new ArrayList<>();boolean gun=false;
        for(String group:List.of("tasks","specialQuests"))for(Tag task:snapshot.getList(group,Tag.TAG_COMPOUND))for(Tag value:((CompoundTag)task).getList("items",Tag.TAG_COMPOUND)){
            var r=(CompoundTag)value;var stack=(ItemStack)make.invoke(null,r);
            if(stack.isEmpty())throw new AssertionError("Empty icon: "+r);
            if(!r.getString("gunId").isEmpty()){
                if(!stack.hasTag()||!r.getString("gunId").equals(stack.getTag().getString("GunId")))throw new AssertionError("Wrong gun icon");gun=true;
            }
            if(mc.getItemRenderer().getModel(stack,mc.level,mc.player,0)==mc.getModelManager().getMissingModel())throw new AssertionError("Missing model: "+r);
            ids.add(r.getString("item")+"|"+r.getString("gunId"));
        }
        Map<String,Object> out=new LinkedHashMap<>();out.put("status","PASS");out.put("icons",ids);out.put("gunIdPreserved",gun);out.put("special",field(PdaScreen.class,"special"));
        var language=net.minecraft.locale.Language.getInstance();
        for(String key:List.of("hamster.gun.coltm1892.name","hamster.gun.coltm1892.desc","hamster.gun.colt1873.name","hamster.gun.colt1873.desc","hamster.ammo.compact_ammo.name","tooltip.gnfrontier.jam_chance"))
            if(!language.getOrDefault(key).matches("(?s).*[ぁ-んァ-ヶ一-龥].*"))throw new AssertionError("Missing Japanese quest tooltip: "+key);
        Method label=Arrays.stream(net.minecraft.client.gui.Font.class.getDeclaredMethods()).filter(m->m.getName().contains("gnfrontier$jamLabel")).findFirst().orElseThrow();label.setAccessible(true);
        var original=net.minecraft.network.chat.Component.literal("Jam Chance: ").append(net.minecraft.network.chat.Component.literal("12.34%").withStyle(net.minecraft.ChatFormatting.AQUA));
        var translated=(net.minecraft.network.chat.Component)label.invoke(mc.font,original);
        if(!translated.getString().equals("作動不良率：12.34%")||!translated.getSiblings().equals(original.getSiblings()))throw new AssertionError("Jam label lost value or style");
        var unrelated=net.minecraft.network.chat.Component.literal("unchanged");if(label.invoke(mc.font,unrelated)!=unrelated)throw new AssertionError("Unrelated label rewritten");
        out.put("japaneseKeys",6);out.put("jamValueAndStylePreserved",true);
        if(action.equals("hover")){
            if((Boolean)field(PdaScreen.class,"special")||(Integer)field(PdaScreen.class,"textScroll")!=0)throw new IllegalStateException("Unscrolled daily page required");
            int step=Math.max(8,Math.round(11*((Number)field(PdaScreen.class,"textScale")).floatValue()));
            int x=(Integer)field(PdaScreen.class,"left")+3,y=(Integer)field(PdaScreen.class,"top")+4*step+3;
            Path output=evidence.resolve(gun?"final/gun-tooltip.png":"final/material-tooltip.png");if(Files.exists(output))throw new IllegalStateException("Fresh evidence image required");
            mc.getMainRenderTarget().bindWrite(true);
            var pose=com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();pose.pushPose();
            com.mojang.blaze3d.systems.RenderSystem.backupProjectionMatrix();
            try{
                com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0,1000,21000),com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
                pose.setIdentity();pose.translate(0,0,-11000);com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
                com.mojang.blaze3d.systems.RenderSystem.clear(256,Minecraft.ON_OSX);
                GuiGraphics graphics=new GuiGraphics(mc,mc.renderBuffers().bufferSource());mc.screen.render(graphics,x,y,0);graphics.flush();
                try(var picture=Screenshot.takeScreenshot(mc.getMainRenderTarget())){picture.writeToFile(output);}
            }finally{pose.popPose();com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();com.mojang.blaze3d.systems.RenderSystem.restoreProjectionMatrix();}
            out.put("image",output.toString());out.put("screenRenderWithVirtualHover",true);out.put("osPointerMoved",false);
        }else if(!action.equals("state"))throw new IllegalArgumentException(action);
        return out;
    }
}
