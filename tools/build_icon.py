"""Single source for the Fonds icon: emits Android adaptive-icon vectors and PNG previews."""
import cairosvg, io, sys
from PIL import Image, ImageDraw

SLATE = "#2E4A5A"
# ---- foreground shapes: (path, fill, alpha, stroke, strokeWidth)
BRACKETS = [
    "M31,40 V34.5 Q31,31 34.5,31 H40",
    "M68,31 H73.5 Q77,31 77,34.5 V40",
    "M31,68 V73.5 Q31,77 34.5,77 H40",
    "M68,77 H73.5 Q77,77 77,73.5 V68",
]
FG = [
    # soft shadow under the folder
    ("M38.5,48 Q38.5,45.5 41,45.5 H67 Q69.5,45.5 69.5,48 V70 Q69.5,72.6 67,72.6 H41 Q38.5,72.6 38.5,70 Z", "#0B1820", 0.28),
    # back panel with tab
    ("M39,46 Q39,43.5 41.5,43.5 H48.5 Q50,43.5 50.9,44.7 L52.6,47 H66.5 Q69,47 69,49.5 V68.5 Q69,71 66.5,71 H41.5 Q39,71 39,68.5 Z", "#C9A865", 1),
    # sheet of paper
    ("M43,38.5 Q43,37 44.5,37 H60.5 L65,41.5 V63 H43 Z", "#FFFDF8", 1),
    ("M60.5,37 V41.5 H65 Z", "#E2D9C6", 1),
    ("M46,43.6 h11 v1.7 h-11 z", SLATE, 0.62),
    ("M46,47.2 h15.2 v1.7 h-15.2 z", SLATE, 0.62),
    ("M46,50.8 h12.5 v1.6 h-12.5 z", SLATE, 0.62),
    # front panel
    ("M37.5,53 H70.5 L69,68.8 Q68.8,71 66.6,71 H41.4 Q39.2,71 39,68.8 Z", "#F1E2BE", 1),
    ("M37.5,53 H70.5 L70.38,54.3 H37.62 Z", "#FBF4E2", 1),
    # box label on the folder
    ("M48,58.6 H60 Q61.2,58.6 61.2,59.8 V64 Q61.2,65.2 60,65.2 H48 Q46.8,65.2 46.8,64 V59.8 Q46.8,58.6 48,58.6 Z", "#FFFDF8", 1),
    ("M49.4,61.2 H58.6 V62.7 H49.4 Z", SLATE, 1),
]
BRACKET_COLOR = "#F1E2BE"
BRACKET_W = 3.1
BG_TOP, BG_BOTTOM = "#3C6075", "#1E3340"

# Monochrome (themed icons): silhouette only
MONO = [
    ("M39,46 Q39,43.5 41.5,43.5 H48.5 Q50,43.5 50.9,44.7 L52.6,47 H66.5 Q69,47 69,49.5 V52 H39 Z", 1),
    ("M37.5,53 H70.5 L69,68.8 Q68.8,71 66.6,71 H41.4 Q39.2,71 39,68.8 Z M48,58.6 Q46.8,58.6 46.8,59.8 V64 Q46.8,65.2 48,65.2 H60 Q61.2,65.2 61.2,64 V59.8 Q61.2,58.6 60,58.6 Z", 1),
    ("M43,38.5 Q43,37 44.5,37 H60.5 L65,41.5 V51.5 H43 Z M46,43.6 v1.7 h11 v-1.7 z M46,47.2 v1.7 h15.2 v-1.7 z", 0.75),
]

