# サーバーの導入

[MODパック](../README.md) · [専用コアMOD](../addon/README.md) · [配布方式](CURSEFORGE.md)

クライアントと同じバージョンの`GN-Frontier-<version>-Server.zip`を[Release](https://github.com/syarukasu/GN-Frontier/releases/tag/v0.2.2)から取得します。MODとForgeのJARは入っていません。初回起動で、メタデータに固定した原版を取得してSHA-256を検証します。

## 初回セットアップ

1. 新しいフォルダーにサーバーZIPを解凍します。クライアント用パックと混ぜないでください。
2. **64ビットのJava 17**を用意します。WindowsはPowerShell 5.1以上、LinuxはPython 3.9以上も必要です。
3. Windowsは`startserver.bat`、Linuxは`bash startserver.sh`を実行します。MOD・ガンパック・Forgeを取得し、Forgeの専用サーバーをセットアップします。
4. [MinecraftのEULA](https://www.minecraft.net/eula)を確認し、同意する場合は`eula.txt`の`eula=false`を`eula=true`へ変更します。スクリプトが自動で同意することはありません。
5. 起動スクリプトを再実行します。ログに`Done`が出れば起動完了です。終了時はコンソールへ`stop`を入力します。

複数のJavaを使う場合、環境変数`GN_JAVA`にJava 17の実行ファイルを指定します。取得済みの正しいファイルは再ダウンロードしません。内容が違うファイルは、置き換える原版の検証に成功してから`GN-Frontier-Setup/backups/`へ退避します。

| 操作 | Windows | Linux |
| --- | --- | --- |
| セットアップと起動 | `startserver.bat` | `bash startserver.sh` |
| セットアップのみ | `startserver.bat -InstallOnly` | `bash startserver.sh --install-only` |
| MOD・ガンパックのハッシュ照合のみ | `startserver.bat -VerifyOnly` | `bash startserver.sh --verify-only` |

## 設定

| ファイル | 内容 |
| --- | --- |
| `user_jvm_args.txt` | 初期2GB／最大8GBのメモリ設定。人数や生成負荷に合わせて変更 |
| `server.properties` | サバイバル、ハード、オンライン認証、ポート25565、描画距離8／演算距離6 |
| `defaultconfigs/lostcities-server.toml` | 新規ワールドの専用プロファイル`gn_azzz15flat_nature` |
| `GN-Frontier-Setup/downloads.json` | 原版のURL・サイズ・ハッシュ、固定したForgeの取得元 |

サーバーには、クライアント用の24本のMOD、シェーダー、リソースパック、画面設定を含めません。Xaero's World Mapは共有ワールドに登録される4つの地図制限効果を保つためサーバー側にも残します。ゲーム内ルール・レシピ・生成・loot・勢力処理を保ち、0.2.2では**トップレベル107本のJARと5個のガンパックZIP**を取得します。難易度は専用コアのルールによりハード固定です。

RCONとコマンドブロックは既定で無効です。参加者へOPを与えるかどうか、ホワイトリスト、接続先の公開方法は運用者が設定してください。

## 更新と確認範囲

更新前に`stop`で保存・停止し、ワールドと変更した設定をバックアップしてください。新しい版は別の空フォルダーにセットアップしてから、停止済みサーバーのワールドと必要な設定を移します。古い`mods/`をコピーすると旧版JARが残るため、取得リストから新しく揃えます。

`config/`と`defaultconfigs/`は新版を基準に差分を確認します。既存ワールドの`serverconfig/`はワールドに保存されている値が優先されるため、必要な変更だけを反映します。クライアントも同じパック番号に揃えてください。

実施した検証と残っている制約は[確認状況](STATUS.md)に記載します。初期メモリ値は性能保証ではなく、長期マルチプレイの負荷検証とは分けています。

初回Forgeセットアップ、Windows／Linuxの起動、`user_jvm_args.txt`と`server.properties`を分ける構成は、[All the Modsのサーバー導入ガイド](https://allthemods.github.io/alltheguides/help/server/)を参考にしています。セットアップコードはこのパック用に実装しています。
