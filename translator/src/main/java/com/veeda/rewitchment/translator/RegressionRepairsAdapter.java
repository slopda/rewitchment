package com.veeda.rewitchment.translator;

import java.util.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

final class RegressionRepairsAdapter implements Opcodes {
    static final String CAULDRON="moriyashiine/bewitchment/common/packet/CauldronTeleportPacket$Receiver";
    static final String SIGIL="moriyashiine/bewitchment/common/block/entity/interfaces/SigilHolder";
    private RegressionRepairsAdapter() {}
    static Map<String,Integer> rewrite(ClassNode node) {
        if(node.name.equals(CAULDRON))return cauldron(node)==0?Map.of():Map.of("cauldron_nearest_destination",1);
        if(node.name.equals(SIGIL))return sigil(node)==0?Map.of():Map.of("sigil_rule_nbt",1);
        return Map.of();
    }
    private static int cauldron(ClassNode node) {
        var method=node.methods.stream().filter(m->m.name.equals("lambda$receive$0") && m.desc.equals("(Lnet/minecraft/class_3222;Ljava/lang/String;Lnet/minecraft/class_2338;)V")).findFirst().orElseThrow(()->new IllegalStateException("Missing cauldron selection method"));
        var positions=new ArrayList<VarInsnNode>();
        for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/class_2338") && call.name.equals("method_19770")) {
            if(call.getOpcode()!=INVOKEVIRTUAL || !call.desc.equals("(Lnet/minecraft/class_2374;)D"))throw new IllegalStateException("Unknown cauldron distance call");
            var getter=GameplayCompletionAdapter.previous(call);
            var player=getter==null?null:GameplayCompletionAdapter.previous(getter);
            var position=player==null?null:GameplayCompletionAdapter.previous(player);
            if(!(getter instanceof MethodInsnNode get && get.name.equals("method_19538") && get.desc.equals("()Lnet/minecraft/class_243;")) ||
               !(player instanceof VarInsnNode p && p.getOpcode()==ALOAD && p.var==0) || !(position instanceof VarInsnNode v && v.getOpcode()==ALOAD))throw new IllegalStateException("Unknown cauldron distance operands");
            positions.add((VarInsnNode)position);
        }
        if(positions.size()!=3 || positions.get(1).var!=4 || positions.get(2).var!=4 || (positions.getFirst().var!=2 && positions.getFirst().var!=8))throw new IllegalStateException("Unknown cauldron selection locals");
        if(positions.getFirst().var==8)return 0;
        boolean candidate=Arrays.stream(method.instructions.toArray()).anyMatch(i->i instanceof VarInsnNode v && v.getOpcode()==ASTORE && v.var==8);
        if(!candidate)throw new IllegalStateException("Missing candidate destination local");
        positions.getFirst().var=8;return 1;
    }
    private static int sigil(ClassNode node) {
        var method=node.methods.stream().filter(m->m.name.equals("fromNbtSigil") && m.desc.equals("(Lnet/minecraft/class_2487;)V")).findFirst().orElseThrow(()->new IllegalStateException("Missing sigil reader"));
        var types=new ArrayList<IntInsnNode>();int clears=0;
        for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call) {
            if(call.owner.equals("java/util/List") && call.name.equals("clear") && call.desc.equals("()V"))clears++;
            if(call.owner.equals("net/minecraft/class_2487") && call.name.equals("method_10554")) {
                var type=GameplayCompletionAdapter.previous(call);
                var key=type==null?null:GameplayCompletionAdapter.previous(type);
                if(call.getOpcode()!=INVOKEVIRTUAL || !call.desc.equals("(Ljava/lang/String;I)Lnet/minecraft/class_2499;") ||
                   !(type instanceof IntInsnNode n && n.getOpcode()==BIPUSH) || !(key instanceof LdcInsnNode k && "Entities".equals(k.cst)))throw new IllegalStateException("Unknown sigil UUID list read");
                types.add((IntInsnNode)type);
            }
        }
        if(types.size()!=1)throw new IllegalStateException("Expected one sigil UUID list read");
        if(types.getFirst().operand==8 && clears==1)return 0;
        if(types.getFirst().operand!=10 || clears!=0)throw new IllegalStateException("Unknown or partially repaired sigil reader");
        types.getFirst().operand=8;
        var clear=new InsnList();clear.add(new VarInsnNode(ALOAD,0));clear.add(new MethodInsnNode(INVOKEINTERFACE,SIGIL,"getEntities","()Ljava/util/List;",true));clear.add(new MethodInsnNode(INVOKEINTERFACE,"java/util/List","clear","()V",true));
        method.instructions.insert(clear);return 1;
    }
}
