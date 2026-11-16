# GN Frontier

**廃都市を探索し、物資を回収し、拠点を整える。**

![Minecraft 1.20.1](https://img.shields.io/badge/Minecraft-1.20.1-5C8C3C)
![Forge 47.4.20](https://img.shields.io/badge/Forge-47.4.20-6D6D6D)
[![Core build](https://github.com/syarukasu/GN-Frontier/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/syarukasu/GN-Frontier/actions/workflows/build.yml)

**[ダウンロード](https://github.com/syarukasu/GN-Frontier/releases/tag/v0.2.2)** · [専用コアMOD](addon/README.md) · [サーバーの導入](docs/SERVER.md) · [更新履歴](CHANGELOG.md) · [不具合の報告](https://github.com/syarukasu/GN-Frontier/issues/new/choose)

GN Frontierは、動画の世界観とゲーム体験の再現を目指す、Minecraft Forge 1.20.1向けのMODパックです。広い廃都市と自然地形を舞台に、銃器を使った戦闘、勢力間の衝突、物資の回収、取引、工業設備の整備を組み合わせます。

このリポジトリでMODパックの設定・データ・専用MODと、不具合や再現上の課題を管理しています。ソースの開発版は**0.2.9**、公開済み配布物は**0.2.2**です。

## 遊びの特徴

- **廃都市と自然地形の探索** — Lost Cities系の都市と、Terralith・Arboriaによる地形や植生を組み合わせます。
- **回収から始まる生活と工業** — 建物や車両から物資を集め、道具・設備・拠点を整えます。
- **銃器と勢力の衝突** — TaCZとガンパックを軸に、人間の勢力と敵モブが入り交じる環境を作ります。
- **PDAで取引と依頼** — ホットキーで開き、売買や任務を確認。納品は電子部品・工業資材・医療品・銃などを1〜3品で組み合わせ、多量の依頼では品目数を減らします。
- **マルチプレイ向けの物資回収** — Lootrと専用処理で、対応するコンテナの戦利品をプレイヤーごとに管理します。

新規ワールドは専用生成プリセット・ハード難易度です。ワールド名、サバイバル／クリエイティブ、チートのオン／オフを選べます。

## 導入・配布状況

| 項目 | 現在の状態 |
| --- | --- |
| MODパック / 専用コアMOD | 共通バージョン **0.2.9**（開発版） |
| Minecraft / Forge | **1.20.1 / 47.4.20** |
| Java | **17** |
| ダウンロード | [GitHub Releases](https://github.com/syarukasu/GN-Frontier/releases/tag/v0.2.2) |
| CurseForgeサイトへの掲載 | 準備中。公開IDと一部依存物の掲載対応が残っています |
| ソース・設定 | このGitHubリポジトリで公開 |
| 開発用コアMODのビルド | [GitHub Actions](https://github.com/syarukasu/GN-Frontier/actions/workflows/build.yml)の成功した実行から確認できます |

Releaseから、利用するランチャーに合うファイルを選んでください。いずれのパックにもMODのJARを同梱せず、固定した取得元から原版をダウンロードします。

| ファイル | 導入方法 |
| --- | --- |
| `GN-Frontier-0.2.2-CurseForge.zip` | ランチャーでインポート後、ゲームフォルダーの`Install-Extras.bat`を一度実行。Linuxは`bash install-extras.sh` |
| `GN-Frontier-0.2.2.mrpack` | Prism Launcherでインポート。外部URLの確認が出た場合は取得元を確認して続行すると、コア・ガンパックまで自動取得 |
| `GN-Frontier-0.2.2-Server.zip` | 新しいフォルダーへ解凍し、`startserver.bat`または`bash startserver.sh`を実行。[詳しい手順](docs/SERVER.md) |

CurseForge標準のインポートは外部URLのメタデータを処理しないため、CurseForge形式には上記の補完手順が必要です。`.mrpack`はPrism向けの直接配布で、Modrinthサイトへの掲載用ではありません。配布方式とCurseForgeの公開ID・更新管理の残件は[配布ガイド](docs/CURSEFORGE.md)を参照してください。

Releaseの`gn-frontier-0.2.2.jar`はメタデータが参照する専用コアの取得先です。パックを導入すれば取得されるため、通常は手作業で追加する必要はありません。GitHubが自動生成する「Source code」のZIPは導入用ではありません。

動画の生成MODやseedには未特定の部分があり、現在の構成には代替実装も含まれます。実装済み・検証済みの範囲と残件は[確認状況](docs/STATUS.md)を参照してください。

## 困ったとき・動画との違いを見つけたとき

まず[既知の課題](docs/STATUS.md)と[既存のIssue](https://github.com/syarukasu/GN-Frontier/issues)を確認し、該当するフォームから報告してください。専用コアMODの問題も同じ窓口で受け付けます。

- [不具合・クラッシュを報告](https://github.com/syarukasu/GN-Frontier/issues/new?template=bug-report.yml) — パックのバージョン、発生状況、再現手順、追加・変更したMODや設定を記載してください。
- [動画との差・未実装要素を報告](https://github.com/syarukasu/GN-Frontier/issues/new?template=video-difference.yml) — 動画の回・URL・時刻と、確認できた内容を記載してください。関連する[改変ID](pack/modification-id-register.json)が分かれば添えてください。

ログや画像を公開する際は、個人用パス・接続先・認証情報が含まれていないか確認してください。

## 専用MOD・開発資料

**[GN Frontier 専用コアMODのページへ →](addon/README.md)**

専用MODは1本に統合し、同じリポジトリの`addon/`で管理します。パックとコアは[共通バージョン](VERSION)を使います。番号は左から「最終回後の答え合わせ・正式リリース」「動画の回」「更新回数」です。詳しくは[バージョンの規則](docs/VERSIONING.md)を参照してください。

| 資料 | 内容 |
| --- | --- |
| [開発・ビルド手順](docs/DEVELOPMENT.md) | 依存物の取得、ビルド、検証、配布物の作成 |
| [更新履歴](CHANGELOG.md) | バージョンごとの変更点 |
| [確認状況](docs/STATUS.md) | 実施した検証と残っている課題 |
| [改変ID台帳](pack/modification-id-register.json) | 改変内容を追跡する固定ID |
| [MOD・ガンパックの依存リスト](pack/runtime.lock.json) | 配布元、固定バージョン、ファイルID、ハッシュ |

## ライセンス・クレジット

GN Frontierの独自コードは[MIT](LICENSE)です。第三者MODの配布JARは原版を使用し、ライブラリ・テクスチャ・生成データには各配布元の条件が適用されます。詳細は[第三者ライセンスとクレジット](THIRD_PARTY.md)を参照してください。
