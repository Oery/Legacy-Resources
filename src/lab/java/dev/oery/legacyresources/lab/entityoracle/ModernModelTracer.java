package dev.oery.legacyresources.lab.entityoracle;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.oery.legacyresources.client.convert.LegacyOcelotModel;
import dev.oery.legacyresources.client.entity.ClassicHorseModel;
import dev.oery.legacyresources.client.entity.LegacyEntityPlanLabAccess;
import dev.oery.legacyresources.client.entity.LegacyPilotModelLabAccess;
import dev.oery.legacyresources.oracle.Scenario;
import dev.oery.legacyresources.oracle.Trace;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.DonkeyRenderState;
import net.minecraft.client.renderer.entity.state.ChickenRenderState;
import net.minecraft.client.renderer.entity.state.CowRenderState;
import net.minecraft.client.renderer.entity.state.EquineRenderState;
import net.minecraft.client.renderer.entity.state.FelineRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PigRenderState;
import net.minecraft.client.renderer.entity.state.RabbitRenderState;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Traces the production model classes and their real setupAnim/ModelPart traversal. */
final class ModernModelTracer {
	private ModernModelTracer() { }
	static Trace capture(Scenario s) {
		Trace trace=new Trace();trace.minecraftVersion="26.2";trace.jarSha1="candidate";trace.scenarioId=s.id();
		Model<?> model;LivingEntityRenderState state;int width=64,height=32;boolean pigSaddle=false;
		String family=s.family();
		if("ocelot".equals(family)||"cat".equals(family)){FelineRenderState f="cat".equals(family)?new net.minecraft.client.renderer.entity.state.CatRenderState():new FelineRenderState();common(s,f);String posture=s.string("posture");f.isCrouching="sneaking".equals(posture);f.isSprinting="sprinting".equals(posture);f.isSitting="sitting".equals(posture);state=f;model="cat".equals(family)?LegacyPilotModelLabAccess.cat():new LegacyOcelotModel(LegacyOcelotModel.createBodyLayer().bakeRoot());}
		else if("wolf".equals(family)||"dog".equals(family)){WolfRenderState w=new WolfRenderState();common(s,w);w.isSitting="sitting".equals(s.string("posture"));w.isAngry=s.bool("angry");w.wetShade=s.bool("wet")?0.6f:1;w.shakeAnim=s.bool("shaking")?s.number("age")*.8f:0;w.headRollAngle=s.bool("interested")?.4f:0;w.tailAngle=s.number("tailAngle");state=w;model=LegacyPilotModelLabAccess.wolf(w.isBaby);}
		else if("chicken".equals(family)){ChickenRenderState c=new ChickenRenderState();common(s,c);c.flap=s.number("flap");c.flapSpeed=s.number("flapSpeed");state=c;model=LegacyPilotModelLabAccess.chicken(c.isBaby);}
		else if("cow".equals(family)){CowRenderState c=new CowRenderState();common(s,c);state=c;model=LegacyPilotModelLabAccess.cow(c.isBaby);}
		else if("pig".equals(family)){PigRenderState p=new PigRenderState();common(s,p);pigSaddle=s.bool("saddled");state=p;model=LegacyPilotModelLabAccess.pig(p.isBaby,0);}
		else if("rabbit".equals(family)){RabbitRenderState r=new RabbitRenderState();common(s,r);r.jumpCompletion=s.number("jump");state=r;model=LegacyPilotModelLabAccess.rabbit(r.isBaby);}
		else if("bat".equals(family)){net.minecraft.client.renderer.entity.state.BatRenderState b=new net.minecraft.client.renderer.entity.state.BatRenderState();common(s,b);b.isResting=s.bool("resting");state=b;model=LegacyPilotModelLabAccess.bat();height=64;}
		else {String type=s.string("type");boolean mule="donkey".equals(type)||"mule".equals(type);EquineRenderState e=mule?new DonkeyRenderState():new EquineRenderState();common(s,e);e.eatAnimation=s.number("eating");e.standAnimation=s.number("rearing");e.feedingAnimation=s.number("mouthOpen");boolean saddled=s.bool("saddled");e.saddle=saddled?nonEmptySaddle():ItemStack.EMPTY;e.isRidden=s.bool("ridden");e.animateTail=s.bool("tailMoving");if(e instanceof DonkeyRenderState d)d.hasChest=s.bool("chested");else s.bool("chested");state=e;model=new ClassicHorseModel(ClassicHorseModel.createLayer().bakeRoot(),mule,true);width=128;height=128;}
		setup(model,state);PoseStack poses=new PoseStack();
		LegacyEntityPlanLabAccess.applyOuter(s.family(),s.bool("baby"),poses);
		add(trace,"base",0,false,width,height,model.root(),poses);
		if("wolf".equals(family)||"dog".equals(family))add(trace,"collar",1,false,width,height,model.root(),poses);
		if(pigSaddle){Model<?> saddle=LegacyPilotModelLabAccess.pig(state.isBaby,.5f);setup(saddle,state);add(trace,"saddle",1,false,width,height,saddle.root(),poses);}
		if("horse".equals(s.family())&&((EquineRenderState)state).saddle!=ItemStack.EMPTY)add(trace,"armor",2,false,width,height,model.root(),poses);
		s.consumeIdentity();s.assertAllConsumed();return trace;
	}
	private static void common(Scenario s,LivingEntityRenderState state){state.isBaby=s.bool("baby");state.ageScale=s.number("growth");state.walkAnimationPos=s.number("limbPhase");state.walkAnimationSpeed=s.number("limbSpeed");state.ageInTicks=s.number("age");state.yRot=s.number("headYaw");state.xRot=s.number("headPitch");s.number("swingProgress");s.bool("riding");}
	@SuppressWarnings({"rawtypes","unchecked"})private static void setup(Model model,LivingEntityRenderState state){model.setupAnim(state);}
	private static ItemStack nonEmptySaddle(){try{Class<?> unsafeClass=Class.forName("sun.misc.Unsafe");java.lang.reflect.Field singleton=unsafeClass.getDeclaredField("theUnsafe");singleton.setAccessible(true);Object unsafe=singleton.get(null);ItemStack stack=(ItemStack)unsafeClass.getMethod("allocateInstance",Class.class).invoke(unsafe,ItemStack.class);java.lang.reflect.Field item=ItemStack.class.getDeclaredField("item"),count=ItemStack.class.getDeclaredField("count");item.setAccessible(true);count.setAccessible(true);item.set(stack,Holder.direct(Items.SADDLE));count.setInt(stack,1);return stack;}catch(Exception e){throw new IllegalStateException("Could not create development-only saddle marker",e);}}
	private static void add(Trace trace,String id,int ordinal,boolean ordered,int width,int height,ModelPart root,PoseStack input){Trace.Pass out=new Trace.Pass(id,ordinal,ordered,width,height);trace.passes.add(out);PoseStack poses=new PoseStack();poses.mulPose(input.last().pose());final int[] draw={0};root.visit(poses,(pose,path,index,cube)->{for(ModelPart.Polygon polygon:cube.polygons){Trace.Quad q=new Trace.Quad();q.drawOrdinal=draw[0]++;q.modernPart=path+"#"+index;ModelPart.Vertex[] vertices=polygon.vertices();for(int i=0;i<4;i++){ModelPart.Vertex v=vertices[i];Vector4f p=pose.pose().transform(new Vector4f(v.worldX(),v.worldY(),v.worldZ(),1));q.positions[i]=new double[]{p.x,p.y,p.z};q.uv[i]=new double[]{v.u(),v.v()};}Vector3f n=pose.transformNormal(polygon.normal(),new Vector3f()).normalize();q.normal=new double[]{n.x,n.y,n.z};q.winding="ccw";out.quads.add(q);}});}
}
