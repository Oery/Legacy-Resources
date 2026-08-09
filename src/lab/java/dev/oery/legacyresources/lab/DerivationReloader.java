package dev.oery.legacyresources.lab;

import java.io.IOException;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.jspecify.annotations.Nullable;

/**
 * Recompiles the derivation package whenever one of its files changes, and hands back the fresh
 * classes.
 * <p>
 * This is the difference between a usable tuning loop and a tedious one. Derivations are written and
 * rewritten constantly - an edit, a look at 79 packs, another edit - and restarting the lab for each
 * one means re-opening 79 zips and re-decoding their sheets every time. Instead the lab keeps running
 * and only the derivation classes are replaced.
 * <p>
 * The trick that makes it work is a <b>child-first</b> class loader scoped to the derivation package.
 * A plain {@link URLClassLoader} delegates to its parent first, and since the lab's own classpath
 * already contains a compiled copy of that package, the parent would win every time and no edit would
 * ever take effect. See {@link ChildFirstLoader}.
 */
final class DerivationReloader {
	static final String DERIVE_PACKAGE = "dev.oery.legacyresources.client.derive";

	private static final String SOURCE_DIRECTORY = "src/client/java/dev/oery/legacyresources/client/derive";
	private static final String OUTPUT_DIRECTORY = "build/lab/derive-classes";

	private final Path sourceDirectory;
	private final Path outputDirectory;
	private final @Nullable JavaCompiler compiler;

	private @Nullable Reload current;
	private long currentFingerprint = -1;

	/**
	 * @param derivations the derivations in their state at reload time
	 * @param errors      compiler diagnostics; when non-empty {@code derivations} is the last version
	 *                    that <em>did</em> compile, so a syntax error leaves the page usable
	 * @param note        why the lab is not hot reloading at all, if it isn't
	 */
	record Reload(List<DerivationHandle> derivations, List<String> errors, @Nullable String note) {
	}

	DerivationReloader(Path projectDirectory) {
		this.sourceDirectory = projectDirectory.resolve(SOURCE_DIRECTORY);
		this.outputDirectory = projectDirectory.resolve(OUTPUT_DIRECTORY);
		this.compiler = ToolProvider.getSystemJavaCompiler();
	}

	/**
	 * The current derivations, recompiling first if any source file changed since the last call.
	 * Cheap enough to call per request - it only stats the source directory.
	 */
	synchronized Reload current() {
		if (compiler == null) {
			return staticFallback("No system Java compiler available - restart the lab to pick up edits");
		}
		long fingerprint = fingerprint();
		if (current != null && fingerprint == currentFingerprint) {
			return current;
		}
		Reload reload = compile();
		if (reload != null) {
			current = reload;
			currentFingerprint = fingerprint;
		}
		return current != null ? current : staticFallback("Compilation failed and no earlier version was loaded");
	}

	/** Modification times and sizes of every derivation source, rolled into one comparable number. */
	private long fingerprint() {
		try (Stream<Path> files = Files.list(sourceDirectory)) {
			long hash = 17;
			for (Path file : files.sorted(Comparator.comparing(Path::toString)).toList()) {
				hash = hash * 31 + file.toString().hashCode();
				hash = hash * 31 + Files.getLastModifiedTime(file).toMillis();
				hash = hash * 31 + Files.size(file);
			}
			return hash;
		} catch (IOException e) {
			return -1;
		}
	}

	private @Nullable Reload compile() {
		List<Path> sources;
		try (Stream<Path> files = Files.list(sourceDirectory)) {
			sources = files.filter(path -> path.toString().endsWith(".java")).toList();
			deleteRecursively(outputDirectory);
			Files.createDirectories(outputDirectory);
		} catch (IOException e) {
			return new Reload(previousDerivations(), List.of("Cannot read " + sourceDirectory + ": " + e), null);
		}

		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		StringWriter output = new StringWriter();
		boolean compiled;
		try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
			List<String> options = List.of(
				"-classpath", System.getProperty("java.class.path"),
				"-d", outputDirectory.toString()
			);
			compiled = compiler.getTask(
				output, files, diagnostics, options, null, files.getJavaFileObjectsFromPaths(sources)
			).call();
		} catch (IOException e) {
			return new Reload(previousDerivations(), List.of("Compiler failed: " + e), null);
		}

		List<String> errors = new ArrayList<>();
		diagnostics.getDiagnostics().stream()
			.filter(diagnostic -> diagnostic.getKind() == javax.tools.Diagnostic.Kind.ERROR)
			.forEach(diagnostic -> errors.add(describe(diagnostic)));
		if (!compiled) {
			if (errors.isEmpty()) {
				errors.add(output.toString().isBlank() ? "Compilation failed" : output.toString());
			}
			// Deliberately not caching this as `current`: the next request should try again, since the
			// fix is usually one keystroke away.
			return new Reload(previousDerivations(), List.copyOf(errors), null);
		}

		try {
			ClassLoader loader = new ChildFirstLoader(outputDirectory, getClass().getClassLoader());
			return new Reload(DerivationHandle.readAll(loader), List.of(), null);
		} catch (ReflectiveOperationException | MalformedURLException | RuntimeException e) {
			// A derivation that compiles but blows up in a static initializer - a duplicate output
			// registration, say - lands here.
			return new Reload(previousDerivations(), List.of("Loading derivations failed: " + e), null);
		}
	}

	private static String describe(javax.tools.Diagnostic<? extends JavaFileObject> diagnostic) {
		JavaFileObject source = diagnostic.getSource();
		String where = source == null
			? ""
			: Path.of(source.getName()).getFileName() + ":" + diagnostic.getLineNumber() + " - ";
		return where + diagnostic.getMessage(null);
	}

	private List<DerivationHandle> previousDerivations() {
		return current == null ? List.of() : current.derivations();
	}

	/** The derivations already on the lab's own classpath, used when hot reloading isn't available. */
	private Reload staticFallback(String note) {
		try {
			return new Reload(DerivationHandle.readAll(getClass().getClassLoader()), List.of(), note);
		} catch (ReflectiveOperationException e) {
			return new Reload(List.of(), List.of("Cannot read derivations: " + e), note);
		}
	}

	private static void deleteRecursively(Path directory) throws IOException {
		if (!Files.exists(directory)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(directory)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(path);
			}
		}
	}

	/**
	 * Loads the derivation package from the freshly compiled directory and everything else - the JDK,
	 * Minecraft, the lab itself - from the parent.
	 * <p>
	 * Scoping the inversion to one package prefix is deliberate: a fully child-first loader would also
	 * reload Minecraft's classes, which would be both slow and wrong (the packs are already open,
	 * holding the parent's copies).
	 */
	private static final class ChildFirstLoader extends URLClassLoader {
		private final String prefix = DERIVE_PACKAGE + ".";

		ChildFirstLoader(Path classes, ClassLoader parent) throws MalformedURLException {
			super(new URL[] { classes.toUri().toURL() }, parent);
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			if (!name.startsWith(prefix)) {
				return super.loadClass(name, resolve);
			}
			synchronized (getClassLoadingLock(name)) {
				Class<?> loaded = findLoadedClass(name);
				if (loaded == null) {
					loaded = findClass(name);
				}
				if (resolve) {
					resolveClass(loaded);
				}
				return loaded;
			}
		}
	}
}
