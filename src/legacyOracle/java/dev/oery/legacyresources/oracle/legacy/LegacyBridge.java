package dev.oery.legacyresources.oracle.legacy;

import dev.oery.legacyresources.oracle.Scenario;
import dev.oery.legacyresources.oracle.Trace;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Headless replacement for the fixed-function stack and ModelRenderer submission. */
public final class LegacyBridge {
	private static final Deque<double[]> STACK=new ArrayDeque<double[]>();
	private static double[] matrix=identity();
	private static Trace.Pass pass; private static Scenario scenario; private static int draw,box;
	private LegacyBridge() { }
	static void activate(Scenario value){scenario=value;}
	static void begin(Scenario value,Trace.Pass output){scenario=value;pass=output;matrix=identity();STACK.clear();draw=0;box=0;}
	static void finish(){if(!STACK.isEmpty())throw new IllegalStateException("legacy matrix stack imbalance: "+STACK.size());scenario=null;pass=null;}
	public static void push(){STACK.push(matrix.clone());}
	public static void pop(){if(STACK.isEmpty())throw new IllegalStateException("legacy matrix stack underflow");matrix=STACK.pop();}
	public static void translate(float x,float y,float z){matrix=multiply(matrix,translation(x,y,z));}
	public static void scale(float x,float y,float z){matrix=multiply(matrix,scaling(x,y,z));}
	public static void rotate(float degrees,float x,float y,float z){matrix=multiply(matrix,rotation(Math.toRadians(degrees),x,y,z));}
	public static void render(Object renderer,float scale){renderPart(renderer,scale,false);}
	public static void renderWithRotation(Object renderer,float scale){renderPart(renderer,scale,true);}
	public static void postRender(Object renderer,float scale){applyPartTransform(renderer,scale,false);}

	private static void renderPart(Object renderer,float unit,boolean rotationVariant){
		if(renderer==null||bool(renderer,"k")||!bool(renderer,"j"))return;push();
		translate(f(renderer,"o"),f(renderer,"p"),f(renderer,"q"));applyPartTransform(renderer,unit,rotationVariant);
		List<?> cubes=(List<?>)field(renderer,"l");int tw=(int)f(renderer,"a"),th=(int)f(renderer,"b");
		for(Object cube:cubes)emitCube(cube,unit,tw,th,box++);
		Object children=field(renderer,"m");if(children instanceof List<?>)for(Object child:(List<?>)children)renderPart(child,unit,false);pop();
	}
	private static void applyPartTransform(Object r,float unit,boolean variant){translate(f(r,"c")*unit,f(r,"d")*unit,f(r,"e")*unit);float x=f(r,"f"),y=f(r,"g"),z=f(r,"h");if(variant){if(y!=0)rotate((float)Math.toDegrees(y),0,1,0);if(x!=0)rotate((float)Math.toDegrees(x),1,0,0);if(z!=0)rotate((float)Math.toDegrees(z),0,0,1);}else{if(z!=0)rotate((float)Math.toDegrees(z),0,0,1);if(y!=0)rotate((float)Math.toDegrees(y),0,1,0);if(x!=0)rotate((float)Math.toDegrees(x),1,0,0);}}
	private static void emitCube(Object cube,float unit,int tw,int th,int boxIndex){Object[] faces=(Object[])field(cube,"i");for(Object face:faces){Object[] vertices=(Object[])field(face,"a");Trace.Quad q=new Trace.Quad();q.drawOrdinal=draw++;q.legacyBox=boxIndex;for(int i=0;i<4;i++){Object vertex=vertices[i],vec=field(vertex,"a");double[] p=transform(d(vec,"a")*unit,d(vec,"b")*unit,d(vec,"c")*unit);q.positions[i]=p;q.uv[i][0]=f(vertex,"b");q.uv[i][1]=f(vertex,"c");}normal(q);pass.quads.add(q);}}
	private static void normal(Trace.Quad q){double[] a=sub(q.positions[1],q.positions[0]),b=sub(q.positions[2],q.positions[0]);double[] n={a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};double length=Math.sqrt(n[0]*n[0]+n[1]*n[1]+n[2]*n[2]);if(!Double.isFinite(length))throw new IllegalStateException("non-finite legacy quad normal");if(length==0){q.normal=new double[]{0,0,0};return;}for(int i=0;i<3;i++)q.normal[i]=n[i]/length;}
	private static Object field(Object value,String name){try{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}catch(Exception e){throw new IllegalStateException(value.getClass().getName()+"."+name,e);}}
	private static float f(Object value,String name){Object x=field(value,name);return ((Number)x).floatValue();}private static double d(Object value,String name){return ((Number)field(value,name)).doubleValue();}private static boolean bool(Object value,String name){return (Boolean)field(value,name);}
	private static double[] sub(double[] a,double[] b){return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
	private static double[] transform(double x,double y,double z){double[] p={x,y,z,1},o=new double[3];for(int r=0;r<3;r++)for(int c=0;c<4;c++)o[r]+=matrix[r*4+c]*p[c];return o;}
	private static double[] identity(){return new double[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};}
	private static double[] multiply(double[] a,double[] b){double[] o=new double[16];for(int r=0;r<4;r++)for(int c=0;c<4;c++)for(int k=0;k<4;k++)o[r*4+c]+=a[r*4+k]*b[k*4+c];return o;}
	private static double[] translation(double x,double y,double z){double[] m=identity();m[3]=x;m[7]=y;m[11]=z;return m;}private static double[] scaling(double x,double y,double z){double[] m=identity();m[0]=x;m[5]=y;m[10]=z;return m;}
	private static double[] rotation(double angle,double x,double y,double z){double l=Math.sqrt(x*x+y*y+z*z);x/=l;y/=l;z/=l;double c=Math.cos(angle),s=Math.sin(angle),t=1-c;return new double[]{t*x*x+c,t*x*y-s*z,t*x*z+s*y,0,t*x*y+s*z,t*y*y+c,t*y*z-s*x,0,t*x*z-s*y,t*y*z+s*x,t*z*z+c,0,0,0,0,1};}

	public static boolean ocelotSneaking(){return "sneaking".equals(scenario.string("posture"));}
	public static boolean ocelotSprinting(){return "sprinting".equals(scenario.string("posture"));}
	public static boolean ocelotSitting(){return "sitting".equals(scenario.string("posture"));}
	public static boolean animalSitting(){return "sitting".equals(scenario.string("posture"));}
	public static boolean wolfAngry(){return scenario.bool("angry");}public static boolean wolfSitting(){return "sitting".equals(scenario.string("posture"));}
	public static float wolfInterested(float partial){return scenario.bool("interested")?.4f:0;}public static float wolfShake(float partial,float offset){if(!scenario.bool("shaking"))return 0;return (float)Math.sin((scenario.number("age")+partial)*.8f+offset)*.25f;}
	public static int horseType(){String t=scenario.string("type");return "donkey".equals(t)?1:"mule".equals(t)?2:"zombie".equals(t)?3:"skeleton".equals(t)?4:0;}
	public static float horseEating(float partial){return scenario.number("eating");}public static float horseRearing(float partial){return scenario.number("rearing");}public static float horseMouth(float partial){return scenario.number("mouthOpen");}
	public static boolean horseSaddled(){return scenario.bool("saddled");}public static boolean horseChested(){return scenario.bool("chested");}public static float horseGrowth(){return scenario.number("growth");}
	public static boolean horseAdult(){return scenario.number("growth")>=1f;}
	public static float rabbitJump(float partial){return scenario.number("jump");}
	public static boolean batResting(){return scenario.bool("resting");}
}
