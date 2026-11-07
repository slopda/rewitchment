package com.veeda.rewitchment.translator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

public final class IdentifierFactoryAdapter {
    static final String IDENTIFIER = "net/minecraft/class_2960";
    static final String ONE_ARG = "(Ljava/lang/String;)V";
    static final String TWO_ARGS = "(Ljava/lang/String;Ljava/lang/String;)V";

    public record Outcome(int converted, int rejected) {}
    public record Factory(String targetOwner, String targetMethod, String targetDescriptor) {}

    private record Pair(TypeInsnNode allocation, AbstractInsnNode duplicate) {}
    private record Candidate(Pair pair, MethodInsnNode call, Factory factory) {}

    private IdentifierFactoryAdapter() {}

    private static AbstractInsnNode firstRealAfter(TypeInsnNode allocation) {
        AbstractInsnNode next = allocation.getNext();
        while (next != null && next.getOpcode() == -1) next = next.getNext();
        return next;
    }

    private static Outcome rewriteMethod(String owner, MethodNode method,
                                         String sourceOwner, Map<String, Factory> factories) {
        List<AbstractInsnNode> all = new ArrayList<>();
        Map<AbstractInsnNode, Pair> receiverProducers = new HashMap<>();
        int totalCalls = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            all.add(insn);
            if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                    && sourceOwner.equals(call.owner) && "<init>".equals(call.name)
                    && factories.containsKey(call.desc)) totalCalls++;
            if (insn instanceof TypeInsnNode allocation && insn.getOpcode() == Opcodes.NEW
                    && sourceOwner.equals(allocation.desc)) {
                AbstractInsnNode dup = firstRealAfter(allocation);
                if (dup != null && dup.getOpcode() == Opcodes.DUP) {
                    Pair pair = new Pair(allocation, dup);
                    receiverProducers.put(allocation, pair);
                    receiverProducers.put(dup, pair);
                }
            }
        }
        if (totalCalls == 0) return new Outcome(0, 0);
        Frame<SourceValue>[] frames;
        try {
            frames = new Analyzer<>(new SourceInterpreter()).analyze(owner, method);
        } catch (AnalyzerException | RuntimeException e) {
            return new Outcome(0, totalCalls);  
        }
        List<Candidate> planned = new ArrayList<>();
        Set<Pair> usedPairs = new HashSet<>();
        int rejected = 0;
        for (int i = 0; i < all.size(); i++) {
            if (!(all.get(i) instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESPECIAL
                    || !sourceOwner.equals(call.owner) || !"<init>".equals(call.name)
                    || !factories.containsKey(call.desc)) continue;
            Frame<SourceValue> frame = frames[i];
            int arguments = Type.getArgumentTypes(call.desc).length;
            if (frame == null || frame.getStackSize() < arguments + 1) {
                rejected++;
                continue;
            }
            SourceValue receiver = frame.getStack(frame.getStackSize() - arguments - 1);
            Pair matched = null;
            boolean ambiguous = receiver.insns.isEmpty();
            for (AbstractInsnNode origin : receiver.insns) {
                Pair candidate = receiverProducers.get(origin);
                if (candidate == null || (matched != null && matched != candidate)) {
                    ambiguous = true;
                    break;
                }
                matched = candidate;
            }
            if (ambiguous || matched == null || !usedPairs.add(matched)) {
                rejected++;
                continue;
            }
            planned.add(new Candidate(matched, call, factories.get(call.desc)));
        }
        for (Candidate candidate : planned) {
            method.instructions.set(candidate.pair.allocation(), new InsnNode(Opcodes.NOP));
            method.instructions.set(candidate.pair.duplicate(), new InsnNode(Opcodes.NOP));
            method.instructions.set(candidate.call(), new MethodInsnNode(
                    Opcodes.INVOKESTATIC, candidate.factory().targetOwner(),
                    candidate.factory().targetMethod(),
                    candidate.factory().targetDescriptor(), false));
        }
        return new Outcome(planned.size(), rejected);
    }

    public static Outcome rewrite(ClassNode classNode) {
        return rewrite(classNode, IDENTIFIER, Map.of(
                ONE_ARG, new Factory(IDENTIFIER, "method_60654",
                        "(Ljava/lang/String;)Lnet/minecraft/class_2960;"),
                TWO_ARGS, new Factory(IDENTIFIER, "method_60655",
                        "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/class_2960;")
        ));
    }

    static Outcome rewrite(ClassNode classNode, String sourceOwner,
                           Map<String, Factory> factories) {
        int converted = 0, rejected = 0;
        for (MethodNode method : classNode.methods) {
            Outcome outcome = rewriteMethod(classNode.name, method, sourceOwner, factories);
            converted += outcome.converted();
            rejected += outcome.rejected();
        }
        return new Outcome(converted, rejected);
    }
}
