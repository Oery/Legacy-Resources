package dev.oery.legacyresources.oracle.legacy;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

final class LegacyTransformer implements ClassFileTransformer {
	private static final String BRIDGE="dev/oery/legacyresources/oracle/legacy/LegacyBridge";
	@Override public byte[] transform(ClassLoader loader,String name,Class<?> type,ProtectionDomain domain,byte[] bytes) {
		if(!"bct".equals(name)&&!"bfl".equals(name)&&!"pk".equals(name)&&!"qa".equals(name)&&!"ts".equals(name)&&!"ua".equals(name)&&!"tp".equals(name)&&!"tu".equals(name)&&!"tk".equals(name))return null;
		ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
		for(MethodNode method:node.methods){
			if("bct".equals(name)&&"(F)V".equals(method.desc)){
				String bridge="a".equals(method.name)?"render":"b".equals(method.name)?"renderWithRotation":"c".equals(method.name)?"postRender":null;
				if(bridge!=null){replace(method,bridge,"(Ljava/lang/Object;F)V",new VarInsnNode(Opcodes.ALOAD,0),new VarInsnNode(Opcodes.FLOAD,1));changed=true;}
			}
			if("bfl".equals(name)){
				if("E".equals(method.name)&&"()V".equals(method.desc)){replace(method,"push","()V");changed=true;}
				else if("F".equals(method.name)&&"()V".equals(method.desc)){replace(method,"pop","()V");changed=true;}
				else if("a".equals(method.name)&&"(FFF)V".equals(method.desc)){replace(method,"scale","(FFF)V",loads3());changed=true;}
				else if("b".equals(method.name)&&"(FFF)V".equals(method.desc)){replace(method,"translate","(FFF)V",loads3());changed=true;}
				else if("b".equals(method.name)&&"(FFFF)V".equals(method.desc)){replace(method,"rotate","(FFFF)V",new VarInsnNode(Opcodes.FLOAD,0),new VarInsnNode(Opcodes.FLOAD,1),new VarInsnNode(Opcodes.FLOAD,2),new VarInsnNode(Opcodes.FLOAD,3));changed=true;}
			}
			if("ts".equals(name)&&"()Z".equals(method.desc)){
				if("av".equals(method.name)){replace(method,"ocelotSneaking","()Z");changed=true;}
				else if("aw".equals(method.name)){replace(method,"ocelotSprinting","()Z");changed=true;}
				else if("cn".equals(method.name)){replace(method,"ocelotSitting","()Z");changed=true;}
			}
			if("pk".equals(name)&&"()Z".equals(method.desc)){
				if("av".equals(method.name)){replace(method,"ocelotSneaking","()Z");changed=true;}
				else if("aw".equals(method.name)){replace(method,"ocelotSprinting","()Z");changed=true;}
			}
			if("qa".equals(name)&&"cn".equals(method.name)&&"()Z".equals(method.desc)){replace(method,"animalSitting","()Z");changed=true;}
			if("ua".equals(name)){
				if("cv".equals(method.name)&&"()Z".equals(method.desc)){replace(method,"wolfAngry","()Z");changed=true;}
				else if("cn".equals(method.name)&&"()Z".equals(method.desc)){replace(method,"wolfSitting","()Z");changed=true;}
				else if("q".equals(method.name)&&"(F)F".equals(method.desc)){replace(method,"wolfInterested","(F)F",new VarInsnNode(Opcodes.FLOAD,1));changed=true;}
				else if("i".equals(method.name)&&"(FF)F".equals(method.desc)){replace(method,"wolfShake","(FF)F",new VarInsnNode(Opcodes.FLOAD,1),new VarInsnNode(Opcodes.FLOAD,2));changed=true;}
			}
			if("tp".equals(name)){
				if("cl".equals(method.name)&&"()I".equals(method.desc)){replace(method,"horseType","()I");changed=true;}
				else if("p".equals(method.name)&&"(F)F".equals(method.desc)){replace(method,"horseEating","(F)F",new VarInsnNode(Opcodes.FLOAD,1));changed=true;}
				else if("q".equals(method.name)&&"(F)F".equals(method.desc)){replace(method,"horseRearing","(F)F",new VarInsnNode(Opcodes.FLOAD,1));changed=true;}
				else if("r".equals(method.name)&&"(F)F".equals(method.desc)){replace(method,"horseMouth","(F)F",new VarInsnNode(Opcodes.FLOAD,1));changed=true;}
				else if("cG".equals(method.name)&&"()Z".equals(method.desc)){replace(method,"horseSaddled","()Z");changed=true;}
				else if("cw".equals(method.name)&&"()Z".equals(method.desc)){replace(method,"horseChested","()Z");changed=true;}
				else if("cu".equals(method.name)&&"()F".equals(method.desc)){replace(method,"horseGrowth","()F");changed=true;}
				else if("cn".equals(method.name)&&"()Z".equals(method.desc)){replace(method,"horseAdult","()Z");changed=true;}
			}
			if("tu".equals(name)&&"p".equals(method.name)&&"(F)F".equals(method.desc)){replace(method,"rabbitJump","(F)F",new VarInsnNode(Opcodes.FLOAD,1));changed=true;}
			if("tk".equals(name)&&"n".equals(method.name)&&"()Z".equals(method.desc)){replace(method,"batResting","()Z");changed=true;}
		}
		if(!changed)return null;ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
	}
	private static org.objectweb.asm.tree.AbstractInsnNode[] loads3(){return new org.objectweb.asm.tree.AbstractInsnNode[]{new VarInsnNode(Opcodes.FLOAD,0),new VarInsnNode(Opcodes.FLOAD,1),new VarInsnNode(Opcodes.FLOAD,2)};}
	private static void replace(MethodNode method,String name,String descriptor,org.objectweb.asm.tree.AbstractInsnNode... loads){InsnList code=new InsnList();for(org.objectweb.asm.tree.AbstractInsnNode load:loads)code.add(load);code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,name,descriptor,false));code.add(new InsnNode(Type.getReturnType(method.desc).getOpcode(Opcodes.IRETURN)));method.instructions.clear();method.tryCatchBlocks.clear();method.localVariables=null;method.instructions.add(code);}
}
