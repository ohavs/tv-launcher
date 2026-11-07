"""
Builds the depth wallpapers: for every photo in wallpapers.json writes
  app/src/main/assets/wallpapers/<id>.webp     background, 1920x1360 (screen + parallax room)
  app/src/main/assets/wallpapers/<id>_fg.webp  foreground mask (land in front of the clock): black + alpha, from row fgTop
  app/src/main/assets/wallpapers/index.json    id, title, credit, fgTop
and WALLPAPER-CREDITS.md.

The foreground is everything nearer than the sky: Depth Anything V2 (base, ONNX) gives relative depth, land connected to
the bottom edge is kept, and a guided filter snaps the edge to the photo. `zoom/x0/y0` place the 1920x1360 window in the
photo scaled to 1920 px wide (x zoom), so the skyline crosses the lower part of the clock at the screen centre.

Usage (Python 3.10+, pip install numpy onnxruntime opencv-python-headless pillow huggingface_hub):
  python tools/wallpapers/make.py <cache dir>
"""
import json, os, re, sys, urllib.parse, urllib.request
import cv2, numpy as np, onnxruntime as ort
from PIL import Image

UA = "TVHomeWallpapers/1.0 (https://github.com/ohavs/tv-launcher; personal project)"
SW, LH = 1920, 1360
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "wallpapers")


def get(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=180).read()


def info(title):
    q = dict(action="query", titles=title, prop="imageinfo", iiprop="url|size|extmetadata", iiurlwidth="3840", format="json")
    page = list(json.loads(get("https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(q)))["query"]["pages"].values())[0]
    return page["imageinfo"][0]


def guided(I, p, r, eps):
    """Colour guided filter (He et al.): the mask's edge follows colour edges, e.g. snow against a pale blue sky."""
    m = lambda x: cv2.boxFilter(x, -1, (2 * r + 1, 2 * r + 1))
    mI, mp = m(I), m(p)
    cov_Ip = m(I * p[..., None]) - mI * mp[..., None]
    S = np.empty(I.shape[:2] + (3, 3), np.float32)
    for i in range(3):
        for j in range(i, 3):
            S[..., i, j] = S[..., j, i] = m(I[..., i] * I[..., j]) - mI[..., i] * mI[..., j]
    S += eps * np.eye(3, dtype=np.float32)
    a = np.linalg.solve(S, cov_Ip[..., None])[..., 0]
    b = mp - (a * mI).sum(-1)
    return (m(a) * I).sum(-1) + m(b)


def depth(sess, rgb):
    w = 1022
    h = max(14, round(rgb.shape[0] * w / rgb.shape[1] / 14) * 14)
    x = cv2.resize(rgb, (w, h), interpolation=cv2.INTER_AREA).astype(np.float32) / 255
    x = (x - [0.485, 0.456, 0.406]) / [0.229, 0.224, 0.225]
    return sess.run(None, {sess.get_inputs()[0].name: x.transpose(2, 0, 1)[None].astype(np.float32)})[0][0]


def main(cache):
    os.makedirs(cache, exist_ok=True)
    os.makedirs(OUT, exist_ok=True)
    from huggingface_hub import hf_hub_download
    model = hf_hub_download("onnx-community/depth-anything-v2-base", "onnx/model.onnx", local_dir=os.path.join(cache, "da2b"))
    sess = ort.InferenceSession(model, providers=["CPUExecutionProvider"])
    spec = json.load(open(os.path.join(os.path.dirname(__file__), "wallpapers.json"), encoding="utf-8"))
    index, credits = [], ["# Wallpaper credits", "", "Photos from Wikimedia Commons Featured Pictures, used under their free licences.",
                          "The app shows them cropped and split into a background and a foreground layer.", ""]
    for n, s in enumerate(spec, 1):
        wid = f"{n:02d}"
        ii = info(s["file"])
        src = os.path.join(cache, re.sub(r"[^\w.-]", "_", s["file"]))
        if not os.path.exists(src):
            open(src, "wb").write(get(ii.get("thumburl") or ii["url"]))
        rgb = np.asarray(Image.open(src).convert("RGB"))
        d = depth(sess, rgb)
        # crop window, in source pixels
        k = rgb.shape[1] / SW / s["zoom"]
        x0, y0 = s["x0"] * k, s["y0"] * k
        cw, ch = SW * k, LH * k
        M = np.float32([[1 / k, 0, -x0 / k], [0, 1 / k, -y0 / k]])
        out = cv2.warpAffine(rgb, M, (SW, LH), flags=cv2.INTER_AREA if k > 1 else cv2.INTER_CUBIC, borderMode=cv2.BORDER_REFLECT)
        if k > 1.5:  # warpAffine has no real area filter: pre-shrink for clean downscales
            small = cv2.resize(rgb, (int(rgb.shape[1] / k * 1.5), int(rgb.shape[0] / k * 1.5)), interpolation=cv2.INTER_AREA)
            kk = rgb.shape[1] / small.shape[1]
            M2 = np.float32([[kk / k, 0, -x0 / k], [0, kk / k, -y0 / k]])
            out = cv2.warpAffine(small, M2, (SW, LH), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REFLECT)
        dk = d.shape[1] / rgb.shape[1]
        Md = np.float32([[1 / (k * dk), 0, -x0 / k], [0, 1 / (k * dk), -y0 / k]])
        dd = cv2.warpAffine(d, Md, (SW, LH), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)
        dn = dd / max(np.percentile(d, 99.5), 1e-6)
        raw = (dn > s.get("tau", 0.06)).astype(np.uint8)
        _, lab = cv2.connectedComponents(raw, connectivity=4)
        raw = np.isin(lab, np.unique(lab[-1][raw[-1] > 0])).astype(np.float32)
        alpha = np.clip((guided(out.astype(np.float32) / 255, raw, 8, 1e-3) - 0.3) / 0.4, 0, 1)
        rows = np.where(alpha.max(1) > 0.01)[0]
        top = int(rows[0]) if len(rows) else LH - 1
        Image.fromarray(out).save(os.path.join(OUT, f"{wid}.webp"), quality=82, method=6)
        # Only the mask is stored (black + alpha); the app cuts the foreground out of the background with it.
        fg = np.zeros((LH - top, SW, 4), np.uint8)
        fg[..., 3] = (alpha[top:] * 255).round().astype(np.uint8)
        Image.fromarray(fg, "RGBA").save(os.path.join(OUT, f"{wid}_fg.webp"), lossless=True, method=6)
        md = ii.get("extmetadata", {})
        artist = re.sub(r"<[^>]+>", "", md.get("Artist", {}).get("value", "")).strip().split("\n")[0]
        lic = md.get("LicenseShortName", {}).get("value", "")
        index.append(dict(id=wid, title=s["title"], credit=f"{artist} · {lic}", fgTop=top))
        page = "https://commons.wikimedia.org/wiki/" + urllib.parse.quote(s["file"].replace(" ", "_"))
        credits.append(f"- `{wid}` {s['title']}: {artist}, {lic}. {page}")
        print(wid, s["title"], "fgTop", top, flush=True)
    json.dump(index, open(os.path.join(OUT, "index.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    credits += ["", "## Fonts", "",
                "Clock fonts (subset to digits), SIL Open Font License 1.1: Oswald (The Oswald Project Authors), Bebas Neue",
                "(Dharma Type), Anton (Vernon Adams), Big Shoulders Display (Patric King). https://fonts.google.com"]
    open(os.path.join(ROOT, "WALLPAPER-CREDITS.md"), "w", encoding="utf-8").write("\n".join(credits) + "\n")


if __name__ == "__main__":
    main(sys.argv[1])
