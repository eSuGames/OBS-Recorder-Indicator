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

- Minecraft 1.8.9 (Ornithe)
- [Ornithe Loader / Fabric Loader](https://ornithemc.net/) 0.19.5+
- [OneConfig](https://modrinth.com/mod/oneconfig)（**必須 / required**）
- OBS Studio

※バージョン要件は時と場合によります。
Version requirements may vary depending on the situation. 

## 1.8.9 Ornithe版について / About the 1.8.9 Ornithe build

- **OneConfig が必須です。** 設定はすべて OneConfig で行います（接続設定: OneConfig の Mod ページ、
  表示・フォント・影・背景・位置: OneConfig の HUD エディター）。
  OneConfig is **required**: connection settings live in OneConfig's mod page, and the indicator's
  font, shadow, background and position are edited in OneConfig's HUD editor — including all of
  OneConfig's HUD customizations.
- 内蔵のバニラ風設定画面はありません（1.8.9 への移植では省略）。
  There is no built-in config screen on this branch; it is dropped for the 1.8.9 port.
- コマンドはチャットに `/obsindicator ...` と打ちます（1.8.9 にはクライアントコマンド API が
  ないため）。 Commands are typed in chat because 1.8.9 has no client command API.
- 鍵設定（キーバインド）はありません。
  There are no key bindings on this branch.

## コマンド / Commands

| コマンド / Command | 説明 / Description |
|--------------------|--------------------|
| `/obsindicator config` | OneConfig の設定を開く / Open OneConfig settings |
| `/obsindicator toggle` | 表示 ON/OFF / Toggle indicator |
| `/obsindicator status` | 接続状態 / Connection status |
| `/obsindicator reconnect` | OBS 再接続 / Reconnect to OBS |

以下はいつでも使えます。 / These are also always available:

- `/obsindicator textonly` — 文字のみ / Text only  
- `/obsindicator position <x> <y>` — 位置 / Position  
- `/obsindicator position reset` — 位置リセット / Reset position  

## 位置調整 / Position

画面中央が (0,0) です。右 +X、下 +Y。

Origin is the screen center. +X right, +Y down.

- 内蔵 Config、または OneConfig の **Position editor** から、画面全体のプレビューでドラッグ
- サイズ: `-0.5` / `-0.25` / `1.00x` / `+0.25` / `+0.5`

Drag on the full-screen preview from the built-in config, or from OneConfig’s **Position editor**.

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
