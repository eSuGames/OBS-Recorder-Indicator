# OBS Rec Indicator

OBS Studio の録画状態を Minecraft 26.2 (Fabric) に表示する HUD インジケーター Mod です。

- 録画中 → 赤い丸 + `REC`（点滅なし）
- 一時停止 → 橙色 + `PAUSED`
- 位置は画面中央が 0,0（右+ / 下+）
- 必須依存は Fabric API のみ。OneConfig は任意

## ライセンス

LGPL-3.0-only

## ビルド

```powershell
$env:JAVA_HOME = "C:\Users\<you>\.jdks\temurin-25.0.4"
.\gradlew.bat build
```

成果物: `build/libs/obs-rec-indicator-1.5.0.jar`

## コマンド

### OneConfig あり

| コマンド | 動作 |
|----------|------|
| `/obsindicator config` | OneConfig の本 Mod 設定画面 |
| `/obsindicator status` | 接続・表示状態 |

HUD 位置は OneConfig の HUD エディターでドラッグして調整します。

### OneConfig なし

| コマンド | 動作 |
|----------|------|
| `/obsindicator config` | 組み込み設定画面（画面全体でドラッグ可能） |
| `/obsindicator status` | 接続・表示状態 |
| `/obsindicator toggle` | インジケーター ON/OFF |
| `/obsindicator textonly` | 文字のみ ON/OFF |
| `/obsindicator position <x> <y>` | 位置設定 |
| `/obsindicator position reset` | 位置リセット |
| `/obsindicator reconnect` | OBS 再接続 |

## OBS

ツール → WebSocket Server Settings → 有効化（ポート 4455）

## 依存

| 種類 | Mod |
|------|-----|
| 必須 | Fabric API |
| 任意 | OneConfig 1.1.x |
| 不使用 | YACL |
