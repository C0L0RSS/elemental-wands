// Approved corner artwork shared by the native texture exporter.
  function paintCorner(){paintPaper();const c=root.querySelector('#mc-element-corner'),ctx=c.getContext('2d');ctx.clearRect(0,0,56,48);const f=(x,y,w,h,color)=>{ctx.fillStyle=color;ctx.fillRect(x,y,w,h)};
    if(element==='Fire'){
      // These steps follow the actual missing paper in the image clip above.
      // Char sits on its edge; the warm discoloration fades into intact paper.
      const edge=y=>y<3?30:y<7?25:y<11?20:y<16?14:y<20?9:y<25?5:y<30?2:0;
      for(let y=0;y<32;y++){
        const x=edge(y),grain=(y*7)%5;
        f(x,y,1,1,grain<2?'#67402a':'#4e3b30');
        f(x+1,y,1,1,grain===3?'#ad5927':'#805033');
        f(x+2,y,1,1,'#a47447');
        f(x+3,y,1,1,grain<2?'#b98d56':'#c09c67');
        if(y%3!==0)f(x+4,y,1,1,'#d2b67f');
      }
      [[28,2],[24,5],[20,9],[14,13],[10,18],[5,23],[3,27]].forEach(([x,y],i)=>{
        f(x,y,i%2+1,1,'#cf6528');f(x+1,y,1,1,'#efa33b');
      });
      const flame=['.....o..','....or..','....or..','...oor..','...oyr..','.r.oyor.','.oroyor.','rooyyorr','roywwyor','.royyor.','..roor..'];
      const glow={r:'#a44823',o:'#e87b27',y:'#ffc34a',w:'#ffed9d'};
      [[21,0],[10,7],[0,17]].forEach(([ox,oy],i)=>flame.forEach((row,y)=>[...row].forEach((p,x)=>{
        if(glow[p])f(ox+(i===1?7-x:x),oy+y,1,1,glow[p]);
      })));
      [[18,2],[8,5],[3,13]].forEach(([x,y],i)=>f(x,y,1,i===1?2:1,i===1?'#e98b32':'#f6bc50'));
      [[31,5],[24,12],[18,17],[11,24],[8,30]].forEach(([x,y])=>f(x,y,1,1,'#b79a6b'));
    }else if(element==='Space'){
      [[8,13,4,3],[12,11,4,3],[17,9,4,3],[22,7,3,3],[7,17,2,8]].forEach(r=>f(...r,'#67557c'));
      [[9,11],[14,8],[20,5],[25,9],[5,18],[8,26],[29,4],[18,13]].forEach(([x,y],i)=>f(x,y,i%2+1,i%2+1,i%2?'#b6a0d5':'#8a73ac'));
      [[9,5],[21,2],[31,7],[3,13],[4,25]].forEach(([x,y])=>{f(x-1,y,3,1,'#d7c1ee');f(x,y-1,1,3,'#e9ddff');});f(16,4,1,1,'#fff0d1');f(2,6,1,1,'#b599d9');
    }else if(element==='Nature'){
      [[8,9,2,28],[8,10,19,2],[25,8,12,2],[35,6,7,2],[6,34,2,8],[10,20,5,2],[14,17,2,4]].forEach(r=>f(...r,'#465c31'));
      [[9,12,1,23],[12,11,13,1],[27,9,8,1],[7,35,1,5]].forEach(r=>f(...r,'#889644'));
      [[6,15],[11,24],[5,31],[17,8],[28,11],[37,5]].forEach(([x,y],i)=>{f(x,y,4,3,'#3d6536');f(x+1,y-1,4,3,'#628a3d');f(x+2,y,2,1,'#9fb95c');});f(24,7,2,2,'#d9c99a');f(23,8,4,1,'#f6ead0');f(24,8,1,1,'#d6a54d');
    }else if(element==='Wind'){
      [[8,13,6,2],[13,10,7,2],[18,7,8,2]].forEach(r=>f(...r,'#d1c198'));f(9,14,6,2,'#9d906e');f(11,11,4,3,'#fff0cd');f(15,8,4,3,'#f5e8c4');
      [[5,6,12,1],[3,9,7,1],[19,4,8,1],[25,6,3,1],[2,21,4,1],[3,24,9,1]].forEach(r=>f(...r,'#b2ceca'));f(26,4,2,2,'#d1e6df');f(16,6,2,2,'#d1e6df');f(11,22,2,2,'#d1e6df');
    }else{
      // A single stone resting on the sheet: stepped contact shadow and
      // a lighter top plane make it read as a paperweight above the parchment.
      f(10,18,21,4,'#735d3633');f(12,21,17,2,'#735d3626');
      f(11,18,18,3,'#5d513754');f(13,20,12,1,'#5d513736');
      const outline=[[14,22],[12,25],[10,26],[9,27],[8,28],[8,28],[7,29],[7,29],[7,29],[8,29],[8,28],[9,27],[11,26],[13,24],[15,21]];
      outline.forEach(([left,right],row)=>{
        const y=row+4;
        for(let x=left;x<=right;x++){
          const grain=(x*13+y*7)%19;
          let color;
          if(row>11||x>26)color=grain<4?'#62675e':'#545b54';
          else if(x<11||row===0)color=grain<6?'#92978b':'#7b8176';
          else if(y<10&&x<22)color=grain<4?'#c0c0af':grain<10?'#a8ad9e':'#b3b6a6';
          else if(x>21)color=grain<5?'#8a9184':'#727c71';
          else color=grain<4?'#a1a797':grain<11?'#8c9687':'#939d8c';
          f(x,y,1,1,color);
        }
      });
      [[13,6,7,1],[11,8,3,1],[15,5,4,1]].forEach(r=>f(...r,'#c7c8b6'));
      [[22,8,1,3],[21,11,1,3],[19,14,2,1]].forEach(r=>f(...r,'#626e64'));
      [[13,12],[17,9],[24,13],[11,15],[19,6]].forEach(([x,y])=>f(x,y,1,1,'#b9bdac'));
    }
  }
