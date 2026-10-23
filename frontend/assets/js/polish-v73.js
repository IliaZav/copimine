/* Targeted v7.3 review polish; preserve the accepted v7.2 visual direction. */
(function(){
'use strict';
const M=CM,V=CMV4,X=CM6,A=CM71,Q=CMReview,E=M.esc;
window.CM73FeeSettings=()=>`<section class="panel cm73-fee-settings" data-search-target="transferFeePercent"><div class="panel-head"><div><h2>Комиссия переводов</h2><p class="hint">При 0 комиссия не упоминается в кабинете и квитанциях.</p></div>${A.help('Параметр локальной демонстрации. В рабочем проекте расчёт и округление должен возвращать EconomyCore.')}</div><form data-form="v73-fee"><label class="field">Процент<input type="number" name="transferFeePercent" value="${Number(M.state.config.transferFeePercent||0)}" min="0" max="100" step="0.01" required inputmode="decimal"></label><button type="submit" class="btn primary">Сохранить</button><div class="form-error" role="alert"></div></form></section>`;
M.forms['v73-fee']=async(f,d)=>{const percent=Number(d.get('transferFeePercent'));if(!Number.isFinite(percent)||percent<0||percent>100)throw Error('Укажи процент от 0 до 100.');M.state.config.transferFeePercent=percent;M.audit('Комиссия тестовых переводов',percent+'%','Локальные тестовые АР');M.save();await Q.flush();await A.refreshWallet(true);M.toast('Настройка сохранена для локальной копии.');};
// Quantity controls use one validator in product cards and in the basket.
M.handlers['qty-plus']=M.handlers['v6-cart-plus'];M.handlers['qty-minus']=M.handlers['v6-cart-minus'];
// The new visual inventory has both containers. Keep old links routable, but not
// two near-identical cards that open exactly the same editor.
const sourceActions=X.actions;X.actions=sourceActions.filter((a,i,list)=>a.id!=='ender.remove'&&list.findIndex(b=>b.id===a.id)===i);
const inv=X.actions.find(a=>a.id==='inventory.remove');if(inv){inv.label='Инвентарь и эндер-сундук';inv.group='Инвентари';}
// A contextual shortcut always leads to the same recovery workflow.
const originalAction=M.handlers['v6-player-action'];M.handlers['v6-player-action']=(b,e)=>{if(b.dataset.id==='sessions.revoke'){M.navTo('admin/recovery?player='+encodeURIComponent(b.dataset.player||X.player()?.id||'demo-001')+'&focus=sessions.revoke');return;}return originalAction(b,e);};
V.after.push((page,tab)=>{
 document.querySelectorAll('.v7-reviewbar,.v7-review-bar,.v7-review-strip').forEach(bar=>{for(const n of bar.querySelectorAll('span,small'))if(n.textContent.trim()==='v7.2')n.textContent='v7.3';});
});
})();
