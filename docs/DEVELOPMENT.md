# 開発とGit管理

[MODパックの案内](../README.md) · [専用コアMODの案内](../addon/README.md)

`main`を基準に、専用MODは`addon/`、設定・日本語・レシピ・loot・生成データは`overlay/`で管理します。パックとコアの共通番号は`VERSION`です。第三者MODのJARは変更しません。

Java 17、Python 3.11以上を用意し、リポジトリのルートで次を実行します。

```powershell
python tools/project.py deps
python tools/project.py check
cd addon
.\gradlew.bat --no-daemon --max-workers=2 jar
cd ..
python tools/project.py check --jar addon/build/libs/gn-frontier-0.2.2.jar
```

Linuxでは`addon`内から`bash gradlew --no-daemon --max-workers=2 jar`を使います。取得する依存物は`pack/build-dependencies.lock.json`のURLとSHA-256で固定し、既存Minecraftインスタンスをビルドに使う必要はありません。

GitHub Actionsは同じ手順でコアをビルドし、番号と内包ライブラリの原版一致、配布用ダウンローダー、生成した3種類のアーカイブを検査します。このCIはゲーム起動や映像一致の検証ではありません。Gradleの古い`verify*`タスクはローカルの過去revisionの資料を必要とするため、クローン直後のCIでは実行しません。

リリース時は変更履歴を更新し、ビルド・必要な隔離起動確認を行ってから`v0.2.1`のようなタグを付けます。配布物の生成はバージョンを共通ファイルから読み、CurseForgeの実在するprojectID/fileIDを使います。未取得のIDを仮の数字で埋めて公開しません。

セーブ、アカウント、ログ、取得済み第三者JAR、生成された配布ZIP、画面検証データ、過去revisionはGitに入れません。動画の原画像・抽出テクスチャもリポジトリに入れません。公開可否が未確認の派生データは[CurseForgeの残件](CURSEFORGE.md)で管理します。

現行ソースはGitHubの`main`と、このチェックアウトの`addon/`・`overlay/`・`pack/`で管理します。機能を変更するたびにソースやゲーム環境を丸ごと複製しません。必要な開発だけをブランチまたはドラフトPRに分け、検証環境は既存の1組を使い回します。[容量方針](../pack/storage-policy.json)に現在の再利用先を記録します。

過去の検証コピーは、`tools/storage.py`で一覧と整理計画を作成してから適用します。実ワールドと既存の全セーブはその場に保持し、現行ソースと再利用先を対象外にします。古い設定・素材・未公開成果物を含む異なる内容は、SHA-256ごとにローカルへ1個ずつ保存します。内容を照合した重複ファイルだけを取り除き、確認できないファイルは削除しません。記録はGit対象外の`local/`、保管本体は`.cache/review-history/`に置きます。Windowsで実行します。

```powershell
python tools/storage.py inventory --output local/storage-review
python tools/storage.py plan --inventory local/storage-review/inventory.json --output local/storage-plan
python tools/storage.py apply --output local/storage-plan
```

JavaやPrismがこの作業先を使用している場合は適用を拒否します。実行前にパス、リンク、変更日時と容量を確認し、適用時は排他アクセスとSHA-256を再確認します。保管先の照合後に個別ファイルを削除し、最後に空のフォルダーだけを除去します。

コアのビルド後、メタデータから3種類のパックと単独コアを作成します。第三者JARをダウンロードしたり、ゲーム環境を複製したりする必要はありません。

```powershell
python tools/build_release.py
python tools/test_distribution.py --release dist/GN-Frontier-0.2.2
```

出力先は`dist/GN-Frontier-<version>/`です。既存成果物の上書きは拒否するため、検討用には`--output <別の出力先>`を指定します。取得済み原版も検査する場合だけ`--cache <原版ファイルのフォルダー>`を追加します。`pack/source-paths.json`が各設定の正規ソースを示し、新規導入用の値は`pack/defaults/`から取得します。サーバー用の除外規則は`pack/server-profile.json`、起動処理は`pack/bootstrap/`です。

Releaseには生成したCurseForge ZIP・mrpack・サーバーZIP・単独コアJAR・`downloads.lock.json`・`SHA256SUMS.txt`をアップロードします。専用コアのURLは`v<version>`タグのReleaseへ固定されます。全成果物を揃えてから公開し、公開URLとハッシュを再確認します。第三者JARをReleaseへ転載しません。CurseForgeサイトへ投稿する条件は[配布ガイド](CURSEFORGE.md)で別途管理します。
