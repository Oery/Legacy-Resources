package dev.oery.legacyresources.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.BatRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ambient.Bat;

final class ClassicBatRenderer extends MobRenderer<Bat, BatRenderState, ClassicBatModel> {
	private static final Identifier TEXTURE=Identifier.withDefaultNamespace("textures/entity/bat/bat.png");
	ClassicBatRenderer(EntityRendererProvider.Context context){super(context,new ClassicBatModel(ClassicBatModel.createLayer().bakeRoot()),.25f);}
	@Override public Identifier getTextureLocation(BatRenderState state){return TEXTURE;}
	@Override public BatRenderState createRenderState(){return new BatRenderState();}
	@Override public void extractRenderState(Bat bat,BatRenderState state,float partial){super.extractRenderState(bat,state,partial);state.isResting=bat.isResting();}
	@Override protected void setupRotations(BatRenderState state, PoseStack pose, float bodyRot, float entityScale) {
		super.setupRotations(state, pose, bodyRot, entityScale);
		// RenderBat translated during rotateCorpse, before preRenderCallback scaled the model.
		// Keeping that order matters: translating in scale() shrinks the offset by 0.35.
		pose.translate(0, state.isResting ? -.1f : (float)Math.cos(state.ageInTicks * .3f) * .1f, 0);
	}
	@Override protected void scale(BatRenderState state,PoseStack pose){pose.scale(.35f,.35f,.35f);}
}
