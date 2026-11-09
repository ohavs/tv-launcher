"""
Chooses where each wallpaper sits behind the big clock (`zoom/x0/y0` in wallpapers.json), so the land in front of
the clock never hides more than CAP of any digit: at rest, while the screensaver lifts the land (up to LIFT px more
than the clock) and while the clock wanders sideways (WANDER px). Among the placements that keep the cap it picks the
one closest to the current one that still lets the land overlap the clock a little (the depth effect).
Photos that can't keep the cap are reported (and get "drop": true).

Same requirements as make.py. Usage:
  python tools/wallpapers/place.py <cache dir>      # then: python tools/wallpapers/make.py <cache dir>
"""
import json, os, re, sys
import cv2, numpy as np, onnxruntime as ort
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from make import SW, LH, ROOT, depth, guided  # noqa: E402

SH = 1080
CAP = 0.23           # searched with a margin; the app's real masks are checked against 0.25 afterwards
LIFT = 24            # screensaver: land rises 12dp more than the clock (24 px at 1080p)
WANDER = 16          # screensaver: clock wanders +-8dp
TARGET = 0.15        # mean overlap at rest that still reads as depth
S = 2                # search at half resolution
FONT = os.path.join(ROOT, "app", "src", "main", "assets", "fonts", "Oswald.ttf")


def digit_inks():
    """Ink of each digit position of "HH:MM" (all ten digits overlaid), as on screen: Oswald 600, 560 px, centre + 20 px."""
    f = ImageFont.truetype(FONT, 560)
    try:
        f.set_variation_by_axes([600])
    except Exception:
        pass
    text = "88:88"
    w = f.getlength(text)
    _, t, _, b = f.getbbox("0123456789")
    x = (SW - w) / 2
    y = SH / 2 + 20 - (t + b) / 2
    inks = []
    for i, ch in enumerate(text):
        if ch == ":":
            continue
        im = Image.new("L", (SW, SH))
        d = ImageDraw.Draw(im)
        for digit in "0123456789":
            d.text((x + f.getlength(text[:i]), y), digit, font=f, fill=255)
        inks.append(np.asarray(im, np.float32) / 255)
    return inks


def land_alpha(rgb, d, tau):
    """Land in front of the sky, as in make.py, for the whole photo scaled to SW wide."""
    rgb = cv2.resize(rgb, (SW, round(rgb.shape[0] * SW / rgb.shape[1])), interpolation=cv2.INTER_AREA)
    dd = cv2.resize(d, (rgb.shape[1], rgb.shape[0]), interpolation=cv2.INTER_LINEAR)
    raw = (dd / max(np.percentile(d, 99.5), 1e-6) > tau).astype(np.uint8)
    _, lab = cv2.connectedComponents(raw, connectivity=4)
    raw = np.isin(lab, np.unique(lab[-1][raw[-1] > 0])).astype(np.float32)
    return np.clip((guided(rgb.astype(np.float32) / 255, raw, 8, 1e-3) - 0.3) / 0.4, 0, 1)


