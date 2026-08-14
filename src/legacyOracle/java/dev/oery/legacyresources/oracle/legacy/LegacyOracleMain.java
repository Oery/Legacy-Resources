package dev.oery.legacyresources.oracle.legacy;

import dev.oery.legacyresources.oracle.Scenario;
import dev.oery.legacyresources.oracle.ScenarioCatalog;
import dev.oery.legacyresources.oracle.Trace;
import dev.oery.legacyresources.oracle.TraceJson;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runs only in the launcher Java 8 process against the hash-pinned official obfuscated jar. */
public final class LegacyOracleMain {
	private static final String VERSION="1.8.9", PINNED_SHA1="3870888a6c3d349d3771a3e9d16c9bf5e076b908";
	private LegacyOracleMain() { }
	public static void main(String[] raw) throws Exception {
		Map<String,String> args=args(raw);Path client=Paths.get(required(args,"client")),out=Paths.get(required(args,"output"));String hash=sha1(client);
		if(!PINNED_SHA1.equals(hash)||!hash.equals(required(args,"sha1")))throw new IllegalStateException("Refusing unknown/modified Minecraft 1.8.9 jar: expected "+PINNED_SHA1+", found "+hash);
		Path catalog=Paths.get(required(args,"scenarios"),"pilot-scenarios.json");List<Scenario> scenarios=ScenarioCatalog.read(catalog,args.get("entity"),args.get("scenario"));
		for(Scenario scenario:scenarios){Trace trace=capture(scenario,hash);Path target=out.resolve(scenario.family()).resolve(scenario.id()+".json");TraceJson.write(target,trace);System.out.println("captured "+target+" ("+trace.passes.get(0).quads.size()+" base quads)");}
	}
	private static Trace capture(Scenario s,String hash)throws Exception{
		ACTIVE.set(s);
		LegacyBridge.activate(s);
		Trace trace=new Trace();trace.minecraftVersion=VERSION;trace.jarSha1=hash;trace.scenarioId=s.id();
		String family=s.family();
		String modelName=("ocelot".equals(family)||"cat".equals(family))?"bbp"
			:("wolf".equals(family)||"dog".equals(family))?"bcm":"horse".equals(family)?"bbh"
			:"chicken".equals(family)?"bba":"cow".equals(family)?"bbb":"pig".equals(family)?"bbq":"bat".equals(family)?"bav":"bbu";
		String entityName=("ocelot".equals(family)||"cat".equals(family))?"ts"
			:("wolf".equals(family)||"dog".equals(family))?"ua":"horse".equals(family)?"tp"
			:"chicken".equals(family)?"tn":"cow".equals(family)?"to":"pig".equals(family)?"tt":"bat".equals(family)?"tk":"tu";
		Object model=Class.forName(modelName).newInstance(),entity=allocate(Class.forName(entityName));
		float limb=s.number("limbPhase"),speed=s.number("limbSpeed"),age=s.number("age"),yaw=s.number("headYaw"),pitch=s.number("headPitch"),swing=s.number("swingProgress");boolean riding=s.bool("riding"),baby=s.bool("baby");s.number("growth");
		setHierarchy(model,"p",swing);setHierarchy(model,"q",riding);setHierarchy(model,"r",baby);
		if("horse".equals(family)){setHierarchy(entity,"W",(int)age);setHierarchy(entity,"bm",s.bool("tailMoving")?1:0);if(s.bool("ridden"))setHierarchy(entity,"l",entity);}
		invokeLiving(model,entity,limb,speed,0);
		float renderAge=("wolf".equals(family)||"dog".equals(family))?s.number("tailAngle"):age;
		if("chicken".equals(family))renderAge=((float)Math.sin(s.number("flap"))+1f)*s.number("flapSpeed");
		addPass(trace,"base",0,false,model,entity,limb,speed,renderAge,yaw,pitch);
		if("wolf".equals(family)||"dog".equals(family)){s.bool("wet");addPass(trace,"collar",1,false,model,entity,limb,speed,s.number("tailAngle"),yaw,pitch);}
		if("horse".equals(family)){boolean saddle=s.bool("saddled");s.bool("chested");if(saddle)addPass(trace,"armor",2,false,model,entity,limb,speed,age,yaw,pitch);}
		if("pig".equals(family)&&s.bool("saddled")){java.lang.reflect.Constructor<?> constructor=Class.forName("bbq").getConstructor(float.class);Object saddleModel=constructor.newInstance(.5f);setHierarchy(saddleModel,"p",swing);setHierarchy(saddleModel,"q",riding);setHierarchy(saddleModel,"r",baby);addPass(trace,"saddle",1,false,saddleModel,entity,limb,speed,age,yaw,pitch);}
		if("ocelot".equals(family)||"cat".equals(family))s.string("posture");
		s.consumeIdentity();s.assertAllConsumed();ACTIVE.remove();return trace;
	}
	private static void addPass(Trace trace,String id,int ordinal,boolean ordered,Object model,Object entity,float limb,float speed,float age,float yaw,float pitch)throws Exception{
		int width="bbh".equals(model.getClass().getName())?128:64,height="bbh".equals(model.getClass().getName())?128:"bav".equals(model.getClass().getName())?64:32;Trace.Pass pass=new Trace.Pass(id,ordinal,ordered,width,height);trace.passes.add(pass);LegacyBridge.begin(activeScenario(),pass);
		if("bat".equals(activeScenario().family())){boolean resting=activeScenario().bool("resting");float y=resting?-.1f:legacyCos(activeScenario().number("age")*.3f)*.1f;LegacyBridge.translate(0,y,0);LegacyBridge.scale(.35f,.35f,.35f);}
		Method render=findRender(model.getClass());
		try{render.invoke(model,entity,limb,speed,age,yaw,pitch,1f/16f);}catch(java.lang.reflect.InvocationTargetException e){throw new IllegalStateException("legacy render failed in "+traceLabel(id),e.getCause());}
		LegacyBridge.finish();
	}
	private static float legacyCos(float value)throws Exception{return ((Float)Class.forName("ns").getMethod("b",float.class).invoke(null,value)).floatValue();}
	private static String traceLabel(String pass){return activeScenario().id()+"/"+pass;}
	private static final ThreadLocal<Scenario> ACTIVE=new ThreadLocal<Scenario>();private static Scenario activeScenario(){Scenario s=ACTIVE.get();if(s==null)throw new IllegalStateException("no active scenario");return s;}
	private static void invokeLiving(Object model,Object entity,float limb,float speed,float partial)throws Exception{Method m=null;for(Method x:model.getClass().getMethods())if("a".equals(x.getName())&&x.getParameterTypes().length==4&&!x.getParameterTypes()[0].isPrimitive()){m=x;break;}if(m!=null)m.invoke(model,entity,limb,speed,partial);}
	private static Method findRender(Class<?> c){for(Method m:c.getMethods())if("a".equals(m.getName())&&m.getParameterTypes().length==7&&!m.getParameterTypes()[0].isPrimitive())return m;throw new IllegalStateException("missing render method on "+c.getName());}
	private static void setHierarchy(Object value,String name,Object x){for(Class<?> c=value.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);if(f.getType()==float.class)f.setFloat(value,((Number)x).floatValue());else if(f.getType()==int.class)f.setInt(value,((Number)x).intValue());else if(f.getType()==boolean.class)f.setBoolean(value,(Boolean)x);else f.set(value,x);return;}catch(NoSuchFieldException ignored){}catch(Exception e){throw new IllegalStateException(e);}throw new IllegalStateException("missing field "+name+" on "+value.getClass());}
	private static Object allocate(Class<?> type)throws Exception{Class<?> unsafe=Class.forName("sun.misc.Unsafe");Field f=unsafe.getDeclaredField("theUnsafe");f.setAccessible(true);Object instance=f.get(null);return unsafe.getMethod("allocateInstance",Class.class).invoke(instance,type);}
	private static String sha1(Path path)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-1");InputStream in=Files.newInputStream(path);byte[] b=new byte[65536];for(int n;(n=in.read(b))>=0;)d.update(b,0,n);in.close();StringBuilder s=new StringBuilder();for(byte x:d.digest())s.append(String.format("%02x",x&255));return s.toString();}
	private static Map<String,String> args(String[] values){Map<String,String> m=new LinkedHashMap<String,String>();for(int i=0;i<values.length;i+=2){if(i+1==values.length||!values[i].startsWith("--"))throw new IllegalArgumentException("expected --key value arguments");m.put(values[i].substring(2),values[i+1]);}return m;}private static String required(Map<String,String> m,String k){String v=m.get(k);if(v==null)throw new IllegalArgumentException("missing --"+k);return v;}
}
