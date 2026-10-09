"""将仓库内原始 GIF 转为完整画布 RGBA 图集，并校验像素和帧时长。"""
from pathlib import Path
from PIL import Image
import hashlib
import json
import shutil

ROOT = Path(__file__).resolve().parents[1]
INPUTS = {
    "kasumi": (
        ROOT / "assets/guests/kasumi/idle.png",
        ROOT / "assets/guests/kasumi/dance.gif",
        # 原 GIF 为 99 帧，每帧 100 毫秒；保持角色自身播放速度。
        [100] * 99,
        10,
    ),
    "emma": (
        ROOT / "assets/guests/emma/idle.png",
        ROOT / "assets/guests/emma/dance.gif",
        # 原 GIF 前 20 帧各 20 毫秒，最后一帧 40 毫秒，共 440 毫秒。
        [20] * 20 + [40],
        5,
    ),
}


def bounds_union(bounds):
    return [min(b[0] for b in bounds), min(b[1] for b in bounds),
            max(b[2] for b in bounds), max(b[3] for b in bounds)]


def prepare(name, png, gif, expected, columns):
    """重建生产资源；逐帧检查透明像素和帧时长，原始素材只读。"""
    destination = ROOT / "mod/src/main/resources/assets/friendswine/textures/entity" / name
    destination.mkdir(parents=True, exist_ok=True)
    # 素材已随仓库保存，直接读取，避免同路径复制并保持原件不变。
    shutil.copy2(png, destination / "idle.png")
    frames, durations = [], []
    with Image.open(gif) as image:
        for i in range(image.n_frames):
            image.seek(i)
            frames.append(image.convert("RGBA"))
            durations.append(image.info["duration"])
    assert durations == expected, (name, durations)
    width, height = frames[0].size
    rows = (len(frames) + columns - 1) // columns
    # 保留完整画布和透明像素；裁帧会改变渲染器使用的角色位置与比例。
    atlas = Image.new("RGBA", (width * columns, height * rows), (0, 0, 0, 0))
    for i, frame in enumerate(frames):
        atlas.paste(frame, ((i % columns) * width, (i // columns) * height))
    atlas.save(destination / "dance.png", optimize=True)
    with Image.open(destination / "dance.png") as encoded:
        for i, frame in enumerate(frames):
            x, y = (i % columns) * width, (i // columns) * height
            assert encoded.crop((x, y, x + width, y + height)).tobytes() == frame.tobytes()
    with Image.open(png) as idle:
        idle_bounds = list(idle.convert("RGBA").getchannel("A").getbbox())
        idle_size = list(idle.size)
    descriptor = {
        "frameWidth": width, "frameHeight": height,
        "columns": columns, "rows": rows,
        "durationsMs": durations, "loopMs": sum(durations),
        "contentBounds": bounds_union([frame.getchannel("A").getbbox() for frame in frames]),
        "idleSize": idle_size, "idleContentBounds": idle_bounds,
        "sourceGifSha256": hashlib.sha256(gif.read_bytes()).hexdigest(),
        "sourcePngSha256": hashlib.sha256(png.read_bytes()).hexdigest(),
    }
    (destination / "dance.json").write_text(json.dumps(descriptor, indent=2) + "\n", encoding="utf-8")
    print(name, json.dumps(descriptor, ensure_ascii=False))


if __name__ == "__main__":
    for name, values in INPUTS.items():
        prepare(name, *values)
