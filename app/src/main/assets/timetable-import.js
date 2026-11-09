(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.TimetableImport=api;})(typeof globalThis!=='undefined'?globalThis:this,function(){
  'use strict';
  const dayNames=['一','二','三','四','五','六','日'];
  const english=['monday','tuesday','wednesday','thursday','friday','saturday','sunday'];
  function clean(s){return String(s||'').normalize('NFKC').replace(/[：]/g,':').replace(/[–—~～至]/g,'-').replace(/[\t]+/g,' ').replace(/([\u3400-\u9fff])\s+(?=[\u3400-\u9fff])/g,'$1').trim();}
  function days(s){s=clean(s);const result=[];for(let i=0;i<7;i++){const chinese=new RegExp('(?:星期|周|週|礼拜|禮拜)\\s*'+(i===6?'[日天]':dayNames[i]));const short=['Mo(?:n)?','Tu(?:e|es)?','We(?:d)?','Th(?:u|urs)?','Fr(?:i)?','Sa(?:t)?','Su(?:n)?'][i];if(chinese.test(s)||new RegExp('\\b(?:'+english[i]+'|'+short+')\\b','i').test(s))result.push(i+1);}return result;}
  const timePattern=/(\d{1,2})\s*[:.]\s*(\d{2})\s*(am|pm)?\s*-\s*(\d{1,2})\s*[:.]\s*(\d{2})\s*(am|pm)?/i;
  function timeRange(s){const m=clean(s).match(timePattern);if(!m)return null;let h1=+m[1],h2=+m[4];const a=(m[3]||m[6]||'').toLowerCase(),b=(m[6]||m[3]||'').toLowerCase();if(a&&h1<=12)h1=h1%12+(a==='pm'?12:0);if(b&&h2<=12)h2=h2%12+(b==='pm'?12:0);if(h1>23||h2>23||+m[2]>59||+m[5]>59||h2*60+(+m[5])<=h1*60+(+m[2]))return null;return {start:String(h1).padStart(2,'0')+':'+m[2],end:String(h2).padStart(2,'0')+':'+m[5],raw:m[0]};}
  const codePattern=/\b([A-Z]{2,8})\s*(\d{4}[A-Z]?)\b/;
  function candidate(text,inferredDays=[]){
    const lines=String(text||'').split(/\n/).map(clean).filter(Boolean),joined=lines.join(' '),time=timeRange(joined),ownDays=days(joined),code=(joined.match(codePattern)||[])[0]||'';
    const placeLine=lines.find(l=>/(?:教室|教学楼|教學樓|地点|地點|课室|課室|room|venue|location|lecture theatre|\b(?:ERB|LSB|CYT|YIA|SHB|LT)\b)/i.test(l));let place=placeLine?placeLine.replace(/^(?:教室|地点|地點|课室|課室|room|venue|location)\s*[:：]?\s*/i,''):'';
    const embeddedPlace=joined.match(/\b(?:ERB|LSB|CYT|YIA|SHB|LT)\s*[- ]?\d+[A-Za-z]?\b/i);if(embeddedPlace)place=embeddedPlace[0];
    if(place.includes('|'))place=place.split('|').pop().trim();
    const repeat=/双周|雙周|even\s+week/i.test(joined)?'even':/单周|單周|odd\s+week/i.test(joined)?'odd':'all';
    let name=lines.map(l=>{let v=l.replace(codePattern,'').replace(timePattern,'').replace(/(?:星期|周|週|礼拜|禮拜)\s*[一二三四五六日天]/g,'').replace(/\b(?:mon(?:day)?|tue(?:sday|s)?|wed(?:nesday)?|thu(?:rsday|rs)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)\b/gi,'').replace(/(?:单周|單周|双周|雙周|每周|every week|odd weeks?|even weeks?)/gi,'');if(l===placeLine&&lines.length>1)return '';return v.split('|').map(x=>x.trim()).filter(x=>x&&!/^(?:课程表|課程表|课表|課表|时间|時間|timetable|schedule|course|day|time|class)$/i.test(x))[0]||'';}).filter(Boolean)[0]||code;
    if(embeddedPlace)name=name.replace(embeddedPlace[0],'');name=name.replace(/\b(?:room|venue|location)\s*:?\s*$/i,'').replace(/^[:\s|,;\-]+|[:\s|,;\-]+$/g,'').slice(0,100);if(!name && code)name=code; if(!name||/^(?:\d+|\d{1,2}:\d{2})$/.test(name))return null;
    if(code && /^(?:lecture|tutorial|lab|laboratory)$/i.test(name))name=code; if(code && lines.some(l=>/^(?:lecture|tutorial|lab)$/i.test(l)) && time){const at=lines.findIndex(l=>timeRange(l));const venue=lines.slice(at+1).filter(l=>!days(l).length&&!/^(?:lecture|tutorial|lab)$/i.test(l));if(venue.length)place=venue.join(' ');} if(name===place&&lines.length===1&&!code)return null;
    return {name,code:code.replace(/\s+/g,' '),place:place.slice(0,100),teacher:'',days:ownDays.length?ownDays:inferredDays,start:time?.start||'',end:time?.end||'',repeat,color:0,source:text};
  }
  function parseText(text){
    const rows=String(text||'').split(/\r?\n/).map(clean).filter(Boolean),groups=[];let group=[];
    for(const line of rows){if(/^(?:课程表|課程表|课表|課表|timetable|schedule|20\d{2}\s*[-/]\s*20?\d{2}|课程\s*星期\s*时间)/i.test(line)&&!timeRange(line))continue;
      const joined=group.join(' '),complete=timeRange(joined)&&days(joined).length;
      if(group.length&&((codePattern.test(line)&&group.some(x=>codePattern.test(x)))||(timeRange(line)&&days(line).length&&complete))){groups.push(group.join('\n'));group=[];}
      group.push(line);
    }
    if(group.length)groups.push(group.join('\n'));return dedupe(groups.map(t=>candidate(t)).filter(c=>c&&(c.days.length||c.start||c.code)));
  }
  function wordsFrom(data){return (data.blocks||[]).flatMap(b=>(b.paragraphs||[]).flatMap(p=>(p.lines||[]).flatMap(l=>(l.words||[]).map(w=>({text:w.text,bbox:w.bbox,confidence:w.confidence}))))).filter(w=>w.bbox&&clean(w.text));}
  function linesFrom(words){const result=[];for(const w of [...words].sort((a,b)=>(a.bbox.y0+a.bbox.y1)-(b.bbox.y0+b.bbox.y1)||a.bbox.x0-b.bbox.x0)){const cy=(w.bbox.y0+w.bbox.y1)/2,h=w.bbox.y1-w.bbox.y0;let line=result.find(l=>Math.abs(l.cy-cy)<Math.max(7,Math.min(l.h,h)*0.65));if(!line){line={cy,h,words:[]};result.push(line);}line.words.push(w);line.cy=line.words.reduce((s,x)=>s+(x.bbox.y0+x.bbox.y1)/2,0)/line.words.length;line.h=Math.max(h,line.h);}return result.sort((a,b)=>a.cy-b.cy).map(l=>{const words=l.words.sort((a,b)=>a.bbox.x0-b.bbox.x0);return {...l,words,text:clean(words.map(w=>w.text).join(' ')),bbox:{x0:Math.min(...words.map(w=>w.bbox.x0)),x1:Math.max(...words.map(w=>w.bbox.x1)),y0:Math.min(...words.map(w=>w.bbox.y0)),y1:Math.max(...words.map(w=>w.bbox.y1))}};});}
  function parseOcr(data){
    const words=wordsFrom(data);if(!words.length)return {rows:parseText(data.text||''),layout:'list'};
    const candidates=[];for(const line of linesFrom(words)){const chunks=[];let chunk=[];for(const w of line.words){if(chunk.length&&w.bbox.x0-chunk[chunk.length-1].bbox.x1>line.h*2){chunks.push(chunk);chunk=[];}chunk.push(w);}if(chunk.length)chunks.push(chunk);for(const part of chunks){const text=part.map(w=>w.text).join(' '),d=days(text);if(d.length===1&&!timeRange(text)&&clean(text).length<20)candidates.push({day:d[0],x:(part[0].bbox.x0+part[part.length-1].bbox.x1)/2,y:line.cy,bottom:line.bbox.y1});}}
    let headers=[];for(const h of candidates){const near=candidates.filter(c=>Math.abs(c.y-h.y)<20);const unique=[...new Map(near.map(c=>[c.day,c])).values()].sort((a,b)=>a.x-b.x);if(unique.length>headers.length)headers=unique;}
    if(headers.length<3)return {rows:parseText(linesFrom(words).map(l=>l.text).join('\n')),layout:'list'};
    const output=[];for(let index=0;index<headers.length;index++){const h=headers[index],left=index?(headers[index-1].x+h.x)/2:h.x-(headers[1].x-h.x)/2,right=index<headers.length-1?(h.x+headers[index+1].x)/2:Infinity;const column=words.filter(w=>{const x=(w.bbox.x0+w.bbox.x1)/2;return x>left&&x<right&&w.bbox.y0>h.bottom+4;});const lines=linesFrom(column);let block=[];function flush(){if(block.length){const c=candidate(block.map(l=>l.text).join('\n'),[h.day]);if(c&&(c.start||c.code))output.push(c);block=[];}}
      for(const line of lines){const previous=block[block.length-1];if(previous&&(line.bbox.y0-previous.bbox.y1>Math.max(28,line.h*1.8)||(codePattern.test(line.text)&&block.some(l=>codePattern.test(l.text))))){flush();}block.push(line);}flush();}
    return {rows:dedupe(output),layout:'grid'};
  }
  function dedupe(rows){const seen=new Set();return rows.filter(c=>{const key=[c.code||c.name,c.days.join(','),c.start,c.end].join('|');if(seen.has(key))return false;seen.add(key);return true;}).slice(0,100).map((c,i)=>({...c,color:i%5}));}
  return {clean,days,timeRange,candidate,parseText,parseOcr,wordsFrom};
});
