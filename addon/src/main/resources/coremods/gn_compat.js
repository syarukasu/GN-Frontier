/* GN Frontier owns these version-pinned compatibility adaptations.
 * Forge applies them in memory; this code never writes third-party archives.
 * Configuration and content remain in the pack's config/KubeJS files.
 */
var Opcodes = Java.type('org.objectweb.asm.Opcodes');
var Type = Java.type('org.objectweb.asm.Type');
var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var FieldInsnNode = Java.type('org.objectweb.asm.tree.FieldInsnNode');
var TypeInsnNode = Java.type('org.objectweb.asm.tree.TypeInsnNode');
var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');
var IincInsnNode = Java.type('org.objectweb.asm.tree.IincInsnNode');
var FrameNode = Java.type('org.objectweb.asm.tree.FrameNode');
var MethodNode = Java.type('org.objectweb.asm.tree.MethodNode');
var ArrayList = Java.type('java.util.ArrayList');
var ASMAPI = Java.type('net.minecraftforge.coremod.api.ASMAPI');
var CI = 'org/spongepowered/asm/mixin/injection/callback/CallbackInfo';
var CIR = CI + 'Returnable';
var RL = 'net/minecraft/resources/ResourceLocation';
var SD = 'com/tacz/guns/client/resource/pojo/display/SoundData';

function require(ok, message) {
    if (!ok) throw new Error('GN Frontier compatibility: ' + message);
}
function list(value) { var result = new ArrayList(); result.add(value); return result; }
function annotation(method, suffix) {
    if (method.visibleAnnotations != null) for each (var a in method.visibleAnnotations) {
        if (String(a.desc).endsWith('/' + suffix + ';')) return a;
    }
    throw new Error('Missing ' + suffix + ' on ' + method.name);
}
function setValue(a, key, value) {
    for (var i = 0; i < a.values.size(); i += 2) if (String(a.values.get(i)) === key) {
        a.values.set(i + 1, value); return;
    }
    a.values.add(key); a.values.add(value);
}
// ModLauncher supplies compressed frames. Expand via the allowed visitor API;
// ClassReader/ClassWriter are deliberately unavailable in Forge's JS sandbox.
function expandFrames(node) {
    var captured;
    var Capture = Java.extend(MethodNode, {visitFrame: function(type, nl, locals, ns, stack) {
        captured = {kind: type, count: nl, locals: locals, stackCount: ns, stack: stack};
    }});
    var visitor = new Capture(Opcodes.ASM9);
    for each (var m in node.methods) {
        var locals = new ArrayList();
        if ((m.access & Opcodes.ACC_STATIC) === 0) locals.add(String(m.name) === '<init>' ? Opcodes.UNINITIALIZED_THIS : node.name);
        for each (var arg in Type.getArgumentTypes(m.desc)) locals.add(frameType(arg));
        for each (var ins in m.instructions.toArray()) if (ins instanceof FrameNode) {
            ins.accept(visitor); var f = captured;
            if (f.kind === Opcodes.F_NEW || f.kind === Opcodes.F_FULL) {
                locals.clear(); for (var i = 0; i < f.count; i++) locals.add(f.locals[i]);
            } else if (f.kind === Opcodes.F_APPEND) {
                for (var i = 0; i < f.count; i++) locals.add(f.locals[i]);
            } else if (f.kind === Opcodes.F_CHOP) {
                for (var i = 0; i < f.count; i++) locals['remove(int)'](locals.size() - 1);
            } else require(f.kind === Opcodes.F_SAME || f.kind === Opcodes.F_SAME1, 'Unknown frame');
            var stack = new ArrayList(); for (var i = 0; i < f.stackCount; i++) stack.add(f.stack[i]);
            m.instructions.set(ins, new FrameNode(Opcodes.F_NEW, locals.size(), locals.toArray(), stack.size(), stack.toArray()));
        }
    }
    return node;
}

function gunDurability(n) {
    var key = null, controller = null, sounds = 0;
    for each (var m in n.methods) {
        if (String(m.name) === 'keyPress') key = m;
        if (String(m.name) === 'controllerPress') controller = m;
        for each (var ins in m.instructions.toArray()) {
            if (ins instanceof MethodInsnNode && String(ins.owner) === 'com/tacz/guns/client/resource/GunDisplayInstance'
                && String(ins.name) === 'getSounds' && String(ins.desc) === '(Ljava/lang/String;)L' + RL + ';') {
                ins.setOpcode(Opcodes.INVOKESTATIC);
                ins.owner = 'local/gnfrontier/client/GunSoundCompat'; ins.name = 'sound';
                ins.desc = '(Lcom/tacz/guns/client/resource/GunDisplayInstance;Ljava/lang/String;)L' + RL + ';';
                ins.itf = false; sounds++;
            }
        }
    }
    require(sounds === 1 && key != null && controller != null, 'Gun Durability 2.2.2 input/sound shape');
    require(String(controller.desc) === '(ZL' + CIR + ';)V', 'Gun Durability controller descriptor');
    n.methods.remove(key); controller.name = 'keyPress'; controller.desc = '(L' + CIR + ';)V';
    controller.signature = '(L' + CIR + '<Ljava/lang/Boolean;>;)V';
    for each (var ins in controller.instructions.toArray()) {
        if (ins instanceof VarInsnNode) { require(ins.var > 0, 'Controller boolean unexpectedly used'); ins.var--; }
        if (ins instanceof FrameNode) {
            require(String(ins.local.get(0)) === '1', 'Controller frame');
            ins.local['remove(int)'](0);
        }
    }
    var it = controller.localVariables.iterator();
    while (it.hasNext()) { var local = it.next(); if (local.index === 0) it.remove(); else local.index--; }
    controller.maxLocals--;
    setValue(annotation(controller, 'Inject'), 'method', list('onInspectPress()Z'));
    return n;
}

