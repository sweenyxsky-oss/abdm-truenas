const state={view:"dashboard",page:1,pageSize:10,downloads:[],allDownloads:[],queues:[],connected:false,query:"",browserPath:"",browserItems:[],categories:[],settings:null};
state.downloads=[];

const titles={dashboard:["Dashboard","Download manager overview"],downloads:["Downloads","All download tasks"],queue:["Queue","Manage download queues"],browser:["Browser","Repository and file browser"],categories:["Categories","Organize downloads"],scheduler:["Scheduler","Scheduled download rules"],settings:["Settings","ABDM service configuration"]};
function esc(s){return String(s).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]))}
function paginate(items){const start=(state.page-1)*state.pageSize;return {items:items.slice(start,start+state.pageSize),pages:Math.max(1,Math.ceil(items.length/state.pageSize)),start}}
function pagination(total,pages){return `<div class="pagination"><span>Showing ${total?((state.page-1)*state.pageSize+1):0}–${Math.min(state.page*state.pageSize,total)} of ${total}</span><div class="pages">${Array.from({length:pages},(_,i)=>`<button class="${i+1===state.page?"active":""}" onclick="setPage(${i+1})">${i+1}</button>`).join("")}</div><label style="display:flex;align-items:center;gap:7px">Per page<select class="page-size" onchange="setSize(this.value)"><option value="10" ${state.pageSize===10?"selected":""}>10</option><option value="25" ${state.pageSize===25?"selected":""}>25</option><option value="50" ${state.pageSize===50?"selected":""}>50</option></select></label></div>`}
function actionButtons(x){
  const id=Number(x.id);
  const pauseResume=x.status==="Downloading"||x.status==="Preparing"||x.status==="Retrying"
    ?`<button class="icon-btn" title="Pause" onclick="downloadAction(${id},'pause')">⏸</button>`
    :x.status==="Paused"||x.status==="Queued"
      ?`<button class="icon-btn" title="Resume" onclick="downloadAction(${id},'resume')">▶</button>`
      :"";
  const retry=x.status==="Completed"?"" : `<button class="icon-btn" title="Retry" onclick="downloadAction(${id},'retry')">↻</button>`;
  return `<span class="actions">${pauseResume}${retry}<button class="icon-btn danger" title="Remove download" onclick="downloadAction(${id},'remove',false)">✕</button></span>`;
}
function table(items){return `<div class="card table-wrap"><table class="table"><thead><tr><th>NAME</th><th>SIZE</th><th>PROGRESS</th><th>SPEED</th><th>ETA</th><th>QUEUE</th><th>STATUS</th><th></th></tr></thead><tbody>${items.map(x=>`<tr><td class="name clickable" title="${esc(x.name)}" onclick="showDownload(${x.id})">${esc(x.name)}</td><td>${x.size}</td><td><div style="display:flex;align-items:center;gap:9px"><div class="progress"><i style="width:${x.progress}%"></i></div><span>${x.progress}%</span></div></td><td>${x.speed}</td><td>${x.eta}</td><td>${esc(x.queueName||"No queue")}</td><td><span class="tag">${esc(x.status)}</span></td><td>${actionButtons(x)}</td></tr>`).join("")}</tbody></table></div>`}
function renderDashboard(){
  const dp=paginate(state.downloads);
  const active=state.allDownloads.filter(x=>["Downloading","Preparing","Retrying"].includes(x.status)).length;
  const queued=state.allDownloads.filter(x=>x.status==="Queued").length;
  const done=state.allDownloads.filter(x=>x.status==="Completed").length;
  const speed=state.allDownloads.reduce((n,x)=>n+Number(x.raw?.speed||0),0);
  return `<div class="grid stats"><div class="card"><div class="stat-label">Active downloads</div><div class="stat-value">${active}</div><div class="stat-extra">Currently transferring</div></div><div class="card"><div class="stat-label">Queued</div><div class="stat-value">${queued}</div><div class="stat-extra">Waiting to start</div></div><div class="card"><div class="stat-label">Completed</div><div class="stat-value">${done}</div><div class="stat-extra">Finished downloads</div></div><div class="card"><div class="stat-label">Current speed</div><div class="stat-value">${formatSpeed(speed)}</div><div class="stat-extra">Combined active speed</div></div></div><div class="grid two-col"><div><div class="section-head"><h2>Latest downloads</h2><span>${state.connected?"Live":"Offline"}</span></div>${table(dp.items)}${pagination(state.downloads.length,dp.pages)}</div><div><div class="section-head"><h2>Queue</h2><span>Live overview</span></div><div class="card list">${state.queues.length?state.queues.slice(0,6).map(queueRow).join(""):`<div class="empty">No queues available.</div>`}</div></div></div>`;
}
function renderDownloads(){
  const p=paginate(state.downloads);
  return `<div class="toolbar"><input class="search grow" placeholder="Search downloads…" value="${esc(state.query)}" oninput="filterDownloads(this.value)"><button class="secondary" onclick="bulkAction('pause')">Pause all</button><button class="secondary" onclick="bulkAction('resume')">Resume all</button></div>${table(p.items)}${pagination(state.downloads.length,p.pages)}`;
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
          <button class="icon-btn" title="Rename" onclick="renameQueue(${q.id},${JSON.stringify(q.name)})">✎</button>
          <button class="icon-btn" title="Concurrency" onclick="setQueueConcurrency(${q.id},${q.maxConcurrent||1})">≡</button>
          ${q.id!==0?'<button class="icon-btn danger" title="Delete" onclick="deleteQueue('+q.id+','+JSON.stringify(q.name)+')">✕</button>':''}
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
  const p=paginate(state.browserItems);
  const crumbs=state.browserPath?state.browserPath.split("/").filter(Boolean):[];
  return `<div class="toolbar"><button class="secondary" onclick="browseTo('')">⌂ Root</button><button class="secondary" ${!state.browserPath?'disabled':''} onclick="browseParent()">↑ Up</button><div class="browser-path grow">/ ${crumbs.map(esc).join(" / ")}</div></div>
  <div class="card table-wrap"><table class="table"><thead><tr><th>NAME</th><th>TYPE</th><th>SIZE</th><th>MODIFIED</th></tr></thead><tbody>${p.items.map(x=>`<tr ${x.directory?'class="clickable" onclick="browseTo('+JSON.stringify(x.path)+')"':''}><td class="name">${x.directory?'📁':'📄'} ${esc(x.name)}</td><td>${x.directory?'Folder':'File'}</td><td>${x.directory?'—':formatBytes(x.size)}</td><td>${x.modified?new Date(x.modified).toLocaleString():'—'}</td></tr>`).join("") || '<tr><td colspan="4" class="empty">This folder is empty.</td></tr>'}</tbody></table></div>${pagination(state.browserItems.length,p.pages)}`;
}
function renderCategories(){
  const p=paginate(state.categories);
  return `<div class="toolbar"><div class="grow"></div><button class="primary" onclick="createCategory()">＋ New category</button></div>
  <div class="card table-wrap"><table class="table"><thead><tr><th>NAME</th><th>PATH</th><th>FILE TYPES</th><th>ITEMS</th><th></th></tr></thead><tbody>${p.items.map(x=>`<tr><td><strong>${esc(x.name)}</strong>${x.defaultCategory?'<span class="tag" style="margin-left:8px">Default</span>':''}</td><td>${x.usePath?esc(x.path):'<span class="muted">Default download path</span>'}</td><td>${esc((x.acceptedFileTypes||[]).join(", ")||"All")}</td><td>${x.items?.length||0}</td><td><span class="actions"><button class="icon-btn" title="Rename" onclick="renameCategory(${x.id},${JSON.stringify(x.name)})">✎</button>${x.defaultCategory?'':'<button class="icon-btn danger" title="Delete" onclick="deleteCategory('+x.id+','+JSON.stringify(x.name)+')">✕</button>'}</span></td></tr>`).join("")}</tbody></table></div>${pagination(state.categories.length,p.pages)}`;
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
        <label><input id="set-api-enabled" type="checkbox" ${s.apiEnabled?"checked":""}> Enable API</label>
        <label><input id="set-api-auth" type="checkbox" ${s.apiAuthEnabled?"checked":""}> Require API key authentication</label>
      </div>
      <div class="settings-note">Changing the API port or authentication can temporarily disconnect this page.</div>
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
  if(key)localStorage.setItem("abdmApiKey",key);
  try{await ABDM_API.updateSettings(s,key||null);state.settings=s;alert("Settings saved.");render()}catch(e){alert("Settings update failed: "+e.message)}
};
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
          <label style="display:flex;align-items:center;gap:9px"><input type="checkbox" id="sched-enabled-${q.id}" ${q.schedulerEnabled?"checked":""}> Enable scheduler</label>
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
  const enabled=document.getElementById("sched-enabled-"+id).checked;
  const autoStartEnabled=document.getElementById("sched-autostart-"+id).checked;
  const autoStopEnabled=document.getElementById("sched-autostop-"+id).checked;
  const stopQueueOnEmpty=document.getElementById("sched-empty-"+id).checked;
  const startTime=document.getElementById("sched-start-"+id).value||"02:30";
  const endTime=document.getElementById("sched-end-"+id).value||"07:30";
  try{await ABDM_API.queueSchedule(id,{enabled,activeDays,autoStartEnabled,startTime,autoStopEnabled,endTime,stopQueueOnEmpty});await loadQueues(false)}catch(e){alert("Scheduler update failed: "+e.message)}
};

