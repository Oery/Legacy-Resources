package dev.oery.legacyresources.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HorseRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * 1.8.9 drew iron, gold and diamond horse armour on the same 128x128 horse layout. Modern's
 * equipment renderer uses a distinct 64x64 model, so it cannot be reused for a classic coat.
 */
final class ClassicHorseArmorLayer extends RenderLayer<HorseRenderState, ClassicHorseModel> {
	private final ClassicHorseModel armorModel = new ClassicHorseModel(ClassicHorseModel.createLayer().bakeRoot(), false, false);

	ClassicHorseArmorLayer(RenderLayerParent<HorseRenderState, ClassicHorseModel> parent) { super(parent); }

	@Override public void submit(PoseStack pose, SubmitNodeCollector collector, int light, HorseRenderState state, float yRot, float xRot) {
		Identifier texture = texture(state.bodyArmorItem);
		if (texture == null || state.isInvisible) return;
		collector.order(2).submitModel(armorModel, state, pose, RenderTypes.entityCutout(texture), light,
			LivingEntityRenderer.getOverlayCoords(state, 0), color(state.bodyArmorItem), null, state.outlineColor, (ModelFeatureRenderer.CrumblingOverlay)null);
	}

	private static Identifier texture(ItemStack armor) {
		String material = armor.is(Items.LEATHER_HORSE_ARMOR) ? "leather" : armor.is(Items.IRON_HORSE_ARMOR) ? "iron" : armor.is(Items.GOLDEN_HORSE_ARMOR) ? "gold" : armor.is(Items.DIAMOND_HORSE_ARMOR) ? "diamond" : armor.is(Items.NETHERITE_HORSE_ARMOR) ? "netherite" : null;
		return material == null ? null : Identifier.withDefaultNamespace("textures/entity/horse/armor/horse_armor_" + material + ".png");
	}

	/** Match the equipment renderer: leather's default chestnut tint or its stack's dyed colour. */
	private static int color(ItemStack armor) {
		return armor.is(Items.LEATHER_HORSE_ARMOR) ? DyedItemColor.getOrDefault(armor, -6265536) : -1;
	}
}
