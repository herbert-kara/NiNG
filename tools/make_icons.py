"""Generate the NiNG monogram in the existing NI dark/white icon family.
Asset-only operation: does not compile Android code.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'V2rayNG/app/src/main/res'
BG = '#202836'
# The wordmark wears the same teal as the connect button, so the launcher and the app agree.
INK = '#009FB7'
# 20% smaller than the 0.29/0.24 these were.
LEGEND_SIZE = 0.232
FOREGROUND_SIZE = 0.192
FONT = Path('C:/Windows/Fonts/arialbd.ttf')

def artwork(size, foreground=False):
    canvas = Image.new('RGBA', (size, size), (0, 0, 0, 0) if foreground else BG)
    draw = ImageDraw.Draw(canvas)
    ratio = FOREGROUND_SIZE if foreground else LEGEND_SIZE
    font = ImageFont.truetype(str(FONT), round(size * ratio))
    draw.text((size/2, size/2), 'NiNG', font=font, anchor='mm', fill=INK)
    return canvas

for density, scale in [('mdpi',1),('hdpi',1.5),('xhdpi',2),('xxhdpi',3),('xxxhdpi',4)]:
    folder = RES / ('mipmap-' + density)
    folder.mkdir(parents=True, exist_ok=True)
    for name in ['ic_launcher.png', 'ic_launcher_round.png']:
        artwork(1024).resize((round(48*scale),)*2, Image.Resampling.LANCZOS).save(folder/name)
    artwork(1024, True).resize((round(108*scale),)*2, Image.Resampling.LANCZOS).save(folder/'ic_launcher_foreground.png')
    Image.new('RGB',(round(108*scale),)*2,BG).save(folder/'ic_launcher_bg.png')
artwork(512).save(ROOT/'V2rayNG/app/src/main/ic_launcher-web.png')
artwork(512).save(RES/'drawable/ic_ning_logo.png')
# Preserve old resource identifier as compatibility, replace its visible artwork too.
artwork(512).save(RES/'drawable-nodpi/ic_pattng_logo.png')
for name in ['ic_banner.png','ic_banner_foreground.png']:
    image = Image.new('RGB',(640,360),BG)
    # 20% smaller than the 150 it was, and in the palette teal rather than white.
    ImageDraw.Draw(image).text((320,180),'NiNG',font=ImageFont.truetype(str(FONT),120),anchor='mm',fill=INK)
    image.save(RES/'mipmap-xhdpi'/name)
print('NiNG launcher, round, adaptive foreground/background, drawer and TV banner PNGs generated')