function additionsSound(n) {
    var changes = 0;
    for each (var m in n.methods) if (String(m.name) === 'taczadditions$fallBackToShootSound') {
        var frames = 0;
        for each (var ins in m.instructions.toArray()) {
            if (ins instanceof TypeInsnNode && ins.getOpcode() === Opcodes.CHECKCAST && String(ins.desc) === RL) {
                m.instructions.set(ins, new MethodInsnNode(Opcodes.INVOKESTATIC, 'local/gnfrontier/client/GunSoundCompat',
                    'location', '(Ljava/lang/Object;)L' + RL + ';', false)); changes++;
            }
            if (ins instanceof MethodInsnNode && String(ins.owner) === 'com/tacz/guns/client/resource/GunDisplayInstance' && String(ins.name) === 'getSounds') {
                require(String(ins.desc) === '(Ljava/lang/String;)L' + RL + ';', 'Additions old sound descriptor');
                ins.desc = '(Ljava/lang/String;)L' + SD + ';'; changes++;
            }
            if (ins instanceof FrameNode && ins.local != null && ins.local.size() > 5 && String(ins.local.get(5)) === RL) {
                ins.local.set(5, SD); frames++; changes++;
            }
        }
        if (m.signature != null) m.signature = String(m.signature).replace('L' + RL + ';', 'L' + SD + ';');
        for each (var local in m.localVariables) if (String(local.name) === 'shootSound') local.desc = 'L' + SD + ';';
    }
    require(changes === 3, 'Additions sound edits ' + changes); return n;
}

function additionsZoom(n) {
    var changes = 0;
    for each (var m in n.methods) if (String(m.name) === 'taczadditions$builtInScopeZoom') {
        m.desc = '(L' + CIR + ';)V'; changes++;
        setValue(annotation(m, 'Inject'), 'method', list('onZoomPress')); changes++;
        for each (var ins in m.instructions.toArray()) if (ins instanceof MethodInsnNode && String(ins.owner) === CI && String(ins.name) === 'cancel') {
            m.instructions.insertBefore(ins, new FieldInsnNode(Opcodes.GETSTATIC, 'java/lang/Boolean', 'TRUE', 'Ljava/lang/Boolean;'));
            ins.owner = CIR; ins.name = 'setReturnValue'; ins.desc = '(Ljava/lang/Object;)V'; changes++;
        }
        for each (var local in m.localVariables) if (String(local.desc) === 'L' + CI + ';') local.desc = 'L' + CIR + ';';
        m.maxStack = Math.max(m.maxStack, 2);
    }
    require(changes === 3, 'Additions zoom edits'); return n;
}

