package dev.oery.legacyresources.oracle.legacy;

import java.lang.instrument.Instrumentation;

/** Instruments only identifiers pinned in symbols-1.8.9.properties. */
public final class LegacyOracleAgent {
	private LegacyOracleAgent() { }
	public static void premain(String arguments, Instrumentation instrumentation) {
		instrumentation.addTransformer(new LegacyTransformer());
	}
}
