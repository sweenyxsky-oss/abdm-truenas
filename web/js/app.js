const state={
  view:"dashboard",
  statusFilter:"all",
  categoryFilter:null,
  queueFilter:null,
  expandedStatus:"all",
  expandedQueues:new Set([0]),
  sortKey:"dateAdded",
  sortDirection:-1,
  page:1,
  downloadPage:1,
  queuePage:1,
  categoryPage:1,
  schedulerPage:1,
  pageSize:10,
  downloads:[],
  allDownloads:[],
  queues:[],
  connected:false,
  query:"",
  browserPath:"",
  browserItems:[],
  categories:[],
  settings:null,
  lastReconnectAttempt:0,
  selectedIds:new Set(),
  importItems:[],
  detailsPollTimer:null,
  browserSession:null
};
state.downloads=[];

const titles={
  dashboard:["Downloads",""],
  downloads:["Downloads","All download tasks"],
  queue:["Queue","Manage download queues"],
  browser:["Browser","Firefox browser session"],
  categories:["Categories","Organize downloads"],
  scheduler:["Scheduler","Scheduled download rules"],
  settings:["Settings","ABDM service configuration"]
};

function esc(s){return String(s).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]))}
function jsAttrArg(s){return esc(JSON.stringify(String(s)))}

function getPage(){
  if(state.view==="queue")return state.queuePage||1;
  if(state.view==="categories")return state.categoryPage||1;
  if(state.view==="scheduler")return state.schedulerPage||1;
  return state.downloadPage||1;
}

window.setPage=function(n){
  n=Math.max(1,Number(n)||1);
  if(state.view==="queue")state.queuePage=n;
  else if(state.view==="categories")state.categoryPage=n;
  else if(state.view==="scheduler")state.schedulerPage=n;
  else state.downloadPage=n;
  state.page=n;
  render();
};

window.setSize=function(n){
  state.pageSize=Math.max(1,Number(n)||10);
  if(state.view==="queue")state.queuePage=1;
  else if(state.view==="categories")state.categoryPage=1;
  else if(state.view==="scheduler")state.schedulerPage=1;
  else state.downloadPage=1;
  state.page=1;
  render();
};

function paginate(items){
  const pages=Math.max(1,Math.ceil(items.length/state.pageSize));
  let cur=getPage();
  if(cur>pages){
    cur=pages;
    if(state.view==="queue")state.queuePage=cur;
    else if(state.view==="categories")state.categoryPage=cur;
    else if(state.view==="scheduler")state.schedulerPage=cur;
    else state.downloadPage=cur;
    state.page=cur;
  }
  const start=(cur-1)*state.pageSize;
  return {items:items.slice(start,start+state.pageSize),pages,start,curPage:cur};
}

function pagination(total,pages){
  const curPage=getPage();
  const startItem=total?((curPage-1)*state.pageSize+1):0;
  const endItem=Math.min(curPage*state.pageSize,total);
  
  let pageButtons="";
  if(pages<=7){
    pageButtons=Array.from({length:pages},(_,i)=>`<button class="${i+1===curPage?"active":""}" onclick="setPage(${i+1})" >${i+1}</button>`).join("");
  } else {
    const btns=[];
    btns.push(1);
    if(curPage>3)btns.push("...");
    for(let p=Math.max(2,curPage-1);p<=Math.min(pages-1,curPage+1);p++){
      btns.push(p);
    }
    if(curPage<pages-2)btns.push("...");
    btns.push(pages);
    pageButtons=btns.map(b=>b==="..."?`<span class="page-ellipsis">…</span>`:`<button class="${b===curPage?"active":""}" onclick="setPage(${b})" >${b}</button>`).join("");
  }

  return `<div class="pagination">
    <span>Showing ${startItem}–${endItem} of ${total}</span>
    <div class="pages">
      <button class="page-nav" ${curPage<=1?"disabled":""} onclick="setPage(${curPage-1})" aria-label="Previous page">‹</button>
      ${pageButtons}
      <button class="page-nav" ${curPage>=pages?"disabled":""} onclick="setPage(${curPage+1})" aria-label="Next page">›</button>
    </div>
    <label style="display:flex;align-items:center;gap:7px">Per page
      <select class="page-size" aria-label="Items per page" onchange="setSize(this.value)">
        <option value="10" ${state.pageSize===10?"selected":""}>10</option>
        <option value="25" ${state.pageSize===25?"selected":""}>25</option>
        <option value="50" ${state.pageSize===50?"selected":""}>50</option>
        <option value="100" ${state.pageSize===100?"selected":""}>100</option>
      </select>
    </label>
  </div>`;
}

function fileIcon(name){
  const ext=name.split(".").pop().toLowerCase();
  return /zip|rar|7z|gz|iso/.test(ext)?"FileZip":/mp3|flac|wav|aac/.test(ext)?"FileMusic":/mp4|mkv|avi|webm/.test(ext)?"FileVideo":/pdf|docx?|txt|epub/.test(ext)?"FileDocument":/png|jpg|jpeg|webp/.test(ext)?"FilePicture":"File";
}

