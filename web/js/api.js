window.ABDM_API={baseUrl:(window.ABDM_CONFIG&&window.ABDM_CONFIG.apiBaseUrl)||""};
window.ABDM_API.request=async function(path,options={},retry=true){
  const apiKey=localStorage.getItem("abdmApiKey");
  const r=await fetch(this.baseUrl.replace(/\/$/,"")+path,{...options,headers:{"Content-Type":"application/json",...(apiKey?{"X-API-Key":apiKey}:{}),...(options.headers||{})}});
  if(r.status===401&&retry&&!sessionStorage.getItem("abdmApiPrompted")){
    sessionStorage.setItem("abdmApiPrompted","1");
    const entered=prompt("ABDM API key:");
    if(entered&&entered.trim()){
      localStorage.setItem("abdmApiKey",entered.trim());
      return this.request(path,options,false);
    }
  }
  if(!r.ok)throw new Error(r.status+" "+r.statusText);
  const text=await r.text();
  if(!text)return null;
  try{return JSON.parse(text)}catch{return text}
};
window.ABDM_API.ping=async function(){return this.request("/ping",{method:"POST"})};
window.ABDM_API.queues=async function(){return this.request("/queues")};
window.ABDM_API.downloads=async function(){return this.request("/downloads")};
window.ABDM_API.add=async function(payload){
  const urls=payload.urls||[];
  return Promise.all(urls.map((link,index)=>this.request("/start-headless-download",{
    method:"POST",
    body:JSON.stringify({downloadSource:{link:link},name:payload.names?.[index]||null,folder:payload.folder||null,queueId:(payload.queueId===null||payload.queueId===undefined||payload.queueId==="")?null:Number(payload.queueId),categoryId:(payload.categoryId===null||payload.categoryId===undefined||payload.categoryId==="")?null:Number(payload.categoryId),startDownload:payload.queueId===null||payload.queueId===undefined||payload.queueId==="",startQueue:payload.queueId!==null&&payload.queueId!==undefined&&payload.queueId!==""})
  })));
};
window.ABDM_API.control=async function(id,action,removeFile=false){return this.request("/downloads/"+encodeURIComponent(id)+"/"+action+(action==="remove"?"?removeFile="+removeFile:""),{method:"POST"})};
window.ABDM_API.queueControl=async function(id,action){return this.request("/queues/"+encodeURIComponent(id)+"/"+action,{method:"POST"})};

window.ABDM_API.createQueue=async function(name){return this.request("/queues",{method:"POST",body:JSON.stringify({name})})};
window.ABDM_API.renameQueue=async function(id,name){return this.request("/queues/"+encodeURIComponent(id)+"/rename",{method:"POST",body:JSON.stringify({name})})};
window.ABDM_API.deleteQueue=async function(id){return this.request("/queues/"+encodeURIComponent(id)+"/delete",{method:"POST"})};
window.ABDM_API.queueConcurrency=async function(id,maxConcurrent){return this.request("/queues/"+encodeURIComponent(id)+"/concurrency",{method:"POST",body:JSON.stringify({maxConcurrent})})};
window.ABDM_API.assignQueue=async function(id,queueId){return this.request("/downloads/"+encodeURIComponent(id)+"/queue",{method:"POST",body:JSON.stringify({queueId})})};
window.ABDM_API.moveQueueItem=async function(id,direction){return this.request("/downloads/"+encodeURIComponent(id)+"/move",{method:"POST",body:JSON.stringify({direction})})};

window.ABDM_API.unqueue=async function(id){return this.request("/downloads/"+encodeURIComponent(id)+"/unqueue",{method:"POST"})};

window.ABDM_API.browser=async function(path){return this.request("/browser"+(path?("?path="+encodeURIComponent(path)):""))};

window.ABDM_API.categories=async function(){return this.request("/categories")};
window.ABDM_API.createCategory=async function(data){return this.request("/categories",{method:"POST",body:JSON.stringify(data)})};
window.ABDM_API.renameCategory=async function(id,name){return this.request("/categories/"+encodeURIComponent(id)+"/rename",{method:"POST",body:JSON.stringify({name})})};
window.ABDM_API.deleteCategory=async function(id){return this.request("/categories/"+encodeURIComponent(id)+"/delete",{method:"POST"})};

window.ABDM_API.queueSchedule=async function(id,data){return this.request("/queues/"+encodeURIComponent(id)+"/schedule",{method:"POST",body:JSON.stringify(data)})};

window.ABDM_API.settings=async function(){return this.request("/settings")};
window.ABDM_API.updateSettings=async function(settings,apiKey){return this.request("/settings",{method:"POST",body:JSON.stringify({settings,apiKey:apiKey||null})})};

window.ABDM_API.updateDownload=async function(id,data){return this.request("/downloads/"+encodeURIComponent(id),{method:"POST",body:JSON.stringify(data)})};
window.ABDM_API.inspectLinks=async function(text){return this.request("/import-links",{method:"POST",body:JSON.stringify({text})})};

window.ABDM_API.downloadParts=async function(id){return this.request("/downloads/"+encodeURIComponent(id)+"/parts")};
window.ABDM_API.browserSession=async function(){return this.request("/browser/session")};
