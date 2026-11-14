GN Frontier 日本語表記 — japanese-pda-r29 / 統合MOD dev.20

翻訳の編集場所
  assets/<MOD名>/lang/ja_jp.json
  assets/gnfrontier/lang/ja_jp.json : 独自アイテム、PDA、手引き、説明文
  client_scripts/gn_frontier_japanese.js : 説明文を表示するアイテムの指定

パックで調整した翻訳はKubeJSのリソースとして読み込まれます。
第三者MODの標準訳は、配布元の無改変JTrans 1.8.4でも補っています。
GN統合MODが日本語の優先順位を整え、KubeJS側で修正した文章を維持します。
ゲームの言語は日本語（ja_jp）を使用してください。新しい版は次回起動から有効です。
PDA、取引、任務、手引きの本文を変えるだけなら、このフォルダーのJSONを編集できます。
死亡時の一言はconfig/enhancedvisuals.json、端末アプリの表示名は
config/chaoscrafts_device_mod/players/default/app_registry.jsonにあります。
これらは各MODの設定形式に従うため、KubeJSの言語JSONとは別です。

表記方針
  廃車・家具・資材は見た目や用途が伝わる名前を使います。
  粗悪な鉄、金属くず、アルミくず、防毒マスク、軍需等級、ノア計画などの呼称を統一しています。
  銃・車両・CPU・GPUの型番、単位、操作キー、コマンドは識別に必要な表記を残します。
  %s、%1$s、%.0f、%%などの差し込み記号、§の装飾指定は消さないでください。

収録範囲
  廃都市の装飾、生活家具、拠点通信の言語キー全件。
  主要な工業・宇宙・生存・敵MODのアイテム、ブロック、敵の名称。
  PDAの地図・取引・依頼・記録、酸欠表示、関連アイテムの説明。
  Anomaly Zoneの会話・手引き、TaCZ Expertの銃器説明・操作案内、部隊設定、工業機械・中間素材、詳細設定。
  最終構成の実際の表示辞書30,078件を照合し、未確認の英文と翻訳上書きは0件。
  型番・単位・キー名・コマンド・MOD名など279件は識別のため原表記を維持。
  全画面の折り返しや、MOD内へ直接書かれた文章まで完全に網羅したことを示す数字ではありません。
  確認していない機械翻訳の下訳は導入していません。

導入記録・訳数・未訳一覧
  作業フォルダー modpacks/gn-frontier/revisions/japanese-pda-r29/ を参照してください。
  JSON・差し込み記号・実表示辞書・リソース優先順位、PDAの倍率2/3/4、実取引と端末不要の開閉を検査。
  日本語資料の出典とライセンスはgn_frontier_licenses/GN_Japanese_sources.jsonに記録。
  確認できた素材・CPU・GPU・マイニング機器の画像は手作業の模写。動画の画素抽出は使用しません。