function isDownloadInCategory(x,catId){
  if(catId===null||catId===undefined)return true;
  const cat=state.categories.find(c=>Number(c.id)===Number(catId));
  if(!cat)return false;
  if(cat.items&&Array.isArray(cat.items)&&cat.items.map(Number).includes(Number(x.id)))return true;
  if(x.raw?.categoryName&&x.raw.categoryName===cat.name)return true;
  return false;
}

function visibleDownloads(){
  let items=state.allDownloads.filter(x=>{
    if(state.query&&!x.name.toLowerCase().includes(state.query))return false;
    if(state.queueFilter!==null&&(x.queueId===null||x.queueId===undefined||Number(x.queueId)!==Number(state.queueFilter)))return false;
    if(state.statusFilter==="finished"&&x.status!=="Completed")return false;
    if(state.statusFilter==="unfinished"&&x.status==="Completed")return false;
    if(state.categoryFilter!==null&&!isDownloadInCategory(x,state.categoryFilter))return false;
    return true;
  });

  return items.slice().sort((a,b)=>{
    if(state.sortKey==="name"){
      return a.name.localeCompare(String(b.name))*state.sortDirection;
    }
    if(state.sortKey==="status"){
      return a.status.localeCompare(String(b.status))*state.sortDirection;
    }
    if(state.sortKey==="eta"){
      const aEta=a.raw?.eta;
      const bEta=b.raw?.eta;
      const aVal=(aEta===null||aEta===undefined||aEta<0)?Infinity:Number(aEta);
      const bVal=(bEta===null||bEta===undefined||bEta<0)?Infinity:Number(bEta);
      return (aVal-bVal)*state.sortDirection;
    }
    let av=a.raw?.[state.sortKey]??0;
    let bv=b.raw?.[state.sortKey]??0;
    return (Number(av)-Number(bv))*state.sortDirection;
  });
}

window.setFilter=function(status,category=null,queue=null){
  state.statusFilter=status;
  state.categoryFilter=category;
  state.queueFilter=queue;
  if(queue===null){
    state.expandedStatus=status;
  } else {
    state.expandedQueues.add(Number(queue));
  }
  state.downloadPage=1;
  state.page=1;
  state.selectedIds.clear();
  render();
};

window.toggleQueueExpand=function(queueId,event){
  if(event)event.stopPropagation();
  queueId=Number(queueId);
  if(state.expandedQueues.has(queueId)){
    state.expandedQueues.delete(queueId);
  } else {
    state.expandedQueues.add(queueId);
  }
  render();
};

window.sortDownloads=function(key){
  state.sortDirection=state.sortKey===key?-state.sortDirection:1;
  state.sortKey=key;
  render();
};

function dateCell(value){
  return (value&&Number(value)>0)?esc(new Date(Number(value)).toLocaleDateString()):"—";
}

function table(items){
  const ids=items.map(x=>Number(x.id));
  const allChecked=ids.length>0&&ids.every(id=>state.selectedIds.has(id));
  const cols=[["name","Name"],["size","Size"],["status","Status"],["speed","Speed"],["eta","Time Left"],["dateAdded","Date Added"]];
  return `<div class="table-wrap" data-scroll-key="download-table"><table class="table download-table"><thead><tr><th class="select-col"><input type="checkbox" aria-label="Select all" ${allChecked?"checked":""} onchange="togglePageSelection(this.checked)"></th>${cols.map(([key,label])=>`<th class="col-${key}"><button onclick="sortDownloads('${key}')">${label}${state.sortKey===key?'<span class="sort-arrow">'+(state.sortDirection===1?'▴':'▾')+'</span>':''}</button></th>`).join("")}</tr></thead><tbody>${items.map(x=>`<tr class="${state.selectedIds.has(Number(x.id))?'selected':''}" tabindex="0" aria-label="${esc(x.name)}" ondblclick="showDownload(${x.id})" onkeydown="if(event.key==='Enter')showDownload(${x.id})" oncontextmenu="event.preventDefault();openDownloadMenu(event,${x.id})"><td class="select-col"><input type="checkbox" aria-label="Select ${esc(x.name)}" ${state.selectedIds.has(Number(x.id))?"checked":""} onchange="toggleSelected(${x.id},this.checked)"></td><td class="name"><button class="file-name" title="${esc(x.name)}" onclick="showDownload(${x.id})">${icon(fileIcon(x.name))}<span>${esc(x.name)}</span></button></td><td>${x.size}</td><td><div class="status-cell ${x.status==='Completed'?'finished':x.status==='Error'?'error':''}"><span>${esc(x.status==='Completed'?'Finished':x.status)}${x.status==='Downloading'?' · '+x.progress+'%':''}</span><div class="progress"><i style="width:${Math.min(100,Math.max(0,Number(x.progress)||0))}%"></i></div></div></td><td>${x.speed}</td><td>${x.eta}</td><td>${dateCell(x.raw?.dateAdded)}</td></tr>`).join("")}</tbody></table>${items.length?'':'<div class="abdm-empty">List is empty</div>'}</div>`;
}

function selectionToolbar(){return "";}

