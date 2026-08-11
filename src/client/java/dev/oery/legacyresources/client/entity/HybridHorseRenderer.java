package dev.oery.legacyresources.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.oery.legacyresources.client.convert.LegacyPackResources;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HorseRenderer;
import net.minecraft.client.renderer.entity.state.HorseRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.animal.equine.Horse;

/**
 * Chooses the renderer per horse render state, rather than per reload.  Resource packs commonly
 * override one or two coats only; a reload-wide choice would put either a legacy coat on modern UVs
 * or a modern coat on legacy UVs.  The state already contains its selected variant, so delegating
 * at submit time keeps the decision exactly where the texture decision is made.
 */
final class HybridHorseRenderer extends ClassicHorseRenderer {
	private final ResourceManager resources;
	private final HorseRenderer modern;

	HybridHorseRenderer(EntityRendererProvider.Context context) {
		super(context);
		resources = context.getResourceManager();
		modern = new HorseRenderer(context);
	}

	@Override
	public void submit(HorseRenderState state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
		// 1.8.9 has no separate baby horse sheets: the classic renderer deliberately scales the
		// legacy adult sheet for foals, matching the source game rather than selecting a new sheet.
		if (usesLegacy(getTextureLocation(state))) {
			super.submit(state, poses, collector, camera);
		} else {
			modern.submit(state, poses, collector, camera);
		}
	}

	private boolean usesLegacy(Identifier texture) {
		return resources.getResource(texture).map(Resource::source).filter(LegacyPackResources.class::isInstance).isPresent();
	}
}
