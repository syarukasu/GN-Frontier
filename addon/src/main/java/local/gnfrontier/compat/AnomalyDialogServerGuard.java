package local.gnfrontier.compat;

import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Prevents verification of client screen constructors on a dedicated server. */
public final class AnomalyDialogServerGuard {
    private static final Set<String> TARGETS = Set.of(
            "com/anomalyzone/quest/OpenQuestDialogPacket",
            "com/anomalyzone/quest/OpenContractDialogPacket");

    private AnomalyDialogServerGuard() {}

    public static void removeClientRenderer(ClassNode target) {
        if (!TARGETS.contains(target.name)) throw new IllegalArgumentException("Unexpected dialog packet");
        MethodNode renderer = target.methods.stream()
                .filter(method -> method.name.equals("lambda$handle$0")
                        && method.desc.equals("(L" + target.name + ";)V"))
                .findFirst().orElseThrow(() -> new IllegalStateException("Pinned dialog renderer missing"));
        int flags = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC;
        if ((renderer.access & flags) != flags) throw new IllegalStateException("Unexpected dialog renderer access");
        long screenCalls = 0;
        for (AbstractInsnNode instruction : renderer.instructions) {
            if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && call.owner.equals("net/minecraft/client/Minecraft") && call.name.equals("m_91152_")
                    && call.desc.equals("(Lnet/minecraft/client/gui/screens/Screen;)V")) screenCalls++;
        }
        if (screenCalls != 1) throw new IllegalStateException("Pinned dialog renderer changed");
        // Packet IDs, codecs, payload fields and the handler's acknowledgement stay
        // intact. Only the terminal UI lambda is inert on DEDICATED_SERVER.
        renderer.instructions.clear();
        renderer.instructions.add(new InsnNode(Opcodes.RETURN));
        renderer.tryCatchBlocks.clear();
        if (renderer.localVariables != null) renderer.localVariables.clear();
        renderer.visibleLocalVariableAnnotations = null;
        renderer.invisibleLocalVariableAnnotations = null;
        renderer.maxLocals = 1;
        renderer.maxStack = 0;
    }
}
