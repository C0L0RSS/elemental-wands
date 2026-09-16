"""Pixel-native paper damage and layered corner art. Coordinates are GUI texels.

The paper's alpha is the damage, rather than painting an emblem on intact paper.
The untouched paper material and approved stone remain inputs from the exporter.
"""
from PIL import Image, ImageDraw

SIZE = (64, 64)


def layer():
    return Image.new('RGBA', SIZE)


def polygon(image, points, color):
    drawing = layer()
    ImageDraw.Draw(drawing).polygon(points, fill=color)
    image.alpha_composite(drawing)


def line(image, points, color, width=1):
    drawing = layer()
    ImageDraw.Draw(drawing).line(points, fill=color, width=width)
    image.alpha_composite(drawing)


def pixels(image, points, color):
    for x, y in points:
        image.putpixel((x, y), color)


def mask_polygon(mask, points):
    ImageDraw.Draw(mask).polygon(points, fill=255)


def bite(edges):
    mask = Image.new('L', SIZE)
    d = ImageDraw.Draw(mask)
    for y, x in enumerate(edges):
        if x > 0:
            d.line((0, y, x-1, y), fill=255)
    return mask


def damage_rim(paper, mask, palette, radius):
    """Shade by distance to actual missing fibers, including the interior pinholes."""
    result = paper.copy()
    missing = [(x, y) for y in range(64) for x in range(64) if mask.getpixel((x, y))]
    for y in range(64):
        for x in range(64):
            if mask.getpixel((x, y)):
                result.putpixel((x, y), (0, 0, 0, 0))
                continue
            if result.getpixel((x, y))[3] == 0:
                continue
            distance = min((max(abs(x-px), abs(y-py)) for px, py in missing), default=100)
            if distance <= radius:
                shade = palette[distance-1]
                variation = ((x*17+y*11) % 7)-3
                r, g, b, amount = shade
                old = result.getpixel((x, y))
                result.putpixel((x, y), tuple(round(old[i]*(1-amount)+(c+variation)*amount)
                                             for i, c in enumerate((r, g, b)))+(old[3],))
    return result


def fire(paper):
    mask = bite([44,44,43,42,39,38,35,35,33,32,30,30,31,29,26,26,23,22,22,19,
                 18,18,16,15,16,14,11,11,9,9,10,8,7,5,5,4,5,3,2,2,1,1])
    mask_polygon(mask, [(24,17),(28,17),(27,19),(25,20)])
    mask_polygon(mask, [(12,31),(14,30),(15,32),(13,34),(12,33)])
    mask_polygon(mask, [(37,7),(39,6),(40,8),(38,9)])
    paper = damage_rim(paper, mask, [(60,45,36,1),(84,54,36,1),(126,73,38,.95),
                                   (166,109,57,.8),(190,147,87,.65),(205,172,118,.4)], 6)
    top = layer()
    # Incandescent fibers are sparse; most of the rim reads as brittle char.
    pixels(top, [(43,2),(36,7),(30,13),(24,18),(17,23),(11,28),(5,35)], (196,86,30,255))
    pixels(top, [(43,3),(30,14),(11,29)], (239,149,48,255))
    for x, y in [(31,0),(1,24)]:
        polygon(top, [(x,y+11),(x+1,y+6),(x+3,y+7),(x+3,y+2),(x+5,y),
                      (x+4,y+5),(x+6,y+8),(x+6,y+11),(x+4,y+13),(x+1,y+12)], '#B94C22')
        polygon(top, [(x+1,y+10),(x+3,y+6),(x+3,y+9),(x+4,y+5),(x+5,y+10),(x+3,y+12)], '#ED872D')
        line(top, [(x+2,y+11),(x+3,y+9),(x+4,y+10)], '#FFD478')
    pixels(top, [(26,3),(19,10),(5,17)], (210,129,58,255))
    pixels(top, [(23,6),(8,12),(16,17)], (117,94,72,180))
    return paper, top


