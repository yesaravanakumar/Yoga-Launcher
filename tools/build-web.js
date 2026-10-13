#!/usr/bin/env node
// Assembles app/src/main/assets/index.html from app/src/web/*.html
// usage: node tools/build-web.js            readable build (debug)
//        MINIFY=1 node tools/build-web.js   release build: minified HTML/CSS/JS (needs `npm ci` in tools/)
const fs=require('fs'),path=require('path');
const src=path.join(__dirname,'..','app','src','web'),out=path.join(__dirname,'..','app','src','main','assets','index.html');
const cache={};
const part=n=>cache[n]||(cache[n]=(()=>{const t=fs.readFileSync(path.join(src,n+'.html'),'utf8'),re=/<!--@@(\w+)-->\n/g,ms=[...t.matchAll(re)],o={};
  ms.forEach((m,i)=>{o[m[1]]=t.slice(m.index+m[0].length,i+1<ms.length?ms[i+1].index:t.length)});return o})());
const html=fs.readFileSync(path.join(src,'index.src.html'),'utf8').replace(/<!--@use (\w+):(\w+)-->\n/g,(_,f,s)=>{const v=part(f)[s];if(v==null)throw new Error('missing section '+f+':'+s);return v});
if(process.env.MINIFY==='1'||process.argv.includes('--minify')){
  const {minify}=require('html-minifier-terser');
  minify(html,{collapseWhitespace:true,conservativeCollapse:true,removeComments:true,minifyCSS:true,
    // compress only: never rename top-level names, the Kotlin side and separate <script> blocks call each other by name
    minifyJS:{compress:{passes:1,unsafe:false},mangle:{toplevel:false,reserved:[]},format:{comments:false}}})
  .then(m=>{fs.writeFileSync(out,m);console.log('wrote (minified)',out,html.length,'->',m.length,'bytes')})
  .catch(e=>{console.error('minify failed:',e.message);process.exit(1)});
}else{fs.writeFileSync(out,html);console.log('wrote',out,html.length,'bytes')}
