const state={view:"dashboard",page:1,pageSize:10,downloads:[],allDownloads:[],queues:[],connected:false,query:"",browserPath:"",browserItems:[],categories:[],settings:null,lastReconnectAttempt:0,selectedIds:new Set(),importItems:[],detailsPollTimer:null,browserSession:null};
state.downloads=[];

const titles={dashboard:["Dashboard","Download manager overview"],downloads:["Downloads","All download tasks"],queue:["Queue","Manage download queues"],browser:["Browser","Repository and file browser"],categories:["Categories","Organize downloads"],scheduler:["Scheduler","Scheduled download rules"],settings:["Settings","ABDM service configuration"]};
function esc(s){return String(s).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]))}
function jsAttrArg(s){return esc(JSON.stringify(String(s)))}
function paginate(items){const pages=Math.max(1,Math.ceil(items.length/state.pageSize));if(state.page>pages)state.page=pages;const start=(state.page-1)*state.pageSize;return {items:items.slice(start,start+state.pageSize),pages,start}}
function pagination(total,pages){if(state.page>pages)state.page=pages;return `<div class="pagination"><span>Showing ${total?((state.page-1)*state.pageSize+1):0}–${Math.min(state.page*state.pageSize,total)} of ${total}</span><div class="pages">${Array.from({length:pages},(_,i)=>`<button class="${i+1===state.page?"active":""}" onclick="setPage(${i+1})">${i+1}</button>`).join("")}</div><label style="display:flex;align-items:center;gap:7px">Per page<select class="page-size" onchange="setSize(this.value)"><option value="10" ${state.pageSize===10?"selected":""}>10</option><option value="25" ${state.pageSize===25?"selected":""}>25</option><option value="50" ${state.pageSize===50?"selected":""}>50</option></select></label></div>`}
function actionButtons(x){
  const id=Number(x.id);
  const pauseResume=x.status==="Downloading"||x.status==="Preparing"||x.status==="Retrying"
    ?`<button class="icon-btn" title="Pause" onclick="downloadAction(${id},'pause')">⏸</button>`
    :x.status==="Paused"||x.status==="Queued"
      ?`<button class="icon-btn" title="Resume" onclick="downloadAction(${id},'resume')">▶</button>`
      :"";
  const retry=x.status==="Completed"?"" : `<button class="icon-btn" title="Retry" onclick="downloadAction(${id},'retry')">↻</button>`;
  return `<span class="actions">${pauseResume}${retry}<button class="icon-btn" title="Move to queue" onclick="moveDownloadToQueue(${id})">☷</button><button class="icon-btn danger" title="Remove download" onclick="downloadAction(${id},'remove',false)">✕</button></span>`;
}
function table(items){const ids=items.map(x=>Number(x.id));const allChecked=ids.length>0&&ids.every(id=>state.selectedIds.has(id));return `<div class="card table-wrap" data-scroll-key="download-table"><table class="table download-table"><thead><tr><th class="select-col"><input type="checkbox" aria-label="Select all" ${allChecked?"checked":""} onchange="togglePageSelection(this.checked)"></th><th>NAME</th><th>SIZE</th><th>PROGRESS</th><th>SPEED</th><th>ETA</th><th>QUEUE</th><th>STATUS</th><th></th></tr></thead><tbody>${items.map(x=>`<tr><td class="select-col"><input type="checkbox" aria-label="Select ${esc(x.name)}" ${state.selectedIds.has(Number(x.id))?"checked":""} onchange="toggleSelected(${x.id},this.checked);event.stopPropagation()"></td><td class="name clickable" title="${esc(x.name)}" onclick="showDownload(${x.id})">${esc(x.name)}</td><td>${x.size}</td><td><div style="display:flex;align-items:center;gap:9px"><div class="progress"><i style="width:${x.progress}%"></i></div><span>${x.progress}%</span></div></td><td>${x.speed}</td><td>${x.eta}</td><td>${esc(x.queueName||"No queue")}</td><td><span class="tag">${esc(x.status)}</span></td><td>${actionButtons(x)}</td></tr>`).join("")}</tbody></table></div>`}
function selectionToolbar(){const count=state.selectedIds.size;if(!count)return "";return `<div class="selection-toolbar"><strong>${count} selected</strong><button class="secondary" onclick="selectedAction('pause')">Pause</button><button class="secondary" onclick="selectedAction('resume')">Start</button><button class="secondary" onclick="selectedAction('remove')">Cancel / Remove</button><button class="secondary danger-action" onclick="selectedAction('remove',true)">Delete files</button><button class="icon-btn" title="Clear selection" onclick="clearSelection()">×</button></div>`}
function renderDashboard(){
  const dp=paginate(state.allDownloads);
  const categories=state.categories||[];
  const queues=state.queues||[];
  const active=state.allDownloads.filter(x=>["Downloading","Preparing","Retrying"].includes(x.status)).length;
  const speed=state.allDownloads.reduce((n,x)=>n+Number(x.raw?.speed||0),0);
  const categoryItems=cat=>state.allDownloads.filter(x=>Number(x.raw?.categoryId??x.categoryId)===Number(cat.id)).length;
  return '<div class="abdm-home">'+
    '<aside class="home-sidebar">'+
      '<div class="home-sidebar-title">Categories</div>'+
      '<button class="home-filter active"><span class="home-filter-icon">◉</span><span>All downloads</span><b>'+state.allDownloads.length+'</b></button>'+
      '<button class="home-filter"><span class="home-filter-icon">↓</span><span>Downloading</span><b>'+active+'</b></button>'+
      '<button class="home-filter"><span class="home-filter-icon">✓</span><span>Completed</span><b>'+state.allDownloads.filter(x=>x.status==="Completed").length+'</b></button>'+
      '<button class="home-filter"><span class="home-filter-icon">!</span><span>Error</span><b>'+state.allDownloads.filter(x=>x.status==="Error").length+'</b></button>'+
      '<div class="home-divider"></div>'+
      categories.map(cat=>'<button class="home-filter"><span class="home-filter-icon">▱</span><span>'+esc(cat.name)+'</span><b>'+categoryItems(cat)+'</b></button>').join('')+
      '<div class="home-sidebar-title queue-title">Queues</div>'+
      queues.map(q=>'<button class="home-filter"><span class="home-filter-icon">☷</span><span>'+esc(q.name)+'</span><b>'+(q.queued||0)+'</b></button>').join('')+
    '</aside>'+
    '<section class="home-content">'+
      '<div class="home-toolbar"><div class="home-toolbar-left">'+
        '<button class="home-add" onclick="document.getElementById(\'addBtn\').click()">＋ <span>Add URL</span></button>'+
        '<button class="home-tool" title="Start selected" onclick="selectedAction(\'resume\')">▶</button>'+
        '<button class="home-tool" title="Pause selected" onclick="selectedAction(\'pause\')">Ⅱ</button>'+
        '<button class="home-tool" title="Remove selected" onclick="selectedAction(\'remove\')">×</button>'+
      '</div><div class="home-search"><span>⌕</span><input id="homeSearch" placeholder="Search downloads" value="'+esc(state.query)+'" oninput="filterDownloads(this.value)"></div></div>'+
      selectionToolbar()+
      '<div class="home-table-wrap">'+table(dp.items)+'</div>'+
      (state.allDownloads.length?pagination(state.allDownloads.length,dp.pages):'<div class="abdm-empty"><div class="abdm-empty-icon">↓</div><strong>No downloads</strong><span>Add a URL to start downloading.</span></div>')+
      '<div class="home-footer"><span>Downloads: <b>'+state.allDownloads.length+'</b></span><span>Active: <b>'+active+'</b></span><span class="footer-spacer"></span><span>Speed: <b>'+formatSpeed(speed)+'</b></span></div>'+
    '</section></div>';
}
function renderDownloads(){
  const p=paginate(state.downloads);
  return `<div class="toolbar"><input id="downloadSearch" class="search grow" placeholder="Search downloads…" value="${esc(state.query)}" oninput="filterDownloads(this.value)"></div>${selectionToolbar()}${table(p.items)}${pagination(state.downloads.length,p.pages)}`;
}
function queueRow(q){return `<div class="row"><div><strong>${esc(q.name)}</strong><small>${q.active} active · ${q.queued} queued · ${q.total} total</small></div><span class="tag">${q.running?"Running":"Stopped"}</span></div>`;}
function renderQueue(){
  const p=paginate(state.queues);
  return `<div class="toolbar"><div class="grow"></div><button class="primary" onclick="createQueue()">＋ New queue</button></div>
  <div class="queue-grid">${p.items.map(q=>{
    const items=(q.items||[]).map(id=>state.allDownloads.find(d=>Number(d.id)===Number(id))).filter(Boolean);
    return `<div class="card queue-card">
      <div class="queue-head">
        <div><h3>${esc(q.name)}</h3><small>${q.active} active · ${q.queued} queued · ${q.total} total · max ${q.maxConcurrent||1} concurrent</small></div>
        <div class="actions">
          ${q.running?'<button class="icon-btn" title="Stop queue" onclick="queueAction('+q.id+',\'stop\')">⏹</button>':'<button class="icon-btn" title="Start queue" onclick="queueAction('+q.id+',\'start\')">▶</button>'}
          <button class="icon-btn" title="Rename" onclick="renameQueue(${q.id})">✎</button>
          <button class="icon-btn" title="Concurrency" onclick="setQueueConcurrency(${q.id},${q.maxConcurrent||1})">≡</button>
          ${q.id!==0?'<button class="icon-btn danger" title="Delete" onclick="deleteQueue('+q.id+')">✕</button>':''}
        </div>
      </div>
      <div class="queue-items">${items.length?items.map((d,i)=>`<div class="queue-item">
        <div class="queue-order">${i+1}</div>
        <div class="queue-name"><strong title="${esc(d.name)}">${esc(d.name)}</strong><small>${d.progress}% · ${esc(d.status)} · ${d.speed}</small></div>
        <div class="actions">
          <button class="icon-btn" title="Move up" ${i===0?'disabled':''} onclick="moveQueueItem(${d.id},-1)">↑</button>
          <button class="icon-btn" title="Move down" ${i===items.length-1?'disabled':''} onclick="moveQueueItem(${d.id},1)">↓</button>
          <button class="icon-btn danger" title="Remove from queue" onclick="assignDownload(${d.id},null)">×</button>
        </div>
      </div>`).join(""):`<div class="empty">No downloads in this queue.</div>`}</div>
    </div>`;
  }).join("")}</div>${pagination(state.queues.length,p.pages)}`;
}
function renderBrowser(){
  const session=state.browserSession;
  if(!session?.enabled){
    return '<div class="card empty"><strong>Browser is starting</strong><span>Chromium display information is not available yet. Try opening the Browser tab again in a moment.</span></div>';
  }
  const host=window.location.hostname;
  const port=Number(session.port)||15153;
  const url=window.location.protocol+"//"+host+":"+port+"/vnc.html?autoconnect=true&resize=remote&path=websockify";
  return '<div class="browser-shell">'+
    '<div class="browser-head"><div><strong>Chromium</strong><span>Real Chromium session · downloads are captured by ABDM</span></div><button class="secondary" onclick="reloadBrowser()">Reload browser</button></div>'+
    '<div class="browser-frame-wrap"><iframe class="browser-frame" src="'+esc(url)+'" title="Chromium browser" allow="clipboard-read; clipboard-write"></iframe></div>'+
    '</div>';
}
window.reloadBrowser=async function(){
  try{
    await ABDM_API.browserSession();
    render();
  }catch(e){alert("Browser is unavailable: "+e.message)}
}
function renderCategories(){
  const p=paginate(state.categories);
  return `<div class="toolbar"><div class="grow"></div><button class="primary" onclick="createCategory()">＋ New category</button></div>
  <div class="card table-wrap"><table class="table"><thead><tr><th>NAME</th><th>PATH</th><th>FILE TYPES</th><th>ITEMS</th><th></th></tr></thead><tbody>${p.items.map(x=>`<tr><td><strong>${esc(x.name)}</strong>${x.defaultCategory?'<span class="tag" style="margin-left:8px">Default</span>':''}</td><td>${x.usePath?esc(x.path):'<span class="muted">Default download path</span>'}</td><td>${esc((x.acceptedFileTypes||[]).join(", ")||"All")}</td><td>${x.items?.length||0}</td><td><span class="actions"><button class="icon-btn" title="Rename" onclick="renameCategory(${x.id})">✎</button>${x.defaultCategory?'':'<button class="icon-btn danger" title="Delete" onclick="deleteCategory('+x.id+')">✕</button>'}</span></td></tr>`).join("")}</tbody></table></div>${pagination(state.categories.length,p.pages)}`;
}
function renderSettings(){
  const s=state.settings||{};
  return `<div class="settings-grid">
    <div class="card">
      <div class="section-head"><div><h3>Downloads</h3><p>Core download behavior and storage.</p></div></div>
      <div class="form-grid settings-form">
        <label>Download folder<input id="set-folder" value="${esc(s.downloadFolder||"/downloads")}"></label>
        <label>Maximum concurrent downloads<input id="set-concurrent" type="number" min="1" max="128" value="${s.maxConcurrentDownloads||4}"></label>
        <label>Threads per download<input id="set-threads" type="number" min="1" max="128" value="${s.threadCount||8}"></label>
        <label>Speed limit (bytes/sec)<input id="set-speed" type="number" min="0" value="${s.speedLimit||0}"></label>
        <label>Maximum retry count<input id="set-retries" type="number" min="0" max="100" value="${s.maxDownloadRetryCount??5}"></label>
      </div>
      <div class="settings-checks">
        <label><input id="set-dynamic" type="checkbox" ${s.dynamicPartCreation?"checked":""}> Dynamic part creation</label>
        <label><input id="set-sparse" type="checkbox" ${s.sparseFileAllocation?"checked":""}> Sparse file allocation</label>
        <label><input id="set-average" type="checkbox" ${s.useAverageSpeed?"checked":""}> Use average download speed</label>
        <label><input id="set-category-default" type="checkbox" ${s.useCategoryByDefault?"checked":""}> Use categories by default</label>
        <label><input id="set-autoboot" type="checkbox" ${s.autoStartOnBoot?"checked":""}> Start ABDM automatically on boot</label>
        <label><input id="set-track" type="checkbox" ${s.trackDeletedFilesOnDisk?"checked":""}> Track deleted files on disk</label>
        <label><input id="set-delete-partial" type="checkbox" ${s.deletePartialFileOnDownloadCancellation?"checked":""}> Delete partial files when cancelled</label>
      </div>
    </div>
    <div class="card">
      <div class="section-head"><div><h3>Web API</h3><p>Network access for the TrueNAS web interface.</p></div></div>
      <div class="form-grid settings-form">
        <label>API port<input id="set-port" type="number" min="1" max="65535" value="${s.apiPort||15151}"></label>
        <label>API key<input id="set-key" type="password" autocomplete="new-password" placeholder="Leave blank to keep current key"></label>
      </div>
      <div class="settings-checks">
        <label><input id="set-api-enabled" type="checkbox" ${s.apiEnabled?"checked":""}> Enable web/API service</label>
        <label><input id="set-api-auth" type="checkbox" ${s.apiAuthEnabled?"checked":""}> Require API key authentication</label>
      </div>
      <div class="settings-note">Changing the API port requires matching the TrueNAS host/container port mapping in <code>docker-compose.truenas.yml</code>. Authentication changes can also temporarily disconnect this page.</div>
    </div>
  </div>
  <div class="dialog-actions"><button class="primary" onclick="saveSettings()">Save settings</button></div>`;
}
window.saveSettings=async function(){
  if(!state.settings)return;
  const s={...state.settings,
    downloadFolder:document.getElementById("set-folder").value.trim(),
    maxConcurrentDownloads:Number(document.getElementById("set-concurrent").value),
    threadCount:Number(document.getElementById("set-threads").value),
    speedLimit:Number(document.getElementById("set-speed").value),
    maxDownloadRetryCount:Number(document.getElementById("set-retries").value),
    dynamicPartCreation:document.getElementById("set-dynamic").checked,
    sparseFileAllocation:document.getElementById("set-sparse").checked,
    useAverageSpeed:document.getElementById("set-average").checked,
    useCategoryByDefault:document.getElementById("set-category-default").checked,
    autoStartOnBoot:document.getElementById("set-autoboot").checked,
    trackDeletedFilesOnDisk:document.getElementById("set-track").checked,
    deletePartialFileOnDownloadCancellation:document.getElementById("set-delete-partial").checked,
    apiEnabled:document.getElementById("set-api-enabled").checked,
    apiPort:Number(document.getElementById("set-port").value),
    apiAuthEnabled:document.getElementById("set-api-auth").checked
  };
  const key=document.getElementById("set-key").value.trim();
  try{
    await ABDM_API.updateSettings(s,key||null);
    if(key)localStorage.setItem("abdmApiKey",key);
    state.settings=s;
    alert("Settings saved.");
    render();
  }catch(e){alert("Settings update failed: "+e.message)}
};
async function loadBrowserSession(quiet=true){
  if(!state.connected)return;
  try{
    state.browserSession=await ABDM_API.browserSession();
    if(!quiet&&state.view==="browser")render();
  }catch(e){console.warn("Unable to load browser session",e);state.browserSession=null}
}

