package dev.oery.legacyresources.client.entity;

import net.minecraft.client.model.animal.sheep.BabySheepModel;
import net.minecraft.client.model.animal.sheep.SheepModel;
import net.minecraft.client.model.BabyModelTransform;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.SheepRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.sheep.Sheep;
import java.util.Set;

class ClassicSheepRenderer extends AgeableMobRenderer<Sheep,SheepRenderState,SheepModel> {
	private static final Identifier TEXTURE=Identifier.withDefaultNamespace("textures/entity/sheep/sheep.png");
	ClassicSheepRenderer(EntityRendererProvider.Context c){super(c,new SheepModel(c.bakeLayer(ModelLayers.SHEEP)),new SheepModel(SheepModel.createBodyLayer().apply(new BabyModelTransform(false,8,4,Set.of("head"))).bakeRoot()),.7f);addLayer(new ClassicSheepWoolLayer(this));}
	/** 1.8.9 scaled the adult texture for lambs; it had no separate baby PNG. */
	@Override public Identifier getTextureLocation(SheepRenderState s){return TEXTURE;}
	@Override public SheepRenderState createRenderState(){return new SheepRenderState();}
	@Override public void extractRenderState(Sheep sheep,SheepRenderState s,float partial){super.extractRenderState(sheep,s,partial);s.headEatAngleScale=sheep.getHeadEatAngleScale(partial);s.headEatPositionScale=sheep.getHeadEatPositionScale(partial);s.isSheared=sheep.isSheared();s.woolColor=sheep.getColor();s.isJebSheep=checkMagicName(sheep,"jeb_");}
}
