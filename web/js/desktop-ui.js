// Browser shell for the original desktop-style menus and dialogs; API operations stay in app.js.
window.navigateTo=function(view){state.view=view;state.page=1;document.querySelectorAll('.desktop-menu[open]').forEach(x=>x.open=false);render();if(view==='browser')loadBrowserSession(false)};
window.controlAllQueues=async function(action){try{await Promise.all(state.queues.map(q=>ABDM_API.queueControl(q.id,action)));await loadQueues(false)}catch(e){alert('Queue action failed: '+e.message)}};
window.addFromClipboard=async function(){try{const text=await navigator.clipboard.readText();document.getElementById('urlInput').value=text}catch(e){/* Clipboard permission is optional; the URL dialog remains usable. */}document.getElementById('addBtn').click()};
window.guiPrompt=function(message,value=''){return showGuiQuestion(message,value,false)};
window.guiConfirm=function(message){return showGuiQuestion(message,'',true)};
function showGuiQuestion(message,value,confirmation){return new Promise(resolve=>{
 const dialog=document.getElementById('questionDialog'),input=document.getElementById('questionInput');
 document.getElementById('questionTitle').textContent=confirmation?'Confirm':'AB Download Manager';
 document.getElementById('questionMessage').textContent=message;input.hidden=confirmation;input.value=value??'';
 const closed=()=>{dialog.removeEventListener('close',closed);resolve(dialog.returnValue==='ok'?(confirmation?true:input.value):(confirmation?false:null))};
 dialog.returnValue='';dialog.addEventListener('close',closed);dialog.showModal();if(!confirmation)input.focus();
})}
window.openDownloadMenu=function(event,id){const menu=document.getElementById('downloadMenu');menu.innerHTML=[['Download details','Info'],['Resume','Resume'],['Pause','Pause'],['Retry','Refresh'],['Move to queue','Queue'],['Remove','Delete'],['Delete file','Delete']].map(([label,ico])=>`<button>${icon(ico)}<span>${label}</span></button>`).join('');menu.querySelectorAll('button').forEach((b,i)=>b.onclick=()=>{menu.hidden=true;[()=>showDownload(id),()=>downloadAction(id,'resume'),()=>downloadAction(id,'pause'),()=>downloadAction(id,'retry'),()=>moveDownloadToQueue(id),()=>downloadAction(id,'remove',false),()=>downloadAction(id,'remove',true)][i]()});menu.hidden=false;menu.style.left=Math.min(event.clientX,window.innerWidth-220)+'px';menu.style.top=Math.min(event.clientY,window.innerHeight-300)+'px'};
let ffRestarting=false;
window.restartChromium=async function(){
 if(ffRestarting)return;ffRestarting=true;
 const btn=document.querySelector('[data-browser-restart]');if(btn){btn.disabled=true;btn.textContent='Restarting…'}
 try{await ABDM_API.request('/browser/restart?t='+Date.now(),{method:'POST',cache:'no-store',credentials:'same-origin',body:'{}'})}
 catch(e){ffRestarting=false;if(btn){btn.disabled=false;btn.textContent='Restart Firefox'}alert('Restart failed: '+e.message);return}
 setTimeout(()=>{ffRestarting=false;reloadBrowser()},6000);
};
document.addEventListener('touchend',event=>{const b=event.target.closest?.('[data-browser-restart]');if(b){event.preventDefault();restartChromium()}},{passive:false});
document.addEventListener('click',event=>{if(!event.target.closest('#downloadMenu'))document.getElementById('downloadMenu').hidden=true;document.querySelectorAll('.desktop-menu[open]').forEach(x=>{if(!x.contains(event.target))x.open=false})});
document.addEventListener('keydown',event=>{if(event.key==='Escape'){document.getElementById('downloadMenu').hidden=true;document.querySelectorAll('.desktop-menu[open]').forEach(x=>x.open=false)}});
document.querySelectorAll('[data-icon]').forEach(el=>el.innerHTML=icon(el.dataset.icon));
document.getElementById('globalSearch').addEventListener('input',event=>{state.query=event.target.value.toLowerCase();state.page=1;state.downloads=state.allDownloads.filter(x=>x.name.toLowerCase().includes(state.query));render()});
document.getElementById('themeBtn').onclick=()=>{const light=document.documentElement.dataset.theme!=='light';document.documentElement.dataset.theme=light?'light':'dark';localStorage.setItem('abdmWebTheme',light?'light':'dark')};
document.documentElement.dataset.theme=localStorage.getItem('abdmWebTheme')||'dark';
