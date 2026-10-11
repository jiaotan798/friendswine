package com.friendswine.client;

import com.friendswine.*;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SettingsScreen extends Screen {
    private final Screen parent;
    private final EditBox[] fields=new EditBox[10];
    private final String[] keys={"compressionPercent","widthPercent","rotationSpeed","orbitSpeed","kasumiMinScale","kasumiMaxScale","emmaMinScale","emmaMaxScale","amiyaMinScale","amiyaMaxScale"};
    private Button save, pageButton;
    private boolean twoColumns;
    private int page;
    private Component error=Component.empty();

    public SettingsScreen(Screen parent) { super(Component.translatable("friendswine.configuration.title")); this.parent=parent; }
    @Override protected void init() {
        twoColumns=width>=620 && height>=340;
        String[] values={ClientConfig.COMPRESSION.get().toString(),ClientConfig.WIDTH.get().toString(),ClientConfig.ROTATION_SPEED.get().toString(),Double.toString(SettingsNetworking.clientOrbit),
                Double.toString(ClientConfig.creatureMin(false)),Double.toString(ClientConfig.creatureMax(false)),Double.toString(ClientConfig.creatureMin(true)),Double.toString(ClientConfig.creatureMax(true)),Double.toString(ClientConfig.creatureMin(GuestVariant.AMIYA)),Double.toString(ClientConfig.creatureMax(GuestVariant.AMIYA))};
        for (int i=0;i<fields.length;i++) {
            EditBox box=new EditBox(font,fieldX(i),58+row(i)*32,85,20,Component.translatable("friendswine.config."+keys[i]));
            box.setMaxLength(12); box.setValue(values[i]);
            box.setTooltip(Tooltip.create(Component.translatable("friendswine.config."+keys[i]).append("\n").append(Component.translatable("friendswine.config."+keys[i]+".tooltip"))));
            fields[i]=addRenderableWidget(box);
        }
        fields[3].active=SettingsNetworking.canEdit && minecraft.getConnection()!=null;
        if (!twoColumns) pageButton=addRenderableWidget(Button.builder(pageTitle(),button->{page=(page+1)%3; updateVisibility(); button.setMessage(pageTitle());}).bounds(width/2-110,30,220,20).build());
        updateVisibility();
        save=addRenderableWidget(Button.builder(Component.translatable("gui.done"),button->save()).bounds(width/2-105,height-30,100,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),button->onClose()).bounds(width/2+5,height-30,100,20).build());
    }
    private Component pageTitle() { return Component.translatable(page==0 ? "friendswine.settings.creaturePage" : page==1 ? "friendswine.settings.amiyaPage" : "friendswine.settings.displayPage"); }
    private int left(int i) { return twoColumns ? width/2-300+(i<4 ? 0 : 300) : width/2-Math.min(150,(width-20)/2); }
    // 窄窗口按四项分页；宽窗口的角色列有六项，避免阿米娅字段越界或覆盖按钮。
    private int row(int i) { return twoColumns && i>=4 ? i-4 : i%4; }
    private int fieldX(int i) { return left(i)+(twoColumns ? 210 : Math.min(300,width-20)-90); }
    private void updateVisibility() {
        for (int i=0;i<fields.length;i++) fields[i].visible=twoColumns || i/4==page;
    }
    private Component validationError() {
        try {
            int compression=Integer.parseInt(fields[0].getValue()), width=Integer.parseInt(fields[1].getValue());
            double rotation=Double.parseDouble(fields[2].getValue()), orbit=Double.parseDouble(fields[3].getValue());
            if (compression<0 || compression>90 || width<0 || width>200 || !Double.isFinite(rotation) || rotation<0 || rotation>4 || !Double.isFinite(orbit) || orbit<0 || orbit>4)
                return Component.translatable("friendswine.settings.invalidRange");
            double kasumiMin=Double.parseDouble(fields[4].getValue()),kasumiMax=Double.parseDouble(fields[5].getValue()),emmaMin=Double.parseDouble(fields[6].getValue()),emmaMax=Double.parseDouble(fields[7].getValue());
            if (!ClientConfig.validCreatureScales(kasumiMin,kasumiMax,emmaMin,emmaMax,Double.parseDouble(fields[8].getValue()),Double.parseDouble(fields[9].getValue()))) return Component.translatable("friendswine.settings.invalidCreatureRange");
            return Component.empty();
        } catch (NumberFormatException invalid) { return Component.translatable("friendswine.settings.invalidNumber"); }
    }
    private boolean valid() { return validationError().getString().isEmpty(); }
    private void save() {
        if (!valid()) return;
        try {
            ClientConfig.save(Integer.parseInt(fields[0].getValue()),Integer.parseInt(fields[1].getValue()),Double.parseDouble(fields[2].getValue()),
                    Double.parseDouble(fields[4].getValue()),Double.parseDouble(fields[5].getValue()),Double.parseDouble(fields[6].getValue()),Double.parseDouble(fields[7].getValue()),Double.parseDouble(fields[8].getValue()),Double.parseDouble(fields[9].getValue()));
            if (fields[3].active) SettingsNetworking.change(Double.parseDouble(fields[3].getValue()));
            onClose();
        } catch (RuntimeException failure) { error=Component.translatable("friendswine.settings.saveFailed"); }
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font,title,width/2,12,0xFFFFFF);
        if (twoColumns) {
            graphics.drawCenteredString(font,Component.translatable("friendswine.settings.displayPage"),width/2-150,34,0xAAAAAA);
            graphics.drawCenteredString(font,Component.translatable("friendswine.settings.creaturePage"),width/2+150,34,0xAAAAAA);
        }
        for (int i=0;i<fields.length;i++) if (fields[i].visible) {
            String label=font.plainSubstrByWidth(Component.translatable("friendswine.config."+keys[i]).getString(),Math.max(70,fieldX(i)-left(i)-8));
            graphics.drawString(font,label,left(i),64+row(i)*32,0xFFFFFF);
        }
        graphics.drawCenteredString(font,Component.translatable("friendswine.settings.scaleRange"),width/2,height-65,0xAAAAAA);
        Component invalid=validationError();
        graphics.drawCenteredString(font,error.getString().isEmpty() ? invalid : error,width/2,height-47,0xFF5555);
        save.active=invalid.getString().isEmpty();
        super.render(graphics,mouseX,mouseY,partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
