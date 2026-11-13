# Mac-style icon: rounded square on a transparent 1024 canvas with a soft shadow.
import sys, io
sys.path.insert(0, "tools")
from PIL import Image, ImageDraw, ImageFilter
import build_icon as b
tile = 824
art = b.render(b.svg(), int(tile * 1.17))
o = (art.size[0] - tile) // 2
art = art.crop((o, o, o + tile, o + tile))
m = Image.new("L", (tile * 2, tile * 2), 0)
ImageDraw.Draw(m).rounded_rectangle((0, 0, tile * 2 - 1, tile * 2 - 1), radius=int(tile * 0.225 * 2), fill=255)
m = m.resize((tile, tile), Image.LANCZOS)
canvas = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
sh = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
sh.paste((0, 0, 0, 110), (100, 112), m)
canvas.alpha_composite(sh.filter(ImageFilter.GaussianBlur(14)))
tileimg = Image.new("RGBA", (tile, tile)); tileimg.paste(art, (0, 0)); tileimg.putalpha(m)
canvas.alpha_composite(tileimg, (100, 100))
canvas.save(sys.argv[1])
