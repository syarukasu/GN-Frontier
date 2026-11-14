# 開発とGit管理

`main`を基準に、専用MODは`addon/`、設定・日本語・レシピ・loot・生成データは`overlay/`で管理します。パックとコアの共通番号は`VERSION`です。第三者MODのJARは変更しません。

Java 17、Python 3.11以上を用意し、リポジトリのルートで次を実行します。

```powershell
python tools/project.py deps
python tools/project.py check
cd addon
.\gradlew.bat --no-daemon --max-workers=2 jar
cd ..
python tools/project.py check --jar addon/build/libs/gn-frontier-0.2.1.jar
```

Linuxでは`addon`内から`bash gradlew --no-daemon --max-workers=2 jar`を使います。取得する依存物は`pack/build-dependencies.lock.json`のURLとSHA-256で固定し、既存Minecraftインスタンスをビルドに使う必要はありません。

GitHub Actionsは同じ手順でコアをビルドし、番号と内包ライブラリの原版一致を検査します。このCIはゲーム起動や映像一致の検証ではありません。Gradleの古い`verify*`タスクはローカルの過去revisionの資料を必要とするため、クローン直後のCIでは実行しません。

リリース時は変更履歴を更新し、ビルド・必要な隔離起動確認を行ってから`v0.2.1`のようなタグを付けます。配布物の生成はバージョンを共通ファイルから読み、CurseForgeの実在するprojectID/fileIDを使います。未取得のIDを仮の数字で埋めて公開しません。

セーブ、アカウント、ログ、取得済み第三者JAR、生成された配布ZIP、画面検証データ、過去revisionはGitに入れません。動画の原画像・抽出テクスチャもリポジトリに入れません。公開可否が未確認の派生データは[CurseForgeの残件](CURSEFORGE.md)で管理します。

ローカル候補ZIPは、固定原版を取得済みのゲームディレクトリを指定して作れます。

```powershell
python tools/build_release.py --cache <原版ファイルのフォルダー> --local-candidate
```

`--local-candidate`を省略すると、CurseForgeに未掲載のMODがある間は作成を拒否します。ローカル候補ZIPは未公開の検討用で、CurseForge管理・更新が完成した配布物ではありません。`pack/source-paths.json`が各設定の正規ソースを示します。清潔な新規導入用の値が必要な項目では`pack/defaults/`を使用します。