def place(alpha, inks, cur):
    H, W = alpha.shape
    z0 = max(1.0, LH / H + 1e-3)
    best = None
    small = [cv2.resize(k, (SW // S, SH // S), interpolation=cv2.INTER_AREA) for k in inks]
    boxes = []
    for k in small:
        ys, xs = np.where(k > 0.02)
        boxes.append((xs.min(), ys.min(), k[ys.min():ys.max() + 1, xs.min():xs.max() + 1].copy()))
    for zoom in sorted({round(z, 3) for z in np.linspace(z0, max(z0, cur["zoom"]) * 1.1, 7)} | {round(max(z0, cur["zoom"]), 3)}):
        w, h = int(W * zoom), int(H * zoom)
        if h < LH or w < SW:
            continue
        A = cv2.resize(alpha, (w // S, h // S), interpolation=cv2.INTER_AREA).astype(np.float32)
        nx, ny = (w - SW) // S + 1, (h - LH) // S + 1
        worst = np.zeros((ny, nx), np.float32)
        mean = np.zeros((ny, nx), np.float32)
        for bx, by, tpl in boxes:
            R = cv2.matchTemplate(A, tpl, cv2.TM_CCORR) / tpl.sum()
            at = lambda dy, dx: R[by + dy: by + dy + ny, bx + dx: bx + dx + nx]
            rest = at(0, 0)
            mean += rest / len(boxes)
            for dy in (0, LIFT // S):
                for dx in (-WANDER // S, 0, WANDER // S):
                    if bx + dx < 0 or bx + dx + nx > R.shape[1]:
                        continue
                    worst = np.maximum(worst, at(dy, dx))
        yy, xx = np.mgrid[0:ny, 0:nx] * S
        dist = np.abs(yy - cur["y0"] * zoom / cur["zoom"]) / 300 + np.abs(xx - cur["x0"] * zoom / cur["zoom"]) / 500 \
            + abs(zoom / cur["zoom"] - 1) / 0.25
        ok = worst <= CAP
        score = np.where(ok, -np.abs(mean - TARGET) * 12 - dist, -10 - worst * 10 - dist * 0.01)
        i = np.unravel_index(np.argmax(score), score.shape)
        cand = (score[i], zoom, int(xx[i]), int(yy[i]), float(worst[i]), float(mean[i]))
        if best is None or cand[0] > best[0]:
            best = cand
    return best


def main(cache):
    from huggingface_hub import hf_hub_download
    model = hf_hub_download("onnx-community/depth-anything-v2-base", "onnx/model.onnx", local_dir=os.path.join(cache, "da2b"))
    sess = ort.InferenceSession(model, providers=["CPUExecutionProvider"])
    path = os.path.join(HERE, "wallpapers.json")
    spec = json.load(open(path, encoding="utf-8"))
    inks = digit_inks()
    for s in spec:
        src = os.path.join(cache, re.sub(r"[^\w.-]", "_", s["file"]))
        rgb = np.asarray(Image.open(src).convert("RGB"))
        cached = os.path.join(cache, "depth", os.path.basename(src) + ".npy")
        if os.path.exists(cached):
            d = np.load(cached)
        else:
            d = depth(sess, rgb)
            os.makedirs(os.path.dirname(cached), exist_ok=True)
            np.save(cached, d)
        alpha = land_alpha(rgb, d, s.get("tau", 0.06))
        _, zoom, x0, y0, worst, mean = place(alpha, inks, s)
        print(f'{s["title"]:32s} zoom {s["zoom"]}->{zoom} x0 {s["x0"]}->{x0} y0 {s["y0"]}->{y0}  worst {worst:.2f} mean {mean:.2f}', flush=True)
        s.update(zoom=zoom, x0=x0, y0=y0)
        if worst > CAP + 0.03:
            s["drop"] = True
        else:
            s.pop("drop", None)
    json.dump(spec, open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)


def check(sheet):
    """Measures the built assets (as the app draws them) and writes a contact sheet of the clock over each photo."""
    out = os.path.join(ROOT, "app", "src", "main", "assets", "wallpapers")
    inks = digit_inks()
    clock = np.clip(sum(inks), 0, 1)[..., None]
    tiles = []
    for e in json.load(open(os.path.join(out, "index.json"), encoding="utf-8")):
        bg = np.asarray(Image.open(os.path.join(out, f'{e["id"]}.webp')).convert("RGB"), np.float32)
        m = np.zeros(bg.shape[:2], np.float32)
        m[e["fgTop"]:] = np.asarray(Image.open(os.path.join(out, f'{e["id"]}_fg.webp')))[..., 3] / 255
        worst = 0.0
        for dy in (0, LIFT):
            for dx in (-WANDER, 0, WANDER):
                a = m[dy:dy + SH]
                for k in inks:
                    k = np.roll(k, dx, axis=1)
                    worst = max(worst, float((a * k).sum() / k.sum()))
        rest = [float((m[:SH] * k).sum() / k.sum()) for k in inks]
        print(f'{e["id"]} {e["title"]:32s} rest {" ".join(f"{c:.2f}" for c in rest)}  worst {worst:.2f}')
        b, a = bg[:SH], m[:SH, :, None]
        comp = (b * (1 - clock * 0.97) + 255 * clock * 0.97) * (1 - a) + b * a
        tiles.append(Image.fromarray(comp.clip(0, 255).astype(np.uint8)).resize((480, 270)))
    cols = 4
    s = Image.new("RGB", (480 * cols, 270 * ((len(tiles) + cols - 1) // cols)))
    for i, t in enumerate(tiles):
        s.paste(t, (480 * (i % cols), 270 * (i // cols)))
    s.save(sheet, quality=85)


if __name__ == "__main__":
    if sys.argv[1] == "--check":
        check(sys.argv[2])
    else:
        main(sys.argv[1])
