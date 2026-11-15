# 更新履歴

## 0.2.2 — 2026-11-15

- JARを同梱しないCurseForge形式ZIP、Prism向けmrpack、サーバーZIPの生成を追加。
- 全依存物の取得元・サイズ・SHA-1／SHA-256／SHA-512を固定。専用コアはReleaseの単独JARから取得。
- CurseForge未掲載MODと`tacz/`に置くガンパックの補完スクリプトを追加。WindowsはPowerShell、LinuxはPython標準ライブラリだけで動作。
- サーバーからクライアント専用MOD・シェーダー・画面設定を除外。Java 17／Forgeの初回セットアップ、EULA、メモリ設定、起動スクリプトを用意。
- ExfilZonesの描画イベント登録とAnomaly Zoneの依頼画面パケットが、専用サーバーでクライアントクラスを読み込む起動不具合をコア内で補正。第三者JARは原版のまま保持。
- ダウンロードの失敗時の保全、パック内のJAR混入、依存メタデータの網羅性を自動検査。
- パックと専用コアのバージョンを0.2.2に統一。旧`v0.2.1`タグは維持。

## 0.2.1 — 2026-11-15

- 動画2までを対象とする更新1回目として、Git管理を開始。
- パックと専用コアMODの番号を `VERSION` に統一。
- 新規ワールド作成を専用プリセット・ハード難易度に固定。ワールド名、サバイバル／クリエイティブ、チートのオン／オフを選択可能。
- Anomaly Zone推奨のFreeZBe、Phoenix、Cold War、Apocalypse、Gucci & Vuittonと必要なTaCZ Mesh Loaderを追加。
- 追加パックの日本語表示、FreeZBeの素材参照と未使用モデル定義をKubeJSで補正。第三者JARは原版を使用。
- CurseForgeの取得元と固定ファイルIDを整理。公開IDが未取得のものは未完了として管理。
- McJtyLib 8.0.10をCurseForge原版へ、TerraBlenderを対応条件内のCurseForge掲載版3.0.1.10へ統一。
- Create内包版の読み込みを確認し、Flywheel・Ponder・Registrateの重複JAR配置を整理。

個別の導入・起動・公開状況は [確認状況](docs/STATUS.md) に記録します。実装済みと公開済みは別に扱います。
