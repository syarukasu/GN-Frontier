# CurseForgeでの管理と残件

既に掲載されている原版は `pack/runtime.lock.json` にprojectID、fileID、SHA-256、公式CDNのURLを固定します。MOD一覧の取得元表示も対応する実在IDを使います。パックの更新欄へ架空のIDを設定しません。

追加したGucci & Vuittonはプロジェクト1103436／ファイル6062098。McJtyLib 8.0.10はCurseForge原版へ統一し、TerraBlenderは依存条件を満たす掲載版3.0.1.10を確認しています。Create同梱のFlywheel・Ponder・Registrateは同じ原版の重複配置を外し、Create内のJar-in-Jarで読み込みます。

残件：

- GN FrontierコアMODとパック本体の正式なprojectID／fileID。現在は未公開です。
- ExfilZones 2.0：現在の取得元はModrinth。CurseForge掲載版を確認できていません。機能を黙って削除したり、別作者のMODとして再公開したりしません。
- Veil 1.20.1用1.0.0.296：現在は作者Mavenの原版。CurseForgeのVeil Libは調査時点で1.21.1向けしか掲載を確認できていません。
- ガンパックZIPの`tacz/`への配置を含む、CurseForgeアプリからの実インポート・更新の最終確認。
- ChaosZPack等に由来する修正済み生成データと配布物に同梱する外部素材の公開条件の最終確認。

https://www.curseforge.com/minecraft/mc-mods/tacz-gucci-vuitton-attachments/files/6062098
https://www.curseforge.com/minecraft/mc-mods/veil-lib
https://modrinth.com/mod/exfilzones

これらが解消するまでは「全MODをCurseForgeだけから取得可能」「PrismのCurseForge管理・更新が完成」とは扱いません。
