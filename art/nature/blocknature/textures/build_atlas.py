from pathlib import Path
from PIL import Image
import io,sys
P=Path(__file__).parent
# Four discrete value steps, sampled from the approved Nature mesh family.
PALETTES=[['294b20','48742e','6d963d','96b956'],['193925','295531','437745','648b43'],['963e79','c85a99','e881b5','f3b1cb'],['52376d','76518f','a26bb3','ca96cd'],['906126','c59138','e9bd58','ffe39a'],['50371e','765029','926b39','b38c50'],['3f7941','68a951','9acf71','cceba0'],['b89774','dcc49b','f1dfba','fff1d3']]
def build():
 im=Image.new('RGBA',(64,128))
 for row,palette in enumerate(PALETTES):
  for frame in range(4):
   for y in range(16):
    for x in range(16):
     # Coarse 2-4px clusters with branching veins, stepped petal folds or bark grain.
     h=((x//3)*17+(y//3)*29+row*11)%13
     v=1 if h<8 else 2
     if row in (0,1,6):
      if x in (7,8) or (y%6==2 and abs(x-7)<5): v=3
      elif (x+y//2)%11<2:v=0
     elif row in (2,3,7):
      if x//3==(y//4)%3+1:v=0
      elif y<4 and 3<x<13:v=3
     elif row==5:
      if (x+y//5)%6<2:v=0
     elif (x//2+y//2+frame)%7==0:v=3
     if row==6 and (x//2+y//2+frame)%11==0:v=3
     im.putpixel((frame*16+x,row*16+y),tuple(bytes.fromhex(palette[v]))+(255,))
 b=io.BytesIO();im.save(b,format='PNG');return b.getvalue()
data=build();out=P/'blocknature.png'
if '--check' in sys.argv:
 assert out.read_bytes()==data,'Atlas differs; rebuild';print('Nature atlas CHECK PASSED')
else:out.write_bytes(data);print(out)
