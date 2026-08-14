package dev.oery.legacyresources.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.DonkeyRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;

final class ClassicDonkeyRenderer<T extends AbstractChestedHorse> extends AbstractHorseRenderer<T, DonkeyRenderState, ClassicHorseModel> {
	private final Identifier texture;
	ClassicDonkeyRenderer(EntityRendererProvider.Context context, Identifier texture) {
		super(context, new ClassicHorseModel(ClassicHorseModel.createLayer().bakeRoot(), true, true), new ClassicHorseModel(ClassicHorseModel.createLayer().bakeRoot(), true, true));
		this.texture=texture;
	}
	@Override public Identifier getTextureLocation(DonkeyRenderState state) { return texture; }
	@Override public DonkeyRenderState createRenderState() { return new DonkeyRenderState(); }
	@Override public void extractRenderState(T horse, DonkeyRenderState state, float partial) { super.extractRenderState(horse,state,partial); state.hasChest=horse.hasChest(); }
	@Override protected void scale(DonkeyRenderState state, PoseStack pose) { LegacyEntityRenderPlan.applyOuter(LegacyEntityRenderPlan.Family.HORSE, state.isBaby, pose); }
}
