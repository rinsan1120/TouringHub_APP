# TouringHub_APP

## ビルドについて

音声認識には sherpa-onnx（Whisper）を使用します。ビルド時に、ピン留めした公式AAR（v2.0.1、SHA-256検証つき）が `app/libs/` へ自動ダウンロードされます（Git管理外）。

## 音声認識モデル

Whisper のモデル（base 約160MB / small 約375MB）は、アプリの設定画面から端末へダウンロードします。認識は端末内で完結し、音声を外部へ送信しません。
