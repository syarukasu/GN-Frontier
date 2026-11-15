package local.gnfrontier.distribution;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipFile;
import local.gnfrontier.compat.ExfilServerGuard;
import local.gnfrontier.compat.AnomalyDialogServerGuard;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class ExfilServerVerification {
    private static final String CLASS = "com/example/examplemod/EvacuationMod.class";

    private static byte[] bytes(ClassNode node) {
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static long allocations(ClassNode node, String suffix) {
        long count = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof TypeInsnNode allocation
                        && allocation.getOpcode() == Opcodes.NEW && allocation.desc.endsWith(suffix)) count++;
            }
        }
        return count;
    }

    public static void main(String[] args) throws Exception {
        byte[] original;
        try (ZipFile file = new ZipFile(Path.of(args[0]).toFile())) {
            original = file.getInputStream(file.getEntry(CLASS)).readAllBytes();
        }
        ClassNode before = read(original);
        ClassNode after = read(original);
        ExfilServerGuard.removeClientRegistration(after);
        if (allocations(before, "/ClientEvents") != 1 || allocations(after, "/ClientEvents") != 0
                || allocations(after, "/ServerEvents") != 1) {
            throw new AssertionError("Client registration removed; server registration must be preserved");
        }
        // Apart from the constructor, the class must serialize identically: packet
        // setup, command registration, fields and annotations cannot be changed.
        before.methods.removeIf(method -> method.name.equals("<init>"));
        after.methods.removeIf(method -> method.name.equals("<init>"));
        if (!Arrays.equals(bytes(before), bytes(after))) throw new AssertionError("Non-constructor code changed");
        ClassNode once = read(original);
        ExfilServerGuard.removeClientRegistration(once);
        boolean rejected = false;
        try { ExfilServerGuard.removeClientRegistration(once); }
        catch (IllegalStateException expected) { rejected = true; }
        if (!rejected) throw new AssertionError("Changed upstream code must be rejected");
        System.out.println("EXFIL_SERVER_COMPAT_OK: pinned original, client-only allocation removed, server logic retained");
        try (ZipFile file = new ZipFile(Path.of(args[1]).toFile())) {
            for (String type : new String[]{"OpenQuestDialogPacket", "OpenContractDialogPacket"}) {
                byte[] packet = file.getInputStream(file.getEntry("com/anomalyzone/quest/" + type + ".class")).readAllBytes();
                ClassNode originalPacket = read(packet);
                ClassNode serverPacket = read(packet);
                AnomalyDialogServerGuard.removeClientRenderer(serverPacket);
                MethodNode renderer = serverPacket.methods.stream().filter(m -> m.name.equals("lambda$handle$0")).findFirst().orElseThrow();
                if (renderer.instructions.size() != 1 || renderer.instructions.getFirst().getOpcode() != Opcodes.RETURN) {
                    throw new AssertionError("Dedicated server must not construct a client dialog");
                }
                originalPacket.methods.removeIf(m -> m.name.equals("lambda$handle$0"));
                serverPacket.methods.removeIf(m -> m.name.equals("lambda$handle$0"));
                if (!Arrays.equals(bytes(originalPacket), bytes(serverPacket))) {
                    throw new AssertionError("Packet codecs, fields or acknowledgement changed");
                }
            }
        }
        System.out.println("ANOMALY_SERVER_COMPAT_OK: both dialog packets retain codecs, payloads and acknowledgement");
    }
}
