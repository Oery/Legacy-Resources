package dev.oery.legacyresources.client.entity;

import net.minecraft.client.model.BabyModelTransform;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.animal.sheep.SheepFurModel;
import net.minecraft.client.renderer.entity.state.SheepRenderState;
import java.util.Set;

/** The 1.8.9 wool mesh is byte-for-byte the modern SheepFurModel mesh; only its texture differs. */
final class ClassicSheepFurModel extends SheepFurModel {
	ClassicSheepFurModel(ModelPart root) { super(root); }
	static LayerDefinition createLayer() { return SheepFurModel.createFurLayer(); }
	static LayerDefinition createBabyLayer() { return createLayer().apply(new BabyModelTransform(false,8,4,Set.of("head"))); }
}
