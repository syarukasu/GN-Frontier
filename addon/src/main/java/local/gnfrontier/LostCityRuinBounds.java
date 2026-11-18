package local.gnfrontier;

import mcjty.lostcities.worldgen.LostCityTerrainFeature;
import mcjty.lostcities.worldgen.lost.BuildingInfo;
import mcjty.lostcities.worldgen.lost.cityassets.BuildingPart;

/** Ruins must cover an entire prefab, including parts taller than a normal floor. */
public final class LostCityRuinBounds {
    private LostCityRuinBounds() {}

    /** -1 keeps native behavior for ordinary six-block floors. */
    public static int tallPartTop(BuildingInfo info) {
        int top = info.getMaxHeight();
        boolean tall = false;
        for (int floor = -info.cellars; floor <= info.getNumFloors(); floor++) {
            if (!info.isValidFloor(floor)) continue;
            int y = info.getCityGroundLevel() + floor * LostCityTerrainFeature.FLOORHEIGHT;
            for (int layer = 0; layer < 2; layer++) {
                BuildingPart part = layer == 0 ? info.getFloor(floor) : info.getFloorPart2(floor);
                if (part == null || part.getSliceCount() <= LostCityTerrainFeature.FLOORHEIGHT) continue;
                tall = true;
                top = Math.max(top, y + part.getSliceCount() - 1);
            }
        }
        return tall ? top : -1;
    }
}
