package dev.oery.legacyresources.oracle;

import java.util.ArrayList;
import java.util.List;

/** Final submitted geometry. Positions use model-space units and UVs are normalized. */
public final class Trace {
	public int schemaVersion = 1;
	public String harnessVersion = "1";
	public String minecraftVersion;
	public String jarSha1;
	public String scenarioId;
	public List<Pass> passes = new ArrayList<Pass>();

	public static final class Pass {
		public String id;
		public int ordinal;
		public boolean orderSensitive;
		public int textureWidth;
		public int textureHeight;
		public List<Quad> quads = new ArrayList<Quad>();
		public Pass() { }
		public Pass(String id, int ordinal, boolean orderSensitive, int textureWidth, int textureHeight) {
			this.id=id; this.ordinal=ordinal; this.orderSensitive=orderSensitive; this.textureWidth=textureWidth; this.textureHeight=textureHeight;
		}
	}

	public static final class Quad {
		public int drawOrdinal;
		public double[][] positions = new double[4][3];
		public double[][] uv = new double[4][2];
		public double[] normal = new double[3];
		public String winding = "ccw";
		public boolean visible = true;
		public boolean culled;
		public Integer legacyBox;
		public String modernPart;
		public Quad() { }
		public Quad copy() {
			Quad q=new Quad(); q.drawOrdinal=drawOrdinal; q.winding=winding; q.visible=visible; q.culled=culled; q.legacyBox=legacyBox; q.modernPart=modernPart;
			for(int i=0;i<4;i++){q.positions[i]=positions[i].clone();q.uv[i]=uv[i].clone();}q.normal=normal.clone();return q;
		}
	}
}