async function loadSettings(quiet=true){
  if(!state.connected)return;
  try{state.settings=await ABDM_API.settings();if(state.view==="settings"&&!quiet)render()}catch(e){console.warn("Unable to load settings",e)}
}

function renderScheduler(){
  const p=paginate(state.queues);
  const days=[["MONDAY","Mon"],["TUESDAY","Tue"],["WEDNESDAY","Wed"],["THURSDAY","Thu"],["FRIDAY","Fri"],["SATURDAY","Sat"],["SUNDAY","Sun"]];
  return `<div class="toolbar"><div><strong>Queue scheduler</strong><span class="muted" style="margin-left:8px">Schedules are applied to each queue</span></div></div>
  <div class="queue-grid">${p.items.map(q=>{
    const activeDays=Array.isArray(q.activeDays)?q.activeDays:[];
    const start=(q.startTime||"02:30").slice(0,5);
    const end=(q.endTime||"07:30").slice(0,5);
    return `<div class="card queue-card">
      <div class="queue-head"><div><h3>${esc(q.name)}</h3><small>${q.schedulerEnabled?"Scheduler enabled":"Scheduler disabled"} · ${q.autoStartEnabled?start+" start": "manual start"} · ${q.autoStopEnabled?end+" stop":"manual stop"}</small></div>
      <span class="tag">${q.schedulerEnabled?"Enabled":"Disabled"}</span></div>
      <div style="padding:16px 18px">
        <div class="form-grid">
          <label>Start time<input type="time" id="sched-start-${q.id}" value="${start}"></label>
          <label>Stop time<input type="time" id="sched-end-${q.id}" value="${end}"></label>
        </div>
        <div style="margin-top:14px">
          <div class="stat-label" style="margin-bottom:8px">Active days</div>
          <div class="actions" style="flex-wrap:wrap">
            ${days.map(([value,label])=>`<label style="display:flex;align-items:center;gap:5px"><input type="checkbox" class="sched-day-${q.id}" value="${value}" ${activeDays.includes(value)?"checked":""}> ${label}</label>`).join("")}
          </div>
        </div>
        <div class="form-grid" style="margin-top:14px">
          <label style="display:flex;align-items:center;gap:9px"><input type="checkbox" id="sched-autostart-${q.id}" ${q.autoStartEnabled?"checked":""}> Automatically start queue</label>
          <label style="display:flex;align-items:center;gap:9px"><input type="checkbox" id="sched-autostop-${q.id}" ${q.autoStopEnabled?"checked":""}> Automatically stop queue</label>
          <label style="display:flex;align-items:center;gap:9px"><input type="checkbox" id="sched-empty-${q.id}" ${q.stopQueueOnEmpty?"checked":""}> Stop queue when empty</label>
        </div>
        <div class="dialog-actions" style="margin-top:16px"><button class="primary" onclick="saveQueueSchedule(${q.id})">Save schedule</button></div>
      </div>
    </div>`
  }).join("")}</div>${pagination(state.queues.length,p.pages)}`;
}
window.saveQueueSchedule=async function(id){
  const activeDays=Array.from(document.querySelectorAll(".sched-day-"+id+":checked")).map(x=>x.value);
  if(!activeDays.length){alert("Select at least one active day.");return}
  const autoStartEnabled=document.getElementById("sched-autostart-"+id).checked;
  const autoStopEnabled=document.getElementById("sched-autostop-"+id).checked;
  const stopQueueOnEmpty=document.getElementById("sched-empty-"+id).checked;
  const startTime=document.getElementById("sched-start-"+id).value||"02:30";
  const endTime=document.getElementById("sched-end-"+id).value||"07:30";
  const enabled=autoStartEnabled||autoStopEnabled;
  try{await ABDM_API.queueSchedule(id,{enabled,activeDays,autoStartEnabled,startTime,autoStopEnabled,endTime,stopQueueOnEmpty});await loadQueues(false)}catch(e){alert("Scheduler update failed: "+e.message)}
};

