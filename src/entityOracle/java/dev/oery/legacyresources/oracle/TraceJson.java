package dev.oery.legacyresources.oracle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Stable UTF-8 serialization; negative zero is canonicalized without rounding values. */
public final class TraceJson {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().setPrettyPrinting().create();
	private TraceJson() { }
	public static Trace read(Path path) throws IOException { return GSON.fromJson(new String(Files.readAllBytes(path), StandardCharsets.UTF_8), Trace.class); }
	public static void write(Path path, Trace trace) throws IOException {
		canonicalize(trace); Files.createDirectories(path.getParent());
		Files.write(path, (GSON.toJson(trace) + "\n").getBytes(StandardCharsets.UTF_8));
	}
	public static String string(Object value) { return GSON.toJson(value) + "\n"; }
	private static double z(double value) { if (!Double.isFinite(value)) throw new IllegalStateException("non-finite trace value " + value); return value == 0d ? 0d : value; }
	public static void canonicalize(Trace trace) {
		for (Trace.Pass pass : trace.passes) for (Trace.Quad quad : pass.quads) {
			for(int i=0;i<4;i++){for(int j=0;j<3;j++)quad.positions[i][j]=z(quad.positions[i][j]);for(int j=0;j<2;j++)quad.uv[i][j]=z(quad.uv[i][j]);}
			for(int j=0;j<3;j++)quad.normal[j]=z(quad.normal[j]);
		}
	}
}
