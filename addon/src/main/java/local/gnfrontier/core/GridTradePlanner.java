package local.gnfrontier.core;

import com.sighs.petiteinventory.init.Area;
import java.util.List;

/** Detached transaction simulation using the Petite Inventory dimensions, isolated from live inventory. */
public final class GridTradePlanner {
    private static final class OccupancyGrid {
        private final int width, height;
        private final boolean[] occupied;
        OccupancyGrid(int width, int height) { this.width=width; this.height=height; occupied=new boolean[width*height]; }
        boolean rectFitsAndIsEmpty(int x, int y, int w, int h) {
            if (w < 1 || h < 1 || x < 0 || y < 0 || x+w > width || y+h > height) return false;
            for (int dy=0; dy<h; dy++) for (int dx=0; dx<w; dx++) if (occupied[(y+dy)*width+x+dx]) return false;
            return true;
        }
        void occupyRect(int x, int y, int w, int h, int anchor, Area shape) {
            for (int dy=0; dy<h; dy++) for (int dx=0; dx<w; dx++) occupied[(y+dy)*width+x+dx]=true;
        }
    }
    public record Slot(int count, int limit, boolean tradable, Area footprint) {}
    public static boolean usable(int i) { return i >= 0 && (i < 5 || i >= 9 && i < 36); }
    private static int[] copy(List<Slot> slots) {
        if (slots.size() != 36) throw new IllegalArgumentException("Expected 36 player slots");
        return slots.stream().mapToInt(Slot::count).toArray();
    }
    public static int count(List<Slot> slots) {
        int total = 0;
        for (int i = 0; i < 36; i++) if (usable(i) && slots.get(i).tradable()) total = Math.addExact(total, slots.get(i).count());
        return total;
    }
    private static int insert(List<Slot> slots, int[] counts, Area item, int limit, int remaining) {
        var grid = new OccupancyGrid(9, 3);
        for (int i = 9; i < 36; i++) if (counts[i] > 0) {
            var f = slots.get(i).footprint(); int x = (i - 9) % 9, y = (i - 9) / 9;
            if (!grid.rectFitsAndIsEmpty(x, y, f.width(), f.height())) throw new IllegalArgumentException("収納の配置が重なっています。整理してから再試行してください");
            grid.occupyRect(x, y, f.width(), f.height(), i, f);
        }
        for (int i = 0; i < 36 && remaining > 0; i++) if (usable(i) && counts[i] > 0 && slots.get(i).tradable()) {
            int amount = Math.min(remaining, Math.max(0, Math.min(limit, slots.get(i).limit()) - counts[i]));
            counts[i] += amount; remaining -= amount;
        }
        for (int i = 0; i < 36 && remaining > 0; i++) if (usable(i) && counts[i] == 0) {
            int x = (i - 9) % 9, y = (i - 9) / 9;
            if (i >= 9 && !grid.rectFitsAndIsEmpty(x, y, item.width(), item.height())) continue;
            int amount = Math.min(remaining, limit); counts[i] = amount; remaining -= amount;
            if (i >= 9) grid.occupyRect(x, y, item.width(), item.height(), i, item);
        }
        return remaining;
    }
    public static int capacity(List<Slot> slots, Area item, int limit) {
        return Integer.MAX_VALUE - insert(slots, copy(slots), item, limit, Integer.MAX_VALUE);
    }
    public static int[] plan(List<Slot> slots, Area item, int limit, int remove, int add) {
        if (remove < 0 || add < 0 || limit <= 0) throw new IllegalArgumentException("Invalid transaction");
        int[] counts = copy(slots);
        for (int i = 0; i < 36 && remove > 0; i++) if (usable(i) && slots.get(i).tradable()) {
            int amount = Math.min(remove, counts[i]); counts[i] -= amount; remove -= amount;
        }
        if (remove != 0) throw new IllegalArgumentException("必要なアイテムが不足しています");
        if (add > 0 && insert(slots, counts, item, limit, add) != 0) throw new IllegalArgumentException("購入品を置ける空きマスがありません");
        return counts;
    }
}
