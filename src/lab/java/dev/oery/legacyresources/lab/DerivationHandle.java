package dev.oery.legacyresources.lab;

import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A {@code Derivation} reached purely by reflection.
 * <p>
 * The indirection is what makes hot reloading work. {@link DerivationReloader} recompiles the
 * derivation package into a fresh class loader on every edit, so the {@code Derivation} interface,
 * {@code Param} and {@code Params} it defines are <em>different classes</em> from the ones the lab
 * itself was compiled against, even though they have the same names. Any attempt to hold one in a
 * typed field or cast to it fails with a {@code ClassCastException} naming two identical-looking
 * types. Going through {@link Method} sidesteps the question entirely: only {@link String},
 * {@link List}, {@link Map} and {@link BufferedImage} - all loaded by the parent - cross the boundary.
 */
final class DerivationHandle {
	/** A {@code Param}'s declaration, flattened into a type the lab owns. */
	record ParamSpec(String name, double min, double max, double defaultValue, double step) {
		boolean isInteger() {
			return step == 1;
		}
	}

	private final Object instance;
	private final Class<?> derivationType;
	private final Class<?> paramsType;
	/** The child loader's own {@code List<Param>}, kept verbatim to hand back to {@code Params.of}. */
	private final Object declaredParams;
	private final String id;
	private final List<String> sources;
	private final List<String> outputs;
	private final List<ParamSpec> params;

	private DerivationHandle(
		Object instance, Class<?> derivationType, Class<?> paramsType, Object declaredParams,
		String id, List<String> sources, List<String> outputs, List<ParamSpec> params
	) {
		this.instance = instance;
		this.derivationType = derivationType;
		this.paramsType = paramsType;
		this.declaredParams = declaredParams;
		this.id = id;
		this.sources = sources;
		this.outputs = outputs;
		this.params = params;
	}

	/** Reads every derivation out of {@code Derivations.ALL} as loaded by {@code loader}. */
	static List<DerivationHandle> readAll(ClassLoader loader) throws ReflectiveOperationException {
		Class<?> registry = Class.forName(DerivationReloader.DERIVE_PACKAGE + ".Derivations", true, loader);
		Class<?> derivationType = Class.forName(DerivationReloader.DERIVE_PACKAGE + ".Derivation", true, loader);
		Class<?> paramType = Class.forName(DerivationReloader.DERIVE_PACKAGE + ".Param", true, loader);
		Class<?> paramsType = Class.forName(DerivationReloader.DERIVE_PACKAGE + ".Params", true, loader);

		List<DerivationHandle> handles = new ArrayList<>();
		for (Object instance : (List<?>) registry.getField("ALL").get(null)) {
			// Invoked through the public interface, not the implementation class, which is
			// package-private and would refuse the call.
			Object declaredParams = derivationType.getMethod("params").invoke(instance);
			handles.add(new DerivationHandle(
				instance,
				derivationType,
				paramsType,
				declaredParams,
				(String) derivationType.getMethod("id").invoke(instance),
				stringList(derivationType.getMethod("sources").invoke(instance)),
				stringList(derivationType.getMethod("outputs").invoke(instance)),
				readParams(paramType, declaredParams)
			));
		}
		return List.copyOf(handles);
	}

	private static List<ParamSpec> readParams(Class<?> paramType, Object declared) throws ReflectiveOperationException {
		Method name = paramType.getMethod("name");
		Method min = paramType.getMethod("min");
		Method max = paramType.getMethod("max");
		Method defaultValue = paramType.getMethod("defaultValue");
		Method step = paramType.getMethod("step");
		List<ParamSpec> specs = new ArrayList<>();
		for (Object param : (List<?>) declared) {
			specs.add(new ParamSpec(
				(String) name.invoke(param),
				(double) min.invoke(param),
				(double) max.invoke(param),
				(double) defaultValue.invoke(param),
				(double) step.invoke(param)
			));
		}
		return List.copyOf(specs);
	}

	@SuppressWarnings("unchecked")
	private static List<String> stringList(Object value) {
		return List.copyOf((List<String>) value);
	}

	String id() {
		return id;
	}

	List<String> sources() {
		return sources;
	}

	List<String> outputs() {
		return outputs;
	}

	List<ParamSpec> params() {
		return params;
	}

	/**
	 * @param overrides slider values by param name; anything absent falls back to the declared default
	 * @return the derived textures by output path - possibly empty, which is a derivation declining
	 *         this pack rather than an error
	 * @throws DerivationFailure if the derivation itself threw, so a half-written derivation surfaces
	 *                           in the page instead of blanking the whole corpus
	 */
	@SuppressWarnings("unchecked")
	Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Map<String, Double> overrides) {
		try {
			Object params = paramsType.getMethod("of", List.class, Map.class).invoke(null, declaredParams, overrides);
			return (Map<String, BufferedImage>) derivationType
				.getMethod("derive", Map.class, paramsType)
				.invoke(instance, sources, params);
		} catch (InvocationTargetException e) {
			throw new DerivationFailure(e.getCause());
		} catch (ReflectiveOperationException e) {
			throw new DerivationFailure(e);
		}
	}

	/** A derivation threw while running; carries the original cause for the page to display. */
	static final class DerivationFailure extends RuntimeException {
		DerivationFailure(Throwable cause) {
			super(cause);
		}
	}
}