// Exact TaCZ 1.2.0 target signatures. All original handler bodies are retained.
var renderTargets = {
    'hideDisabledLaserBeam': 'renderLaserBeam(Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Ljava/util/List;Lnet/minecraft/client/renderer/MultiBufferSource;)V',
    'hideDisabledAttachmentLaser': 'render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFLnet/minecraft/client/renderer/MultiBufferSource;)V',
    'restoreAttachmentLaser': 'render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFLnet/minecraft/client/renderer/MultiBufferSource;)V',
    'hideDisabledLaserBone': 'render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource;)V',
    'restoreLaserBoneVisibility': 'render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource;)V',
    'applyRecoilKick': 'lambda$renderFirstPerson$5(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/player/LocalPlayer;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;ILnet/minecraft/client/renderer/MultiBufferSource;Lcom/tacz/guns/client/resource/GunDisplayInstance;)V',
    'taczadditions$reachForScope': 'render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/item/ItemDisplayContext;IILnet/minecraft/client/renderer/MultiBufferSource;)V',
    'injectText': 'render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource;)V'
};
function frameType(t) {
    if (t.getSort() === Type.FLOAT) return Opcodes.FLOAT;
    if (t.getSort() === Type.LONG) return Opcodes.LONG;
    if (t.getSort() === Type.DOUBLE) return Opcodes.DOUBLE;
    if (t.getSort() >= Type.BOOLEAN && t.getSort() <= Type.INT) return Opcodes.INTEGER;
    return t.getInternalName();
}
function additionsRender(n) {
    var count = 0;
    for each (var m in n.methods) {
        var target = renderTargets[String(m.name)]; if (target == null) continue;
        var old = Type.getArgumentTypes(m.desc), fresh = Type.getArgumentTypes(target.substring(target.indexOf('(')));
        var oldCount = old.length - 1, base = (m.access & Opcodes.ACC_STATIC) === 0 ? 1 : 0;
        var indices = [], next = 0, delta = fresh.length - oldCount;
        for (var i = 0; i < oldCount; i++) {
            while (next < fresh.length && !fresh[next].equals(old[i])) next++;
            require(next < fresh.length, 'Render arguments changed: ' + m.name); indices[i] = next++;
        }
        indices[oldCount] = fresh.length;
        function shift(index) { var local = index - base; return base + (local <= oldCount ? indices[local] : local + delta); }
        for each (var ins in m.instructions.toArray()) {
            if (ins instanceof VarInsnNode && ins.var >= base) ins.var = shift(ins.var);
            if (ins instanceof IincInsnNode && ins.var >= base) ins.var = shift(ins.var);
            if (ins instanceof FrameNode) {
                require(ins.local.size() >= base + oldCount + 1, 'Render frame');
                var locals = new ArrayList(ins.local.subList(0, base));
                for each (var t in fresh) locals.add(frameType(t));
                locals.addAll(ins.local.subList(base + oldCount, ins.local.size())); ins.local = locals;
            }
        }
        if (m.localVariables != null) for each (var local in m.localVariables) if (local.index >= base) local.index = shift(local.index);
        m.desc = target.substring(target.indexOf('('), target.lastIndexOf(')')) + 'L' + CI + ';)V';
        m.signature = null; m.maxLocals += delta;
        setValue(annotation(m, 'Inject'), 'method', list(target)); count++;
    }
    require(count >= 1 && count <= 2, 'Render handler count'); return n;
}

function shrapnelBuilder(n) {
    var count = 0;
    for each (var field in n.fields) if (String(field.name) === 'damageMultiplier' && String(field.desc) === 'F') {
        require(field.value == null, 'Shrapnel initializer');
        for each (var a in field.visibleAnnotations) if (String(a.desc) === 'Lorg/spongepowered/asm/mixin/Shadow;') {
            a.desc = 'Lorg/spongepowered/asm/mixin/Unique;'; a.values = null; count++;
        }
    }
    require(count === 1, 'Shrapnel removed SBW field'); return n;
}
function shrapnelProjectile(n) {
    var count = 0;
    for each (var m in n.methods) if (m.visibleAnnotations != null) for each (var a in m.visibleAnnotations) {
        if (String(a.desc) === 'Lorg/spongepowered/asm/mixin/injection/Redirect;') {
            setValue(a, 'method', list('causeCustomExplode(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/entity/Entity;FF)V')); count++;
        }
    }
    require(count === 1, 'Shrapnel explosion redirect'); return n;
}
function expertGrid(n) {
    // This pack delegates inventory layout to PetiteInventory (Expert itemSizes=false).
    // Remove only this class's five grid handlers before Mixin preparation.
    var count = 0, it = n.methods.iterator();
    while (it.hasNext()) { var m = it.next(); if (String(m.name).indexOf('taczexpert$grid') === 0) { it.remove(); count++; } }
    require(count === 5, 'Expert 0.1.65 grid handler shape'); return n;
}
function entry(name, transform) {
    return {target: {type: 'CLASS', name: name}, transformer: function(node) {
        var result = transform(expandFrames(node));
        ASMAPI.log('INFO', '[GN Frontier] In-memory compatibility applied: ' + name);
        return result;
    }};
}
function initializeCoreMod() {
    var result = {
        'gundb_input': entry('mod.cdv.gdb.mixin.InspectKeybindHook', gunDurability),
        'additions_sound': entry('com.raiiiden.taczadditions.mixin.GunDisplaySilenceSoundFallbackMixin', additionsSound),
        'additions_zoom': entry('com.raiiiden.taczadditions.mixin.ZoomKeyMixin', additionsZoom),
        'shrapnel_builder': entry('com.cyberbutcher.shrapnelexplosionsmod.mixin.SBWEntitiesExplosionMixin', shrapnelBuilder),
        'shrapnel_projectile': entry('com.cyberbutcher.shrapnelexplosionsmod.mixin.SBWCancelVanillaExplosionMixin', shrapnelProjectile),
        'expert_grid': entry('com.ori.taczplus.mixin.client.ContainerScreenGridMixin', expertGrid)
    };
    for each (var name in ['BeamRendererMixin', 'BedrockAttachmentModelLaserToggleMixin', 'BedrockGunModelLaserToggleMixin', 'GunMovementMixin', 'LeftHandReachMixin', 'MagazineTextOverlayMixin'])
        result['additions_' + name] = entry('com.raiiiden.taczadditions.mixin.' + name, additionsRender);
    return result;
}

