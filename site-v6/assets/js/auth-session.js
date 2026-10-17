/*
 * Offline sign-in preview, not production security.
 * Data source: demo accounts in assets/js/data.js; backend later handles
 * real HttpOnly/Secure cookies, idle timeout and persistent login rotation.
 * Persistent sessions are limited to 30 days; tab sessions to 12 hours.
 */
(function(root){
  'use strict';
  const KEYS={durable:'copimine.studio.remember.v5',temporary:'copimine.studio.tab.v5'};
  const DAY=86400000, HOUR=3600000;
  function begin(id,remember=false,now=Date.now()){
    if(typeof id!=='string'||!id)throw Error('Invalid demo account');
    return {id,remember:remember===true,createdAt:now,expiresAt:now+(remember?30*DAY:12*HOUR)};
  }
  function clear(durable,temporary){
    try{durable?.removeItem(KEYS.durable);}catch{}
    try{temporary?.removeItem(KEYS.temporary);}catch{}
  }
  function persist(durable,temporary,session){
    if(!session?.id){clear(durable,temporary);return;}
    const key=session.remember===true?KEYS.durable:KEYS.temporary;
    const target=session.remember===true?durable:temporary;
    if(session.remember===true){try{temporary?.removeItem(KEYS.temporary);}catch{}}
    else {try{durable?.removeItem(KEYS.durable);}catch{}}
    try{target?.setItem(key,JSON.stringify(session));}catch{}
  }
  function restore(durable,temporary,players,now=Date.now()){
    let session=null;
    for(const [store,key] of [[temporary,KEYS.temporary],[durable,KEYS.durable]]){
      let parsed;
      try{parsed=JSON.parse(store?.getItem(key)||'null');}catch{parsed=null;}
      if(!parsed)continue;
      const valid=typeof parsed.id==='string'&&Number.isFinite(parsed.createdAt)&&Number.isFinite(parsed.expiresAt)
        && parsed.createdAt<=now+60000&&parsed.expiresAt>now&&parsed.expiresAt>parsed.createdAt
        && (parsed.remember===true?key===KEYS.durable:key===KEYS.temporary)
        && parsed.expiresAt-parsed.createdAt<=(parsed.remember===true?30*DAY:12*HOUR)
        && Array.isArray(players)&&players.some(p=>p.id===parsed.id&&p.status==='active');
      if(valid&&!session){session=parsed;}
      else if(!valid){try{store?.removeItem(key);}catch{}}
    }
    return session;
  }
  function stripSession(state){return {...state,session:null};}
  const api={KEYS,begin,clear,persist,restore,stripSession};
  if(typeof module!=='undefined'&&module.exports)module.exports=api;
  else root.CMAuthSession=api;
})(globalThis);
