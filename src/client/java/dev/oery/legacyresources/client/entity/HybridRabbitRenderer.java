package dev.oery.legacyresources.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.oery.legacyresources.client.convert.LegacyPackResources;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RabbitRenderer;
import net.minecraft.client.renderer.entity.state.RabbitRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.animal.rabbit.Rabbit;

/** Selects 1.8's model only for a rabbit variant supplied by the active legacy pack. */
final class HybridRabbitRenderer extends AgeableMobRenderer<Rabbit, RabbitRenderState, ClassicRabbitModel> {
	private static final Map<Rabbit.Variant, String> LEGACY_STEMS = Map.of(
		Rabbit.Variant.BROWN, "brown", Rabbit.Variant.WHITE, "white", Rabbit.Variant.BLACK, "black",
		Rabbit.Variant.GOLD, "gold", Rabbit.Variant.SALT, "salt", Rabbit.Variant.WHITE_SPLOTCHED, "white_splotched",
		Rabbit.Variant.EVIL, "caerbannog"
	);
	private final ResourceManager resources;
	private final RabbitRenderer modern;

	HybridRabbitRenderer(EntityRendererProvider.Context context) {
		super(context, new ClassicRabbitModel(ClassicRabbitModel.createLayer(false).bakeRoot()), new ClassicRabbitModel(ClassicRabbitModel.createLayer(true).bakeRoot()), .3f);
		resources = context.getResourceManager();
		modern = new RabbitRenderer(context);
	}

	@Override public Identifier getTextureLocation(RabbitRenderState state) { return legacyTexture(state); }
	@Override public RabbitRenderState createRenderState() { return new RabbitRenderState(); }
	@Override public void extractRenderState(Rabbit rabbit, RabbitRenderState state, float partial) {
		super.extractRenderState(rabbit, state, partial);
		state.jumpCompletion = rabbit.getJumpCompletion(partial);
		state.isToast = checkMagicName(rabbit, "Toast");
		state.variant = rabbit.getVariant();
		state.hopAnimationState.copyFrom(rabbit.hopAnimationState);
		state.idleHeadTiltAnimationState.copyFrom(rabbit.idleHeadTiltAnimationState);
	}
	@Override public void submit(RabbitRenderState state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
		Identifier texture = legacyTexture(state);
		if (resources.getResource(texture).map(Resource::source).filter(LegacyPackResources.class::isInstance).isPresent()) super.submit(state, poses, collector, camera);
		else modern.submit(state, poses, collector, camera);
	}

	private static Identifier legacyTexture(RabbitRenderState state) {
		String stem = state.isToast ? "toast" : LEGACY_STEMS.getOrDefault(state.variant, "brown");
		return Identifier.withDefaultNamespace("textures/entity/rabbit/" + stem + ".png");
	}
}
