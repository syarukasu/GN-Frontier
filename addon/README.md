# GN Frontier — 専用コアMOD

**[← GN Frontier MODパックのページ](../README.md)** · [不具合の報告](https://github.com/syarukasu/GN-Frontier/issues/new?template=bug-report.yml) · [開発・ビルド手順](../docs/DEVELOPMENT.md)

[![Core build](https://github.com/syarukasu/GN-Frontier/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/syarukasu/GN-Frontier/actions/workflows/build.yml)

GN Frontierのパック専用処理をまとめた、Forge 1.20.1向けのコアMODです。PDA・取引・依頼、回収と生存ルール、敵対関係、各MODとの互換処理をこの1本で扱います。

| 項目 | 内容 |
| --- | --- |
| 表示名 / MOD ID | GN Frontier / `gnfrontier` |
| バージョン | パックと共通の **0.2.6**（[VERSION](../VERSION)、開発版） |
| 対応環境 | Minecraft **1.20.1** / Forge **47.4.20** / Java **17** |
| 利用するMODパック | [GN Frontier](../README.md) |
| ソース | [src/main](src/main/) |

## 担当する機能

| 分野 | 主な処理 |
| --- | --- |
| PDA・経済・依頼 | ホットキーで開くPDA、取引、日次依頼、納品物の組み合わせ、専用任務 |
| 回収と探索 | 専用モデルの車両の解体、適正ツールとボーナスドロップ、窓蹴り、対応家具の個人別loot |
| 生存と拠点 | リスポーン地点の登録・変更待ち時間・保護、登録されたベッドに対する敵の行動 |
| 勢力と戦闘 | 人間の勢力同士や敵モブとの敵対関係、採掘行動、銃弾の防御処理、正規軍の装備・携行品、共有の遺体、時間制の捜索、回収装備に連動する外観 |
| パックの統合 | ワールド作成画面、専用プリセットとハード難易度の固定、都市中心ごとの住宅・工業・軍事スタイル、既存MODとの互換処理 |

確認できた挙動と未確認の範囲は[検証・残件一覧](../docs/STATUS.md)、各改変の管理番号は[改変ID台帳](../pack/modification-id-register.json)を参照してください。

## MODパックとの関係

コアMODは、GN Frontierの設定・KubeJS・データと組み合わせて使います。パック全体の導入・配布状況は[MODパックのページ](../README.md)にまとめています。

| 管理場所 | 内容 |
| --- | --- |
| この`addon/` | 専用の動作と互換処理を実装するJavaソース |
| [overlay/](../overlay/) | config、KubeJS、日本語、レシピ、loot、生成データ |
| [pack/defaults/](../pack/defaults/) | 新規導入用の既定設定 |
| [pack/runtime.lock.json](../pack/runtime.lock.json) | パックを構成するMOD・ガンパックの原版と取得元 |

第三者MODのJARは書き換えず、configやKubeJSで調整できない動作をこのコアに実装します。必須依存MODは[mods.toml](src/main/resources/META-INF/mods.toml)、ビルド依存物は[固定依存リスト](../pack/build-dependencies.lock.json)で管理しています。

## 開発・ビルド

リポジトリ全体を取得し、[開発・ビルド手順](../docs/DEVELOPMENT.md)に従ってください。`addon/`だけではなく、ルートの`VERSION`と固定依存リストを使ってビルドします。

生成されるJARは`build/libs/gn-frontier-<バージョン>.jar`です。[Release](https://github.com/syarukasu/GN-Frontier/releases/tag/v0.2.2)では、パックのメタデータが参照する取得先として単独のJARを公開します。パックZIP・mrpack・サーバーZIPには同梱しません。[GitHub Actions](https://github.com/syarukasu/GN-Frontier/actions/workflows/build.yml)の`gn-frontier-core`は開発確認用の成果物です。

## 不具合の報告

[GN Frontier共通の報告フォーム](https://github.com/syarukasu/GN-Frontier/issues/new?template=bug-report.yml)で、対象に「専用コアMOD」を選んでください。バージョン、再現手順、追加・変更したMODや設定を添えると調査しやすくなります。

動画との差を報告する場合は、[動画との差・未実装要素フォーム](https://github.com/syarukasu/GN-Frontier/issues/new?template=video-difference.yml)に回・URL・時刻を記載してください。

## ライセンス

独自コードには[MITライセンス](../LICENSE)が適用されます。内包する原版ライブラリ等の条件は[第三者ライセンスとクレジット](../THIRD_PARTY.md)を参照してください。

**[← MODパックの概要・配布状況へ戻る](../README.md)**
