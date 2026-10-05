const state={page:"dashboard",pageSize:10,page:1,downloads:[],allDownloads:[],queues:[],connected:false,query:""};
const sample=[{name:"Ubuntu.iso",size:"4.7 GB",progress:82,speed:"18.4 MB/s",eta:"1m 32s",status:"Downloading"},{name:"TrueNAS-25.10.7.iso",size:"1.9 GB",progress:100,speed:"—",eta:"Complete",status:"Completed"},{name:"LinuxMint.iso",size:"3.1 GB",progress:34,speed:"8.7 MB/s",eta:"4m 18s",status:"Downloading"},{name:"backup.zip",size:"12.4 GB",progress:0,speed:"—",eta:"Queued",status:"Queued"}];
state.downloads=[];

const titles={dashboard:["Dashboard","Download manager overview"],downloads:["Downloads","All download tasks"],queue:["Queue","Manage download queues"],browser:["Browser","Repository and file browser"],categories:["Categories","Organize downloads"],scheduler:["Scheduler","Scheduled download rules"],settings:["Settings","ABDM service configuration"]};
function esc(s){return String(s).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]))}
function paginate(items){const start=(state.page-1)*state.pageSize;return {items:items.slice(start,start+state.pageSize),pages:Math.max(1,Math.ceil(items.length/state.pageSize)),start}}
function pagination(total,pages){return `<div class="pagination"><span>Showing ${total?((state.page-1)*state.pageSize+1):0}–${Math.min(state.page*state.pageSize,total)} of ${total}</span><div class="pages">${Array.from({length:pages},(_,i)=>`<button class="${i+1===state.page?"active":""}" onclick="setPage(${i+1})">${i+1}</button>`).join("")}</div><label style="display:flex;align-items:center;gap:7px">Per page<select class="page-size" onchange="setSize(this.value)"><option>10</option><option>25</option><option>50</option></select></label></div>`}
function table(items){return `<div class="card table-wrap"><table class="table"><thead><tr><th>NAME</th><th>SIZE</th><th>PROGRESS</th><th>SPEED</th><th>ETA</th><th>STATUS</th></tr></thead><tbody>${items.map(x=>`<tr><td class="name" title="${esc(x.name)}">${esc(x.name)}</td><td>${x.size}</td><td><div style="display:flex;align-items:center;gap:9px"><div class="progress"><i style="width:${x.progress}%"></i></div><span>${x.progress}%</span></div></td><td>${x.speed}</td><td>${x.eta}</td><td><span class="tag">${x.status}</span></td></tr>`).join("")}</tbody></table></div>`}
function renderDashboard(){const dp=paginate(state.downloads);const active=state.downloads.filter(x=>x.status==="Downloading").length;const done=state.downloads.filter(x=>x.progress===100).length;return `<div class="grid stats"><div class="card"><div class="stat-label">Active downloads</div><div class="stat-value">${active}</div><div class="stat-extra">Currently transferring</div></div><div class="card"><div class="stat-label">Queued</div><div class="stat-value">${state.downloads.filter(x=>x.status==="Queued").length}</div><div class="stat-extra">Waiting to start</div></div><div class="card"><div class="stat-label">Completed</div><div class="stat-value">${done}</div><div class="stat-extra">Finished downloads</div></div><div class="card"><div class="stat-label">Current speed</div><div class="stat-value">27.1 MB/s</div><div class="stat-extra">Combined active speed</div></div></div><div class="grid two-col"><div><div class="section-head"><h2>Latest downloads</h2><span>Latest downloads</span></div>${table(dp.items)}${pagination(state.downloads.length,dp.pages)}</div><div><div class="section-head"><h2>Queue</h2><span>Overview</span></div><div class="card list"><div class="row"><div><strong>Default</strong><small>2 active · 1 queued</small></div><span class="tag">Running</span></div><div class="row"><div><strong>Movies</strong><small>0 active · 4 queued</small></div><span class="tag">Paused</span></div></div></div></div>`}
function renderDownloads(){const p=paginate(state.downloads);return `<div class="toolbar"><input class="search grow" placeholder="Search downloads…" oninput="filterDownloads(this.value)"><button class="secondary">Pause all</button><button class="secondary">Resume all</button></div>${table(p.items)}${pagination(state.downloads.length,p.pages)}`}
function renderQueue(){const p=paginate([{name:"Default",active:2,queued:1,status:"Running"},{name:"Movies",active:0,queued:4,status:"Paused"},{name:"Games",active:1,queued:7,status:"Running"}]);return `<div class="toolbar"><div class="grow"></div><button class="primary">＋ New queue</button></div><div class="card table-wrap"><table class="table"><thead><tr><th>QUEUE</th><th>ACTIVE</th><th>QUEUED</th><th>STATUS</th></tr></thead><tbody>${p.items.map(x=>`<tr><td>${x.name}</td><td>${x.active}</td><td>${x.queued}</td><td><span class="tag">${x.status}</span></td></tr>`).join("")}</tbody></table></div>${pagination(3,p.pages)}`}
function renderSimple(name,text){return `<div class="card empty"><strong>${name}</strong>${text}</div>`}
function render(){document.getElementById("page-title").textContent=titles[state.page][0];document.getElementById("page-subtitle").textContent=titles[state.page][1];document.querySelectorAll(".nav-item").forEach(x=>x.classList.toggle("active",x.dataset.page===state.page));let html=state.page==="dashboard"?renderDashboard():state.page==="downloads"?renderDownloads():state.page==="queue"?renderQueue():state.page==="browser"?renderSimple("Browser","Repository browsing will use the headless backend API."):state.page==="categories"?renderSimple("Categories","Create and manage download categories."):state.page==="scheduler"?renderSimple("Scheduler","Configure scheduled download rules."):settings();document.getElementById("app").innerHTML=html}
function settings(){return `<div class="settings-grid"><div class="card"><div class="section-head"><h2>Backend API</h2></div><div class="form-grid"><label>API base URL<input value="${esc(window.ABDM_API.baseUrl)}" readonly></label><label>Connection status<input value="${state.connected?"Connected":"Not connected"}" readonly></label></div></div><div class="card"><div class="section-head"><h2>Storage</h2></div><div class="form-grid"><label>Downloads path<input value="/mnt/dataPool/abdm/downloads"></label><label>Temporary path<input value="/mnt/dataPool/abdm/temp"></label></div></div></div>`}
window.setPage=n=>{state.page=Number(n);render()};window.setSize=n=>{state.pageSize=Number(n);state.page=1;render()};window.filterDownloads=q=>{state.query=(q||"").toLowerCase();state.downloads=state.query?state.allDownloads.filter(x=>x.name.toLowerCase().includes(state.query)):state.allDownloads.slice();state.page=1;render()};
async function loadDownloads(quiet=true){
  if(!state.connected)return;
  try{
    const items=await ABDM_API.downloads();
    state.allDownloads=(Array.isArray(items)?items:[]).map(x=>({id:x.id,name:x.name,size:formatBytes(x.size),progress:x.percent==null?0:x.percent,speed:formatSpeed(x.speed),eta:formatEta(x.eta),status:x.status,raw:x}));
    state.downloads=state.query?state.allDownloads.filter(x=>x.name.toLowerCase().includes(state.query)):state.allDownloads.slice();
    if(!quiet)render();
  }catch(err){console.warn("Unable to load downloads",err)}
}
function formatBytes(value){
  if(value==null||value<0)return "—";
  const units=["B","KB","MB","GB","TB"];let n=Number(value),i=0;
  while(n>=1024&&i<units.length-1){n/=1024;i++}
  return (n>=100||i===0?n.toFixed(0):n.toFixed(1))+" "+units[i];
}
function formatSpeed(value){return value>0?formatBytes(value)+"/s":"—"}
function formatEta(value){
  if(value==null||value<0)return "—";
  if(value===0)return "Complete";
  let s=Math.floor(value),h=Math.floor(s/3600);s%=3600;
  let m=Math.floor(s/60);s%=60;
  if(h)return h+"h "+m+"m";
  if(m)return m+"m "+s+"s";
  return s+"s";
}
document.getElementById("nav").addEventListener("click",e=>{const b=e.target.closest(".nav-item");if(b){state.page=b.dataset.page;state.page=1;render()}});
document.getElementById("addBtn").onclick=()=>document.getElementById("addDialog").showModal();
document.getElementById("addForm").addEventListener("submit",async e=>{e.preventDefault();const urls=document.getElementById("urlInput").value.split(/\r?\n/).map(x=>x.trim()).filter(Boolean);if(!urls.length)return;try{await ABDM_API.add({urls,folder:document.getElementById("pathInput").value});document.getElementById("addDialog").close();}catch(err){alert("Backend API error: "+err.message)}});
async function connect(){try{await ABDM_API.ping();state.connected=true;await loadDownloads(true);document.querySelector(".status-dot").classList.add("ok");document.getElementById("connection").textContent="Backend connected"}catch(e){document.getElementById("connection").textContent="Demo mode"}render()}
render();connect();
setInterval(()=>loadDownloads(true),1500);
