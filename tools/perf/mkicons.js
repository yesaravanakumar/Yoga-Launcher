const sharp=require('sharp');const fs=require('fs');
(async()=>{const out={};const names=[];
 for(let i=0;i<160;i++){const hue=(i*37)%360;
  const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="hsl(${hue},70%,55%)"/><stop offset="1" stop-color="hsl(${(hue+60)%360},70%,35%)"/></linearGradient></defs><rect width="96" height="96" rx="22" fill="url(#g)"/><circle cx="48" cy="48" r="${14+(i%5)*5}" fill="rgba(255,255,255,.85)"/><path d="M${10+i%30} 80 L90 ${20+i%40}" stroke="#fff" stroke-width="5" opacity=".5"/></svg>`;
  const buf=await sharp(Buffer.from(svg)).webp({quality:82}).toBuffer();
  out['com.test.app'+i]='data:image/webp;base64,'+buf.toString('base64');}
 fs.writeFileSync('icons.json',JSON.stringify(out));console.log('icons',Object.keys(out).length,'avg bytes',Math.round(Object.values(out).reduce((a,b)=>a+b.length,0)/160));})();
