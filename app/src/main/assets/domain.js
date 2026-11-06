(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.Linkday = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  const DAY = 86400000, ZONE = 8 * 3600000;
  const modes = {taxi:'打车',bus:'公交',metro:'地铁',walk:'步行',other:'其他'};
  function id() { return Date.now().toString(36) + Math.random().toString(36).slice(2,9); }
  function dayKey(ms = Date.now()) { return new Date(ms + ZONE).toISOString().slice(0,10); }
  function atMs(s) { return Date.parse(s + (s.length === 10 ? 'T00:00:00+08:00' : ':00+08:00')); }
  function plusDay(s,n) { return dayKey(atMs(s) + n * DAY); }
  function weekday(s) { return new Date(atMs(s) + ZONE).getUTCDay() || 7; }
  function monday(s) { return plusDay(s,1-weekday(s)); }
  function minutes(s) { const [h,m] = s.split(':').map(Number); return h*60+m; }
  function money(n) { return Number(n).toFixed(2).replace(/\.00$/,'').replace(/(\.\d)0$/,'$1'); }
  function cents(n) { return Math.round(Number(n)*100); }
  function fare(n) { return n === '' || n === null || n === undefined ? null : Math.round(Number(n)*100)/100; }
  function validFare(n) { return n === null || (typeof n === 'number' && Number.isFinite(n) && n >= 0 && n <= 100000 && Math.abs(n*100-Math.round(n*100))<0.00001); }
  function leg(name,mode,currency,min=null,max=null,access=false) { return {id:id(),name,mode,currency,min,max:max===null?min:max,access}; }
  function defaults() {
    return {version:2,courses:[],tasks:[],trips:[],active:null,settings:{name:'同学',autoLocation:false},routes:[
      {id:'luohu-direct',name:'罗湖 · 港铁直达',port:'罗湖',tag:'少换乘',description:'罗湖过关后，东铁线直达大学站',legs:[leg('深圳出发地 → 罗湖口岸','taxi','CNY',15,17,true),leg('罗湖 → 大学站','metro','HKD')]},
      {id:'luohu-split',name:'罗湖 · 上水出站再入站',port:'罗湖',tag:'分段乘车',description:'在上水出站后重新入闸，两程分别记账',legs:[leg('深圳出发地 → 罗湖口岸','bus','CNY',1.6,1.6,true),leg('罗湖 → 上水','metro','HKD'),leg('上水 → 大学站','metro','HKD')]},
      {id:'wenjindu',name:'文锦渡 · 上水换乘',port:'文锦渡',tag:'巴士接驳',description:'打车到文锦渡，巴士接驳上水，再转东铁线',legs:[leg('深圳出发地 → 文锦渡口岸','taxi','CNY'),leg('文锦渡 → 上水','bus','HKD'),leg('上水 → 大学站','metro','HKD')]}
    ]};
  }
  function occurs(course,date) {
    if(date<course.from || date>course.until || !course.days.includes(weekday(date))) return false;
    const week=Math.floor((atMs(monday(date))-atMs(monday(course.from)))/(7*DAY))+1;
    return course.repeat==='all' || (course.repeat==='odd' ? week%2===1 : week%2===0);
  }
  function sessions(data,date) { return data.courses.filter(c=>occurs(c,date)).sort((a,b)=>a.start.localeCompare(b.start)); }
  function conflicts(data,course) {
    return data.courses.filter(c=>c.id!==course.id && minutes(c.start)<minutes(course.end) && minutes(course.start)<minutes(c.end) && (()=>{
      const first=c.from>course.from?c.from:course.from, last=c.until<course.until?c.until:course.until;
      for(let d=first,count=0;d<=last && count<15;d=plusDay(d,1),count++) if(occurs(c,d)&&occurs(course,d))return true;
      return false;
    })());
  }
  function totals(legs,actual=false) {
    const sums={CNY:{min:0,max:0,missing:0},HKD:{min:0,max:0,missing:0}};
    for(const l of legs) { const s=sums[l.currency]; if(!s)continue; const lo=actual?l.actual:l.min, hi=actual?l.actual:l.max;
      if(lo===null||lo===undefined||hi===null||hi===undefined) s.missing++;
      else {s.min+=cents(lo); s.max+=cents(hi);}
    }
    for(const s of Object.values(sums)){s.min/=100;s.max/=100;}
    return sums;
  }
  function monthly(data,month) { return totals(data.trips.filter(t=>t.date.slice(0,7)===month).flatMap(t=>t.legs),true); }
  function tripLegs(route,direction='out') {
    const legs=JSON.parse(JSON.stringify(route.legs));
    if(direction==='back') {legs.reverse();for(const l of legs){const p=l.name.split('→');if(p.length===2)l.name=p[1].trim()+' → '+p[0].trim();}}
    return legs;
  }
  function accessMode(route,mode) {
    const r=JSON.parse(JSON.stringify(route));const l=r.legs.find(x=>x.access);if(!l||!modes[mode])return r;
    l.presets=l.presets||{};l.presets[l.mode]={min:l.min,max:l.max,currency:l.currency};
    const f=l.presets[mode]||(mode==='taxi'?{min:15,max:17,currency:'CNY'}:mode==='bus'?{min:1.6,max:1.6,currency:'CNY'}:mode==='walk'?{min:0,max:0,currency:'CNY'}:{min:null,max:null,currency:'CNY'});
    l.mode=mode;l.min=f.min;l.max=f.max;l.currency=f.currency;return r;
  }
  function upcoming(data,now=Date.now()) {return data.tasks.filter(t=>!t.done).sort((a,b)=>atMs(a.at)-atMs(b.at));}
  function dateValid(s) {return typeof s==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(s)&&Number.isFinite(atMs(s))&&dayKey(atMs(s))===s;}
  function timeValid(s) {return typeof s==='string'&&/^([01]\d|2[0-3]):[0-5]\d$/.test(s);}
  function text(s,n=200) {return typeof s==='string'&&s.length<=n;}
  function validate(d) {
    if(!d||d.version!==2||!Array.isArray(d.courses)||!Array.isArray(d.tasks)||!Array.isArray(d.routes)||!Array.isArray(d.trips))throw Error('不是通勤助手 2.0 的备份文件');
    if(d.courses.length>500||d.tasks.length>2000||d.routes.length>100||d.trips.length>10000)throw Error('记录数量超过上限');
    const ids=new Set();function unique(x){if(!text(x.id,100)||!x.id||ids.has(x.id))throw Error('记录编号重复或无效');ids.add(x.id);}
    function checkLeg(l,actual=false) {if(!l||!text(l.name)||!l.name.trim()||!modes[l.mode]||!['CNY','HKD'].includes(l.currency)||!validFare(l.min)||!validFare(l.max)||((l.min===null)!==(l.max===null))||(l.min!==null&&l.max!==null&&l.min>l.max)||(actual&&!validFare(l.actual)))throw Error('路段或金额格式不正确');if(l.presets)for(const [mode,p] of Object.entries(l.presets)){if(!modes[mode]||!p||!validFare(p.min)||!validFare(p.max)||((p.min===null)!==(p.max===null))||(p.min!==null&&p.min>p.max)||!['CNY','HKD'].includes(p.currency))throw Error('交通方式预设格式不正确');}}
    for(const c of d.courses) {unique(c);if(!text(c.name)||!c.name.trim()||!text(c.code||'')||!text(c.place||'')||!text(c.teacher||'')||!Array.isArray(c.days)||!c.days.length||c.days.some(x=>!Number.isInteger(x)||x<1||x>7)||!timeValid(c.start)||!timeValid(c.end)||c.end<=c.start||!dateValid(c.from)||!dateValid(c.until)||c.until<c.from||!['all','odd','even'].includes(c.repeat)||!Number.isInteger(c.color)||c.color<0||c.color>4)throw Error('课程时间或学期范围不正确');}
    for(const t of d.tasks) {unique(t);if(!text(t.title)||!t.title.trim()||!['assignment','exam'].includes(t.type)||!text(t.courseId,100)||!text(t.place||'')||!text(t.note||'',2000)||typeof t.at!=='string'||!dateValid(t.at.slice(0,10))||!timeValid(t.at.slice(11))||t.at[10]!=='T'||typeof t.done!=='boolean'||![null,0,15,60,1440].includes(t.remind))throw Error('作业或考试格式不正确');}
    for(const r of d.routes) {unique(r);if(!text(r.name)||!r.name.trim()||!text(r.port)||!text(r.description||'')||!text(r.tag||'')||!Array.isArray(r.legs)||!r.legs.length||r.legs.length>12)throw Error('路线格式不正确');r.legs.forEach(l=>checkLeg(l));}
    function checkTrip(t,active){if(!t||!text(t.id,100)||!text(t.routeId,100)||!text(t.name)||!dateValid(t.date)||!['out','back'].includes(t.direction)||!Number.isFinite(t.startedAt)||!Array.isArray(t.legs)||!t.legs.length||t.legs.length>12||!text(t.note||'',2000))throw Error('行程格式不正确');t.legs.forEach(l=>checkLeg(l,!active));}
    for(const t of d.trips){unique(t);checkTrip(t,false);}
    if(d.active)checkTrip(d.active,true);
    if(!d.settings||!text(d.settings.name,30)||typeof d.settings.autoLocation!=='boolean')throw Error('偏好设置不正确');
    return d;
  }
  function demo() {
    const d=defaults(),today=dayKey(),mon=monday(today),from=plusDay(mon,-28),until=plusDay(mon,56);
    d.courses=[
      {id:'demo-c1',name:'机器学习',code:'DEMO 5101',teacher:'示例课程',place:'教学楼 · 302',days:[weekday(today)],start:'14:30',end:'17:15',from,until,repeat:'all',color:0},
      {id:'demo-c2',name:'自然语言处理',code:'DEMO 5202',teacher:'示例课程',place:'教学楼 · 201',days:[2,4],start:'18:30',end:'21:15',from,until,repeat:'all',color:1},
      {id:'demo-c3',name:'AI 项目研讨',code:'DEMO 5303',teacher:'示例课程',place:'线上会议',days:[6],start:'10:00',end:'12:00',from,until,repeat:'all',color:2}
    ];
    d.tasks=[{id:'demo-t1',courseId:'demo-c1',type:'assignment',title:'实验报告 · 模型对比',at:plusDay(today,2)+'T23:59',place:'',note:'示例内容，请以自己的课程要求为准',done:false,remind:60},{id:'demo-t2',courseId:'demo-c2',type:'exam',title:'期中考试',at:plusDay(today,5)+'T18:30',place:'教学楼 · 201',note:'',done:false,remind:1440}];
    for(let i=0;i<7;i++){const r=d.routes[i%2],date=plusDay(today,-i);const legs=tripLegs(r);for(const l of legs)l.actual=l.currency==='CNY'?(i%2?1.6:16):(l.name.includes('罗湖')?24:8);d.trips.push({id:'demo-trip'+i,routeId:r.id,name:r.name,direction:'out',date,startedAt:atMs(date+'T09:00'),endedAt:atMs(date+'T10:30'),legs,note:'演示费用，不代表实际票价'});}
    return d;
  }
  return {DAY,id,dayKey,atMs,plusDay,weekday,monday,minutes,money,fare,validFare,modes,leg,defaults,occurs,sessions,conflicts,totals,monthly,tripLegs,accessMode,upcoming,validate,dateValid,timeValid,demo};
});
