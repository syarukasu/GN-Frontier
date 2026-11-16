# 0.2.6 細部仕様の監査

確認日：2026-11-16。対象：main `66223bd` と、その0.2.6構成の設定・原版MOD。以下のA番号は今回の指摘番号で、実装済み改変のGN番号とは別です。

**対応後の記録：** A01〜A07とC01〜C03は0.2.7で修正・検証し、指定インスタンスへ導入しました。[現在の確認状況](STATUS.md#027--細部監査の修正とpda表示の照合)と[0.2.7検証記録](../pack/validation-0.2.7.json)を参照してください。以下は修正前の0.2.6監査記録です。

現行コード・設定と、既存の動画観測記録E001〜E079およびユーザーの確定指示を照合しました。今回はゲーム起動、画面操作、画面キャプチャ、元ワールドへの書き込み、JAR交換を行っていません。コード上の経路・条件の欠落を確認した記録であり、全項目をゲーム内で再現したという意味ではありません。

## 優先して直す細部

| 指摘 | 優先度 | 関連する改変 | 現状と足りない判定 |
| --- | --- | --- | --- |
| A01 | 高 | GN-038 / GN-046 / GN-075 | **通信妨害中も地図が止まらない。** `signalLost` はGN側の取引・依頼・統計ページで判定する一方、MAPタブはその描画処理へ入る前に元MODへ戻す。独立したXaeroのMキーも有効。動画E034の「地図にSIGNAL LOST」を満たしていない。 |
| A02 | 高 | GN-023 / GN-034 | **友好化した軍も登録ベッドを攻撃候補にできる。** ベッド探索は全アカウントの登録座標を集め、軍・PMC・盗賊であるかだけを判定する。持ち主との友好・中立・敵対関係を使う除外がない。友好化E049とベッド破壊AIを接続する条件が欠けている。友軍がベッドを壊さない仕様自体は動画で直接確定したものではなく、既存機能間の整合性の指摘。 |
| A03 | 高 | GN-034 / GN-086 | **東西軍の工兵・衛生兵などを倒しても、GNの評判・警戒度に反映しない。** GNの撃破ペナルティは `simpleenemymod:ruunit` / `usunit` の2種に限定。`tacz_sewv` 側の東西支援兵・戦闘工兵は陣営・装備側にはあるが、この分岐へ入らない。元MOD独自の外交反応とは別に、PDA側の数値が連動しない。 |
| A04 | 高 | GN-031 / GN-069 | **別の敵と交戦中の兵士を倒すと、討伐依頼に数えない場合がある。** 通常兵の計上条件は「現在の標的がServerPlayer」または「自分向け増援タグ」。自分と敵対する兵士でも、ゾンビ・スケルトン・他陣営を標的にしている間や、まだ標的を取っていない間は漏れる。さらに兵士用分岐は `simpleenemymod` だけで、支援兵は兵士扱いの対象外。外交関係と撃破判定を統一できていない。 |
| A05 | 中 | GN-031 / GN-069 | **納品内容が同じ依頼を文面違いで再掲示できる。** 永続重複キーに文章モジュール・本文を含めるため、依頼主・品・個数が同じでも本文が変わると新規扱い。納品物の重複回避は直近9対象までで、候補不足時には緩和する。「同じ任務をできないように」を納品内容の同一性まで含めるなら未達。完全に同じ文面の再発行防止は存在している。 |
| A06 | 中 | GN-027 / GN-028 | **Vキーの競合を整理できていない。** 導入先でGNの蹴り、TaCZの近接攻撃・ズーム、ほかの状況別操作がVに割り当てられている。GNの蹴り送信は銃・照準状態を考慮せず、他の操作との排他条件がない。競合割当は確認済み。どの装備・姿勢で両方発動するかの接続クライアント確認は未実施。 |
| A07 | 中 | GN-057 / GN-071 | **日光耐性が、日中屋外での他の炎上も消す。** ゾンビは「昼・上空が見える・溶岩中でない・足元がFIREタグでない」で毎tick `clearFire()`。炎の矢など別の原因による炎上も区別しない。動画E019の「昼に燃えない」を実現する処理の副作用で、一般の火炎攻撃まで消火対象になる。 |

### コードの根拠

- A01：[`NativePdaPagesMixin`](../addon/src/main/java/local/gnfrontier/mixin/NativePdaPagesMixin.java) のMAP早期return、[`PdaScreen`](../addon/src/main/java/local/gnfrontier/client/PdaScreen.java) の`renderPage`、[`PdaMapClipMixin`](../addon/src/main/java/local/gnfrontier/mixin/PdaMapClipMixin.java)。原版CampChat 1.0.0の埋込地図経路も確認。CampChatのクラスにはGNの`signalLost`を受け取る接続がなく、GNの地図Mixinはクリッピングのみ。導入先optionsの `gui.xaero_open_map=M` も確認した。
- A02：[`RegisteredBedGoal`](../addon/src/main/java/local/gnfrontier/RegisteredBedGoal.java) の`human` / `canUse` / `griefAllowed`、[`RespawnRules`](../addon/src/main/java/local/gnfrontier/RespawnRules.java) の`nearbyRegisteredBeds`、[`DiplomacyBridge`](../addon/src/main/java/local/gnfrontier/DiplomacyBridge.java)。Forgeの保護イベント呼び出しはあるが、GN内の持ち主との外交判定ではない。
- A03：[`Gameplay`](../addon/src/main/java/local/gnfrontier/Gameplay.java) の`death`。`Economy.aggression`への参照を全専用MOD内で検索し、別経路での支援兵反映がないことを確認した。`DiplomacyBridge`はGNの状態を元MODへ送る方向。
- A04：[`CampaignEvents`](../addon/src/main/java/local/gnfrontier/CampaignEvents.java) の`killed`、[依頼カタログ](../overlay/kubejs/data/gnfrontier/daily_missions/catalog.json) の`hostile_infantry`。現在の兵士対象はruunit/usunit/pmcunit。
- A05：[`DeliveryModules`](../addon/src/main/java/local/gnfrontier/core/DeliveryModules.java) の`identity` / `appearance`、[`DailyMissions`](../addon/src/main/java/local/gnfrontier/core/DailyMissions.java) の`available` / `drawModules`。
- A06：[`KickClient`](../addon/src/main/java/local/gnfrontier/client/KickClient.java)、配布元の[初期キー設定](../overlay/options.txt)。導入先optionsの実値、および原版TaCZ 1.2.0の`MeleeKey.onMeleePress`も確認した。キー重複だけから、常に全操作が同時発動するとまでは判断していない。
- A07：[`Gameplay`](../addon/src/main/java/local/gnfrontier/Gameplay.java) の`livingTick`。

### A05の実行確認

[診断プログラム](../tools/audit_quest_repetition.java)から、現行ビルド済みの`DailyMissions.draw`を365日分呼び出した。UUIDは固定の検証用値で、実プレイヤー情報・セーブ・ワールドは使用しない。実際の受注・納品操作ではなく、掲示される依頼の重複を調べている。

- 闇商人：Colt M1892×1 + Webley×1 が、内部日番号2と29、さらに34に掲示された。各回の内部IDと本文は別。
- 闇商人：MP18×2 が、内部日番号8と35に掲示された。内部IDと本文は別。
- 合計：納品643件、依頼主・品・個数で数えた固有内容458件、同一内容の再掲示185件。
- 「同じ任務」を本文の組み合わせまで含めて定義するなら、現行の重複防止はその定義に沿う。納品内容だけの永久重複禁止は実装していない。

## 未実装と断定しない要確認事項

| 指摘 | 関連する改変 | コードで分かったこと / 残る確認 |
| --- | --- | --- |
| C01 | GN-087 | 防具を剥いだ後の元モデル表面の置換はRUunitModel/USunitModelだけ。原版にはPmcUnitModel/PmcCommanderModelもある。実装対象が限定されていることは確定しているが、PMCの元テクスチャ・専用描画レイヤーまで見て、回収後に防具らしい外観が残るかは未確認。全兵種の外観連動を完了扱いにはできない。 |
| C02 | GN-088 / GN-057 | 昼の通常追加ゾンビは`MobSpawnType.EVENT`で作り、周囲48mの10体制限だけを使う。In Controlの上限はnatural/chunk_generationを対象にしている。設定の適用範囲が別であることは確定しているが、実際に総数超過・負荷増を起こすかは未測定。増援・コマンド出現を一律に制限するのではなく、通常の昼湧きを共通管理に含めるかの整理が必要。 |
| C03 | GN-069 | 日替わりは`getGameTime()/24000`、残り時間も同じ時計。`dayTime`の朝とは別。時刻変更や時刻固定後は朝との対応がずれるが、動画が必ず朝更新なのかは確定していないため、現時点で再現違反とは断定しない。 |

## 今回、未実装から除外したもの

- 作業台の手元2×2レシピは上段が粗悪な鉄2、下段が板材2になっている。
- 白いベッドの作成材料は専用の「丈夫な布」。普通の布だけで作る旧レシピという指摘は当たらない。
- 鉄インゴットへの元レシピは出力指定で除去されている。元の鉄鉱石レシピがそのままCreateのファン精錬を通る、という推測は採用しない。
- 夜生まれゾンビの強化はGN-096として0.2.6に実装・導入済み。攻撃力をプレイヤーだけに特別加算する実装でもない。
- ホットバー溢れ品の保留データは、死亡後の新プレイヤーへコピーする処理が見つからなかった。「保留品が死亡後に戻る」という推測は確認済み不具合に数えない。

## 既知の大きい不足と区別するもの

かまどの実修理、Noahの新しい居住可能世界、既存チャンクの欠けた可視車体の復元、CurseForgeへの正式掲載・実インポートは以前からの残件。動画で数値・条件が不明なものと、機能自体に未対応の箇所を一括して「未着手」に数えない。今回のA/C指摘は、これらとは別の細部監査である。

この監査でゲーム仕様は変更していない。バージョンは0.2.6のまま。
