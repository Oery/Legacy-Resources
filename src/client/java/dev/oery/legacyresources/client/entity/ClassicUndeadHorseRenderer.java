package dev.oery.legacyresources.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EquineRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

final class ClassicUndeadHorseRenderer<T extends AbstractHorse> extends AbstractHorseRenderer<T, EquineRenderState, ClassicHorseModel> {
	private final Identifier texture;
	ClassicUndeadHorseRenderer(EntityRendererProvider.Context context, Identifier texture) {
		super(context, new ClassicHorseModel(ClassicHorseModel.createLayer().bakeRoot(), false, true), new ClassicHorseModel(ClassicHorseModel.createLayer().bakeRoot(), false, true));
		this.texture=texture;
	}
	@Override public Identifier getTextureLocation(EquineRenderState state) { return texture; }
	@Override public EquineRenderState createRenderState() { return new EquineRenderState(); }
	@Override protected void scale(EquineRenderState state, PoseStack pose) { LegacyEntityRenderPlan.applyOuter(LegacyEntityRenderPlan.Family.HORSE, state.isBaby, pose); }
}
