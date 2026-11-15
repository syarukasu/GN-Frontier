package local.gnfrontier.compat;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Side correction for the pinned ExfilZones 2.0 constructor, applied in memory. */
public final class ExfilServerGuard {
    private static final String CLIENT_EVENTS = "com/example/examplemod/events/ClientEvents";

    private ExfilServerGuard() {}

    public static void removeClientRegistration(ClassNode target) {
        if (!target.name.equals("com/example/examplemod/EvacuationMod")) {
            throw new IllegalArgumentException("Unexpected ExfilZones target");
        }
        MethodNode constructor = target.methods.stream()
                .filter(method -> method.name.equals("<init>") && method.desc.equals("()V"))
                .findFirst().orElseThrow(() -> new IllegalStateException("ExfilZones constructor missing"));
        List<AbstractInsnNode> code = new ArrayList<>();
        for (AbstractInsnNode instruction : constructor.instructions) {
            if (instruction.getOpcode() >= 0) code.add(instruction);
        }
        List<AbstractInsnNode> registration = null;
        for (int i = 1; i + 3 < code.size(); i++) {
            if (!(code.get(i) instanceof TypeInsnNode allocation)
                    || allocation.getOpcode() != Opcodes.NEW || !allocation.desc.equals(CLIENT_EVENTS)) continue;
            if (registration != null
                    || !(code.get(i - 1) instanceof FieldInsnNode bus)
                    || bus.getOpcode() != Opcodes.GETSTATIC
                    || !bus.owner.equals("net/minecraftforge/common/MinecraftForge")
                    || !bus.name.equals("EVENT_BUS")
                    || !bus.desc.equals("Lnet/minecraftforge/eventbus/api/IEventBus;")
                    || code.get(i + 1).getOpcode() != Opcodes.DUP
                    || !(code.get(i + 2) instanceof MethodInsnNode init)
                    || init.getOpcode() != Opcodes.INVOKESPECIAL || !init.owner.equals(CLIENT_EVENTS)
                    || !init.name.equals("<init>") || !init.desc.equals("()V")
                    || !(code.get(i + 3) instanceof MethodInsnNode register)
                    || register.getOpcode() != Opcodes.INVOKEINTERFACE
                    || !register.owner.equals("net/minecraftforge/eventbus/api/IEventBus")
                    || !register.name.equals("register") || !register.desc.equals("(Ljava/lang/Object;)V")) {
                throw new IllegalStateException("ExfilZones client registration differs from the pinned original");
            }
            registration = new ArrayList<>(code.subList(i - 1, i + 4));
        }
        if (registration == null) throw new IllegalStateException("ExfilZones client registration not found");
        // Removes only EVENT_BUS.register(new ClientEvents()). Server events, commands,
        // common setup and packet registration retain their original instructions.
        registration.forEach(constructor.instructions::remove);
    }
}
