/* Small test driver using the existing Edge and Node WebSocket APIs. No runtime install. */
const fs=require('fs'),path=require('path'),http=require('http'),child=require('child_process');
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function freePort(){const server=http.createServer();await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const port=server.address().port;await new Promise(resolve=>server.close(resolve));return port;}
class Protocol{
 constructor(ws){this.ws=ws;this.next=0;this.pending=new Map();this.listeners=new Map();ws.addEventListener('message',event=>{const message=JSON.parse(event.data);if(message.id){const promise=this.pending.get(message.id);if(!promise)return;this.pending.delete(message.id);message.error?promise.reject(new Error(message.error.message)):promise.resolve(message.result);}else for(const listener of this.listeners.get(message.method)||[])listener(message.params);});}
 send(method,params={}){return new Promise((resolve,reject)=>{const id=++this.next;const timeout=setTimeout(()=>{this.pending.delete(id);reject(new Error('CDP timeout: '+method));},10000);this.pending.set(id,{resolve:value=>{clearTimeout(timeout);resolve(value);},reject:error=>{clearTimeout(timeout);reject(error);}});this.ws.send(JSON.stringify({id,method,params}));});}
 on(method,listener){if(!this.listeners.has(method))this.listeners.set(method,[]);this.listeners.get(method).push(listener);}
}
class Locator{
 constructor(page,expression){this.page=page;this.expression=expression;}
 childExpression(){return `(${this.expression})`;}
 async evaluate(fn,...args){return this.page.evaluate(`(${fn.toString()})(${this.childExpression()},...${JSON.stringify(args)})`);}
 first(){return new Locator(this.page,this.expression);}
 locator(selector){return new Locator(this.page,`(${this.expression})?.querySelector(${JSON.stringify(selector)})`);}
 getByRole(role,options={}){return this.page.role(role,options,this.expression);}
 async click(){const result=await this.evaluate(element=>{if(!element)throw new Error('Element missing');if(element.disabled)throw new Error('Element disabled');element.click();});await this.page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));await delay(180);return result;}
 async fill(value){return this.evaluate((element,value)=>{if(!element)throw new Error('Element missing');element.value=value;element.dispatchEvent(new Event('input',{bubbles:true}));element.dispatchEvent(new Event('change',{bubbles:true}));},value);}
 async check(){return this.evaluate(element=>{if(!element.checked)element.click();});}
 async uncheck(){return this.evaluate(element=>{if(element.checked)element.click();});}
 async textContent(){return this.evaluate(element=>element?.textContent);}
 async isHidden(){return this.evaluate(element=>!element||element.hidden||!element.getClientRects().length);}
 async count(){return this.evaluate(element=>element?1:0);}
 async waitFor(options={}){const hidden=options.state==='hidden';const start=Date.now();while(Date.now()-start<10000){if(await this.isHidden()===hidden)return;await delay(50);}throw new Error('Wait timeout: '+this.expression);}
 async setInputFiles(file){const target=path.resolve(this.page.browser.directory,file.name);fs.writeFileSync(target,file.buffer);const result=await this.page.cdp.send('Runtime.evaluate',{expression:this.expression});if(!result.result.objectId)throw new Error('File input missing');await this.page.cdp.send('DOM.setFileInputFiles',{objectId:result.result.objectId,files:[target]});}
}
class Page{
 constructor(browser,cdp){this.browser=browser;this.cdp=cdp;this.routeHandler=null;}
 async init(){await this.cdp.send('Page.enable');await this.cdp.send('Runtime.enable');}
 async evaluate(fn,...args){const expression=typeof fn==='string'?fn:`(${fn.toString()})(...${JSON.stringify(args)})`;const result=await this.cdp.send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(result.exceptionDetails)throw new Error(result.exceptionDetails.exception?.description||result.exceptionDetails.text);return result.result.value;}
 locator(selector){return new Locator(this,`document.querySelector(${JSON.stringify(selector)})`);}
 role(role,options={},scope='document'){
  const selector={button:'button',heading:'h1,h2,h3,h4',checkbox:'input[type="checkbox"]'}[role];if(!selector)throw new Error('Unsupported test role');
  return new Locator(this,`[...(${scope})?.querySelectorAll(${JSON.stringify(selector)})||[]].find(element=>{const name=element.getAttribute('aria-label')||(${JSON.stringify(role)}==='checkbox'?element.closest('label')?.textContent:element.textContent)||'';return ${options.exact?`name.trim()===${JSON.stringify(options.name)}`:`name.includes(${JSON.stringify(options.name||'')})`}})`);
 }
 getByRole(role,options={}){return this.role(role,options);}
 getByText(text,options={}){return new Locator(this,`[...document.querySelectorAll('p,h1,h2,h3,span')].find(element=>${options.exact?`element.textContent===${JSON.stringify(text)}`:`element.textContent.includes(${JSON.stringify(text)})`})`);}
 on(event,listener){if(event==='pageerror')this.cdp.on('Runtime.exceptionThrown',params=>listener(new Error(params.exceptionDetails.exception?.description||params.exceptionDetails.text)));}
 async route(pattern,handler){this.routeHandler=handler;await this.cdp.send('Fetch.enable',{patterns:[{urlPattern:'*/api/*',requestStage:'Request'}]});this.cdp.on('Fetch.requestPaused',async params=>{try{const request=params.request;const headers={};for(const [key,value]of Object.entries(request.headers))headers[key.toLowerCase()]=value;await handler({request:()=>({url:()=>request.url,method:()=>request.method,headers:()=>headers,postDataJSON:()=>request.postData?JSON.parse(request.postData):null}),fulfill:async({status,contentType,body})=>this.cdp.send('Fetch.fulfillRequest',{requestId:params.requestId,responseCode:status,responseHeaders:[{name:'Content-Type',value:contentType}],body:Buffer.from(body).toString('base64')})});}catch(error){await this.cdp.send('Fetch.failRequest',{requestId:params.requestId,errorReason:'Failed'});}});}
 async goto(url){await this.cdp.send('Page.navigate',{url});const start=Date.now();while(Date.now()-start<10000){if(await this.evaluate(()=>document.readyState==='complete'))return;await delay(50);}throw new Error('Navigation timeout');}
 async setViewportSize(size){await this.cdp.send('Emulation.setDeviceMetricsOverride',{...size,deviceScaleFactor:1,mobile:false});}
 async screenshot(options){const result=await this.cdp.send('Page.captureScreenshot',{format:'png',captureBeyondViewport:!!options.fullPage});fs.writeFileSync(options.path,Buffer.from(result.data,'base64'));}
}
module.exports={chromium:{launch:async options=>{
 const port=await freePort(),directory=path.resolve(__dirname,'../build/lan-web-checks/browser-profile-'+Date.now());fs.mkdirSync(directory,{recursive:true});
 const process=child.spawn(options.executablePath,['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--disable-extensions',`--remote-debugging-port=${port}`,`--user-data-dir=${directory}`,'about:blank'],{windowsHide:true,stdio:'ignore'});
 let tabs;for(let i=0;i<100;i++){try{tabs=await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();if(tabs.some(tab=>tab.type==='page'))break;}catch{}await delay(100);}if(!tabs)throw new Error('Edge debugging endpoint unavailable');
 const ws=new WebSocket(tabs.find(tab=>tab.type==='page').webSocketDebuggerUrl);await new Promise((resolve,reject)=>{ws.addEventListener('open',resolve,{once:true});ws.addEventListener('error',reject,{once:true});});
 const protocol=new Protocol(ws);
 const browser={directory,newPage:async({viewport})=>{const page=new Page(browser,protocol);await page.init();await page.setViewportSize(viewport);return page;},close:async()=>{try{await protocol.send('Browser.close');}catch{}ws.close();process.kill();}};
 return browser;
}}};