function renderSimple(name,text){return `<div class="card empty"><strong>${name}</strong>${text}</div>`}
window.createCategory=async function(){const name=prompt("Category name","New Category");if(!name)return;const usePath=confirm("Use a dedicated download path for this category?");const path=usePath?prompt("Download path","/downloads/"+name.replace(/\s+/g,"_")):"";if(usePath&&path===null)return;const fileTypes=(prompt("Accepted file types (comma separated, e.g. zip,7z,iso)","")||"").split(",").map(x=>x.trim()).filter(Boolean);const urlPatterns=(prompt("Accepted URL patterns (comma separated)","")||"").split(",").map(x=>x.trim()).filter(Boolean);try{await ABDM_API.createCategory({name,path,usePath,fileTypes,urlPatterns});await loadCategories(false)}catch(e){alert("Category creation failed: "+e.message)}};
window.renameCategory=async function(id){const x=state.categories.find(c=>Number(c.id)===Number(id));if(!x)return;const n=prompt("Category name",x.name);if(!n||n===x.name)return;try{await ABDM_API.renameCategory(id,n);await loadCategories(false)}catch(e){alert("Rename failed: "+e.message)}};
window.deleteCategory=async function(id){const x=state.categories.find(c=>Number(c.id)===Number(id));if(!x)return;if(!confirm("Delete category '"+x.name+"'?"))return;try{await ABDM_API.deleteCategory(id);await loadCategories(false)}catch(e){alert("Delete failed: "+e.message)}};
async function loadCategories(quiet=true){if(!state.connected)return;try{const items=await ABDM_API.categories();state.categories=Array.isArray(items)?items:[];state.page=Math.min(state.page,Math.max(1,Math.ceil(state.categories.length/state.pageSize)));refreshCategorySelect();if(state.view==="categories"&&!quiet)render()}catch(e){console.warn("Unable to load categories",e)}}

