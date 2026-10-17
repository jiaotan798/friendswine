# 击杀闪屏原始素材

- `original.png`：用户提供的 1920×1080 冰块图片；生产纹理直接复制，保持像素不变。
- `original.wav`：用户提供的完整 1.33 秒音频，PCM 16 位、48 kHz、双声道。

生产音效使用 FFmpeg 的 `libvorbis`、质量等级 4 转换为 `mod/src/main/resources/assets/friendswine/sounds/guest_death_flash.ogg`；不裁剪、不补齐到两秒。图片显示时长与声音长度独立，总共两秒：前 1.5 秒不透明，最后 0.5 秒图片和白边同步线性渐隐。

素材核对脚本 `.verification/check-media-0.1.18.py` 使用 Pillow 比较 PNG 像素，并实际解码 OGG，检查时长、编码、采样率及声道。
