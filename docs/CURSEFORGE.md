# 配布方式とCurseForgeの残件

[MODパック](../README.md) · [専用コアMOD](../addon/README.md) · [サーバー](SERVER.md)

## Releaseのファイル

0.2.13の配布ファイルをGitHubで公開しています。依存物は固定した取得元からダウンロードします。[GitHub Release](https://github.com/syarukasu/GN-Frontier/releases/tag/v0.2.13)

| ファイル | 依存物の取得 |
| --- | --- |
| `GN-Frontier-0.2.13-CurseForge.zip` | `manifest.json`の136項目をランチャーが取得。残り8項目は付属スクリプトで補完 |
| `GN-Frontier-0.2.13.mrpack` | Prismが144項目をURLとハッシュから取得。外部配布元の確認が出る場合あり |
| `GN-Frontier-0.2.13-Server.zip` | Windows／Linuxの起動スクリプトが115項目とForgeを取得 |
| `gn-frontier-0.2.13.jar` | パックのメタデータが参照する専用コアの単独取得先 |
| `downloads.lock.json` / `SHA256SUMS.txt` | 全依存物の固定メタデータ／Release成果物の照合用ハッシュ |

`pack/runtime.lock.json`に第三者原版のURL、配布元ID、サイズ、SHA-1・SHA-256・SHA-512を固定します。専用コアはビルド後のハッシュとRelease URLを生成時に追加します。Prismの取得元表示用`.pw.toml`にも、実在するCurseForge／ModrinthのIDを使います。

## CurseForge形式の補完

CurseForge標準のmanifestはprojectIDとfileIDを参照する形式で、任意の外部URLを自動取得する機能はありません。公式ガイドでは非掲載MODを`overrides`へ同梱する方式が案内されていますが、このパックはJAR非同梱の方針のため、インポート後に付属スクリプトを実行する構成です。[公式のエクスポート仕様](https://support.curseforge.com/support/solutions/articles/9000197908-exporting-a-modpack-for-curseforge-project-submission)

Windowsはインスタンスのゲームフォルダーで`Install-Extras.bat`、Linuxは`bash install-extras.sh`を実行してください。次の8項目を取得します。

- ExfilZones 2.0：Modrinthの原版。
- Veil 1.0.0.296：作者MavenのForge 1.20.1用原版。
- GN Frontierコア：このリポジトリのRelease。
- Gunpowder Revolution、Trench Tactics、Phoenix、Cold War、Apocalypse：公式配布の5個のZIPを`tacz/`へ配置。

FreeZBeとGucci & VuittonはMOD形式のため、通常のCurseForge manifest側に含まれます。Create同梱のFlywheel・Ponder・RegistrateはJar-in-Jarで読み込み、重複したJARを別途取得しません。

## インポート時に全依存物を取得する場合

Prism Launcherで`.mrpack`をインポートしてください。[mrpackの仕様](https://support.modrinth.com/en/articles/8802351-modrinth-modpack-format-mrpack)は配置先・URL・ハッシュを記録でき、Prismも[URL取得とSHA-512照合](https://github.com/PrismLauncher/PrismLauncher/blob/develop/launcher/modplatform/modrinth/ModrinthInstanceCreationTask.cpp)に対応しています。Prismの外部配布元確認は省略しません。

これはGitHubから直接配布するPrism向けファイルです。ForgeCDNと作者MavenのURLを含むため、Modrinthサイトの投稿先ドメイン制限を満たす配布物ではありません。

## CurseForgeサイトへの掲載・更新管理の残件

- GN Frontierコアとパック本体の正式なprojectID／fileID。未取得のため、更新欄に架空の値は設定しません。
- [ExfilZones](https://modrinth.com/mod/exfilzones)と[Veil](https://www.curseforge.com/minecraft/mc-mods/veil-lib)の対象バージョンのCurseForge掲載対応。
- 補完スクリプトを含むパックのCurseForge審査、およびアプリからの実インポート・更新確認。
- ChaosZPack等に由来する修正済み生成データと外部素材について、CurseForge掲載条件の最終確認。

GitHub Releaseでの配布と、CurseForgeサイトへの掲載・ランチャーの自動更新管理は別の段階です。現状を「全MODをCurseForgeだけから取得可能」「CurseForgeの管理・更新が完成」とは扱いません。
