"""The player in the Roc's intro: a vanilla-shaped figure that wears the player's own skin.

Bedrock humanoid geometry with the 64x64 skin layout, wide (4 px) or slim (3 px) arms, and the
outer layer (hat, jacket, sleeves, trousers) as slightly inflated cubes. A waist joint lets the
upper body curl and twist over the legs; a 'wand' locator sits in the right hand.
"""


def cube(origin, size, uv, inflate=0.0):
    c = {'origin': origin, 'size': size, 'uv': uv}
    if inflate: c['inflate'] = inflate
    return c


def geometry(slim=False):
    arm = 3 if slim else 4
    y = 11.5 if slim else 12
    shoulder = 21.5 if slim else 22
    bones = [
        {'name': 'root', 'pivot': [0, 0, 0]},
        {'name': 'waist', 'parent': 'root', 'pivot': [0, 12, 0]},
        {'name': 'body', 'parent': 'waist', 'pivot': [0, 24, 0],
         'cubes': [cube([-4, 12, -2], [8, 12, 4], [16, 16]), cube([-4, 12, -2], [8, 12, 4], [16, 32], .25)]},
        {'name': 'head', 'parent': 'waist', 'pivot': [0, 24, 0],
         'cubes': [cube([-4, 24, -4], [8, 8, 8], [0, 0]), cube([-4, 24, -4], [8, 8, 8], [32, 0], .5)]},
        {'name': 'right_arm', 'parent': 'waist', 'pivot': [-5, shoulder, 0],
         'cubes': [cube([-4 - arm, y, -2], [arm, 12, 4], [40, 16]), cube([-4 - arm, y, -2], [arm, 12, 4], [40, 32], .25)]},
        {'name': 'wand', 'parent': 'right_arm', 'pivot': [-4 - arm/2, y + 1, -1]},
        {'name': 'left_arm', 'parent': 'waist', 'pivot': [5, shoulder, 0],
         'cubes': [cube([4, y, -2], [arm, 12, 4], [32, 48]), cube([4, y, -2], [arm, 12, 4], [48, 48], .25)]},
        {'name': 'right_leg', 'parent': 'root', 'pivot': [-1.9, 12, 0],
         'cubes': [cube([-3.9, 0, -2], [4, 12, 4], [0, 16]), cube([-3.9, 0, -2], [4, 12, 4], [0, 32], .25)]},
        {'name': 'left_leg', 'parent': 'root', 'pivot': [1.9, 12, 0],
         'cubes': [cube([-.1, 0, -2], [4, 12, 4], [16, 48]), cube([-.1, 0, -2], [4, 12, 4], [0, 48], .25)]},
    ]
    name = 'storm_roc_intro_player_slim' if slim else 'storm_roc_intro_player'
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': f'geometry.{name}', 'texture_width': 64, 'texture_height': 64,
                        'visible_bounds_width': 3, 'visible_bounds_height': 3, 'visible_bounds_offset': [0, 1.5, 0]},
        'bones': bones}]}
