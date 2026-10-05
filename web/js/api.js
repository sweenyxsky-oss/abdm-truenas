window.ABDM_API={baseUrl:(window.ABDM_CONFIG&&window.ABDM_CONFIG.apiBaseUrl)||"/api"};
window.ABDM_API.request=async function(path,options={}){
  const r=await fetch(this.baseUrl.replace(/\/$/,"")+path,{...options,headers:{"Content-Type":"application/json",...(options.headers||{})}});
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
  return Promise.all(urls.map(link=>this.request("/start-headless-download",{
    method:"POST",
    body:JSON.stringify({downloadSource:{link:link},folder:payload.folder||null,queueId:payload.queueId||null,startDownload:true,startQueue:false})
  })));
};
window.ABDM_API.control=async function(id,action,removeFile=false){return this.request("/downloads/"+encodeURIComponent(id)+"/"+action+(action==="remove"?"?removeFile="+removeFile:""),{method:"POST"})};
window.ABDM_API.queueControl=async function(id,action){return this.request("/queues/"+encodeURIComponent(id)+"/"+action,{method:"POST"})};