function filterButton(status,label,ico){
  const chosen=state.statusFilter===status&&state.categoryFilter===null&&state.queueFilter===null;
  const count=state.allDownloads.filter(x=>status==="all"||(status==="finished"?x.status==="Completed":x.status!=="Completed")).length;
  return `<button class="home-filter ${chosen?'active':''}" aria-label="${label}" onclick="setFilter('${status}',null,null)"><span class="chevron">${state.expandedStatus===status?'⌄':'›'}</span>${icon(ico)}<span>${label}</span><span class="filter-count">${count}</span></button>`;
}

function renderDashboard(){
  const items=visibleDownloads(),dp=paginate(items),active=state.allDownloads.filter(x=>["Downloading","Preparing","Retrying"].includes(x.status)).length,speed=state.allDownloads.reduce((n,x)=>n+Number(x.raw?.speed||0),0);
  const statuses=[["all","All","Folder"],["finished","Finished","FolderFinished"],["unfinished","Unfinished","FolderUnfinished"]];
  const filters=statuses.map(([key,label,ico])=>filterButton(key,label,ico)+(state.expandedStatus===key?state.categories.map(c=>{
    const cCount=state.allDownloads.filter(x=>{
      if(key==="finished"&&x.status!=="Completed")return false;
      if(key==="unfinished"&&x.status==="Completed")return false;
      return isDownloadInCategory(x,c.id);
    }).length;
    return `<button class="home-filter category-filter ${state.categoryFilter===c.id&&state.queueFilter===null?'active':''}" onclick="setFilter('${key}',${Number(c.id)},null)">${icon(fileIcon('file.'+(c.acceptedFileTypes?.[0]||'')))}<span>${esc(c.name)}</span><span class="filter-count">${cCount}</span></button>`;
  }).join(""):"")).join("");
  
  const tool=(label,ico,action,disabled=false)=>`<button class="home-tool" title="${label}" aria-label="${label}" onclick="${action}" ${disabled?'disabled':''}>${icon(ico)}</button>`;

  const queueNavHtml=state.queues.map(q=>{
    const qId=Number(q.id);
    const isThisQueue=state.queueFilter!==null&&Number(state.queueFilter)===qId;
    const qAll=state.allDownloads.filter(x=>x.queueId!==null&&x.queueId!==undefined&&Number(x.queueId)===qId).length;
    const qFin=state.allDownloads.filter(x=>x.queueId!==null&&x.queueId!==undefined&&Number(x.queueId)===qId&&x.status==="Completed").length;
    const qUnfin=qAll-qFin;
    const isExpanded=state.expandedQueues.has(qId)||isThisQueue;

    return `<div class="queue-nav-group">
      <div class="queue-header-row ${isThisQueue&&state.statusFilter==='all'?'active':''}">
        <button class="queue-toggle-btn" aria-label="Toggle ${esc(q.name)} sub-filters" onclick="toggleQueueExpand(${qId},event)">
          <span class="chevron">${isExpanded?'⌄':'›'}</span>
        </button>
        <button class="queue-select-btn" onclick="setFilter('all',null,${qId})" title="View all downloads in ${esc(q.name)}">
          ${icon('Queue')}
          <span>${esc(q.name)}</span>
          ${q.running?'<i class="queue-running" title="Running"></i>':''}
          <span class="filter-count">${qAll}</span>
        </button>
      </div>
      ${isExpanded?`
        <div class="queue-subfilters">
          <button class="home-filter queue-subfilter ${isThisQueue&&state.statusFilter==='all'?'active':''}" onclick="setFilter('all',null,${qId})" title="${esc(q.name)}: All">
            <span>All</span>
            <span class="filter-count">${qAll}</span>
          </button>
          <button class="home-filter queue-subfilter ${isThisQueue&&state.statusFilter==='finished'?'active':''}" onclick="setFilter('finished',null,${qId})" title="${esc(q.name)}: Finished">
            <span>Finished</span>
            <span class="filter-count">${qFin}</span>
          </button>
          <button class="home-filter queue-subfilter ${isThisQueue&&state.statusFilter==='unfinished'?'active':''}" onclick="setFilter('unfinished',null,${qId})" title="${esc(q.name)}: Unfinished">
            <span>Unfinished</span>
            <span class="filter-count">${qUnfin}</span>
          </button>
        </div>
      `:''}
    </div>`;
  }).join("");

  let queueStatusBar="";
  if(state.queueFilter!==null){
    const activeQ=state.queues.find(q=>Number(q.id)===Number(state.queueFilter));
    const qId=Number(state.queueFilter);
    const qAll=state.allDownloads.filter(x=>x.queueId!==null&&x.queueId!==undefined&&Number(x.queueId)===qId).length;
    const qFin=state.allDownloads.filter(x=>x.queueId!==null&&x.queueId!==undefined&&Number(x.queueId)===qId&&x.status==="Completed").length;
    const qUnfin=qAll-qFin;
    queueStatusBar=`<div class="queue-status-bar">
      <div class="queue-status-bar-info">
        <span class="queue-badge">${icon('Queue')} ${esc(activeQ?.name||"Queue")}</span>
        <span class="queue-state-desc">${state.statusFilter==='finished'?'Finished tasks':state.statusFilter==='unfinished'?'Unfinished tasks':'All tasks'} (${items.length})</span>
      </div>
      <div class="queue-status-pills">
        <button class="queue-pill ${state.statusFilter==='all'?'active':''}" onclick="setFilter('all',null,${qId})">All (${qAll})</button>
        <button class="queue-pill ${state.statusFilter==='finished'?'active':''}" onclick="setFilter('finished',null,${qId})">Finished (${qFin})</button>
        <button class="queue-pill ${state.statusFilter==='unfinished'?'active':''}" onclick="setFilter('unfinished',null,${qId})">Unfinished (${qUnfin})</button>
        <button class="queue-pill-clear" onclick="setFilter('all',null,null)" title="View all downloads">✕ All downloads</button>
      </div>
    </div>`;
  }

  return `<div class="abdm-home"><aside class="home-sidebar" aria-label="Download categories and queues" tabindex="0" data-scroll-key="home-filters"><div class="filter-group">${filters}</div><div class="queue-sidebar"><div class="home-sidebar-title"><span>Queues</span><button class="tiny-tool" title="New queue" aria-label="New queue" onclick="createQueue()">${icon('Plus')}</button></div>${queueNavHtml}</div></aside><div class="pane-divider"></div><section class="home-content"><div class="home-toolbar"><button class="home-add" onclick="document.getElementById('addBtn').click()">${icon('AddLink')}<span>Add URL</span></button><span class="tool-divider"></span>${tool('Resume','Resume',"selectedAction('resume')",!state.selectedIds.size)}${tool('Pause','Pause',"selectedAction('pause')",!state.selectedIds.size)}<span class="tool-divider"></span>${tool('Start queues','QueueStart',"controlAllQueues('start')")}${tool('Stop queues','QueueStop',"controlAllQueues('stop')")}${tool('Queues','Queue',"navigateTo('queue')")}<span class="tool-divider"></span>${tool('Stop all','Stop',"bulkAction('pause')")}${tool('Delete','Delete',"selectedAction('remove')",!state.selectedIds.size)}<span class="tool-divider"></span>${tool('Settings','Settings',"navigateTo('settings')")}</div>${queueStatusBar}<div class="home-table-wrap">${table(dp.items)}</div>${pagination(items.length,dp.pages)}<div class="home-footer"><span>${icon('Check')} ${state.selectedIds.size} / ${items.length}</span><span>${icon('File')} ${formatBytes(items.filter(x=>state.selectedIds.has(Number(x.id))).reduce((n,x)=>n+Math.max(0,Number(x.raw?.size)||0),0))}</span><span class="footer-spacer"></span><span>${icon('Resume')} ${active}</span><span>${icon('DownSpeed')} ${formatSpeed(speed)}</span></div></section></div>`;
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
    const qAll=items.length;
    const qFin=items.filter(d=>d.status==='Completed').length;
    const qUnfin=qAll-qFin;
    return `<div class="card queue-card">
      <div class="queue-head">
        <div>
          <h3>${esc(q.name)}</h3>
          <small>${q.active} active · ${q.queued} queued · ${q.total} total · max ${q.maxConcurrent||1} concurrent</small>
        </div>
        <div class="actions">
          ${q.running?'<button class="icon-btn" title="Stop queue" onclick="queueAction('+q.id+',\'stop\')">⏹</button>':'<button class="icon-btn" title="Start queue" onclick="queueAction('+q.id+',\'start\')">▶</button>'}
          <button class="icon-btn" title="Rename" onclick="renameQueue(${q.id})">✎</button>
          <button class="icon-btn" title="Concurrency" onclick="setQueueConcurrency(${q.id},${q.maxConcurrent||1})">≡</button>
          ${q.id!==0?'<button class="icon-btn danger" title="Delete" onclick="deleteQueue('+q.id+')">✕</button>':''}
        </div>
      </div>
      <div class="queue-status-bar" style="border-top:0">
        <div class="queue-status-pills">
          <button class="queue-pill" onclick="setFilter('all',null,${q.id});navigateTo('dashboard')">All tasks (${qAll})</button>
          <button class="queue-pill" onclick="setFilter('finished',null,${q.id});navigateTo('dashboard')">Finished (${qFin})</button>
          <button class="queue-pill" onclick="setFilter('unfinished',null,${q.id});navigateTo('dashboard')">Unfinished (${qUnfin})</button>
        </div>
      </div>
      <div class="queue-items">${items.length?items.map((d,i)=>`<div class="queue-item">
        <div class="queue-order">${i+1}</div>
        <div class="queue-name"><strong title="${esc(d.name)}">${esc(d.name)}</strong><small>${d.progress}% · ${esc(d.status==='Completed'?'Finished':d.status)} · ${d.speed}</small></div>
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
  if(!session?.enabled||!session?.ready){
    scheduleBrowserRetry();
    return '<div class="card empty"><strong>Browser is starting</strong><span>Firefox</span></div>';
  }
  const wsPath=encodeURIComponent("browser/websockify?token="+(session.token||""));
  const url=window.location.origin+"/browser/novnc/vnc.html?autoconnect=true&reconnect=true&reconnect_delay=2000&resize=scale&show_dot=true&path="+wsPath;
  return '<div class="browser-shell">'+
    '<div class="browser-head"><div><strong>Firefox</strong><span></span></div><div class="actions"><button class="secondary" onclick="reloadBrowser()">Reconnect</button><button class="secondary" data-browser-restart onclick="restartChromium()">Restart Firefox</button></div></div>'+
    '<div class="browser-frame-wrap"><iframe class="browser-frame" src="'+esc(url)+'" title="Firefox browser" allow="clipboard-read; clipboard-write"></iframe></div>'+
    '</div>';
}

