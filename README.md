# OBS Recorder Indicator

OBS の録画状態を Minecraft に表示する Fabric Mod です。

Shows OBS Studio recording status as a HUD indicator in Minecraft (Fabric).

みなさんこんにちは、そしてはじめまして。[えすGames](https://www.youtube.com/channel/UClnmJsxYKdl2dHwHsqsZVOg)と申します。

まず、このModはMiMoが作成し、私が動作テストをするという形で完成したものです。

また、こちらのREADMEもこの文以外はMiMoに書かせているため、不十分なものがあるかもしれませんし、もちろんコードもMiMoが書いたものなので汚いところがあるかもしれません。

私がこの機能が一番欲しくて作らせたModをみなさんが修正/組込できるように公開を決意したModですので、私が修正を加えることは少ないかもしれませんが、皆さんで好きにフォークして改善していっていただけるとありがたいです！

もちろん機能提案だったり、プルリクエストを送っていただくことも大歓迎です！そういうのが来なくても最新バージョンと1.8.9への対応はさせたいなーと思ってます

結局私が一番助かるのでね()

そんなところです！ありがとうございます！

- 録画中: 赤い丸 + `REC`
- 一時停止: 橙色の丸 + `PAUSED`
- 停止 / 未接続: 非表示

Recording: red circle + `REC`  
Paused: orange + `PAUSED`  
Idle / disconnected: hidden

## OBS の設定 / OBS setup

1. **ツール → WebSocket サーバー設定**
2. **WebSocketサーバーを有効にする**を有効にする
3. **認証を有効にする**はオフ推奨（使う場合は下の `obsPassword` へ）
4. ポートはデフォルト **4455** のままで OK
5. 適用

EN
1. **Tools → WebSocket Server Settings**  
2. Enable **WebSocket server**  
3. Auth off is simplest (or set `obsPassword` below)  
4. Default port **4455** is fine
5. OK

## 必要なもの / Requirements

- Minecraft
- [Fabric Loader](https://fabricmc.net/use/installer/)
- [Fabric API](https://modrinth.com/mod/fabric-api)
- OBS Studio

※バージョン要件は時と場合によります。
Version requirements may vary depending on the situation. 

## コマンド / Commands

| コマンド / Command | 説明 / Description |
|--------------------|--------------------|
| `/obsindicator config` | 設定を開く / Open settings |
| `/obsindicator toggle` | 表示 ON/OFF / Toggle indicator |
| `/obsindicator status` | 接続状態 / Connection status |
| `/obsindicator reconnect` | OBS 再接続 / Reconnect to OBS |

OneConfig を入れていない場合は次も使えます。

Without OneConfig, these are also available:

- `/obsindicator textonly` — 文字のみ / Text only  
- `/obsindicator position <x> <y>` — 位置 / Position  
- `/obsindicator position reset` — 位置リセット / Reset position  

## 位置調整 / Position

### OneConfig を導入している場合 / With OneConfig

インジケーターは OneConfig の HUD として登録されます。

- OneConfig の **HUD エディター** でインジケーターをドラッグして位置を変更(タイトル画面から HUD エディターを開いた場合も同じように動かせます)
- 同じ設定画面の **Scale** でサイズを変更
- 同じ設定画面に **インジケーターの丸 / 文字 / 録画中のテキスト / 録画停止中のテキスト / 録画中のインジケーターの色** が 1 つのリストで並びます(タブはありません)
- **背景** と **影** は OneConfig 標準の項目(**Background** / **Appearance** セクション)で設定します(インジケーターは他の HUD と同じ影で描画されます)
- 各項目名は Minecraft の言語に追従します(言語を切り替えるとその場で切り替わります)
- 位置とサイズは OneConfig 側に保存されます
- 複製するとインジケーターを複数置けます(位置・サイズ・表示/非表示・見た目は 1 個ずつ)
- **Delete HUD**(HUD 設定の最下部、または HUD の右クリックメニュー)は押したインジケーターだけを削除します
  - 他のインジケーターには影響しません
  - すべて削除するとインジケーターは消えたままになり、再起動しても復活しません
  - 戻すときは OneConfig の **HUD ライブラリ** から「OBS Recorder Indicator」を追加してください
- 表示とテキストの設定はインジケーターごとに OneConfig が保存します(最初の 1 個の値は `config/obs_rec_indicator.json` にも書き戻されます)
- OBS の接続設定だけは **OBS** タブにまとまっていて、すべてのインジケーターで共通です

EN: The indicator is a OneConfig HUD. Drag it in OneConfig's **HUD editor** to move it (also when the
editor is opened from the title screen), change the size with **Scale**, and edit **Indicator Circle /
Text / Recording text / Paused text / Recording Indicator color** in one list on the same settings page
(no tabs). **Background** and **Shadow** are OneConfig's own options, in its **Background** and
**Appearance** sections — the shadow is the ordinary Minecraft glyph shadow every other HUD uses.
The option names follow Minecraft's language and switch over immediately when it is changed.
Position and size are stored by OneConfig. Duplicate it to place several indicators (each keeps its
own position, scale, visibility and look), and use **Delete HUD** (bottom of the HUD settings or the
right-click menu) to remove just that one indicator. Deleting every copy keeps it deleted across
restarts; add it back from OneConfig's **HUD library**. The display and text options are stored per
indicator by OneConfig (the first copy also writes them back into the mod's own config file); only
the OBS connection settings are shared in the **OBS** tab.

### OneConfig を導入していない場合 / Without OneConfig

画面中央が (0,0) です。右 +X、下 +Y。

Origin is the screen center. +X right, +Y down.

- 内蔵 Config を開き、インジケーターをドラッグ(プレビューが追従)、または X / Y を直接入力
- サイズ: `-0.5` / `-0.25` / `1.00x` / `+0.25` / `+0.5`

Open the built-in config and drag the indicator itself (the preview follows the mouse), or type X / Y directly.

## 設定ファイル / Config

`config/obs_rec_indicator.json`（初回起動時に生成）

| キー / Key | 説明 / Description |
|------------|--------------------|
| `enabled` | インジケーター ON/OFF |
| `positionX` / `positionY` | 位置（中央原点） |
| `scale` | サイズ |
| `showCircle` | 丸の表示（false で文字のみ） |
| `showText` | 文字の表示 |
| `showBackground` | 背景 |
| `showShadow` | 影 |
| `recordingText` / `pausedText` | 表示文字 |
| `recordingColor` / `pausedColor` | 色（例 `#E53935`） |
| `obsHost` / `obsPort` | OBS WebSocket |
| `obsPassword` | パスワード（認証時） |

## ライセンス / License

LGPL-3.0-only
