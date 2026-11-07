package com.veeda.rewitchment.translator;

import java.util.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public final class EntityLifecycleAdapter implements Opcodes {
    static final String P = "moriyashiine/bewitchment/";
    static final String BUILDER = "net/minecraft/class_2945$class_9222";
    static final String TRACKER_NEW = "(L" + BUILDER + ";)V";
    static final String SPAWN_OLD = "(Lnet/minecraft/class_5425;Lnet/minecraft/class_1266;Lnet/minecraft/class_3730;Lnet/minecraft/class_1315;Lnet/minecraft/class_2487;)Lnet/minecraft/class_1315;";
    static final String SPAWN_NEW = SPAWN_OLD.replace("Lnet/minecraft/class_2487;", "");
    static final Set<String> TRACKERS = Set.of(P+"api/entity/BroomEntity", P+"common/entity/living/DemonEntity",
            P+"common/entity/living/GhostEntity", P+"common/entity/living/VampireEntity",
            P+"common/entity/living/util/BWHostileEntity", P+"common/entity/living/util/BWTameableEntity");
    static final Set<String> SPAWNERS = Set.of(P+"common/entity/living/DemonEntity", P+"common/entity/living/WerewolfEntity",
            P+"common/entity/living/util/BWHostileEntity", P+"common/entity/living/util/BWTameableEntity");
    public static final Map<String,Integer> EXPECTED_COUNTS = Map.of("tracker_overrides",6,"tracker_super_calls",5,
            "builder_registrations",5,"required_variant_slots",2,"spawn_overrides",4,"spawn_calls",20,"broom_packet_overrides",1,"broom_packet_constructors",1);
    private EntityLifecycleAdapter() {}
    static AbstractInsnNode previous(AbstractInsnNode n) {
        do { n=n.getPrevious(); } while(n!=null && n.getOpcode()<0); return n;
    }
    static void count(Map<String,Integer> counts,String key) { counts.merge(key,1,Integer::sum); }
    static boolean load(AbstractInsnNode n,int slot) { return n instanceof VarInsnNode v && v.getOpcode()==ALOAD && v.var==slot; }
    static void addBuilder(MethodNode m) {

        for(var n:m.instructions.toArray()) {
            if(n instanceof VarInsnNode v && v.var!=0) throw new IllegalStateException("Unexpected tracker local");
            if(n instanceof IincInsnNode) throw new IllegalStateException("Unexpected tracker local increment");
            if(n instanceof FrameNode f) {
                if(f.type!=F_NEW || f.local.size()!=1) throw new IllegalStateException("Expanded tracker frames required");
                f.local.add(BUILDER);
            }
        }
        m.desc=TRACKER_NEW;m.signature=null;m.maxLocals=2;m.maxStack+=1;
    }
    static void removeTag(MethodNode m) {

        for(var n:m.instructions.toArray()) {
            if(n instanceof VarInsnNode v) {
                if(v.var==5) throw new IllegalStateException("Spawn NBT argument still used");
                if(v.var>5) v.var--;
            } else if(n instanceof IincInsnNode v) {
                if(v.var==5) throw new IllegalStateException("Spawn NBT slot increment");
                if(v.var>5) v.var--;
            } else if(n instanceof FrameNode f) {
                if(f.type!=F_NEW || f.local.size()<6) throw new IllegalStateException("Expanded spawn frames required");
                f.local.remove(5);
            }
        }
        if(m.localVariables!=null) {
            m.localVariables.removeIf(v->v.index==5);
            m.localVariables.forEach(v->{if(v.index>5)v.index--;});
        }

        if(m.visibleLocalVariableAnnotations!=null || m.invisibleLocalVariableAnnotations!=null)
            throw new IllegalStateException("Unaudited local annotations");
        if(m.visibleParameterAnnotations!=null) m.visibleParameterAnnotations=Arrays.copyOf(m.visibleParameterAnnotations,4);
        if(m.invisibleParameterAnnotations!=null) m.invisibleParameterAnnotations=Arrays.copyOf(m.invisibleParameterAnnotations,4);
        if(m.visibleAnnotableParameterCount>4)m.visibleAnnotableParameterCount=4;
        if(m.invisibleAnnotableParameterCount>4)m.invisibleAnnotableParameterCount=4;
        if(m.parameters!=null && m.parameters.size()==5)m.parameters.remove(4);
        m.desc=SPAWN_NEW;m.signature=null;m.maxLocals--;
    }
    public static Map<String,Integer> rewrite(ClassNode node) {
        var counts=new TreeMap<String,Integer>();
        if(!node.name.startsWith(P))return counts;
        for(var m:node.methods) {
            boolean tracker=m.name.equals("method_5693") && m.desc.equals("()V");
            boolean spawn=m.name.equals("method_5943") && m.desc.equals(SPAWN_OLD);
            if(tracker && !TRACKERS.contains(node.name) || spawn && !SPAWNERS.contains(node.name))
                throw new IllegalStateException("Unaudited lifecycle override "+node.name);
            if((tracker || spawn) && (m.access&ACC_STATIC)!=0)throw new IllegalStateException("Static lifecycle override");
            if(tracker) {
                addBuilder(m);
                if(node.name.endsWith("/BWHostileEntity") || node.name.endsWith("/BWTameableEntity")) {

                    var guards=Arrays.stream(m.instructions.toArray()).filter(n->n.getOpcode()==IF_ICMPLE).toList();
                    if(guards.size()!=1)throw new IllegalStateException("Unexpected variant guard");
                    var jump=guards.getFirst();var one=previous(jump);var query=previous(one);var self=previous(query);
                    if(one.getOpcode()!=ICONST_1 || !(query instanceof MethodInsnNode q) || q.getOpcode()!=INVOKEVIRTUAL
                            || !q.owner.equals(node.name) || !q.name.equals("getVariants") || !q.desc.equals("()I") || !load(self,0))
                        throw new IllegalStateException("Unexpected variant predicate");
                    var target=((JumpInsnNode)jump).label;
                    AbstractInsnNode after=target;do {after=after.getNext();}while(after!=null && after.getOpcode()<0);
                    if(after==null || after.getOpcode()!=RETURN)throw new IllegalStateException("Unexpected variant guard target");
                    for(var n:List.of(self,query,one,jump))m.instructions.remove(n);
                    count(counts,"required_variant_slots");
                }
            }
            for(var n:m.instructions.toArray()) {
                if(!(n instanceof MethodInsnNode c))continue;
                if(tracker && c.name.equals("method_5693") && c.desc.equals("()V")) {
                    if(c.getOpcode()!=INVOKESPECIAL || !c.owner.equals(node.superName) || !load(previous(c),0))
                        throw new IllegalStateException("Unexpected tracker superclass call");
                    m.instructions.insertBefore(c,new VarInsnNode(ALOAD,1));c.desc=TRACKER_NEW;count(counts,"tracker_super_calls");
                } else if(c.owner.equals("net/minecraft/class_2945") && c.name.equals("method_12784")) {
                    if(!tracker || c.getOpcode()!=INVOKEVIRTUAL || !c.desc.equals("(Lnet/minecraft/class_2940;Ljava/lang/Object;)V"))
                        throw new IllegalStateException("Unaudited tracker registration");

                    FieldInsnNode field=null;
                    for(var candidate=previous(c);candidate!=null;candidate=previous(candidate)) {
                        if(candidate instanceof FieldInsnNode f && f.getOpcode()==GETFIELD) {field=f;break;}
                    }
                    if(field==null || !field.name.equals("field_6011") || !field.desc.equals("Lnet/minecraft/class_2945;")
                            || !field.owner.equals(node.name) || !load(previous(field),0))
                        throw new IllegalStateException("Unexpected tracker receiver");
                    var receiver=previous(field);m.instructions.set(receiver,new VarInsnNode(ALOAD,1));m.instructions.remove(field);
                    c.owner=BUILDER;c.name="method_56912";c.desc="(Lnet/minecraft/class_2940;Ljava/lang/Object;)L"+BUILDER+";";
                    m.instructions.insert(c,new InsnNode(POP));count(counts,"builder_registrations");
                } else if(c.name.equals("method_5943") && c.desc.equals(SPAWN_OLD)) {
                    if(!(c.owner.startsWith("net/minecraft/") || c.owner.startsWith(P+"common/entity/"))
                            || (c.getOpcode()!=INVOKEVIRTUAL && c.getOpcode()!=INVOKESPECIAL))
                        throw new IllegalStateException("Unexpected spawn dispatch");
                    var tag=previous(c);
                    if(spawn) {
                        if(c.getOpcode()!=INVOKESPECIAL || !c.owner.equals(node.superName) || !load(tag,5))
                            throw new IllegalStateException("Unexpected spawn tag forwarding");
                        m.instructions.remove(tag);
                    } else {
                        if(tag==null || tag.getOpcode()!=ACONST_NULL)throw new IllegalStateException("Non-null spawn NBT cannot be discarded");
                        m.instructions.remove(tag);
                    }
                    c.desc=SPAWN_NEW;count(counts,"spawn_calls");
                }
            }
            if(tracker)count(counts,"tracker_overrides");
            if(spawn){removeTag(m);count(counts,"spawn_overrides");}
            if(node.name.equals(P+"api/entity/BroomEntity") && m.name.equals("method_18002") && m.desc.equals("()Lnet/minecraft/class_2596;")) {

                var code=Arrays.stream(m.instructions.toArray()).filter(n->n.getOpcode()>=0).toList();
                if(code.size()!=6 || !(code.get(0) instanceof TypeInsnNode type) || type.getOpcode()!=NEW || !type.desc.equals("net/minecraft/class_2604")
                        || code.get(1).getOpcode()!=DUP || !load(code.get(2),0) || code.get(3).getOpcode()!=ICONST_0
                        || !(code.get(4) instanceof MethodInsnNode ctor) || ctor.getOpcode()!=INVOKESPECIAL || !ctor.name.equals("<init>")
                        || !ctor.owner.equals(type.desc) || !ctor.desc.equals("(Lnet/minecraft/class_1297;I)V") || code.get(5).getOpcode()!=ARETURN)
                    throw new IllegalStateException("Unexpected broom packet body");
                m.desc="(Lnet/minecraft/class_3231;)Lnet/minecraft/class_2596;";m.signature=null;m.maxLocals=2;m.maxStack++;
                m.instructions.insertBefore(code.get(3),new VarInsnNode(ALOAD,1));
                ctor.desc="(Lnet/minecraft/class_1297;Lnet/minecraft/class_3231;I)V";
                count(counts,"broom_packet_overrides");count(counts,"broom_packet_constructors");
            }
        }
        return counts;
    }
}
