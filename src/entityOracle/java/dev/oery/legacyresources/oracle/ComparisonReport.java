package dev.oery.legacyresources.oracle;

import java.util.ArrayList;
import java.util.List;

/** Machine-readable comparison result. Score bands make topology dominate UV, then geometry. */
public final class ComparisonReport {
	public String scenarioId;
	public boolean matches;
	public double score;
	public int missingPasses, extraPasses, missingQuads, extraQuads;
	public int textureMismatches, uvMismatches, windingMismatches, normalMismatches, positionMismatches;
	public List<Mismatch> mismatches = new ArrayList<Mismatch>();
	public String rerunCommand;

	public static final class Mismatch {
		public String category, pass, message, modernPart;
		public Integer oracleDraw, candidateDraw, legacyBox, vertex;
		public double maxPositionError, rmsPositionError;
		public double[] oraclePosition, candidatePosition;
	}
	public String concise() {
		return scenarioId + ": " + (matches ? "MATCH" : "MISMATCH") + " score=" + score
			+ " pass=" + (missingPasses+extraPasses) + " topology=" + (missingQuads+extraQuads)
			+ " uv=" + uvMismatches + " winding=" + windingMismatches + " position=" + positionMismatches;
	}
}
