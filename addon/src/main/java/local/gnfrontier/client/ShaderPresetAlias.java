package local.gnfrontier.client;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

public final class ShaderPresetAlias {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PRESET = "GN Frontier Shaders.zip";
    private static final String UPSTREAM = "ComplementaryUnbound_r5.9.3.zip";
    private static final int SIZE = 553400;
    private static final String SHA256 = "98ac741dee294d2a3c82d153349148c3bb7fb688c30eb3b49c3b9ac37e968c5a";

    private ShaderPresetAlias() {}

    public static void prepare(Path shaderpacks, String selection) {
        if (!PRESET.equals(selection)) return;
        Path target = shaderpacks.resolve(PRESET);
        if (Files.exists(target, NOFOLLOW_LINKS)) return;

        Path upstream = shaderpacks.resolve(UPSTREAM);
        if (!Files.isRegularFile(upstream)) {
            LOGGER.warn("GN Frontier: install {} to use {}", UPSTREAM, PRESET);
            return;
        }
        try {
            byte[] archive;
            try (var input = Files.newInputStream(upstream)) {
                archive = input.readNBytes(SIZE + 1);
            }
            if (archive.length != SIZE || !SHA256.equals(sha256(archive))) {
                LOGGER.warn("GN Frontier: {} does not match the official archive", UPSTREAM);
                return;
            }
            Path staged = Files.createTempFile(shaderpacks, ".gn-shader-", ".tmp");
            try {
                Files.write(staged, archive);
                Files.move(staged, target);
                LOGGER.info("GN Frontier: prepared {} from verified {}", PRESET, UPSTREAM);
            } catch (FileAlreadyExistsException ignored) {
                // Another process prepared the local copy between the two checks.
            } finally {
                Files.deleteIfExists(staged);
            }
        } catch (IOException error) {
            LOGGER.warn("GN Frontier: could not prepare {}", PRESET, error);
        }
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("Java runtime is missing SHA-256", error);
        }
    }
}
