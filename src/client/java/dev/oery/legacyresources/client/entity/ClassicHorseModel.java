package dev.oery.legacyresources.client.entity;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.EquineRenderState;
import net.minecraft.util.Mth;

/**
 * A direct port of 1.8.9's {@code ModelHorse}.  Unlike the old layer replacement this preserves
 * the model's separate knees, hooves, mouth, ears and tail joints, so its classic animation can
 * drive the texture's 128x128 UV layout without collapsing parts into rigid boxes.
 */
public final class ClassicHorseModel extends EntityModel<EquineRenderState> {
	private final boolean mule;
	private final boolean accessories;
	private final ModelPart body;
	private final ModelPart tailBase, tailMiddle, tailTip;
	private final ModelPart head, upperMouth, lowerMouth, horseLeftEar, horseRightEar, muleLeftEar, muleRightEar, neck, mane;
	private final ModelPart backLeftLeg, backLeftShin, backLeftHoof, backRightLeg, backRightShin, backRightHoof;
	private final ModelPart frontLeftLeg, frontLeftShin, frontLeftHoof, frontRightLeg, frontRightShin, frontRightHoof;
	private final ModelPart muleLeftChest, muleRightChest;
	private final ModelPart saddleBottom, saddleFront, saddleBack, leftSaddleRope, leftSaddleMetal, rightSaddleRope, rightSaddleMetal;
	private final ModelPart faceRopes, leftFaceMetal, rightFaceMetal, leftRein, rightRein;