def space(paper):
    mask = bite([45,43,44,40,41,37,39,34,35,32,30,32,27,28,25,23,25,20,21,
                 17,19,15,15,12,14,10,11,8,8,6,7,4,5,3,4,1,2,1])
    for points in [[(36,7),(40,6),(39,10),(37,10)],[(27,17),(29,17),(29,20),(26,20)],
                   [(18,25),(21,24),(21,27),(19,28)],[(9,33),(11,33),(12,35),(10,36)]]:
        mask_polygon(mask, points)
    paper = damage_rim(paper, mask, [(66,45,82,1),(102,67,124,.95),(145,107,161,.85),
                                   (170,140,175,.6),(201,179,185,.35)], 5)
    top = layer()
    # Receding paper fragments keep tan centers; their edges dissolve into violet dust.
    for x,y,w,h in [(30,4,3,2),(24,9,2,3),(17,15,3,2),(9,22,2,2),(3,29,2,3)]:
        polygon(top, [(x,y),(x+w-1,y),(x+w-1,y+h),(x,y+h-1)], '#9D79B3')
        pixels(top, [(x+1,y+1)], (219,193,165,255))
    pixels(top, [(40,1),(35,4),(30,10),(28,13),(21,19),(17,23),(13,26),(8,31)], (177,119,220,255))
    pixels(top, [(39,1),(28,12),(18,23)], (239,198,255,255))
    pixels(top, [(22,2),(15,7),(6,12),(4,20),(11,2),(1,8),(2,34)], (150,109,188,210))
    pixels(top, [(26,5),(10,16),(1,26)], (216,186,236,255))
    return paper, top


def vine(image, points, highlight=True):
    line(image, [(x+1,y+1) for x,y in points], (53,46,27,70), 4)
    line(image, points, '#344F2E', 3)
    line(image, points, '#66823C')
    if highlight:
        pixels(image, points[1::2], (147,169,77,255))


def leaf(image, x, y, flip=False):
    points = [(0,0),(3,-1),(6,-1),(5,2),(2,4),(0,3)]
    p = [(x+(-a if flip else a),y+b) for a,b in points]
    polygon(image, [(a+1,b+1) for a,b in p], (61,49,27,85))
    polygon(image, p, '#335632')
    line(image, [(x,y+2),(x+(-2 if flip else 2),y+1),(x+(-4 if flip else 4),y)], '#7FA34C', 2)
    pixels(image, [(x+(-2 if flip else 2),y)], (166,188,95,255))


def nature(paper):
    mask = Image.new('L', SIZE)
    mask_polygon(mask, [(19,0),(26,0),(23,6),(25,12),(23,18),(19,24),(17,22),
                        (20,16),(20,12),(17,7)])
    mask_polygon(mask, [(0,23),(6,24),(11,23),(15,26),(20,26),(23,29),(18,33),
                        (12,32),(8,35),(0,34)])
    paper = damage_rim(paper, mask, [(137,109,67,.8),(210,188,138,.4)], 2)
    back = layer()
    vine(back, [(0,43),(5,38),(8,30),(11,24),(16,20),(21,14),(21,5),(23,0)], False)
    vine(back, [(8,30),(4,26),(0,26)], False)
    leaf(back, 10,28); leaf(back, 19,11, True)
    composed = Image.new('RGBA', paper.size); composed.paste(back, (0,0)); composed.alpha_composite(paper)
    top = layer()
    # Bright torn fibers on one lip and a dark underside on the opposite lip.
    line(top, [(18,1),(18,6),(20,10),(21,15),(18,21)], '#F7EAC5')
    line(top, [(1,22),(6,23),(11,22),(16,25),(19,25)], '#FAEDCB')
    for x,y in [(18,6),(20,15),(10,23),(17,25),(8,33)]:
        line(top, [(x,y),(x-1,y+2)], '#E6D3A8')
    # The folded lip overlays the stem; the stem can be seen again through the slit.
    polygon(top, [(23,6),(28,13),(24,19),(22,15)], (72,52,32,105))
    polygon(top, [(22,5),(26,12),(23,17),(20,12)], '#CEB78D')
    line(top, [(22,5),(21,10),(20,12),(23,17)], '#FFF0CB')
    polygon(top, [(0,27),(9,24),(13,26),(8,30),(0,31)], (68,47,28,90))
    polygon(top, [(0,25),(8,23),(12,25),(8,28),(0,29)], '#E0CAA0')
    line(top, [(0,25),(8,23),(12,25)], '#FDF0D0')
    vine(top, [(18,22),(22,20),(27,18),(30,15),(34,14),(37,10)])
    leaf(top,24,19); leaf(top,32,14,True); leaf(top,36,11)
    vine(top, [(8,33),(12,38),(9,43),(8,48)])
    leaf(top,11,38); leaf(top,9,45,True)
    return composed, top