def svg(include_bg=True, mono=False):
    out = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="108" height="108">']
    if include_bg:
        out.append(f'<defs><linearGradient id="g" x1="0" y1="0" x2="0.35" y2="1"><stop offset="0" stop-color="{BG_TOP}"/><stop offset="1" stop-color="{BG_BOTTOM}"/></linearGradient></defs>')
        out.append('<rect width="108" height="108" fill="url(#g)"/>')
    color = "#FFFFFF" if mono else BRACKET_COLOR
    for d in BRACKETS:
        out.append(f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{BRACKET_W}" stroke-linecap="round" stroke-linejoin="round"/>')
    if mono:
        for d, a in MONO:
            out.append(f'<path d="{d}" fill="#FFFFFF" fill-opacity="{a}" fill-rule="evenodd"/>')
    else:
        for d, fill, a in FG:
            out.append(f'<path d="{d}" fill="{fill}" fill-opacity="{a}"/>')
    out.append('</svg>')
    return "".join(out)

def render(svgtext, px):
    return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svgtext.encode(), output_width=px, output_height=px))).convert("RGBA")

def mask(img, shape):
    s = img.size[0]
    crop = img.crop((int(s*18/108), int(s*18/108), int(s*90/108), int(s*90/108)))
    m = Image.new("L", crop.size, 0)
    d = ImageDraw.Draw(m)
    w = crop.size[0]
    if shape == "circle":
        d.ellipse((0, 0, w-1, w-1), fill=255)
    else:
        d.rounded_rectangle((0, 0, w-1, w-1), radius=int(w*0.30), fill=255)
    crop.putalpha(m)
    return crop

def previews(path):
    full = render(svg(), 648)
    mono = render(svg(include_bg=False, mono=True), 648)
    themed_bg = Image.new("RGBA", mono.size, "#2B3A33")
    themed_bg.paste(Image.new("RGBA", mono.size, "#CDE8D4"), (0, 0), mono.split()[3])
    tiles = [mask(full, "circle"), mask(full, "squircle"), mask(themed_bg, "circle")]
    small = [t.resize((96, 96), Image.LANCZOS) for t in tiles[:2]]
    W = 3*tiles[0].size[0] + 4*40 + 2*96 + 40
    H = tiles[0].size[1] + 80
    sheet = Image.new("RGBA", (W, H), "#F5F1E8")
    x = 40
    for t in tiles:
        sheet.alpha_composite(t, (x, 40)); x += t.size[0] + 40
    for sm in small:
        sheet.alpha_composite(sm, (x, 40 + tiles[0].size[1]//2 - 48)); x += 96 + 20
    sheet.convert("RGB").save(path)

# ---- Android vector XML
def vec_header():
    return ('<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="108">\n')

def bg_xml():
    return (vec_header() +
        '    <path android:pathData="M0,0h108v108h-108z">\n'
        '        <aapt:attr name="android:fillColor">\n'
        f'            <gradient android:type="linear" android:startX="0" android:startY="0" android:endX="37.8" android:endY="108"\n'
        f'                android:startColor="{BG_TOP}" android:endColor="{BG_BOTTOM}" />\n'
        '        </aapt:attr>\n    </path>\n'
        '</vector>\n')

def fg_xml(mono=False):
    s = vec_header()
    color = "#FFFFFF" if mono else BRACKET_COLOR
    for d in BRACKETS:
        s += (f'    <path android:pathData="{d}" android:fillColor="#00000000" android:strokeColor="{color}"\n'
              f'        android:strokeWidth="{BRACKET_W}" android:strokeLineCap="round" android:strokeLineJoin="round" />\n')
    if mono:
        for d, a in MONO:
            s += f'    <path android:pathData="{d}" android:fillColor="#FFFFFF" android:fillAlpha="{a}" android:fillType="evenOdd" />\n'
    else:
        for d, fill, a in FG:
            s += f'    <path android:pathData="{d}" android:fillColor="{fill}"' + (f' android:fillAlpha="{a}"' if a != 1 else '') + ' />\n'
    return s + '</vector>\n'

if __name__ == "__main__":
    res = sys.argv[1]
    open(f"{res}/drawable/ic_launcher_background.xml", "w").write(bg_xml())
    open(f"{res}/drawable/ic_launcher_foreground.xml", "w").write(fg_xml())
    open(f"{res}/drawable/ic_launcher_monochrome.xml", "w").write(fg_xml(mono=True))
    previews(sys.argv[2])
    render(svg(), 512).save(sys.argv[3])
    print("ok")