let browserRetryTimer=null;
function scheduleBrowserRetry(){
  if(browserRetryTimer)return;
  browserRetryTimer=setTimeout(async()=>{browserRetryTimer=null;if(state.view!=="browser")return;await loadBrowserSession(true);if(state.view==="browser")render()},3000);
}

window.reloadBrowser=async function(){
  state.browserSession=null;
  render();
  await loadBrowserSession(false);
};

window.restartChromium=async function(){
  try{await ABDM_API.restartBrowser()}catch(e){alert("Restart failed: "+e.message);return}
  setTimeout(()=>reloadBrowser(),4000);
};

function renderCategories(){
  const p=paginate(state.categories);
  return `<div class="toolbar"><div class="grow"></div><button class="primary" onclick="createCategory()">＋ New category</button></div>
  <div class="card table-wrap"><table class="table"><thead><tr><th>NAME</th><th>PATH</th><th>FILE TYPES</th><th>ITEMS</th><th></th></tr></thead><tbody>${p.items.map(x=>`<tr><td><strong>${esc(x.name)}</strong>${x.defaultCategory?'<span class="tag" style="margin-left:8px">Default</span>':''}</td><td>${x.usePath?esc(x.path):'<span class="muted">Default download path</span>'}</td><td>${esc((x.acceptedFileTypes||[]).join(", ")||"All")}</td><td>${x.items?.length||0}</td><td><span class="actions"><button class="icon-btn" title="Rename" onclick="renameCategory(${x.id})">✎</button>${x.defaultCategory?'':'<button class="icon-btn danger" title="Delete" onclick="deleteCategory('+x.id+')">✕</button>'}</span></td></tr>`).join("")}</tbody></table></div>${pagination(state.categories.length,p.pages)}`;
}

