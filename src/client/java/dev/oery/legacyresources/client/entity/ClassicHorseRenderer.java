package dev.oery.legacyresources.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.HorseRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Variant;

/** Renderer used only when the winning horse coat comes from a converted legacy pack. */
public class ClassicHorseRenderer extends AbstractHorseRenderer<Horse, HorseRenderState, ClassicHorseModel> {
	private static final Map<Variant, Identifier> TEXTURES = Map.of(
		Variant.WHITE, id("horse_white"), Variant.CREAMY, id("horse_creamy"), Variant.CHESTNUT, id("horse_chestnut"),
		Variant.BROWN, id("horse_brown"), Variant.BLACK, id("horse_black"), Variant.GRAY, id("horse_gray"), Variant.DARK_BROWN, id("horse_darkbrown")
	);

	public ClassicHorseRenderer(EntityRendererProvider.Context context) {
		super(context, new ClassicHorseModel(ClassicHorseModel.createLayer().bakeRoot(), false, true), new ClassicHorseModel(ClassicHorseModel.createLayer().bakeRoot(), false, true));
		addLayer(new ClassicHorseMarkingLayer(this));
		addLayer(new ClassicHorseArmorLayer(this));
	}

	@Override public Identifier getTextureLocation(HorseRenderState state) { return TEXTURES.get(state.variant); }
	@Override public HorseRenderState createRenderState() { return new HorseRenderState(); }
	@Override public void extractRenderState(Horse horse, HorseRenderState state, float partial) { super.extractRenderState(horse, state, partial); state.variant=horse.getVariant(); state.markings=horse.getMarkings(); state.bodyArmorItem=horse.getBodyArmorItem().copy(); }
	@Override protected void scale(HorseRenderState state, PoseStack pose) { LegacyEntityRenderPlan.applyOuter(LegacyEntityRenderPlan.Family.HORSE, state.isBaby, pose); }
	private static Identifier id(String stem) { return Identifier.withDefaultNamespace("textures/entity/horse/" + stem + ".png"); }
}
