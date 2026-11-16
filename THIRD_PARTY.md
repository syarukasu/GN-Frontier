# Third-party notices

The MIT license at the repository root covers original GN Frontier code and configuration only. It does not relicense other mods, textures, generated structures or supplied reference material.

Upstream mods and gunpacks are pinned by URL, project/file IDs and SHA-256 in `pack/runtime.lock.json`. They are downloaded without rewriting their archives. See each upstream project for its license and attribution. ExfilZones is by fhsdhl; its current distribution is on Modrinth.

The core embeds unchanged Molang Compiler 3.1.1.19 (Ocelot, MIT) and imgui-java 1.86.11 (Ilya Prymshyts / SpaiR, Apache-2.0, binding/lwjgl3/Windows natives). Original files are fetched by `tools/project.py deps`; their checksums are in `pack/build-dependencies.lock.json`. Notices are in `licenses/`.

Arboria 1.1.2 generation data is by Sunna (MIT). The original license is retained in `licenses/`. Some corrected structure data derives from ChaosZPack and Ad Astra. Public redistribution review for derived data remains open; see `docs/CURSEFORGE.md`. The public repository uses privacy-reviewed history. The original preparation history remains in a separate private archive.

Japanese translations and pack rules are applied through KubeJS/resources or the one GN Frontier core. No third-party Java class is copied into the core as a patched replacement.

City facility geometry under `addon/src/main/resources/data/gnfrontier/lostcities/` derives from Modern Structures 1.6 (upstream credits: User, MCreator), distributed under Academic Free License 3.0. Original archive: https://www.curseforge.com/projects/967101/files/5953626. The factory, green-roof factory, bunker and research outpost were split into Lost Cities parts; geometry and block states are retained, coordinates are made relative and container loot uses GN industrial/military tables. These derived assets retain AFL-3.0, not the root MIT license. Source hashes and changes: `addon/city-template-sources.json`; reproducible conversion: `addon/tools/generate_city_assets.py`. No upstream Java class or texture is included. License text: https://opensource.org/license/afl-3-0/.
