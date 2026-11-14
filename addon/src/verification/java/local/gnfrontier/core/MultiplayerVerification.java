package local.gnfrontier.core;

import java.nio.file.*;
import java.util.*;
import com.sighs.petiteinventory.init.Area;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import com.electronwill.nightconfig.toml.TomlParser;
import noobanidus.mods.lootr.config.ConfigManager;
import cpw.mods.jarhandling.SecureJar;

public final class MultiplayerVerification {
    private static int checks;
    private static void check(boolean condition, String text) { checks++; if (!condition) throw new AssertionError(text); }
    private static GridTradePlanner.Slot empty() { return new GridTradePlanner.Slot(0, 64, false, new Area(1, 1, null)); }
    private static List<GridTradePlanner.Slot> emptySlots() { return new ArrayList<>(Collections.nCopies(36, empty())); }
    private static GridTradePlanner.Slot cargo(int count, int limit, boolean sell, int w, int h) {
        return new GridTradePlanner.Slot(count, limit, sell, new Area(w, h, null));
    }
    private static void reject(Runnable task) { boolean failed = false; try { task.run(); } catch (IllegalArgumentException ex) { failed = true; } check(failed, "Must reject atomically"); }
    public static void main(String[] args) throws Exception {
        var slots = emptySlots();
        for (int i = 0; i < 5; i++) slots.set(i, cargo(64, 64, false, 1, 1));
        slots.set(9, cargo(1, 1, false, 3, 2));
        int[] placed = GridTradePlanner.plan(slots, new Area(1, 1, null), 64, 0, 64);
        check(placed[12] == 64 && placed[10] == 0 && placed[18] == 0, "Reserved rectangle cells cannot receive stock");
        for (int i = 9; i < 36; i++) slots.set(i, (i % 2 == 0) ? cargo(1, 1, false, 1, 1) : empty());
        check(GridTradePlanner.capacity(slots, new Area(2, 2, null), 1) == 0, "Fragmented free cells are not a 2x2 space");
        reject(() -> GridTradePlanner.plan(slots, new Area(2, 2, null), 1, 0, 1));
        check(slots.get(9).count() == 0 && slots.get(10).count() == 1, "Failed plan cannot mutate input");
        var merge = emptySlots(); merge.set(0, cargo(63, 64, true, 1, 1)); merge.set(5, cargo(64, 64, true, 1, 1));
        int[] merged = GridTradePlanner.plan(merge, new Area(1, 1, null), 64, 0, 2);
        check(merged[0] == 64 && merged[1] == 1 && merged[5] == 64, "Merge first, disabled hotbar untouched");
        check(GridTradePlanner.count(merge) == 63, "Disabled slots excluded from sale count");
        reject(() -> GridTradePlanner.plan(merge, new Area(1, 1, null), 64, 64, 0));
        var rotation = emptySlots();
        for (int i = 0; i < 5; i++) rotation.set(i, cargo(64, 64, false, 1, 1));
        check(GridTradePlanner.capacity(rotation, new Area(3, 2, null), 1) == 3, "3x2 footprint");
        check(GridTradePlanner.capacity(rotation, new Area(2, 3, null), 1) == 4, "Rotated 2x3 footprint");
        var tagged = emptySlots(); tagged.set(0, cargo(12, 64, false, 1, 1));
        check(GridTradePlanner.count(tagged) == 0, "Tagged goods are not silently sold");
        Random random = new Random(461904L);
        for (int round = 0; round < 1000; round++) {
            var input = emptySlots();
            for (int i = 0; i < 36; i++) input.set(i, cargo(random.nextInt(65), 64, random.nextBoolean(), 1, 1));
            int capacity = GridTradePlanner.capacity(input, new Area(1, 1, null), 64);
            int amount = random.nextInt(capacity + 1);
            int[] output = GridTradePlanner.plan(input, new Area(1, 1, null), 64, 0, amount);
            int oldTotal = input.stream().mapToInt(GridTradePlanner.Slot::count).sum();
            check(Arrays.stream(output).sum() - oldTotal == amount, "Quantity conserved on purchase");
            for (int i = 5; i < 9; i++) check(output[i] == input.get(i).count(), "No writes to disabled hotbar");
            int owned = GridTradePlanner.count(input), sold = random.nextInt(owned + 1);
            output = GridTradePlanner.plan(input, new Area(1, 1, null), 64, sold, 0);
            check(oldTotal - Arrays.stream(output).sum() == sold, "Quantity conserved on sale");
        }
        UUID container = UUID.randomUUID();
        var state = new PersonalLootState(container, new ResourceLocation("gnfrontier", "chests/drawer"), 514L);
        CompoundTag root = new CompoundTag(); root.put(PersonalLootState.KEY, state.save());
        var restored = PersonalLootState.load(TagParser.parseTag(root.toString()));
        check(state.equals(restored), "Stable identity/table/seed through save and reload");
        check(PersonalLootState.load(new CompoundTag()) == null, "Player-placed furniture has no personal loot");
        var config = new TomlParser().parse(Files.readString(Path.of(args[0])));
        for (var entry : config.entrySet()) {
            net.minecraftforge.common.ForgeConfigSpec.ValueSpec spec = ConfigManager.COMMON_CONFIG.getSpec().get(entry.getKey());
            check(spec != null && spec.test(entry.getValue()), "Unknown/invalid Lootr option: " + entry.getKey());
        }
        for (Path path : Files.list(Path.of(args[1])).filter(p -> p.toString().endsWith(".jar")).toList()) {
            var jar = SecureJar.from(path);
            check(Files.isRegularFile(jar.getPath("META-INF/mods.toml")), "Missing Forge metadata");
        }
        System.out.println("GN_MULTIPLAYER_LOGIC_PASS checks=" + checks + " footprints=PetiteInventory-1.0.9 planner=GN config=installed-Lootr runtimeServer=false");
    }
}
