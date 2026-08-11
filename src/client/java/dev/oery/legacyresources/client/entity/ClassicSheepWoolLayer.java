package dev.oery.legacyresources.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.SheepRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.client.model.animal.sheep.SheepModel;

final class ClassicSheepWoolLayer extends RenderLayer<SheepRenderState, SheepModel> {
	private static final Identifier TEXTURE=Identifier.withDefaultNamespace("textures/entity/sheep/sheep_wool.png");
	private final ClassicSheepFurModel adultModel=new ClassicSheepFurModel(ClassicSheepFurModel.createLayer().bakeRoot());
	private final ClassicSheepFurModel babyModel=new ClassicSheepFurModel(ClassicSheepFurModel.createBabyLayer().bakeRoot());
	ClassicSheepWoolLayer(RenderLayerParent<SheepRenderState, SheepModel> parent){super(parent);}
	@Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,SheepRenderState state,float y,float x){if(!state.isSheared&&!state.isInvisible)collector.submitModel(state.isBaby?babyModel:adultModel,state,pose,RenderTypes.entityCutout(TEXTURE),light,LivingEntityRenderer.getOverlayCoords(state,0),LegacySheepColors.color(state),null,state.outlineColor,null);}
}
