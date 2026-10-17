package com.friendswine.client;

import com.friendswine.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** 显示参数与服务端参数分开分页；界面权限只作提示，服务端仍须再次校验。 */
public final class SettingsScreen extends Screen {
    private final Screen parent;
    private final EditBox[] fields=new EditBox[10];
    private final String[] keys={"compressionPercent","widthPercent","rotationSpeed","orbitSpeed","kasumiMinScale","kasumiMaxScale","emmaMinScale","emmaMaxScale","amiyaMinScale","amiyaMaxScale"};
    private Button save, pageButton, scopeButton;
    private DeathFlashScope scope;
    private boolean twoColumns;
    private int page;
    private Component error=Component.empty();

    public SettingsScreen(Screen parent) { super(Component.translatable("friendswine.configuration.title")); this.parent=parent; }
    @Override protected void init() {
        twoColumns=false;
        scope=SettingsNetworking.clientScope;
        String[] values={ClientConfig.COMPRESSION.get().toString(),ClientConfig.WIDTH.get().toString(),ClientConfig.ROTATION_SPEED.get().toString(),Double.toString(SettingsNetworking.clientOrbit),
                Double.toString(ClientConfig.creatureMin(false)),Double.toString(ClientConfig.creatureMax(false)),Double.toString(ClientConfig.creatureMin(true)),Double.toString(ClientConfig.creatureMax(true)),Double.toString(ClientConfig.creatureMin(GuestVariant.AMIYA)),Double.toString(ClientConfig.creatureMax(GuestVariant.AMIYA))};
        for (int i=0;i<fields.length;i++) {
            EditBox box=new EditBox(font,fieldX(i),58+row(i)*32,85,20,Component.translatable("friendswine.config."+keys[i]));
            box.setMaxLength(12); box.setValue(values[i]);
            box.setTooltip(Tooltip.create(Component.translatable("friendswine.config."+keys[i]).append("\n").append(Component.translatable("friendswine.config."+keys[i]+".tooltip"))));
            fields[i]=addRenderableWidget(box);
        }
        fields[3].active=SettingsNetworking.canEdit && minecraft.getConnection()!=null;
        scopeButton=addRenderableWidget(Button.builder(scopeText(),button->{
            scope=scope==DeathFlashScope.NEARBY ? DeathFlashScope.GLOBAL : DeathFlashScope.NEARBY;
            button.setMessage(scopeText());
        }).bounds(fieldX(3),90,Math.min(160,width-fieldX(3)-10),20).build());
        scopeButton.setTooltip(Tooltip.create(Component.translatable("friendswine.config.deathFlashScope.tooltip")));
        scopeButton.active=fields[3].active;
        pageButton=addRenderableWidget(Button.builder(pageTitle(),button->{page=(page+1)%4; updateVisibility(); button.setMessage(pageTitle());}).bounds(width/2-110,30,220,20).build());
        updateVisibility();
        save=addRenderableWidget(Button.builder(Component.translatable("gui.done"),button->save()).bounds(width/2-105,height-30,100,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),button->onClose()).bounds(width/2+5,height-30,100,20).build());
    }
    private Component pageTitle() { return Component.translatable(switch(page) {
        case 0 -> "friendswine.settings.creaturePage"; case 1 -> "friendswine.settings.amiyaPage";
        case 2 -> "friendswine.settings.serverPage"; default -> "friendswine.settings.displayPage";
    }); }
    private Component scopeText() { return Component.translatable("friendswine.config.deathFlashScope."+scope.name()); }
    private int left(int i) { return width/2-Math.min(200,(width-20)/2); }
    private int row(int i) { return i==3 ? 0 : i>=8 ? i-8 : i>=4 ? i-4 : i; }
    private int fieldX(int i) { return width/2+Math.min(20,width/8); }
    private void updateVisibility() {
        for(int i=0;i<fields.length;i++) fields[i].visible=switch(page) {
            case 0 -> i>=4 && i<8; case 1 -> i>=8; case 2 -> i==3; default -> i<3;
        };
        scopeButton.visible=page==2;
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
            if (fields[3].active) ClientPlayNetworking.send(new SettingsNetworking.Change(Double.parseDouble(fields[3].getValue()),scope.name()));
            onClose();
        } catch (RuntimeException failure) { error=Component.translatable("friendswine.settings.saveFailed"); }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick) {
        
        graphics.centeredText(font,title,width/2,12,0xFFFFFFFF);
        if (twoColumns) {
            graphics.centeredText(font,Component.translatable("friendswine.settings.displayPage"),width/2-150,34,0xFFAAAAAA);
            graphics.centeredText(font,Component.translatable("friendswine.settings.creaturePage"),width/2+150,34,0xFFAAAAAA);
        }
        for (int i=0;i<fields.length;i++) if (fields[i].visible) {
            String label=font.plainSubstrByWidth(Component.translatable("friendswine.config."+keys[i]).getString(),Math.max(70,fieldX(i)-left(i)-8));
            graphics.text(font,label,left(i),64+row(i)*32,0xFFFFFFFF);
        }
        graphics.centeredText(font,Component.translatable("friendswine.settings.scaleRange"),width/2,height-65,0xFFAAAAAA);
        if(page==2) graphics.text(font,Component.translatable("friendswine.config.deathFlashScope"),left(3),96,0xFFFFFFFF);
        Component invalid=validationError();
        graphics.centeredText(font,error.getString().isEmpty() ? invalid : error,width/2,height-47,0xFFFF5555);
        save.active=invalid.getString().isEmpty();
        super.extractRenderState(graphics,mouseX,mouseY,partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
