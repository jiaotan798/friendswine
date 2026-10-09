package com.friendswine.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.*;
import java.util.function.Consumer;
import org.joml.Vector3fc;
import org.joml.Vector3f;
public final class DollItemRenderer implements NoDataSpecialModelRenderer {
    public void submit(PoseStack pose,SubmitNodeCollector collector,int light,int overlay,boolean foil,int outline) {
        pose.pushPose();pose.translate(0.5,0,0.5);DollRenderer.draw(pose,collector,light,overlay);pose.popPose();
    }
    public void getExtents(Consumer<Vector3fc> output) { output.accept(new Vector3f(0,0,0));output.accept(new Vector3f(1,1,1)); }
    public record Unbaked() implements NoDataSpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> CODEC=MapCodec.unit(new Unbaked());
        public MapCodec<Unbaked> type(){return CODEC;}
        public DollItemRenderer bake(SpecialModelRenderer.BakingContext context){return new DollItemRenderer();}
    }
}
