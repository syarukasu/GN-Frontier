package local.gnfrontier.client.ui;

import local.gnfrontier.mixin.CreateWorldAccess;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Keeps vanilla registry loading, world-folder validation and creation lifecycle. */
public final class FrontierCreateWorldScreen extends Screen {
    private final CreateWorldScreen delegate;
    private final WorldCreationUiState state;
    private EditBox name;
    private int left, top, panelWidth;
    private boolean compact;

    public FrontierCreateWorldScreen(CreateWorldScreen delegate) {
        super(Component.translatable("selectWorld.create"));
        this.delegate=delegate; this.state=delegate.getUiState();
        boolean cheats=state.isAllowCheats();
        if(state.getGameMode()!=WorldCreationUiState.SelectedGameMode.CREATIVE)
            state.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
        state.setAllowCheats(cheats);
        state.setSeed("");
        applyPreset();
    }
    private void applyPreset() {
        var preset=state.getSettings().worldgenLoadContext().registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL);
        state.setWorldType(new WorldCreationUiState.WorldTypeEntry(preset));
        state.setDifficulty(Difficulty.HARD);
        state.setGenerateStructures(true);
        state.setBonusChest(false);
    }
    @Override protected void init() {
        // Initialize the native controller, but expose only the pack's controls.
        delegate.init(minecraft,width,height);
        panelWidth=Math.min(380,width-40); left=(width-panelWidth)/2;
        compact=height<300; top=Math.max(10,(height-(compact?224:244))/2);
        name=new EditBox(font,left,row(49,40),panelWidth,20,Component.translatable("selectWorld.enterName"));
        name.setMaxLength(128); name.setValue(state.getName());
        name.setResponder(state::setName); addRenderableWidget(name);
        addRenderableWidget(new FrontierButton(left,row(81,67),panelWidth,24,modeLabel(),b->{
            boolean cheats=state.isAllowCheats();
            state.setGameMode(state.getGameMode()==WorldCreationUiState.SelectedGameMode.CREATIVE
                ?WorldCreationUiState.SelectedGameMode.SURVIVAL:WorldCreationUiState.SelectedGameMode.CREATIVE);
            state.setAllowCheats(cheats); b.setMessage(modeLabel());
        },false));
        addRenderableWidget(new FrontierButton(left,row(113,95),panelWidth,24,cheatLabel(),b->{
            state.setAllowCheats(!state.isAllowCheats()); b.setMessage(cheatLabel());
        },false));
        int half=(panelWidth-8)/2;
        addRenderableWidget(new FrontierButton(left,row(211,185),half,26,Component.translatable("selectWorld.create"),b->{
            applyPreset(); ((CreateWorldAccess)delegate).gn$create();
        },true));
        addRenderableWidget(new FrontierButton(left+half+8,row(211,185),half,26,Component.translatable("gui.cancel"),b->onClose(),false));
        setInitialFocus(name);
    }
    private int row(int regular,int small){return top+(compact?small:regular);}
    private Component modeLabel() {
        String mode=state.getGameMode()==WorldCreationUiState.SelectedGameMode.CREATIVE?"creative":"survival";
        return Component.translatable("gui.gnfrontier.create.mode",Component.translatable("gameMode."+mode));
    }
    private Component cheatLabel() {
        return Component.translatable("gui.gnfrontier.create.cheats",Component.translatable(state.isAllowCheats()?"options.on":"options.off"));
    }
    @Override public void tick(){name.tick();}
    @Override public void onClose(){delegate.onClose();}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta) {
        FrontierTheme.background(g,width,height,false);
        g.fill(left-14,top-12,left+panelWidth+14,row(249,223),0xdb101b1d);
        g.drawCenteredString(font,title,width/2,top,FrontierTheme.INK);
        g.drawString(font,Component.translatable("selectWorld.enterName"),left,row(34,26),FrontierTheme.MUTED,false);
        g.drawString(font,Component.translatable("gui.gnfrontier.create.difficulty"),left,row(151,127),FrontierTheme.ACCENT,false);
        g.drawString(font,Component.translatable("gui.gnfrontier.create.preset"),left,row(171,146),FrontierTheme.INK,false);
        g.drawString(font,Component.translatable("gui.gnfrontier.create.random"),left,row(188,162),FrontierTheme.MUTED,false);
        super.render(g,mouseX,mouseY,delta);
    }
}