function renderSimple(name,text){return `<div class="card empty"><strong>${name}</strong>${text}</div>`}
window.createCategory=async function(){const name=prompt("Category name","New Category");if(!name)return;const path=prompt("Download path","/downloads/"+name.replace(/\\s+/g,"_"));if(path===null)return;try{await ABDM_API.createCategory({name,path,usePath:true,fileTypes:[],urlPatterns:[]});await loadCategories(false)}catch(e){alert("Category creation failed: "+e.message)}};
window.renameCategory=async function(id,name){const n=prompt("Category name",name);if(!n||n===name)return;try{await ABDM_API.renameCategory(id,n);await loadCategories(false)}catch(e){alert("Rename failed: "+e.message)}};
window.deleteCategory=async function(id,name){if(!confirm("Delete category '"+name+"'?"))return;try{await ABDM_API.deleteCategory(id);await loadCategories(false)}catch(e){alert("Delete failed: "+e.message)}};
async function loadCategories(quiet=true){if(!state.connected)return;try{const items=await ABDM_API.categories();state.categories=Array.isArray(items)?items:[];refreshCategorySelect();if(state.view==="categories"&&!quiet)render()}catch(e){console.warn("Unable to load categories",e)}}

window.browseTo=async function(path){try{const data=await ABDM_API.browser(path);state.browserPath=data.path||"";state.browserItems=Array.isArray(data.items)?data.items:[];state.page=1;render()}catch(e){alert("Browser error: "+e.message)}};
window.browseParent=async function(){const p=state.browserPath.split("/").filter(Boolean);p.pop();await browseTo(p.join("/"))};
function render(){document.getElementById("page-title").textContent=titles[state.page][0];document.getElementById("page-subtitle").textContent=titles[state.page][1];document.querySelectorAll(".nav-item").forEach(x=>x.classList.toggle("active",x.dataset.page===state.page));let html=state.view==="dashboard"?renderDashboard():state.view==="downloads"?renderDownloads():state.view==="queue"?renderQueue():state.view==="browser"?renderBrowser():state.view==="categories"?renderCategories():state.view==="scheduler"?renderScheduler():state.view==="settings"?renderSettings():renderSimple("Page","Coming soon.");document.getElementById("app").innerHTML=html}
window.setPage=n=>{state.page=Number(n);render()};window.setSize=n=>{state.pageSize=Number(n);state.page=1;render()};window.showDownload=function(id){
  const d=state.allDownloads.find(x=>Number(x.id)===Number(id));
  if(!d)return;
  const r=d.raw||d;
  document.getElementById("detailsTitle").textContent=d.name;
  document.getElementById("detailsSubtitle").textContent=d.status+" · "+(d.queueName||"No queue");
  document.getElementById("detailsBody").innerHTML=`<div class="details-grid">
    <div><span>Progress</span><strong>${d.progress}%</strong></div>
    <div><span>Speed</span><strong>${esc(d.speed)}</strong></div>
    <div><span>ETA</span><strong>${esc(d.eta)}</strong></div>
    <div><span>Size</span><strong>${esc(d.size)}</strong></div>
    <div><span>Folder</span><strong>${esc(r.folder||"—")}</strong></div>
    <div><span>Queue</span><strong>${esc(d.queueName||"No queue")}</strong></div>
    <div><span>Added</span><strong>${r.dateAdded?new Date(r.dateAdded).toLocaleString():"—"}</strong></div>
    <div><span>Completed</span><strong>${r.completeTime?new Date(r.completeTime).toLocaleString():"—"}</strong></div>
  </div>`;
  document.getElementById("detailsDialog").showModal();
};
window.downloadAction=async function(id,action,removeFile=false){
  if(action==="remove"&&!confirm(removeFile?"Remove the download and delete its file?":"Remove this download from ABDM?"))return;
  try{await ABDM_API.control(id,action,removeFile);await loadDownloads(false)}catch(e){alert("Action failed: "+e.message)}
};
window.filterDownloads=q=>{state.query=(q||"").toLowerCase();state.downloads=state.query?state.allDownloads.filter(x=>x.name.toLowerCase().includes(state.query)):state.allDownloads.slice();state.page=1;render()};
window.bulkAction=async function(action){
  const ids=state.allDownloads.filter(x=>action==="pause"?["Downloading","Preparing","Retrying"].includes(x.status):["Paused","Queued"].includes(x.status)).map(x=>x.id);
  try{await Promise.all(ids.map(id=>ABDM_API.control(id,action)));await loadDownloads(false)}catch(e){alert("Bulk action failed: "+e.message)}
};
window.createQueue=async function(){const name=prompt("Queue name","New Queue");if(!name)return;try{await ABDM_API.createQueue(name);await loadQueues(false)}catch(e){alert(e.message)}};
window.renameQueue=async function(id,name){const n=prompt("Queue name",name);if(!n||n===name)return;try{await ABDM_API.renameQueue(id,n);await loadQueues(false)}catch(e){alert(e.message)}};
window.deleteQueue=async function(id,name){if(id===0)return;if(!confirm("Delete queue '"+name+"'? Downloads remain."))return;try{await ABDM_API.deleteQueue(id);await loadQueues(false)}catch(e){alert(e.message)}};
window.setQueueConcurrency=async function(id,current){const n=Number(prompt("Maximum simultaneous downloads",current));if(!Number.isInteger(n)||n<1)return;try{await ABDM_API.queueConcurrency(id,n);await loadQueues(false)}catch(e){alert(e.message)}};
window.moveDownloadToQueue=async function(id){const list=state.queues.map(q=>q.id+" = "+q.name).join("\n");const value=prompt("Enter queue ID:\n"+list,"0");if(value===null)return;const q=Number(value);if(!Number.isInteger(q))return;try{await ABDM_API.assignQueue(id,q);await loadDownloads(false)}catch(e){alert("Queue assignment failed: "+e.message)}};
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
  }catch(err){console.warn("Unable to load queues",err)}
}
async function loadDownloads(quiet=true){
  if(!state.connected)return;
  try{
    const items=await ABDM_API.downloads();
    state.allDownloads=(Array.isArray(items)?items:[]).map(x=>({id:x.id,name:x.name,size:formatBytes(x.size),progress:x.percent==null?0:x.percent,speed:formatSpeed(x.speed),eta:formatEta(x.eta),status:x.status,queueId:x.queueId,queueName:x.queueName,raw:x}));
    state.downloads=state.query?state.allDownloads.filter(x=>x.name.toLowerCase().includes(state.query)):state.allDownloads.slice();
    const editingSearch=state.view==="downloads"&&document.activeElement?.classList.contains("search");
    if(!quiet||state.view==="dashboard"||state.view==="queue"||(state.view==="downloads"&&!editingSearch))render();
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
document.getElementById("nav").addEventListener("click",e=>{const b=e.target.closest(".nav-item");if(b){state.page=b.dataset.page;state.page=1;render();if(state.view==="browser")browseTo(state.browserPath)}}});

document.getElementById("addBtn").onclick=async()=>{await loadQueues(true);await loadCategories(true);refreshQueueSelect();refreshCategorySelect();if(state.settings?.downloadFolder)document.getElementById("pathInput").value=state.settings.downloadFolder;document.getElementById("addDialog").showModal()};
document.getElementById("addForm").addEventListener("submit",async e=>{e.preventDefault();const urls=document.getElementById("urlInput").value.split(/\r?\n/).map(x=>x.trim()).filter(Boolean);if(!urls.length)return;try{await ABDM_API.add({urls,folder:document.getElementById("pathInput").value,queueId:(document.getElementById("queueInput").value===""?null:Number(document.getElementById("queueInput").value)),categoryId:(document.getElementById("categoryInput").value===""?null:Number(document.getElementById("categoryInput").value))});await loadDownloads(false);document.getElementById("addDialog").close();}catch(err){alert("Backend API error: "+err.message)}});
async function connect(){try{await ABDM_API.ping();state.connected=true;await loadDownloads(true);await loadQueues(true);await loadCategories(true);await loadSettings(true);document.querySelector(".status-dot").classList.add("ok");document.getElementById("connection").textContent="Backend connected"}catch(e){document.getElementById("connection").textContent="Demo mode"}render()}
render();connect();
setInterval(()=>{loadDownloads(true);loadQueues(true);if(state.view==="categories")loadCategories(true);if(state.view==="settings")loadSettings(true)},1500);
