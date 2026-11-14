package local.gnfrontier.client;
import net.minecraft.network.chat.Component;

/** Literal scan text bridge. The author JAR and scan behavior are unchanged. */
public final class ScanText {
    private static final String[][] PHRASES={
        {"Pointed Dripstone §7- for dripstone collapse in caves","gui.gnfrontier.scan.dripstone_effect"},
        {"and index specific blocks for explosion effects:","gui.gnfrontier.scan.about2"},
        {"Note: §7Block changes are tracked automatically","gui.gnfrontier.scan.note1"},
        {"Redstone Lamps §7- for lamp flickering effects","gui.gnfrontier.scan.lamp_effect"},
        {"Glass Blocks §7- for realistic glass breaking","gui.gnfrontier.scan.glass_effect"},
        {"This world has previously scanned block data.","gui.gnfrontier.scan.saved1"},
        {"This allows the mod to create more realistic","gui.gnfrontier.scan.why1"},
        {"after loading, so both options work well for","gui.gnfrontier.scan.note2"},
        {"Chunk scanning analyzes your world to find","gui.gnfrontier.scan.about1"},
        {"affect your gameplay. You can adjust speed","gui.gnfrontier.scan.performance2"},
        {"Recommended if world changed significantly","gui.gnfrontier.scan.new4"},
        {"Found existing scan data for this world!","gui.gnfrontier.scan.found"},
        {"explosion effects by knowing where these","gui.gnfrontier.scan.why2"},
        {"Scanning happens in background and won't","gui.gnfrontier.scan.performance1"},
        {"May miss blocks placed after last scan","gui.gnfrontier.scan.load3"},
        {"Uses previously found block positions","gui.gnfrontier.scan.load2"},
        {"Scans all chunks for current blocks","gui.gnfrontier.scan.new1"},
        {"Recommended for established worlds","gui.gnfrontier.scan.load4"},
        {"Replaces old data with new results","gui.gnfrontier.scan.new3"},
        {"blocks are located in your world.","gui.gnfrontier.scan.why3"},
        {"Instant setup - no waiting time","gui.gnfrontier.scan.load1"},
        {"Takes time but finds all blocks","gui.gnfrontier.scan.new2"},
        {"- Load existing scan results","gui.gnfrontier.scan.load_desc"},
        {"Chunk Scanning Information","gui.gnfrontier.scan.info_title"},
        {"in mod settings if needed.","gui.gnfrontier.scan.performance3"},
        {"] again to close this info","gui.gnfrontier.scan.close"},
        {"Existing Scan Data Found","gui.gnfrontier.scan.saved_title"},
        {"What is chunk scanning?","gui.gnfrontier.scan.what"},
        {"Start chunk scanning?","gui.gnfrontier.scan.start"},
        {"Scan Complete! (100%)","gui.gnfrontier.scan.complete_full"},
        {"You have two options:","gui.gnfrontier.scan.saved2"},
        {"- Perform fresh scan","gui.gnfrontier.scan.new_desc"},
        {"Rescan all chunks?","gui.gnfrontier.scan.rescan"},
        {"Scanning Chunks: ","gui.gnfrontier.scan.scanning"},
        {"ongoing gameplay.","gui.gnfrontier.scan.note3"},
        {"Why scan chunks?","gui.gnfrontier.scan.why"},
        {"Calculating...","gui.gnfrontier.scan.calculating"},
        {"Dripstones: ","gui.gnfrontier.scan.dripstones"},
        {"Performance:","gui.gnfrontier.scan.performance"},
        {"Load Data","gui.gnfrontier.scan.load"},
        {"Complete!","gui.gnfrontier.scan.complete"},
        {"New Scan","gui.gnfrontier.scan.new"},
        {" = Info","gui.gnfrontier.scan.info"},
        {" chunks","gui.gnfrontier.scan.chunks"},
        {"Lamps: ","gui.gnfrontier.scan.lamps"},
        {"Glass: ","gui.gnfrontier.scan.glass"},
        {"Press ","gui.gnfrontier.scan.press"},
        {" = Yes","gui.gnfrontier.scan.yes"},
        {" = No","gui.gnfrontier.scan.no"},
        {"ETA: ","gui.gnfrontier.scan.eta"}
    };
    public static String localize(String value){
        for(String[] row:PHRASES)if(value.contains(row[0]))value=value.replace(row[0],Component.translatable(row[1]).getString());
        return value;
    }
}
