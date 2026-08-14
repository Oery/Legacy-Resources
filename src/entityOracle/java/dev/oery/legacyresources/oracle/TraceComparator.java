package dev.oery.legacyresources.oracle;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TraceComparator {
	public static final double POSITION_TOLERANCE=1e-5, UV_TOLERANCE=1e-6, NORMAL_TOLERANCE=1e-5;
	private TraceComparator() { }

	public static ComparisonReport compare(Trace oracle, Trace candidate, String family) {
		ComparisonReport r=new ComparisonReport(); r.scenarioId=oracle.scenarioId;
		r.rerunCommand="./gradlew compareLegacyEntityModels -PlegacyEntity="+family+" -PlegacyScenario="+oracle.scenarioId;
		Map<String,Trace.Pass> expected=index(oracle), actual=index(candidate);
		for(String key:expected.keySet()) if(!actual.containsKey(key)){r.missingPasses++; add(r,"missing-pass",key,"expected pass is absent",null,null);}
		for(String key:actual.keySet()) if(!expected.containsKey(key)){r.extraPasses++; add(r,"extra-pass",key,"unexpected pass",null,null);}
		for(String key:expected.keySet()) if(actual.containsKey(key)) comparePass(expected.get(key),actual.get(key),r);
		r.score=(r.missingPasses+r.extraPasses)*1e12+(r.missingQuads+r.extraQuads)*1e9
			+(r.textureMismatches+r.uvMismatches+r.windingMismatches)*1e6+r.normalMismatches*1e3;
		for(ComparisonReport.Mismatch m:r.mismatches)r.score+=m.rmsPositionError;
		r.matches=r.mismatches.isEmpty(); return r;
	}

	private static Map<String,Trace.Pass> index(Trace trace){Map<String,Trace.Pass> m=new LinkedHashMap<String,Trace.Pass>();for(Trace.Pass p:trace.passes)m.put(p.ordinal+":"+p.id,p);return m;}
	private static void comparePass(Trace.Pass a,Trace.Pass b,ComparisonReport r){
		String key=a.ordinal+":"+a.id;
		if(a.textureWidth!=b.textureWidth||a.textureHeight!=b.textureHeight){r.textureMismatches++;add(r,"texture-size",key,a.textureWidth+"x"+a.textureHeight+" != "+b.textureWidth+"x"+b.textureHeight,null,null);}
		int n=a.quads.size(),m=b.quads.size(); if(n>m){r.missingQuads+=n-m;add(r,"missing-quad",key,(n-m)+" quad(s) absent",null,null);} if(m>n){r.extraQuads+=m-n;add(r,"extra-quad",key,(m-n)+" unexpected quad(s)",null,null);}
		int pairs=Math.min(n,m); int[][] assignment;
		if(a.orderSensitive||b.orderSensitive){assignment=new int[pairs][2];for(int i=0;i<pairs;i++){assignment[i][0]=i;assignment[i][1]=i;}}
		else { double[][] costs=new double[n][m];for(int i=0;i<n;i++)for(int j=0;j<m;j++)costs[i][j]=matchCost(a.quads.get(i),b.quads.get(j));assignment=assign(costs); }
		for(int[] pair:assignment) compareQuad(a.quads.get(pair[0]),b.quads.get(pair[1]),key,r);
	}
	private static double matchCost(Trace.Quad a,Trace.Quad b){
		double c=0; double[] ac=centroid(a),bc=centroid(b);for(int k=0;k<3;k++){double d=ac[k]-bc[k];c+=d*d;}
		double[] au=bounds(a.uv),bu=bounds(b.uv);for(int k=0;k<4;k++)c+=100*Math.abs(au[k]-bu[k]);
		for(int k=0;k<3;k++)c+=10*Math.abs(a.normal[k]-b.normal[k]); c+=Math.abs(a.drawOrdinal-b.drawOrdinal)*1e-9;return c;
	}
	private static void compareQuad(Trace.Quad a,Trace.Quad b,String pass,ComparisonReport r){
		if(!a.winding.equals(b.winding)){r.windingMismatches++;add(r,"winding",pass,a.winding+" != "+b.winding,a,b);}
		int shift=bestCyclicShift(a,b); double uvMax=0,posMax=0,sum=0;int worst=0;
		for(int i=0;i<4;i++){int j=(i+shift)&3;for(int k=0;k<2;k++)uvMax=Math.max(uvMax,Math.abs(a.uv[i][k]-b.uv[j][k]));for(int k=0;k<3;k++){double d=a.positions[i][k]-b.positions[j][k];sum+=d*d;if(Math.abs(d)>posMax){posMax=Math.abs(d);worst=i;}}}
		if(uvMax>UV_TOLERANCE){r.uvMismatches++;add(r,"uv",pass,"maximum UV error "+uvMax,a,b);}
		double normal=0;for(int k=0;k<3;k++)normal=Math.max(normal,Math.abs(a.normal[k]-b.normal[k]));if(normal>NORMAL_TOLERANCE){r.normalMismatches++;add(r,"normal",pass,"maximum normal error "+normal,a,b);}
		if(posMax>POSITION_TOLERANCE){r.positionMismatches++;ComparisonReport.Mismatch x=add(r,"position",pass,"maximum position error "+posMax,a,b);x.maxPositionError=posMax;x.rmsPositionError=Math.sqrt(sum/12d);x.vertex=worst;x.oraclePosition=a.positions[worst].clone();x.candidatePosition=b.positions[(worst+shift)&3].clone();}
	}
	private static int bestCyclicShift(Trace.Quad a,Trace.Quad b){int best=0;double score=Double.POSITIVE_INFINITY;for(int s=0;s<4;s++){double q=0;for(int i=0;i<4;i++)for(int k=0;k<2;k++){double d=a.uv[i][k]-b.uv[(i+s)&3][k];q+=d*d;}if(q<score){score=q;best=s;}}return best;}
	private static double[] centroid(Trace.Quad q){double[] c=new double[3];for(double[] p:q.positions)for(int k=0;k<3;k++)c[k]+=p[k]/4;return c;}
	private static double[] bounds(double[][] p){double[] b={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};for(double[] v:p){b[0]=Math.min(b[0],v[0]);b[1]=Math.min(b[1],v[1]);b[2]=Math.max(b[2],v[0]);b[3]=Math.max(b[3],v[1]);}return b;}
	/** Rectangular minimum assignment, padded so topology is reported separately. */
	private static int[][] assign(double[][] cost){
		int n=cost.length,m=n==0?0:cost[0].length,size=Math.max(n,m);double[][] a=new double[size+1][size+1];for(int i=1;i<=size;i++)for(int j=1;j<=size;j++)a[i][j]=(i<=n&&j<=m)?cost[i-1][j-1]:1e15;
		double[] u=new double[size+1],v=new double[size+1];int[] p=new int[size+1],way=new int[size+1];
		for(int i=1;i<=size;i++){p[0]=i;double[] min=new double[size+1];Arrays.fill(min,Double.POSITIVE_INFINITY);boolean[] used=new boolean[size+1];int j0=0;do{used[j0]=true;int i0=p[j0],j1=0;double delta=Double.POSITIVE_INFINITY;for(int j=1;j<=size;j++)if(!used[j]){double cur=a[i0][j]-u[i0]-v[j];if(cur<min[j]){min[j]=cur;way[j]=j0;}if(min[j]<delta){delta=min[j];j1=j;}}for(int j=0;j<=size;j++)if(used[j]){u[p[j]]+=delta;v[j]-=delta;}else min[j]-=delta;j0=j1;}while(p[j0]!=0);do{int j1=way[j0];p[j0]=p[j1];j0=j1;}while(j0!=0);}
		int[][] out=new int[Math.min(n,m)][2];int next=0;for(int j=1;j<=size;j++)if(p[j]>0&&p[j]<=n&&j<=m){out[next][0]=p[j]-1;out[next][1]=j-1;next++;}if(next!=out.length)throw new IllegalStateException("incomplete quad assignment");Arrays.sort(out,new java.util.Comparator<int[]>(){public int compare(int[] x,int[] y){return Integer.compare(x[0],y[0]);}});return out;
	}
	private static ComparisonReport.Mismatch add(ComparisonReport r,String category,String pass,String message,Trace.Quad a,Trace.Quad b){ComparisonReport.Mismatch x=new ComparisonReport.Mismatch();x.category=category;x.pass=pass;x.message=message;if(a!=null){x.oracleDraw=a.drawOrdinal;x.legacyBox=a.legacyBox;}if(b!=null){x.candidateDraw=b.drawOrdinal;x.modernPart=b.modernPart;}r.mismatches.add(x);return x;}
}
