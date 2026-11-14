package local.gnfrontier.core;

import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import com.google.gson.*;

/** Read-only linkage/selector audit; does not pretend to run a Mixin transformer or game. */
public final class ModLinkVerification {
    static final Map<String, Path> classes = new HashMap<>();
    static final Map<String, ClassNode> cache = new HashMap<>();
    static final Set<String> checked = new TreeSet<>(), issues = new TreeSet<>();
    static ClassNode node(String name) throws Exception {
        if (cache.containsKey(name)) return cache.get(name);
        Path path = classes.get(name);
        if (path == null) {
            try (var stream = ModLinkVerification.class.getClassLoader().getResourceAsStream(name + ".class")) {
                if (stream == null) return null;
                ClassNode n = new ClassNode(); new ClassReader(stream).accept(n, 0); cache.put(name, n); return n;
            }
        }
        try (var jar = new ZipFile(path.toFile())) {
            ClassNode node = new ClassNode();
            new ClassReader(jar.getInputStream(jar.getEntry(name + ".class"))).accept(node, 0);
            cache.put(name, node); return node;
        }
    }
    static boolean has(String owner, String name, String desc, boolean field, Set<String> visited) throws Exception {
        if (!visited.add(owner)) return false;
        ClassNode n = node(owner); if (n == null) return false;
        if (field && n.fields.stream().anyMatch(f -> f.name.equals(name) && (desc == null || f.desc.equals(desc)))) return true;
        if (!field && n.methods.stream().anyMatch(m -> m.name.equals(name) && (desc == null || m.desc.equals(desc)))) return true;
        if (n.superName != null && has(n.superName, name, desc, field, visited)) return true;
        for (String face : n.interfaces) if (has(face, name, desc, field, visited)) return true;
        return false;
    }
    static Object value(AnnotationNode a, String key) {
        if (a.values != null) for (int i = 0; i < a.values.size(); i += 2) if (a.values.get(i).equals(key)) return a.values.get(i + 1);
        return null;
    }
    static List<AnnotationNode> annotations(List<AnnotationNode> a, List<AnnotationNode> b) {
        var result = new ArrayList<AnnotationNode>(); if (a != null) result.addAll(a); if (b != null) result.addAll(b); return result;
    }
    static void check(String owner, String name, String desc, boolean field, String source) throws Exception {
        if (!owner.startsWith("com/tacz/")) return;
        String key = owner + "." + name + (desc == null ? "" : desc);
        checked.add(key);
        if (!has(owner, name, desc, field, new HashSet<>())) issues.add(source + " -> " + key);
    }
    public static void main(String[] args) throws Exception {
        Path revision = Path.of(args[1]).getParent(), root = revision.getParent().getParent();
        List<Path> newJars = new ArrayList<>();
        for (String filename : List.of("candidate-downloads.json", "note-downloads.json")) {
            for (var entry : JsonParser.parseString(Files.readString(revision.resolve(filename))).getAsJsonArray()) {
                var row = entry.getAsJsonObject();
                if (row.has("jar") && !row.get("decision").getAsString().equals("superseded")) newJars.add(root.resolve(row.get("file").getAsString()));
            }
        }
        List<Path> all = new ArrayList<>(Files.list(Path.of(args[0])).filter(p -> p.toString().endsWith(".jar")).toList()); all.addAll(newJars);
        all.add(Path.of(System.getProperty("user.home"), ".gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.4.20/forge-1.20.1-47.4.20-srg.jar"));
        for (Path path : all) try (var jar = new ZipFile(path.toFile())) {
            for (var e : jar.stream().filter(e -> e.getName().endsWith(".class")).toList()) classes.put(e.getName().substring(0, e.getName().length() - 6), path);
        }
        for (Path path : newJars) try (var jar = new ZipFile(path.toFile())) {
            Map<String, JsonObject> remaps = new HashMap<>();
            for (var e : jar.stream().filter(e -> e.getName().endsWith("refmap.json")).toList()) {
                var json = JsonParser.parseString(new String(jar.getInputStream(e).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                if (json.has("mappings")) json.getAsJsonObject("mappings").entrySet().forEach(v -> remaps.put(v.getKey(), v.getValue().getAsJsonObject()));
            }
            for (var e : jar.stream().filter(e -> e.getName().endsWith(".class")).toList()) {
                ClassNode n = node(e.getName().substring(0, e.getName().length() - 6));
                var targets = new ArrayList<String>();
                for (var a : annotations(n.visibleAnnotations, n.invisibleAnnotations)) if (a.desc.endsWith("/Mixin;")) {
                    if (value(a, "value") instanceof List<?> list) for (Object v : list) targets.add(((Type)v).getInternalName());
                    if (value(a, "targets") instanceof List<?> list) for (Object v : list) targets.add(v.toString().replace('.', '/'));
                }
                for (MethodNode m : n.methods) {
                    for (AbstractInsnNode i : m.instructions) {
                        if (i instanceof MethodInsnNode call) check(call.owner, call.name, call.desc, false, path.getFileName() + ":" + n.name);
                        if (i instanceof FieldInsnNode field) check(field.owner, field.name, field.desc, true, path.getFileName() + ":" + n.name);
                    }
                    for (var a : annotations(m.visibleAnnotations, m.invisibleAnnotations)) {
                        if (value(a, "method") instanceof List<?> methods) for (String target : targets) for (Object raw : methods) {
                            String selector = raw.toString(), owner = target;
                            if (remaps.containsKey(n.name) && remaps.get(n.name).has(selector)) selector = remaps.get(n.name).get(selector).getAsString();
                            if (selector.startsWith("L") && selector.contains(";")) { int end = selector.indexOf(';'); owner = selector.substring(1, end); selector = selector.substring(end + 1); }
                            int at = selector.indexOf('(');
                            check(owner, at < 0 ? selector : selector.substring(0, at), at < 0 ? null : selector.substring(at), false, n.name);
                        }
                        if (a.desc.endsWith("/Accessor;") && value(a, "value") instanceof String field) for (String target : targets) check(target, field, null, true, n.name);
                    }
                }
            }
        }
        JsonObject report = new JsonObject(); report.addProperty("scope", "TaCZ binary member links and mixin target method selectors; not injection-point application or multiplayer runtime");
        report.addProperty("checks", checked.size()); report.add("issues", new Gson().toJsonTree(issues));
        report.add("members", new Gson().toJsonTree(checked));
        Files.writeString(Path.of(args[2]), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        if (!issues.isEmpty()) throw new AssertionError("TaCZ binary link issues: " + issues);
        System.out.println("GN_TACZ_BINARY_LINK_PASS checks=" + checked.size() + " transformedRuntime=false");
    }
}
