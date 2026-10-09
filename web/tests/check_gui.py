import asyncio,json
from pathlib import Path
from playwright.async_api import async_playwright,expect
ROOT=Path(__file__).parent
async def main():
 async with async_playwright() as p:
  browser=await p.chromium.launch(headless=True)
  context=await browser.new_context(viewport={'width':1280,'height':1800})
  page=await context.new_page();errors=[];requests=[]
  page.on('pageerror',lambda e:errors.append(str(e)))
  downloads=[{'id':1,'name':'TrueNAS-SCALE-26.iso','size':2400000000,'percent':43,'speed':12500000,'eta':90,'status':'Downloading','queueId':0,'queueName':'Main','categoryId':1,'dateAdded':1791536400000,'downloadLink':'https://example.com/file.iso','folder':'/downloads'}, {'id':2,'name':'ABDM-release.zip','size':85000000,'percent':100,'speed':0,'eta':0,'status':'Completed','categoryId':2,'dateAdded':1791536100000}, {'id':3,'name':'Music.flac','size':45000000,'percent':14,'speed':0,'eta':-1,'status':'Paused','categoryId':3,'dateAdded':1791536000000}]
  queues=[{'id':0,'name':'Main','active':1,'queued':1,'total':2,'running':True,'items':[1],'activeDays':['MONDAY'],'schedulerEnabled':False}]
  categories=[{'id':i,'name':name,'acceptedFileTypes':[ext]} for i,(name,ext) in enumerate([('Applications','iso'),('Compressed','zip'),('Music','mp3'),('Videos','mp4'),('Documents','pdf'),('Pictures','png')],1)]
  async def handler(route):
   path=route.request.url.split('8091')[-1].split('?')[0];requests.append((route.request.method,path))
   if path=='/ping':data='pong'
   elif path=='/downloads':data=downloads
   elif path=='/queues':data=queues
   elif path=='/categories':data=categories
   elif path=='/settings':data={'downloadFolder':'/downloads','apiEnabled':True}
   elif path=='/browser/session':data={'enabled':True,'ready':True,'token':'test-token'}
   elif path.endswith('/parts'):data=[]
   else:data={}
   await route.fulfill(status=200,content_type='application/json',body=json.dumps(data))
  await page.route('**/*',lambda r:r.continue_() if '/js/' in r.request.url or '/css/' in r.request.url or '/assets/' in r.request.url or r.request.url.endswith(':8091/') else handler(r))
  await page.goto('http://localhost:8091/',wait_until='networkidle');await page.screenshot(path=str(ROOT/'desktop.png'))
  assert await page.locator('.file-name').count()==3
  await page.get_by_role('button',name='Finished',exact=True).click();assert await page.locator('.file-name').count()==1
  await page.get_by_role('button',name='All',exact=True).click()
  await page.get_by_role('textbox',name='Search downloads',exact=True).fill('TrueNAS');assert await page.locator('.file-name').count()==1
  await page.wait_for_timeout(1700);assert await page.get_by_role('textbox',name='Search downloads',exact=True).input_value()=='TrueNAS'
  await page.get_by_role('textbox',name='Search downloads',exact=True).fill('')
  await page.get_by_role('checkbox',name='Select TrueNAS-SCALE-26.iso',exact=True).check()
  await page.get_by_role('button',name='Pause',exact=True).click();assert ('POST','/downloads/1/pause') in requests
  await page.get_by_role('button',name='Add URL',exact=True).click();await expect(page.locator('#addDialog')).to_be_visible()
  await page.screenshot(path=str(ROOT/'add-dialog.png'))
  await page.locator('#urlInput').fill('https://example.com/test.zip');await page.get_by_role('button',name='Add download',exact=True).click();assert ('POST','/start-headless-download') in requests
  await page.get_by_role('button',name='TrueNAS-SCALE-26.iso',exact=True).click();await expect(page.locator('#detailsDialog')).to_be_visible();await page.locator('#detailsDialog').get_by_role('button',name='Close',exact=True).click()
  for view in ['queue','categories','scheduler','settings','browser']:
   await page.evaluate('(v)=>navigateTo(v)',view);await page.screenshot(path=str(ROOT/(view+'.png')))
  await page.get_by_role('button',name='Restart Firefox',exact=True).click();assert any(path=='/browser/restart' for _,path in requests)
  await page.evaluate("navigateTo('dashboard')")
  await page.get_by_role('button',name='Switch theme').click();await page.screenshot(path=str(ROOT/'light.png'))
  await page.get_by_role('button',name='Switch theme').click()
  await page.set_viewport_size({'width':390,'height':844});await page.screenshot(path=str(ROOT/'phone.png'))
  dims=await page.locator('.home-sidebar').evaluate('(el)=>({width:el.clientWidth,scroll:el.scrollWidth,height:el.clientHeight})');assert dims['scroll']>dims['width'];assert dims['height']<90
  await page.locator('.home-sidebar').evaluate('(el)=>el.scrollLeft=150');await page.evaluate('render()');assert await page.locator('.home-sidebar').evaluate('(el)=>el.scrollLeft')==150
  assert await page.evaluate('document.documentElement.scrollWidth<=window.innerWidth')
  await page.set_viewport_size({'width':820,'height':1180});await page.screenshot(path=str(ROOT/'ipad.png'))
  assert await page.evaluate('document.documentElement.scrollWidth<=window.innerWidth')
  assert not errors,errors
  print('PASS: desktop, light theme, phone/iPad, horizontal filters, search, selection, pause, add, details, every view, Firefox restart; no runtime errors.')
  await browser.close()
asyncio.run(main())
