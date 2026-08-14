package dev.oery.legacyresources.oracle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Dependency-free mutation suite for comparator categories and deterministic serialization. */
public final class SyntheticHarness {
	private SyntheticHarness() { }
	public static void main(String[] args) throws Exception {
		Trace base=trace(); assertMatch(base,copy(base));
		mutate(base,"translation",q->q.positions[0][0]+=.01,"position");
		mutate(base,"scale",q->q.positions[2][1]*=1.1,"position");
		Trace rotation=copy(base);double x=q(rotation).positions[1][0],y=q(rotation).positions[1][1];q(rotation).positions[1][0]=-y;q(rotation).positions[1][1]=x;assertCategory("rotation",base,rotation,"position");
		Trace multiplication=copy(base);q(multiplication).positions[3][2]=.25;assertCategory("matrix-order",base,multiplication,"position");
		Trace missing=copy(base);missing.passes.get(0).quads.clear();assertCategory("missing-cube",base,missing,"missing-quad");
		Trace extra=copy(base);extra.passes.get(0).quads.add(q(extra).copy());assertCategory("extra-cube",base,extra,"extra-quad");
		Trace hidden=copy(base);hidden.passes.get(0).quads.clear();assertCategory("hidden-cube",base,hidden,"missing-quad");
		Trace uv=copy(base);q(uv).uv[0][0]+=.01;assertCategory("uv",base,uv,"uv");
		Trace texture=copy(base);texture.passes.get(0).textureWidth=32;assertCategory("texture",base,texture,"texture-size");
		Trace winding=copy(base);q(winding).winding="cw";assertCategory("winding",base,winding,"winding");
		Trace child=copy(base);q(child).positions[0][2]+=.1;assertCategory("child-transform",base,child,"position");
		Trace baby=copy(base);q(baby).positions[1][1]+=.2;assertCategory("baby-group",base,baby,"position");
		Trace pass=copy(base);pass.passes.get(0).id="overlay";assertCategory("pass-order",base,pass,"missing-pass");
		Trace reversed=copy(base);for(int i=0;i<2;i++){double[] p=q(reversed).positions[i];q(reversed).positions[i]=q(reversed).positions[3-i];q(reversed).positions[3-i]=p;}q(reversed).winding="cw";assertCategory("mirror",base,reversed,"winding");
		Trace nan=copy(base);q(nan).normal[0]=Double.NaN;try{TraceJson.canonicalize(nan);throw new AssertionError("non-finite accepted");}catch(IllegalStateException expected){}
		Path dir=Files.createTempDirectory("legacy-oracle-synthetic");Path one=dir.resolve("one.json"),two=dir.resolve("two.json");TraceJson.write(one,copy(base));TraceJson.write(two,copy(base));if(!Arrays.equals(Files.readAllBytes(one),Files.readAllBytes(two)))throw new AssertionError("trace serialization is not deterministic");
		ComparisonReport report=TraceComparator.compare(base,copy(base),"synthetic");String a=TraceJson.string(report),b=TraceJson.string(TraceComparator.compare(base,copy(base),"synthetic"));if(!a.equals(b))throw new AssertionError("report serialization is not deterministic");
		System.out.println("Legacy entity oracle synthetic conformance: 15 checks passed");
	}
	private interface Change{void apply(Trace.Quad q);} private static void mutate(Trace base,String name,Change c,String category){Trace t=copy(base);c.apply(q(t));assertCategory(name,base,t,category);}
	private static void assertMatch(Trace a,Trace b){if(!TraceComparator.compare(a,b,"synthetic").matches)throw new AssertionError("identical traces mismatch");}
	private static void assertCategory(String name,Trace a,Trace b,String category){ComparisonReport r=TraceComparator.compare(a,b,"synthetic");if(r.matches||r.mismatches.stream().noneMatch(x->category.equals(x.category)))throw new AssertionError(name+" did not report "+category+": "+TraceJson.string(r));}
	private static Trace trace(){Trace t=new Trace();t.minecraftVersion="synthetic";t.jarSha1="synthetic";t.scenarioId="synthetic";Trace.Pass p=new Trace.Pass("body",0,false,64,32);Trace.Quad q=new Trace.Quad();q.drawOrdinal=0;q.modernPart="root/cube";q.legacyBox=0;q.positions=new double[][]{{0,0,0},{1,0,0},{1,1,0},{0,1,0}};q.uv=new double[][]{{0,0},{.5,0},{.5,.5},{0,.5}};q.normal=new double[]{0,0,1};p.quads.add(q);t.passes.add(p);return t;}
	private static Trace copy(Trace t){return new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(t),Trace.class);}private static Trace.Quad q(Trace t){return t.passes.get(0).quads.get(0);}
}