function renderSettings(){
  const s=state.settings||{};
  return `<div class="settings-grid">
    <div class="card">
      <div class="section-head"><div><h3>Downloads</h3></div></div>
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
      <div class="section-head"><div><h3>Web API</h3></div></div>
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

window.createCategory=async function(){
  const name=await guiPrompt("Category name","New Category");
  if(!name)return;
  const usePath=await guiConfirm("Use a dedicated download path for this category?");
  const path=usePath?await guiPrompt("Download path","/downloads/"+name.replace(/\s+/g,"_")):"";
  if(usePath&&path===null)return;
  const fileTypes=(await guiPrompt("Accepted file types (comma separated, e.g. zip,7z,iso)","")||"").split(",").map(x=>x.trim()).filter(Boolean);
  const urlPatterns=(await guiPrompt("Accepted URL patterns (comma separated)","")||"").split(",").map(x=>x.trim()).filter(Boolean);
  try{await ABDM_API.createCategory({name,path,usePath,fileTypes,urlPatterns});await loadCategories(false)}catch(e){alert("Category creation failed: "+e.message)}
};

window.renameCategory=async function(id){
  const x=state.categories.find(c=>Number(c.id)===Number(id));
  if(!x)return;
  const n=await guiPrompt("Category name",x.name);
  if(!n||n===x.name)return;
  try{await ABDM_API.renameCategory(id,n);await loadCategories(false)}catch(e){alert("Rename failed: "+e.message)}
};

window.deleteCategory=async function(id){
  const x=state.categories.find(c=>Number(c.id)===Number(id));
  if(!x)return;
  if(!await guiConfirm("Delete category '"+x.name+"'?"))return;
  try{await ABDM_API.deleteCategory(id);await loadCategories(false)}catch(e){alert("Delete failed: "+e.message)}
};

async function loadCategories(quiet=true){
  if(!state.connected)return;
  try{
    const items=await ABDM_API.categories();
    state.categories=Array.isArray(items)?items:[];
    if(state.view==="categories"){
      const maxPages=Math.max(1,Math.ceil(state.categories.length/state.pageSize));
      if((state.categoryPage||1)>maxPages){
        state.categoryPage=maxPages;
        state.page=maxPages;
      }
    }
    refreshCategorySelect();
    if(state.view==="categories"&&!quiet)render();
  }catch(e){console.warn("Unable to load categories",e)}
}

window.browseTo=async function(path){
  try{
    const data=await ABDM_API.browser(path);
    state.browserPath=data.path||"";
    state.browserItems=Array.isArray(data.items)?data.items:[];
    state.page=1;
    render();
  }catch(e){alert("Browser error: "+e.message)}
};

window.browseParent=async function(){
  const p=state.browserPath.split("/").filter(Boolean);
  p.pop();
  await browseTo(p.join("/"));
};

function render(){
  const pageScroll=window.scrollY;
  const scrollState={};
  document.querySelectorAll("[data-scroll-key]").forEach(el=>scrollState[el.dataset.scrollKey]={top:el.scrollTop,left:el.scrollLeft});
  document.body.dataset.view=state.view;
  
  if(state.view==="dashboard"&&state.queueFilter!==null){
    const activeQ=state.queues.find(q=>Number(q.id)===Number(state.queueFilter));
    const qName=activeQ?activeQ.name:"Queue";
    const sub=state.statusFilter==="finished"?"Finished tasks":state.statusFilter==="unfinished"?"Unfinished tasks":"All tasks";
    document.getElementById("page-title").textContent="Downloads — "+qName;
    document.getElementById("page-subtitle").textContent=sub;
  } else {
    document.getElementById("page-title").textContent=titles[state.view][0];
    document.getElementById("page-subtitle").textContent=titles[state.view][1];
  }

  document.querySelectorAll(".nav-item").forEach(x=>x.classList.toggle("active",x.dataset.page===state.view));
  let html=state.view==="dashboard"?renderDashboard():state.view==="downloads"?renderDownloads():state.view==="queue"?renderQueue():state.view==="browser"?renderBrowser():state.view==="categories"?renderCategories():state.view==="scheduler"?renderScheduler():state.view==="settings"?renderSettings():renderSimple("Page","Coming soon.");
  document.getElementById("app").innerHTML=html;
  Object.entries(scrollState).forEach(([key,pos])=>{const el=document.querySelector(`[data-scroll-key="${key}"]`);if(el){el.scrollTop=pos.top;el.scrollLeft=pos.left}});
  requestAnimationFrame(()=>window.scrollTo(0,pageScroll));
}

function renderDownloadParts(parts){
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
  document.getElementById("detailsSubtitle").textContent=(d.status==='Completed'?'Finished':d.status)+" · "+(d.queueName||"No queue");
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
    '<div class="parts-section"><div class="parts-head"><div><strong>Parts Info</strong></div></div><div id="download-parts"><div class="parts-empty">Loading connection details…</div></div></div>';
  document.getElementById("saveDetailsBtn").onclick=()=>saveDownloadDetails(id);
  document.getElementById("detailsDialog").showModal();
  loadDownloadParts(id);
  state.detailsPollTimer=setInterval(()=>loadDownloadParts(id),1000);
};

document.getElementById("detailsDialog").addEventListener("close",()=>{
  if(state.detailsPollTimer){clearInterval(state.detailsPollTimer);state.detailsPollTimer=null}
});

window.saveDownloadDetails=async function(id){
  const link=document.getElementById("detail-link")?.value.trim();
  const connections=Number(document.getElementById("detail-connections")?.value);
  if(!link){alert("Download link cannot be empty.");return}
  if(!Number.isInteger(connections)||connections<1||connections>128){alert("Connections must be between 1 and 128.");return}
  try{
    await ABDM_API.updateDownload(id,{link,preferredConnectionCount:connections});
    document.getElementById("detailsDialog").close();
    await loadDownloads(false);
  }catch(e){alert("Download update failed: "+e.message)}
};

window.downloadAction=async function(id,action,removeFile=false){
  if(action==="remove"&&!await guiConfirm(removeFile?"Remove the download and delete its file?":"Remove this download from ABDM?"))return;
  try{await ABDM_API.control(id,action,removeFile);await loadDownloads(false)}catch(e){alert("Action failed: "+e.message)}
};

window.toggleSelected=function(id,checked){
  id=Number(id);
  if(checked)state.selectedIds.add(id);
  else state.selectedIds.delete(id);
  render();
};

window.togglePageSelection=function(checked){
  const source=state.view==="downloads"?state.downloads:visibleDownloads();
  const curPage=getPage();
  source.slice((curPage-1)*state.pageSize,curPage*state.pageSize).forEach(x=>checked?state.selectedIds.add(Number(x.id)):state.selectedIds.delete(Number(x.id)));
  render();
};

window.clearSelection=function(){state.selectedIds.clear();render()};

window.selectedAction=async function(action,removeFile=false){
  const ids=Array.from(state.selectedIds);
  if(!ids.length)return;
  if(action==="remove"&&!await guiConfirm(removeFile?"Delete the selected files and remove the downloads?":"Remove the selected downloads?"))return;
  try{
    await Promise.all(ids.map(id=>ABDM_API.control(id,action,removeFile)));
    state.selectedIds.clear();
    await loadDownloads(false);
  }catch(e){alert("Selected action failed: "+e.message)}
};

window.filterDownloads=q=>{
  state.query=(q||"").toLowerCase();
  state.downloads=state.query?state.allDownloads.filter(x=>x.name.toLowerCase().includes(state.query)):state.allDownloads.slice();
  state.downloadPage=1;
  state.page=1;
  render();
  requestAnimationFrame(()=>{const el=document.getElementById("downloadSearch");if(el){el.focus();el.setSelectionRange(el.value.length,el.value.length)}});
};

window.bulkAction=async function(action){
  const ids=state.allDownloads.filter(x=>action==="pause"?["Downloading","Preparing","Retrying"].includes(x.status):["Paused","Queued"].includes(x.status)).map(x=>x.id);
  try{await Promise.all(ids.map(id=>ABDM_API.control(id,action)));await loadDownloads(false)}catch(e){alert("Bulk action failed: "+e.message)}
};

window.createQueue=async function(){
  const name=await guiPrompt("Queue name","New Queue");
  if(!name)return;
  try{await ABDM_API.createQueue(name);await loadQueues(false)}catch(e){alert(e.message)}
};

window.renameQueue=async function(id){
  const q=state.queues.find(x=>Number(x.id)===Number(id));
  if(!q)return;
  const n=await guiPrompt("Queue name",q.name);
  if(!n||n===q.name)return;
  try{await ABDM_API.renameQueue(id,n);await loadQueues(false)}catch(e){alert(e.message)}
};

window.deleteQueue=async function(id){
  const q=state.queues.find(x=>Number(x.id)===Number(id));
  if(!q||Number(id)===0)return;
  if(!await guiConfirm("Delete queue '"+q.name+"'? Downloads remain."))return;
  try{
    if(Number(state.queueFilter)===Number(id)){
      state.queueFilter=null;
    }
    await ABDM_API.deleteQueue(id);
    await loadQueues(false);
  }catch(e){alert(e.message)}
};

window.setQueueConcurrency=async function(id,current){
  const n=Number(await guiPrompt("Maximum simultaneous downloads",current));
  if(!Number.isInteger(n)||n<1)return;
  try{await ABDM_API.queueConcurrency(id,n);await loadQueues(false)}catch(e){alert(e.message)}
};

window.moveDownloadToQueue=async function(id){
  const list=state.queues.map(q=>q.id+" = "+q.name).join("\n");
  const value=await guiPrompt("Enter queue ID:\n"+list,"0");
  if(value===null)return;
  const q=Number(value);
  if(!Number.isInteger(q)||!state.queues.some(x=>Number(x.id)===q)){alert("Invalid queue ID.");return}
  try{await ABDM_API.assignQueue(id,q);await loadDownloads(false);await loadQueues(false)}catch(e){alert("Queue assignment failed: "+e.message)}
};

window.assignDownload=async function(id,queueId){
  try{
    if(queueId===null)await ABDM_API.unqueue(id);
    else await ABDM_API.assignQueue(id,queueId);
    await loadDownloads(false);
    await loadQueues(false);
  }catch(e){alert("Queue assignment failed: "+e.message)}
};

window.moveQueueItem=async function(id,direction){
  try{
    await ABDM_API.moveQueueItem(id,direction);
    await loadQueues(false);
    await loadDownloads(true);
  }catch(e){alert("Queue ordering failed: "+e.message)}
};

window.queueAction=async function(id,action){
  try{await ABDM_API.queueControl(id,action);await loadQueues(false)}catch(e){alert("Queue action failed: "+e.message)}
};

function refreshQueueSelect(){
  const s=document.getElementById("queueInput");
  if(!s)return;
  const current=s.value;
  const mainQueue=state.queues.find(q=>Number(q.id)===0||q.name.toLowerCase()==="main")||state.queues[0];
  const mainId=mainQueue?String(mainQueue.id):"0";
  
  let optionsHtml=state.queues.map(q=>`<option value="${q.id}" ${String(q.id)===mainId?'selected':''}>${esc(q.name)}</option>`).join("");
  optionsHtml+=`<option value="">No queue</option>`;
  s.innerHTML=optionsHtml;

  if(current!==""&&current!==undefined&&Array.from(s.options).some(o=>o.value===current)){
    s.value=current;
  } else if(mainQueue){
    s.value=mainId;
  }
}

function refreshCategorySelect(){
  const s=document.getElementById("categoryInput");
  if(!s)return;
  const current=s.value;
  s.innerHTML=`<option value="">Automatic</option>`+state.categories.map(x=>`<option value="${x.id}">${esc(x.name)}</option>`).join("");
  if(Array.from(s.options).some(o=>o.value===current))s.value=current;
}

function shouldDeferRender(){
  const el=document.activeElement;
  if(!el)return false;
  if(el.id==="globalSearch")return true;
  if(el.tagName==="INPUT"||el.tagName==="TEXTAREA"||el.tagName==="SELECT")return true;
  if(el.closest("dialog[open]"))return true;
  if(el.closest(".pagination"))return true;
  if(el.closest(".context-menu:not([hidden])"))return true;
  return false;
}

async function loadQueues(quiet=true){
  if(!state.connected)return;
  try{
    const items=await ABDM_API.queues();
    state.queues=Array.isArray(items)?items:[];
    refreshQueueSelect();
    if(state.view==="queue"){
      const maxPages=Math.max(1,Math.ceil(state.queues.length/state.pageSize));
      if((state.queuePage||1)>maxPages){
        state.queuePage=maxPages;
        state.page=maxPages;
      }
    }
    if(!quiet||(!shouldDeferRender()&&(state.view==="dashboard"||state.view==="queue"||state.view==="scheduler")))render();
  }catch(err){console.warn("Unable to load queues",err);state.connected=false;document.getElementById("connection").textContent="Disconnected"}
}

async function loadDownloads(quiet=true){
  if(!state.connected)return;
  try{
    const items=await ABDM_API.downloads();
    state.allDownloads=(Array.isArray(items)?items:[]).map(x=>({
      id:x.id,
      name:x.name,
      size:formatBytes(x.size),
      progress:x.percent==null?0:x.percent,
      speed:formatSpeed(x.speed),
      eta:formatEta(x.eta),
      status:x.status,
      queueId:x.queueId,
      queueName:x.queueName,
      raw:x
    }));
    const liveIds=new Set(state.allDownloads.map(x=>Number(x.id)));
    state.selectedIds.forEach(id=>{if(!liveIds.has(Number(id)))state.selectedIds.delete(id)});
    state.downloads=state.query?state.allDownloads.filter(x=>x.name.toLowerCase().includes(state.query)):state.allDownloads.slice();
    
    const visibleCount=visibleDownloads().length;
    const maxPages=Math.max(1,Math.ceil(visibleCount/state.pageSize));
    if((state.downloadPage||1)>maxPages){
      state.downloadPage=maxPages;
      state.page=maxPages;
    }

    const editingSearch=document.activeElement?.id==="globalSearch"||(state.view==="downloads"&&document.activeElement?.classList.contains("search"));
    if(!quiet||(!editingSearch&&!shouldDeferRender()&&(state.view==="dashboard"||state.view==="queue"||state.view==="downloads")))render();
  }catch(err){console.warn("Unable to load downloads",err);state.connected=false;document.getElementById("connection").textContent="Disconnected"}
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

document.getElementById("nav").addEventListener("click",e=>{
  const b=e.target.closest(".nav-item");
  if(b){
    state.view=b.dataset.page;
    if(state.view==="queue")state.queuePage=1;
    else if(state.view==="categories")state.categoryPage=1;
    else if(state.view==="scheduler")state.schedulerPage=1;
    else state.downloadPage=1;
    state.page=1;
    render();
    if(state.view==="browser")loadBrowserSession(false);
  }
});

document.getElementById("addBtn").onclick=async()=>{
  await loadQueues(true);
  await loadCategories(true);
  refreshQueueSelect();
  refreshCategorySelect();
  const s=document.getElementById("queueInput");
  if(s&&(!s.value||s.value==="")){
    const mainQ=state.queues.find(q=>Number(q.id)===0||q.name.toLowerCase()==="main");
    if(mainQ)s.value=String(mainQ.id);
  }
  if(state.settings?.downloadFolder)document.getElementById("pathInput").value=state.settings.downloadFolder;
  document.getElementById("addDialog").showModal();
};

document.getElementById("addDialog").addEventListener("close",()=>{
  if(document.getElementById("addDialog").returnValue==="cancel"){
    document.getElementById("urlInput").value="";
  }
});

document.getElementById("addForm").addEventListener("submit",async e=>{
  if(e.submitter?.value==="cancel")return;
  e.preventDefault();
  const urlInput=document.getElementById("urlInput");
  const urls=urlInput.value.split(/\r?\n/).map(x=>x.trim()).filter(Boolean);
  if(!urls.length)return;
  try{
    await ABDM_API.add({
      urls,
      folder:document.getElementById("pathInput").value,
      queueId:(document.getElementById("queueInput").value===""?null:Number(document.getElementById("queueInput").value)),
      categoryId:(document.getElementById("categoryInput").value===""?null:Number(document.getElementById("categoryInput").value))
    });
    urlInput.value="";
    document.getElementById("addDialog").close();
    await loadDownloads(false);
  }catch(err){alert("Backend API error: "+err.message)}
});

document.getElementById("importBtn").onclick=()=>document.getElementById("importDialog").showModal();

document.getElementById("importFile").addEventListener("change",async e=>{
  const file=e.target.files?.[0];
  if(!file)return;
  try{
    state.importItems=await ABDM_API.inspectLinks(await file.text());
    document.getElementById("importPreview").innerHTML=state.importItems.length?state.importItems.map((x,i)=>`<label class="import-row"><input type="checkbox" class="import-check" data-index="${i}" checked><span><strong>${esc(x.name||"Unknown")}</strong><small>${esc(x.sizeText||"Size unknown")} · ${esc(x.status||"")}${x.error?" · "+esc(x.error):""}</small></span></label>`).join(""):"<div class='empty'>No valid links found.</div>";
  }catch(err){
    document.getElementById("importPreview").innerHTML=`<div class="empty">Import failed: ${esc(err.message)}</div>`;
  }
});

document.getElementById("importForm").addEventListener("submit",async e=>{
  if(e.submitter?.value==="cancel")return;
  e.preventDefault();
  const selected=Array.from(document.querySelectorAll(".import-check:checked")).map(x=>state.importItems[Number(x.dataset.index)]).filter(Boolean);
  if(!selected.length)return;
  try{
    await ABDM_API.add({urls:selected.map(x=>x.url),names:selected.map(x=>x.name),folder:state.settings?.downloadFolder||"/downloads"});
    state.importItems=[];
    document.getElementById("importDialog").close();
    await loadDownloads(false);
  }catch(err){alert("Import failed: "+err.message)}
});

async function connect(){
  try{
    await ABDM_API.ping();
    state.connected=true;
    await loadDownloads(true);
    await loadQueues(true);
    await loadCategories(true);
    await loadSettings(true);
    await loadBrowserSession(true);
    document.querySelector(".status-dot").classList.add("ok");
    document.getElementById("connection").textContent="Connected";
  }catch(e){
    document.getElementById("connection").textContent="Disconnected";
  }
  render();
}

render();
connect();

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
  if(state.view==="settings")loadSettings(true);
},1500);