	public ClassicHorseModel(ModelPart root, boolean mule, boolean accessories) {
		super(root);
		this.mule = mule;
		this.accessories = accessories;
		this.body = root.getChild("body");
		this.tailBase = root.getChild("tail_base"); this.tailMiddle = root.getChild("tail_middle"); this.tailTip = root.getChild("tail_tip");
		this.head = root.getChild("head"); this.upperMouth = root.getChild("upper_mouth"); this.lowerMouth = root.getChild("lower_mouth");
		this.horseLeftEar = root.getChild("horse_left_ear"); this.horseRightEar = root.getChild("horse_right_ear");
		this.muleLeftEar = root.getChild("mule_left_ear"); this.muleRightEar = root.getChild("mule_right_ear");
		this.neck = root.getChild("neck"); this.mane = root.getChild("mane");
		this.backLeftLeg = root.getChild("back_left_leg"); this.backLeftShin = root.getChild("back_left_shin"); this.backLeftHoof = root.getChild("back_left_hoof");
		this.backRightLeg = root.getChild("back_right_leg"); this.backRightShin = root.getChild("back_right_shin"); this.backRightHoof = root.getChild("back_right_hoof");
		this.frontLeftLeg = root.getChild("front_left_leg"); this.frontLeftShin = root.getChild("front_left_shin"); this.frontLeftHoof = root.getChild("front_left_hoof");
		this.frontRightLeg = root.getChild("front_right_leg"); this.frontRightShin = root.getChild("front_right_shin"); this.frontRightHoof = root.getChild("front_right_hoof");
		this.muleLeftChest = root.getChild("mule_left_chest"); this.muleRightChest = root.getChild("mule_right_chest");
		this.saddleBottom = root.getChild("saddle_bottom"); this.saddleFront = root.getChild("saddle_front"); this.saddleBack = root.getChild("saddle_back");
		this.leftSaddleRope = root.getChild("left_saddle_rope"); this.leftSaddleMetal = root.getChild("left_saddle_metal");
		this.rightSaddleRope = root.getChild("right_saddle_rope"); this.rightSaddleMetal = root.getChild("right_saddle_metal");
		this.faceRopes = root.getChild("face_ropes"); this.leftFaceMetal = root.getChild("left_face_metal"); this.rightFaceMetal = root.getChild("right_face_metal");
		this.leftRein = root.getChild("left_rein"); this.rightRein = root.getChild("right_rein");
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition(); PartDefinition r = mesh.getRoot();
		r.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0,34).addBox(-5,-8,-19,10,10,24), PartPose.offset(0,11,9));
		r.addOrReplaceChild("tail_base", CubeListBuilder.create().texOffs(44,0).addBox(-1,-1,0,2,2,3), PartPose.offsetAndRotation(0,3,14,-1.134464f,0,0));
		r.addOrReplaceChild("tail_middle", CubeListBuilder.create().texOffs(38,7).addBox(-1.5f,-2,3,3,4,7), PartPose.offsetAndRotation(0,3,14,-1.134464f,0,0));
		r.addOrReplaceChild("tail_tip", CubeListBuilder.create().texOffs(24,3).addBox(-1.5f,-4.5f,9,3,4,7), PartPose.offsetAndRotation(0,3,14,-1.40215f,0,0));
		PartDefinition head=r.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0,0).addBox(-2.5f,-10,-1.5f,5,5,7), PartPose.offsetAndRotation(0,4,-10,.5235988f,0,0));
		// These boxes share the head's animated frame.  Keeping them as siblings is necessary
		// for ModelPart's pose system; the old ModelRenderer child API stored its transform
		// differently.  setupAnim below copies the shared head pose before opening the jaw.
		r.addOrReplaceChild("upper_mouth", CubeListBuilder.create().texOffs(24,18).addBox(-2,-10,-7,4,3,6), PartPose.offsetAndRotation(0,4,-10,.5235988f,0,0));
		r.addOrReplaceChild("lower_mouth", CubeListBuilder.create().texOffs(24,27).addBox(-2,-7,-6.5f,4,2,5), PartPose.offsetAndRotation(0,4,-10,.5235988f,0,0));
		r.addOrReplaceChild("horse_left_ear", CubeListBuilder.create().texOffs(0,0).addBox(.45f,-12,4,2,3,1), PartPose.offsetAndRotation(0,4,-10,.5235988f,0,0));
		r.addOrReplaceChild("horse_right_ear", CubeListBuilder.create().texOffs(0,0).addBox(-2.45f,-12,4,2,3,1), PartPose.offsetAndRotation(0,4,-10,.5235988f,0,0));
		r.addOrReplaceChild("mule_left_ear", CubeListBuilder.create().texOffs(0,12).addBox(-2,-16,4,2,7,1), PartPose.offsetAndRotation(0,4,-10,.5235988f,0,.2617994f));
		r.addOrReplaceChild("mule_right_ear", CubeListBuilder.create().texOffs(0,12).addBox(0,-16,4,2,7,1), PartPose.offsetAndRotation(0,4,-10,.5235988f,0,-.2617994f));
		r.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0,12).addBox(-2.05f,-9.8f,-2,4,14,8), PartPose.offsetAndRotation(0,4,-10,.5235988f,0,0));
		r.addOrReplaceChild("mane", CubeListBuilder.create().texOffs(58,0).addBox(-1,-11.5f,5,2,16,4), PartPose.offsetAndRotation(0,4,-10,.5235988f,0,0));
		leg(r,"back_left_leg",78,29,-2.5f,-2,-2.5f,4,9,5,4,9,11); leg(r,"back_right_leg",96,29,-1.5f,-2,-2.5f,4,9,5,-4,9,11);
		leg(r,"front_left_leg",44,29,-1.9f,-1,-2.1f,3,8,4,4,9,-8); leg(r,"front_right_leg",60,29,-1.1f,-1,-2.1f,3,8,4,-4,9,-8);
		shin(r,"back_left_shin",78,43,-2,0,-1.5f,3,5,3,4,16,11); hoof(r,"back_left_hoof",78,51,-2.5f,5.1f,-2,4,3,4,4,16,11);
		shin(r,"back_right_shin",96,43,-1,0,-1.5f,3,5,3,-4,16,11); hoof(r,"back_right_hoof",96,51,-1.5f,5.1f,-2,4,3,4,-4,16,11);
		shin(r,"front_left_shin",44,41,-1.9f,0,-1.6f,3,5,3,4,16,-8); hoof(r,"front_left_hoof",44,51,-2.4f,5.1f,-2.1f,4,3,4,4,16,-8);
		shin(r,"front_right_shin",60,41,-1.1f,0,-1.6f,3,5,3,-4,16,-8); hoof(r,"front_right_hoof",60,51,-1.6f,5.1f,-2.1f,4,3,4,-4,16,-8);
		r.addOrReplaceChild("mule_left_chest", CubeListBuilder.create().texOffs(0,34).addBox(-3,0,0,8,8,3), PartPose.offsetAndRotation(-7.5f,3,10,0,1.5707964f,0));
		r.addOrReplaceChild("mule_right_chest", CubeListBuilder.create().texOffs(0,47).addBox(-3,0,0,8,8,3), PartPose.offsetAndRotation(4.5f,3,10,0,1.5707964f,0));
		r.addOrReplaceChild("saddle_bottom", CubeListBuilder.create().texOffs(80,0).addBox(-5,0,-3,10,1,8), PartPose.offset(0,2,2));
		r.addOrReplaceChild("saddle_front", CubeListBuilder.create().texOffs(106,9).addBox(-1.5f,-1,-3,3,1,2), PartPose.offset(0,2,2));
		r.addOrReplaceChild("saddle_back", CubeListBuilder.create().texOffs(80,9).addBox(-4,-1,3,8,1,2), PartPose.offset(0,2,2));
		part(r,"left_saddle_metal",74,0,-.5f,6,-1,1,2,2,5,3,2); part(r,"left_saddle_rope",70,0,-.5f,0,-.5f,1,6,1,5,3,2);
		part(r,"right_saddle_metal",74,4,-.5f,6,-1,1,2,2,-5,3,2); part(r,"right_saddle_rope",80,0,-.5f,0,-.5f,1,6,1,-5,3,2);
		r.addOrReplaceChild("face_ropes", CubeListBuilder.create().texOffs(80,12).addBox(-2.5f,-10.1f,-7,5,5,12,new CubeDeformation(.2f)), PartPose.offsetAndRotation(0,4,-10,.5235988f,0,0));
		partRot(r,"left_face_metal",74,13,1.5f,-8,-4,1,2,2,0,4,-10,.5235988f); partRot(r,"right_face_metal",74,13,-2.5f,-8,-4,1,2,2,0,4,-10,.5235988f);
		partRot(r,"left_rein",44,10,2.6f,-6,-6,0,3,16,0,4,-10,.5235988f); partRot(r,"right_rein",44,5,-2.6f,-6,-6,0,3,16,0,4,-10,.5235988f);
		return LayerDefinition.create(mesh,128,128);
	}

	private static void leg(PartDefinition r,String n,int u,int v,float x,float y,float z,float w,float h,float d,float px,float py,float pz){part(r,n,u,v,x,y,z,w,h,d,px,py,pz);} private static void shin(PartDefinition r,String n,int u,int v,float x,float y,float z,float w,float h,float d,float px,float py,float pz){part(r,n,u,v,x,y,z,w,h,d,px,py,pz);} private static void hoof(PartDefinition r,String n,int u,int v,float x,float y,float z,float w,float h,float d,float px,float py,float pz){part(r,n,u,v,x,y,z,w,h,d,px,py,pz);} private static void part(PartDefinition r,String n,int u,int v,float x,float y,float z,float w,float h,float d,float px,float py,float pz){r.addOrReplaceChild(n,CubeListBuilder.create().texOffs(u,v).addBox(x,y,z,w,h,d),PartPose.offset(px,py,pz));} private static void partRot(PartDefinition r,String n,int u,int v,float x,float y,float z,float w,float h,float d,float px,float py,float pz,float rx){r.addOrReplaceChild(n,CubeListBuilder.create().texOffs(u,v).addBox(x,y,z,w,h,d),PartPose.offsetAndRotation(px,py,pz,rx,0,0));}

	@Override public void setupAnim(EquineRenderState s) {
		super.setupAnim(s); float walk=s.walkAnimationSpeed, phase=s.walkAnimationPos, rear=s.standAnimation, eat=s.eatAnimation, rest=1-rear, age=s.ageInTicks;
		float pitch=s.xRot*((float)Math.PI/180); if(walk>.2f)pitch+=Mth.cos(phase*.4f)*.15f*walk; float yaw=Mth.clamp(s.yRot,-20,20)*((float)Math.PI/180);
		head.xRot=rear*(.2617994f+pitch)+eat*(2.18166f+Mth.sin(age)*.05f)+(1-Math.max(rear,eat))*(.5235988f+pitch); head.yRot=rear*yaw+(1-Math.max(rear,eat))*yaw; head.y=rear*-6+eat*11+(1-Math.max(rear,eat))*4; head.z=rear*-1+eat*-10+(1-Math.max(rear,eat))*-10;
		body.xRot=rear*-.7853982f; float wave=Mth.cos(phase*.6662f+(float)Math.PI), walkRot=wave*.8f*walk, stand=.2617994f*rear, bob=Mth.cos(age*.6f+(float)Math.PI);
		frontLeftLeg.y=frontRightLeg.y=-2*rear+9*rest; frontLeftLeg.z=frontRightLeg.z=-2*rear-8*rest;
		backLeftLeg.xRot=stand-wave*.5f*walk*rest; backRightLeg.xRot=stand+wave*.5f*walk*rest; float fl=(-1.0471976f+bob)*rear+walkRot*rest, fr=(-1.0471976f-bob)*rear-walkRot*rest; frontLeftLeg.xRot=fl; frontRightLeg.xRot=fr;
		setLower(backLeftLeg,backLeftShin,backLeftHoof,-.08726646f*rear+(-wave*.5f*walk-Math.max(0,wave*.5f*walk))*rest); setLower(backRightLeg,backRightShin,backRightHoof,-.08726646f*rear+(wave*.5f*walk-Math.max(0,-wave*.5f*walk))*rest); setLower(frontLeftLeg,frontLeftShin,frontLeftHoof,(fl+(float)Math.PI*Math.max(0,.2f+bob*.2f))*rear+(walkRot+Math.max(0,wave*.5f*walk))*rest); setLower(frontRightLeg,frontRightShin,frontRightHoof,(fr+(float)Math.PI*Math.max(0,.2f-bob*.2f))*rear+(-walkRot+Math.max(0,-wave*.5f*walk))*rest);
		for(ModelPart p:new ModelPart[]{horseLeftEar,horseRightEar,muleLeftEar,muleRightEar,neck,mane}){p.y=head.y;p.z=head.z;p.xRot=head.xRot;p.yRot=head.yRot;}
		upperMouth.y=lowerMouth.y=head.y; upperMouth.z=head.z+.02f-s.feedingAnimation; lowerMouth.z=head.z+s.feedingAnimation;
		upperMouth.yRot=lowerMouth.yRot=head.yRot; upperMouth.xRot=head.xRot-.09424778f*s.feedingAnimation; lowerMouth.xRot=head.xRot+.15707964f*s.feedingAnimation;
		tailBase.y=rear*9+rest*3; tailBase.xRot=Math.min(0,-1.3089f+walk*1.5f); tailBase.yRot=s.animateTail?Mth.cos(age*.7f):0; for(ModelPart p:new ModelPart[]{tailMiddle,tailTip}){p.y=tailBase.y;p.z=tailBase.z;p.yRot=tailBase.yRot;} tailMiddle.xRot=tailBase.xRot; tailTip.xRot=-.2618f+tailBase.xRot;
		muleLeftChest.xRot=walkRot/5; muleRightChest.xRot=-walkRot/5;
		boolean saddled=accessories&&!s.saddle.isEmpty();
		float saddleY=rear*.5f+rest*2, saddleZ=rear*11+rest*2;
		for(ModelPart p:new ModelPart[]{saddleBottom,saddleFront,saddleBack,leftSaddleRope,leftSaddleMetal,rightSaddleRope,rightSaddleMetal}) { p.y=saddleY; p.z=saddleZ; }
		saddleBottom.xRot=saddleFront.xRot=saddleBack.xRot=body.xRot;
		for(ModelPart p:new ModelPart[]{faceRopes,leftFaceMetal,rightFaceMetal,leftRein,rightRein}) { p.y=head.y; p.z=head.z; p.yRot=head.yRot; }
		faceRopes.xRot=leftFaceMetal.xRot=rightFaceMetal.xRot=head.xRot; leftRein.xRot=rightRein.xRot=pitch;
		float tackSwing=s.isRidden ? -1.0471976f : walkRot/3, tackRoll=s.isRidden ? 0 : walkRot/5;
		leftSaddleRope.xRot=leftSaddleMetal.xRot=rightSaddleRope.xRot=rightSaddleMetal.xRot=tackSwing;
		leftSaddleRope.zRot=leftSaddleMetal.zRot=tackRoll; rightSaddleRope.zRot=rightSaddleMetal.zRot=-tackRoll;
		for(ModelPart p:new ModelPart[]{saddleBottom,saddleFront,saddleBack,leftSaddleRope,leftSaddleMetal,rightSaddleRope,rightSaddleMetal,faceRopes,leftFaceMetal,rightFaceMetal})p.visible=saddled;
		leftRein.visible=rightRein.visible=saddled&&s.isRidden;
		boolean chested=s instanceof net.minecraft.client.renderer.entity.state.DonkeyRenderState donkey && donkey.hasChest; muleLeftChest.visible=mule&&chested; muleRightChest.visible=mule&&chested; horseLeftEar.visible=horseRightEar.visible=!mule; muleLeftEar.visible=muleRightEar.visible=mule;
	}
	private static void setLower(ModelPart upper,ModelPart lower,ModelPart hoof,float rot){lower.xRot=hoof.xRot=rot; lower.y=hoof.y=upper.y+Mth.sin(1.5707964f+upper.xRot)*7; lower.z=hoof.z=upper.z+Mth.cos(4.712389f+upper.xRot)*7;}
}
