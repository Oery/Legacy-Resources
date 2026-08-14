package dev.oery.legacyresources.oracle;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Strict, versioned inputs shared by the legacy and current-game adapters. */
public final class Scenario {
	public static final int SCHEMA_VERSION = 1;
	private static final Set<String> COMMON = set("schemaVersion", "id", "family", "baby", "growth", "limbPhase", "limbSpeed", "age", "headYaw", "headPitch", "swingProgress", "riding");
	private static final Set<String> OCELOT = set("posture");
	private static final Set<String> WOLF = set("posture", "angry", "wet", "shaking", "interested", "tailAngle");
	private static final Set<String> HORSE = set("type", "eating", "rearing", "mouthOpen", "saddled", "ridden", "chested", "tailMoving");
	private static final Set<String> CHICKEN = set("flap", "flapSpeed");
	private static final Set<String> PIG = set("saddled");
	private static final Set<String> RABBIT = set("jump");
	private static final Set<String> BAT = set("resting");
	private static final Set<String> EMPTY = set();
	private final JsonObject values;
	private final Set<String> consumed = new LinkedHashSet<String>();

	private Scenario(JsonObject values) { this.values = values; }

	public static Scenario read(Path path) throws IOException {
		JsonElement parsed = new JsonParser().parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
		if (!parsed.isJsonObject()) throw new IllegalArgumentException(path + ": scenario must be a JSON object");
		JsonObject object = parsed.getAsJsonObject();
		return from(object, path.toString());
	}

	public static Scenario from(JsonObject object, String source) {
		require(object, COMMON);
		int version = object.get("schemaVersion").getAsInt();
		if (version != SCHEMA_VERSION) throw new IllegalArgumentException(source + ": unsupported schemaVersion " + version);
		String family = object.get("family").getAsString();
		Set<String> allowed = new LinkedHashSet<String>(COMMON);
		if ("ocelot".equals(family) || "cat".equals(family)) allowed.addAll(OCELOT);
		else if ("wolf".equals(family) || "dog".equals(family)) allowed.addAll(WOLF);
		else if ("horse".equals(family)) allowed.addAll(HORSE);
		else if ("chicken".equals(family)) allowed.addAll(CHICKEN);
		else if ("pig".equals(family)) allowed.addAll(PIG);
		else if ("rabbit".equals(family)) allowed.addAll(RABBIT);
		else if ("bat".equals(family)) allowed.addAll(BAT);
		else if ("cow".equals(family)) allowed.addAll(EMPTY);
		else throw new IllegalArgumentException(source + ": unknown family " + family);
		Set<String> required = ("ocelot".equals(family)||"cat".equals(family)) ? OCELOT
			: ("wolf".equals(family)||"dog".equals(family)) ? WOLF : "horse".equals(family) ? HORSE
			: "chicken".equals(family) ? CHICKEN : "pig".equals(family) ? PIG : "rabbit".equals(family) ? RABBIT : "bat".equals(family) ? BAT : EMPTY;
		require(object, required);
		for (java.util.Map.Entry<String, JsonElement> field : object.entrySet())
			if (!allowed.contains(field.getKey())) throw new IllegalArgumentException(source + ": unknown field " + field.getKey());
		return new Scenario(object);
	}

	private static void require(JsonObject object, Set<String> names) {
		for (String name : names) if (!object.has(name) || object.get(name).isJsonNull()) throw new IllegalArgumentException("missing required field " + name);
	}
	private static Set<String> set(String... names) { return Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(names))); }
	private JsonElement take(String name) { consumed.add(name); return values.get(name); }
	public String string(String name) { return take(name).getAsString(); }
	public boolean bool(String name) { return take(name).getAsBoolean(); }
	public float number(String name) { return take(name).getAsFloat(); }
	public int integer(String name) { return take(name).getAsInt(); }
	public String id() { return values.get("id").getAsString(); }
	public String family() { return values.get("family").getAsString(); }
	public JsonObject json() { return values.deepCopy(); }

	/** Adapters call this after rendering. Silently defaulting any declared input is a hard failure. */
	public void assertAllConsumed() {
		Set<String> missing = new LinkedHashSet<String>();
		for (java.util.Map.Entry<String, JsonElement> field : values.entrySet()) missing.add(field.getKey());
		missing.remove("schemaVersion"); missing.remove("id"); missing.remove("family"); missing.removeAll(consumed);
		if (!missing.isEmpty()) throw new IllegalStateException(id() + ": adapter did not consume " + missing);
	}
	public void consumeIdentity() { integer("schemaVersion"); string("id"); string("family"); }
	@Override public String toString() { return new Gson().toJson(values); }
}