window.browseTo=async function(path){try{const data=await ABDM_API.browser(path);state.browserPath=data.path||"";state.browserItems=Array.isArray(data.items)?data.items:[];state.page=1;render()}catch(e){alert("Browser error: "+e.message)}};
window.browseParent=async function(){const p=state.browserPath.split("/").filter(Boolean);p.pop();await browseTo(p.join("/"))};
function render(){const pageScroll=window.scrollY;const scrollState={};document.querySelectorAll("[data-scroll-key]").forEach(el=>scrollState[el.dataset.scrollKey]={top:el.scrollTop,left:el.scrollLeft});document.getElementById("page-title").textContent=titles[state.view][0];document.getElementById("page-subtitle").textContent=titles[state.view][1];document.querySelectorAll(".nav-item").forEach(x=>x.classList.toggle("active",x.dataset.page===state.view));let html=state.view==="dashboard"?renderDashboard():state.view==="downloads"?renderDownloads():state.view==="queue"?renderQueue():state.view==="browser"?renderBrowser():state.view==="categories"?renderCategories():state.view==="scheduler"?renderScheduler():state.view==="settings"?renderSettings():renderSimple("Page","Coming soon.");document.getElementById("app").innerHTML=html;Object.entries(scrollState).forEach(([key,pos])=>{const el=document.querySelector(`[data-scroll-key="${key}"]`);if(el){el.scrollTop=pos.top;el.scrollLeft=pos.left}});requestAnimationFrame(()=>window.scrollTo(0,pageScroll))}
window.setPage=n=>{state.page=Number(n);render()};window.setSize=n=>{state.pageSize=Number(n);state.page=1;render()};function renderDownloadParts(parts){
  if(!Array.isArray(parts)||!parts.length){
    return '<div class="parts-empty">No connection parts are currently available.</div>';
  }
  return '<div class="parts-table-wrap"><table class="parts-table"><thead><tr><th>CONNECTION</th><th>PROGRESS</th><th>DOWNLOADED</th><th>SIZE</th><th>SPEED</th><th>STATUS</th><th>ERROR</th></tr></thead><tbody>'+
    parts.map((p,i)=>'<tr><td>Part '+(i+1)+'</td><td><div class="part-progress"><i style="width:'+(p.percent==null?0:p.percent)+'%"></i></div><span>'+esc(p.percent==null?"—":p.percent+"%")+'</span></td><td>'+formatBytes(p.downloaded)+'</td><td>'+formatBytes(p.size)+'</td><td>'+formatSpeed(p.speed)+'</td><td><span class="tag">'+esc(p.status||"Unknown")+'</span></td><td class="part-error">'+esc(p.error||"—")+'</td></tr>').join("")+
    '</tbody></table></div>';
}
async function loadDownloadParts(id){
  try{
    const parts=await ABDM_API.downloadParts(id);
    const target=document.getElementById("download-parts");
    if(target)target.innerHTML=renderDownloadParts(parts);
  }catch(e){
    const target=document.getElementById("download-parts");
    if(target)target.innerHTML='<div class="parts-empty">Unable to load connection details: '+esc(e.message)+'</div>';
  }
}
window.showDownload=function(id){
  const d=state.allDownloads.find(x=>Number(x.id)===Number(id));
  if(!d)return;
  const r=d.raw||d;
  if(state.detailsPollTimer){clearInterval(state.detailsPollTimer);state.detailsPollTimer=null}
  document.getElementById("detailsTitle").textContent=d.name;
  document.getElementById("detailsSubtitle").textContent=d.status+" · "+(d.queueName||"No queue");
  document.getElementById("detailsBody").innerHTML=
    '<div class="details-grid">'+
      '<div><span>Progress</span><strong>'+d.progress+'%</strong></div>'+
      '<div><span>Speed</span><strong>'+esc(d.speed)+'</strong></div>'+
      '<div><span>ETA</span><strong>'+esc(d.eta)+'</strong></div>'+
      '<div><span>Size</span><strong>'+esc(d.size)+'</strong></div>'+
      '<div><span>Connections</span><strong>'+((r.connections??0))+' active / '+(r.maxConnections??"global")+'</strong></div>'+
      '<div><span>Category</span><strong>'+esc(r.categoryName||"Uncategorized")+'</strong></div>'+
      '<div><span>Folder</span><strong>'+esc(r.folder||"—")+'</strong></div>'+
      '<div><span>Queue</span><strong>'+esc(d.queueName||"No queue")+'</strong></div>'+
      '<div><span>Added</span><strong>'+(r.dateAdded?new Date(r.dateAdded).toLocaleString():"—")+'</strong></div>'+
      '<div><span>Completed</span><strong>'+(r.completeTime?new Date(r.completeTime).toLocaleString():"—")+'</strong></div>'+
      '<div class="detail-wide"><span>Direct link</span><input id="detail-link" value="'+esc(r.downloadLink||"")+'" autocomplete="off"></div>'+
      '<div class="detail-wide"><span>Connections for this download</span><input id="detail-connections" type="number" min="1" max="128" value="'+(r.maxConnections||8)+'"></div>'+
      '<div class="detail-wide"><span>Last error</span><strong class="error-detail">'+esc(r.errorDescription||r.errorMessage||"No recorded error")+'</strong></div>'+
    '</div>'+
    '<div class="parts-section"><div class="parts-head"><div><strong>Parts Info</strong><span>Live status and speed for each active connection</span></div></div><div id="download-parts"><div class="parts-empty">Loading connection details…</div></div></div>';
  document.getElementById("saveDetailsBtn").onclick=()=>saveDownloadDetails(id);
  document.getElementById("detailsDialog").showModal();
  loadDownloadParts(id);
  state.detailsPollTimer=setInterval(()=>loadDownloadParts(id),1000);
};
document.getElementById("detailsDialog").addEventListener("close",()=>{
  if(state.detailsPollTimer){clearInterval(state.detailsPollTimer);state.detailsPollTimer=null}
});
window.saveDownloadDetails=async function(id){const link=document.getElementById("detail-link")?.value.trim();const connections=Number(document.getElementById("detail-connections")?.value);if(!link){alert("Download link cannot be empty.");return}if(!Number.isInteger(connections)||connections<1||connections>128){alert("Connections must be between 1 and 128.");return}try{await ABDM_API.updateDownload(id,{link,preferredConnectionCount:connections});document.getElementById("detailsDialog").close();await loadDownloads(false)}catch(e){alert("Download update failed: "+e.message)}};
window.downloadAction=async function(id,action,removeFile=false){
  if(action==="remove"&&!confirm(removeFile?"Remove the download and delete its file?":"Remove this download from ABDM?"))return;
  try{await ABDM_API.control(id,action,removeFile);await loadDownloads(false)}catch(e){alert("Action failed: "+e.message)}
};
window.toggleSelected=function(id,checked){id=Number(id);if(checked)state.selectedIds.add(id);else state.selectedIds.delete(id);render()};
window.togglePageSelection=function(checked){const source=state.view==="downloads"?state.downloads:state.allDownloads;source.slice((state.page-1)*state.pageSize,state.page*state.pageSize).forEach(x=>checked?state.selectedIds.add(Number(x.id)):state.selectedIds.delete(Number(x.id)));render()};
window.clearSelection=function(){state.selectedIds.clear();render()};
window.selectedAction=async function(action,removeFile=false){const ids=Array.from(state.selectedIds);if(!ids.length)return;if(action==="remove"&&!confirm(removeFile?"Delete the selected files and remove the downloads?":"Remove the selected downloads?"))return;try{await Promise.all(ids.map(id=>ABDM_API.control(id,action,removeFile)));state.selectedIds.clear();await loadDownloads(false)}catch(e){alert("Selected action failed: "+e.message)}};
window.filterDownloads=q=>{state.query=(q||"").toLowerCase();state.downloads=state.query?state.allDownloads.filter(x=>x.name.toLowerCase().includes(state.query)):state.allDownloads.slice();state.page=1;render();requestAnimationFrame(()=>{const el=document.getElementById("downloadSearch");if(el){el.focus();const n=el.value.length;el.setSelectionRange(n,n)}})};
window.bulkAction=async function(action){
  const ids=state.allDownloads.filter(x=>action==="pause"?["Downloading","Preparing","Retrying"].includes(x.status):["Paused","Queued"].includes(x.status)).map(x=>x.id);
  try{await Promise.all(ids.map(id=>ABDM_API.control(id,action)));await loadDownloads(false)}catch(e){alert("Bulk action failed: "+e.message)}
};
window.createQueue=async function(){const name=prompt("Queue name","New Queue");if(!name)return;try{await ABDM_API.createQueue(name);await loadQueues(false)}catch(e){alert(e.message)}};
window.renameQueue=async function(id){const q=state.queues.find(x=>Number(x.id)===Number(id));if(!q)return;const n=prompt("Queue name",q.name);if(!n||n===q.name)return;try{await ABDM_API.renameQueue(id,n);await loadQueues(false)}catch(e){alert(e.message)}};
window.deleteQueue=async function(id){const q=state.queues.find(x=>Number(x.id)===Number(id));if(!q||Number(id)===0)return;if(!confirm("Delete queue '"+q.name+"'? Downloads remain."))return;try{await ABDM_API.deleteQueue(id);await loadQueues(false)}catch(e){alert(e.message)}};
window.setQueueConcurrency=async function(id,current){const n=Number(prompt("Maximum simultaneous downloads",current));if(!Number.isInteger(n)||n<1)return;try{await ABDM_API.queueConcurrency(id,n);await loadQueues(false)}catch(e){alert(e.message)}};
window.moveDownloadToQueue=async function(id){const list=state.queues.map(q=>q.id+" = "+q.name).join("\n");const value=prompt("Enter queue ID:\n"+list,"0");if(value===null)return;const q=Number(value);if(!Number.isInteger(q)||!state.queues.some(x=>Number(x.id)===q)){alert("Invalid queue ID.");return}try{await ABDM_API.assignQueue(id,q);await loadDownloads(false);await loadQueues(false)}catch(e){alert("Queue assignment failed: "+e.message)}};
window.assignDownload=async function(id,queueId){try{if(queueId===null)await ABDM_API.unqueue(id);else await ABDM_API.assignQueue(id,queueId);await loadDownloads(false);await loadQueues(false)}catch(e){alert("Queue assignment failed: "+e.message)}};
window.moveQueueItem=async function(id,direction){try{await ABDM_API.moveQueueItem(id,direction);await loadQueues(false);await loadDownloads(true)}catch(e){alert("Queue ordering failed: "+e.message)}};
window.queueAction=async function(id,action){
  try{await ABDM_API.queueControl(id,action);await loadQueues(false)}catch(e){alert("Queue action failed: "+e.message)}
};
function refreshQueueSelect(){const s=document.getElementById("queueInput");if(!s)return;const current=s.value;s.innerHTML=`<option value="">No queue</option>`+state.queues.map(q=>`<option value="${q.id}">${esc(q.name)}</option>`).join("");if(Array.from(s.options).some(o=>o.value===current))s.value=current;}
function refreshCategorySelect(){const s=document.getElementById("categoryInput");if(!s)return;const current=s.value;s.innerHTML=`<option value="">Automatic</option>`+state.categories.map(x=>`<option value="${x.id}">${esc(x.name)}</option>`).join("");if(Array.from(s.options).some(o=>o.value===current))s.value=current;}
async function loadQueues(quiet=true){
  if(!state.connected)return;
  try{
    const items=await ABDM_API.queues();
    state.queues=Array.isArray(items)?items:[];refreshQueueSelect();
    if(state.page>Math.max(1,Math.ceil(state.queues.length/state.pageSize)))state.page=1;
    if(!quiet||state.view==="dashboard"||state.view==="queue"||state.view==="scheduler")render();
  }catch(err){console.warn("Unable to load queues",err);state.connected=false;document.getElementById("connection").textContent="Backend unavailable"}
}
async function loadDownloads(quiet=true){
  if(!state.connected)return;
  try{
    const items=await ABDM_API.downloads();
    state.allDownloads=(Array.isArray(items)?items:[]).map(x=>({id:x.id,name:x.name,size:formatBytes(x.size),progress:x.percent==null?0:x.percent,speed:formatSpeed(x.speed),eta:formatEta(x.eta),status:x.status,queueId:x.queueId,queueName:x.queueName,raw:x}));const liveIds=new Set(state.allDownloads.map(x=>Number(x.id)));state.selectedIds.forEach(id=>{if(!liveIds.has(Number(id)))state.selectedIds.delete(id)});
    state.downloads=state.query?state.allDownloads.filter(x=>x.name.toLowerCase().includes(state.query)):state.allDownloads.slice();
    const editingSearch=state.view==="downloads"&&document.activeElement?.classList.contains("search");
    if(!quiet||state.view==="dashboard"||state.view==="queue"||(state.view==="downloads"&&!editingSearch))render();
  }catch(err){console.warn("Unable to load downloads",err);state.connected=false;document.getElementById("connection").textContent="Backend unavailable"}
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
document.getElementById("nav").addEventListener("click",e=>{const b=e.target.closest(".nav-item");if(b){state.view=b.dataset.page;state.page=1;render();if(state.view==="browser")loadBrowserSession(false)}});

document.getElementById("addBtn").onclick=async()=>{await loadQueues(true);await loadCategories(true);refreshQueueSelect();refreshCategorySelect();if(state.settings?.downloadFolder)document.getElementById("pathInput").value=state.settings.downloadFolder;document.getElementById("addDialog").showModal()};
document.getElementById("addForm").addEventListener("submit",async e=>{if(e.submitter?.value==="cancel")return;e.preventDefault();const urls=document.getElementById("urlInput").value.split(/\r?\n/).map(x=>x.trim()).filter(Boolean);if(!urls.length)return;try{await ABDM_API.add({urls,folder:document.getElementById("pathInput").value,queueId:(document.getElementById("queueInput").value===""?null:Number(document.getElementById("queueInput").value)),categoryId:(document.getElementById("categoryInput").value===""?null:Number(document.getElementById("categoryInput").value))});await loadDownloads(false);document.getElementById("addDialog").close();}catch(err){alert("Backend API error: "+err.message)}});
document.getElementById("importBtn").onclick=()=>document.getElementById("importDialog").showModal();
document.getElementById("importFile").addEventListener("change",async e=>{const file=e.target.files?.[0];if(!file)return;try{state.importItems=await ABDM_API.inspectLinks(await file.text());document.getElementById("importPreview").innerHTML=state.importItems.length?state.importItems.map((x,i)=>`<label class="import-row"><input type="checkbox" class="import-check" data-index="${i}" checked><span><strong>${esc(x.name||"Unknown")}</strong><small>${esc(x.sizeText||"Size unknown")} · ${esc(x.status||"")}${x.error?" · "+esc(x.error):""}</small></span></label>`).join(""):"<div class='empty'>No valid links found.</div>"}catch(err){document.getElementById("importPreview").innerHTML=`<div class="empty">Import failed: ${esc(err.message)}</div>`}});
document.getElementById("importForm").addEventListener("submit",async e=>{if(e.submitter?.value==="cancel")return;e.preventDefault();const selected=Array.from(document.querySelectorAll(".import-check:checked")).map(x=>state.importItems[Number(x.dataset.index)]).filter(Boolean);if(!selected.length)return;try{await ABDM_API.add({urls:selected.map(x=>x.url),names:selected.map(x=>x.name),folder:state.settings?.downloadFolder||"/downloads"});state.importItems=[];document.getElementById("importDialog").close();await loadDownloads(false)}catch(err){alert("Import failed: "+err.message)}});
async function connect(){try{await ABDM_API.ping();state.connected=true;await loadDownloads(true);await loadQueues(true);await loadCategories(true);await loadSettings(true);await loadBrowserSession(true);document.querySelector(".status-dot").classList.add("ok");document.getElementById("connection").textContent="Backend connected"}catch(e){document.getElementById("connection").textContent="Backend unavailable"}render()}
render();connect();
setInterval(()=>{
  if(!state.connected){
    const now=Date.now();
    if(now-state.lastReconnectAttempt>=5000){
      state.lastReconnectAttempt=now;
      connect();
    }
    return;
  }
  loadDownloads(true);
  loadQueues(true);
  if(state.view==="categories")loadCategories(true);
  if(state.view==="settings")loadSettings(true)
},1500);
