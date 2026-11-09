(function(root,factory){const api=factory(typeof module==='object'?require('./domain.js'):root.Linkday);if(typeof module==='object'&&module.exports)module.exports=api;else root.CommuteFlow=api;})(typeof globalThis!=='undefined'?globalThis:this,function(L){
  'use strict';
  const termSource='https://www.gs.cuhk.edu.hk/download/2627_TDPP_ERG.pdf';
  const terms=[
    {id:'cuhk-2026-t1',label:'CUHK 2026–27 · 第一学期',from:'2026-09-07',until:'2026-12-05'},
    {id:'cuhk-2026-t2',label:'CUHK 2026–27 · 第二学期',from:'2027-01-11',until:'2027-04-24'},
    {id:'cuhk-2026-summer',label:'CUHK 2026–27 · 暑期',from:'2027-05-17',until:'2027-07-03'}
  ];
  const clone=x=>JSON.parse(JSON.stringify(x));
  function suggestedTerm(today=L.dayKey()) {return clone(terms.find(t=>today>=t.from&&today<=t.until)||terms.find(t=>t.from>today)||{id:'custom',label:'自定义学期',from:today,until:L.plusDay(today,112)});}
  function migrate(d){
    if(!d.settings.term){const counts=new Map();for(const c of d.courses){const key=c.from+'|'+c.until;counts.set(key,(counts.get(key)||0)+1);}const common=[...counts].sort((a,b)=>b[1]-a[1])[0];if(common){const [from,until]=common[0].split('|');d.settings.term=clone(terms.find(t=>t.from===from&&t.until===until)||{id:'existing',label:'已有课程的学期',from,until});}else d.settings.term=suggestedTerm();}
    if(!d.dayPlans)d.dayPlans=[];
    if(!d.settings.accessOptions)d.settings.accessOptions=[];
    if(!d.settings.accessPresets)d.settings.accessPresets={};
    if(!d.settings.lastModes)d.settings.lastModes={out:'taxi',back:'taxi'};
    for(const c of d.courses)if(c.followTerm===undefined)c.followTerm=c.from===d.settings.term.from&&c.until===d.settings.term.until;
    for(const r of d.routes)if(['luohu-direct','luohu-split','wenjindu'].includes(r.id)&&r.legs.length)r.legs[0].access=true;
    return d;
  }
  function following(d){return d.courses.filter(c=>c.followTerm!==false&&c.from===d.settings.term.from&&c.until===d.settings.term.until);}
  function setTerm(d,term,apply=true){if(!term||!L.dateValid(term.from)||!L.dateValid(term.until)||term.until<term.from)throw Error('请选择有效的学期起止日期');const affected=new Set(following(d).map(c=>c.id));if(apply)for(const c of d.courses)if(affected.has(c.id)){c.from=term.from;c.until=term.until;c.followTerm=true;}d.settings.term={id:String(term.id||'custom').slice(0,100),label:String(term.label||'自定义学期').slice(0,100),from:term.from,until:term.until};return apply?affected.size:0;}
  function choice(d,routeId,mode,dir='out'){
    const route=d.routes.find(r=>r.id===routeId);if(!route)throw Error('请先选择路线');const option=(d.settings.accessOptions||[]).find(o=>o.id===mode);let r=L.accessMode(route,option?option.legs[0].mode:mode);if(option){const replacement=clone(option.legs).map(l=>({...l,access:true,accessOptionId:option.id,accessLabel:option.name}));const at=r.legs.findIndex(l=>l.access);r.legs=r.legs.filter(l=>!l.access);r.legs.splice(at<0?0:at,0,...replacement);}const access=r.legs.find(l=>l.access);const preset=d.settings.accessPresets[route.port+'|'+mode];if(access&&preset&&!option)Object.assign(access,clone(preset));
    return {routeId:r.id,name:r.name,port:r.port,accessOptionId:option?.id||'',mode:access?.mode||mode,legs:L.tripLegs(r,dir)};
  }
  function plan(d,date){return d.dayPlans.find(p=>p.date===date)||{date,out:null,back:null};}
  function setChoice(d,date,dir,c){if(!L.dateValid(date)||!['out','back'].includes(dir))throw Error('通勤日期或方向不正确');let p=d.dayPlans.find(p=>p.date===date);if(!p){p={date,out:null,back:null};d.dayPlans.push(p);}p[dir]=clone(c);if(c&&!c.skipped)d.settings.lastModes[dir]=c.accessOptionId||c.mode;return p;}
  function actualTrip(d,date,dir){return d.trips.filter(t=>t.date===date&&t.direction===dir).sort((a,b)=>(b.endedAt||b.startedAt)-(a.endedAt||a.startedAt))[0]||null;}
  function lastChoice(d,dir,date){const previous=d.dayPlans.filter(p=>p.date<date&&p[dir]&&!p[dir].skipped).sort((a,b)=>b.date.localeCompare(a.date))[0];if(previous)return clone(previous[dir]);const trip=d.trips.filter(t=>t.direction===dir&&t.date<date).sort((a,b)=>b.date.localeCompare(a.date)||b.startedAt-a.startedAt)[0];if(!trip)return null;return {routeId:trip.routeId,name:trip.name,port:d.routes.find(r=>r.id===trip.routeId)?.port||'',mode:trip.legs.find(l=>l.access)?.mode||'taxi',legs:clone(trip.legs).map(l=>{delete l.actual;return l;})};}
  function rememberFares(d,c,legs){for(let i=0;i<legs.length;i++){const l=legs[i];if(l.actual===null||l.mode==='taxi')continue;if(l.accessOptionId){const base=d.settings.accessOptions?.find(o=>o.id===l.accessOptionId)?.legs.find(x=>x.id===l.id);if(base){base.min=l.actual;base.max=l.actual;base.currency=l.currency;}}else if(l.access){d.settings.accessPresets[c.port+'|'+l.mode]={min:l.actual,max:l.actual,currency:l.currency};}else{const route=d.routes.find(r=>r.id===c.routeId),base=route?.legs.find(x=>x.id===l.id);if(base){base.min=l.actual;base.max=l.actual;base.currency=l.currency;base.mode=l.mode;}}}}
  function courseKey(c){return [c.code||c.name,[...c.days].sort().join(','),c.start,c.end,c.from,c.until].join('|').toLowerCase().replace(/\s+/g,'');}
  function addCourses(d,rows){let added=0,duplicates=0;const keys=new Set(d.courses.map(courseKey));for(const row of rows){const c={...row,id:L.id()};const key=courseKey(c);if(keys.has(key)){duplicates++;continue;}L.validate({...d,courses:[c]});d.courses.push(c);keys.add(key);added++;}return {added,duplicates};}
  function validate(d){L.validate(d);if(d.settings.accessOptions!==undefined){if(!Array.isArray(d.settings.accessOptions)||d.settings.accessOptions.length>100)throw Error('深圳选项格式不正确');L.validate({...d,courses:[],tasks:[],trips:[],active:null,routes:d.settings.accessOptions.map(o=>({...o,port:''}))});}const t=d.settings.term;if(!t||typeof t.id!=='string'||typeof t.label!=='string'||t.label.length>100||!L.dateValid(t.from)||!L.dateValid(t.until)||t.until<t.from)throw Error('学期设置不正确');if(!Array.isArray(d.dayPlans)||d.dayPlans.length>2000)throw Error('每日通勤安排不正确');const dates=new Set();for(const p of d.dayPlans){if(!L.dateValid(p.date)||dates.has(p.date))throw Error('通勤日期重复或无效');dates.add(p.date);for(const dir of ['out','back']){const c=p[dir];if(!c)continue;if(c.skipped===true)continue;if(!L.modes[c.mode]||typeof c.port!=='string')throw Error('交通方式不正确');L.validate({...d,courses:[],tasks:[],trips:[],active:null,routes:[{id:c.routeId,name:c.name,port:c.port,legs:c.legs}]});}}
    if(!d.settings.accessPresets||Array.isArray(d.settings.accessPresets)||typeof d.settings.accessPresets!=='object')throw Error('费用预设格式不正确');for(const [key,p] of Object.entries(d.settings.accessPresets)){if(key.length>100||!p||!L.validFare(p.min)||!L.validFare(p.max)||((p.min===null)!==(p.max===null))||(p.min!==null&&p.min>p.max)||!['CNY','HKD'].includes(p.currency))throw Error('费用预设格式不正确');}return d;}
  return {terms,termSource,suggestedTerm,migrate,following,setTerm,choice,plan,setChoice,actualTrip,lastChoice,rememberFares,courseKey,addCourses,validate,clone};
});
