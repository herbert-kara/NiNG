"""Generate the NiNG monogram in the existing NI dark/white icon family.
Asset-only operation: does not compile Android code.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'V2rayNG/app/src/main/res'
BG = '#202836'
FONT = Path('C:/Windows/Fonts/arialbd.ttf')

def artwork(size, foreground=False):
    canvas = Image.new('RGBA', (size, size), (0, 0, 0, 0) if foreground else BG)
    draw = ImageDraw.Draw(canvas)
    font = ImageFont.truetype(str(FONT), round(size * (0.24 if foreground else 0.29)))
    draw.text((size/2, size/2), 'NiNG', font=font, anchor='mm', fill='white')
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
    ImageDraw.Draw(image).text((320,180),'NiNG',font=ImageFont.truetype(str(FONT),150),anchor='mm',fill='white')
    image.save(RES/'mipmap-xhdpi'/name)
print('NiNG launcher, round, adaptive foreground/background, drawer and TV banner PNGs generated')