def wind(paper):
    mask = Image.new('L', SIZE)
    mask_polygon(mask, [(0,0),(31,0),(27,3),(29,6),(21,7),(23,11),(15,13),
                        (17,17),(9,18),(10,23),(4,26),(5,30),(0,32)])
    mask_polygon(mask, [(0,37),(9,35),(15,37),(9,39),(0,40)])
    paper = damage_rim(paper, mask, [(181,156,114,.8),(227,210,168,.7)], 2)
    top = layer()
    # Narrow lifted paper tongues: shadow beneath, light underside, frayed tips.
    for pts in [[(9,16),(17,13),(24,9),(22,7),(16,10),(6,12)],
                [(2,28),(8,25),(15,21),(13,19),(7,22),(0,23)],
                [(23,7),(29,5),(34,1),(29,0),(26,3)]]:
        polygon(top, [(x+1,y+2) for x,y in pts], (99,80,45,95))
        polygon(top, pts, '#DBC8A0')
        line(top, pts[:3], '#FFF0CF')
    pixels(top, [(9,14),(11,13),(7,23),(10,21),(28,3)], (250,235,197,255))
    for pts in [[(5,6),(10,4),(12,5),(8,7)],[(1,16),(4,14),(5,15),(3,18)],[(19,1),(21,0),(23,1),(20,3)]]:
        polygon(top, pts, '#E9D7AD')
        line(top, pts[:2], '#FFF0D0')
    line(top, [(0,10),(3,9),(6,9)], (181,207,203,160))
    line(top, [(1,32),(5,32),(8,30)], (181,207,203,160))
    return paper, top


def stone(paper, rock):
    folds = layer()
    # Wide low-contrast valleys lead into crisp ridge pairs under the contact area.
    polygon(folds, [(8,11),(18,19),(24,42),(16,31)], (146,112,62,28))
    polygon(folds, [(20,8),(37,6),(53,9),(38,14)], (159,126,71,23))
    creases = [[(20,12),(29,12),(36,8),(44,10),(53,7)],
               [(23,15),(30,18),(37,16),(41,18)],
               [(17,17),(21,26),(16,33),(21,41),(18,48)],
               [(11,16),(8,22),(3,24),(1,28)],
               [(13,9),(10,5),(4,3)],
               [(13,20),(9,27),(10,33),(5,39)]]
    for path in creases:
        line(folds, [(x+1,y+1) for x,y in path], (141,111,65,65), 3)
        line(folds, path, (145,110,60,110))
        line(folds, [(x,y-1) for x,y in path], (255,245,205,200))
    polygon(folds, [(7,16),(11,21),(22,24),(31,20),(29,16)], (89,72,43,80))
    # Preserve the already-approved shaded rock, now visibly weighing down folds.
    folds.alpha_composite(rock)
    return paper, folds


def build_corner(element, paper, original):
    if element == 'Stone':
        return stone(paper.copy(), original)
    return {'Fire': fire, 'Nature': nature, 'Space': space, 'Wind': wind}[element](paper.copy())
