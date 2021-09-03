package codechicken.chunkloader.gui;

import codechicken.chunkloader.network.ChunkLoaderCPH;
import codechicken.chunkloader.tile.TileChunkLoader;
import codechicken.lib.texture.TextureUtils;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import java.util.stream.Collectors;



public class GuiChunkLoader extends Screen {

    public Button laserButton;
    public Button activeButton;

    public TileChunkLoader tile;

    private int lastButton;


    public GuiChunkLoader(TileChunkLoader tile) {
        super(new StringTextComponent("DOOOOOOOT"));
        this.tile = tile;
    }

    @Override
    public void init() {
        addButton(new Button(width / 2 - 20, height / 2 - 60, 20, 20, new StringTextComponent("+"), e -> ChunkLoaderCPH.sendShapeChange(tile, tile.shape, tile.radius + 1)));
        addButton(new Button(width / 2 - 80, height / 2 - 60, 20, 20, new StringTextComponent("-"), e -> ChunkLoaderCPH.sendShapeChange(tile, tile.shape, tile.radius - 1)));
        addButton(laserButton = new Button(width / 2 + 7, height / 2 - 60, 75, 20, new StringTextComponent("-"), e -> tile.renderInfo.showLasers = !tile.renderInfo.showLasers));
        addButton(activeButton = new Button(width / 2 + 7, height / 2 - 37, 75, 20, new StringTextComponent("-"), e -> tile.renderInfo.activeReceive = !tile.renderInfo.activeReceive));
        updateNames();

        super.init();
        /*addButton(shapeButton = new Button(width / 2 + 7, height / 2 - 37, 75, 20, new StringTextComponent("-"), e -> ChunkLoaderCPH.sendShapeChange(tile, lastButton == 1 ? tile.shape.prev() : tile.shape.next(), tile.radius)));*/
    }

    public void updateNames() {
        laserButton.setMessage(new TranslationTextComponent(String.valueOf(tile.active)));
        activeButton.setMessage(new TranslationTextComponent("Bekish"));

//        laserButton.setMessage(new TranslationTextComponent(tile.renderInfo.showLasers ? "chickenchunks.gui.hidelasers" : "chickenchunks.gui.showlasers"));
//        activeButton.setMessage(new TranslationTextComponent(String.valueOf(tile.getChunks().stream().map(ChunkPos::toString).reduce((s1, s2) -> s1 + ":" + s2))));
//        shapeButton.setMessage(tile.shape.getTranslation());
    }

    @Override
    public void tick() {
        if (minecraft.level.getBlockEntity(tile.getBlockPos()) != tile)//tile changed
        {
            minecraft.screen = null;
            minecraft.mouseHandler.grabMouse();
        }

        updateNames();
        super.tick();
    }

    @Override
    public void render(MatrixStack mStack, int p_render_1_, int p_render_2_, float p_render_3_) {
        renderBackground(mStack);
        GlStateManager._color4f(1F, 1F, 1F, 1F);
        TextureUtils.changeTexture("chickenchunks:textures/gui/gui_small.png");
        int posx = width / 2 - 88;
        int posy = height / 2 - 83;
        blit(mStack, posx, posy, 0, 0, 176, 166);

        super.render(mStack, p_render_1_, p_render_2_, p_render_3_);//buttons

        GlStateManager._disableLighting();
        GlStateManager._disableDepthTest();

        if (tile.owner != null) {
            drawCentered(mStack, tile.ownerName, width / 2 + 44, height / 2 - 72, 0x801080);
        }
        drawCentered(mStack, new TranslationTextComponent("chickenchunks.gui.radius"), width / 2 - 40, height / 2 - 72, 0x404040);
        drawCentered(mStack, new StringTextComponent("" + tile.radius), width / 2 - 40, height / 2 - 54, 0xFFFFFF);


        int chunks = tile.countLoadedChunks();
        drawCentered(mStack, new TranslationTextComponent(chunks == 1 ? "chickenchunks.gui.chunk" : "chickenchunks.gui.chunks", chunks), width / 2 - 39, height / 2 - 39, 0x108000);


        GlStateManager._enableLighting();
        GlStateManager._enableDepthTest();
    }

    private void drawCentered(MatrixStack mStack, ITextComponent s, int x, int y, int colour) {
        font.draw(mStack, s.getVisualOrderText(), x - font.width(s) / 2f, y, colour);
    }


    @Override
    public boolean mouseClicked(double x, double y, int button) {
        lastButton = button;
//        if (button == 1) {
//            button = 0;
//        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
