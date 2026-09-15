# OBS Rec Indicator

OBS の録画状態を Minecraft に表示する Fabric Mod です。

Shows OBS Studio recording status as a HUD indicator in Minecraft (Fabric).

みなさんこんにちは、そしてはじめまして。えすGamesと申します。まず、このModはMiMoが作成し、私が動作テストをするという形で完成したものです。
また、こちらのREADMEもこの文以外はMiMoに書かせているため、不十分なものがあるかもしれませんし、もちろんコードもMiMoが書いたものなので汚いところがあるかもしれません。
私がこの機能が一番欲しくて作らせたModをみなさんが修正/組込できるように公開を決意したModですので、私が修正を加えることは少ないかもしれませんが、皆さんで好きにフォークして改善していっていただけるとありがたいです！
私が一番助かるのでね()
そんなところです！ありがとうございます！

- 録画中: 赤い丸 + `REC`
- 一時停止: 橙色の丸 + `PAUSED`
- 停止 / 未接続: 非表示

Recording: red circle + `REC`  
Paused: orange + `PAUSED`  
Idle / disconnected: hidden

## OBS の設定 / OBS setup

1. **ツール → WebSocket Server Settings**
2. **WebSocket server を有効にする**
3. 認証はオフ推奨（使う場合は下の `obsPassword` へ）
4. ポートはデフォルト **4455** のままで OK

1. **Tools → WebSocket Server Settings**  
2. Enable **WebSocket server**  
3. Auth off is simplest (or set `obsPassword` below)  
4. Default port **4455** is fine

## 必要なもの / Requirements

- Minecraft 26.2
- [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.5+
- [Fabric API](https://modrinth.com/mod/fabric-api)（26.2 向け）
- OBS Studio（WebSocket 5.x、OBS 28+）

## インストール / Install

1. Fabric Loader と Fabric API を導入
2. `obs-rec-indicator-r1.0.0.jar` を `mods` に入れる
3. （任意）OneConfig を入れると設定 UI が使える

1. Install Fabric Loader and Fabric API  
2. Put `obs-rec-indicator-r1.0.0.jar` in `mods`  
3. (Optional) Install OneConfig for a config UI

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
