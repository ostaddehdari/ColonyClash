import type { ApplyContext, GameContext, GameEngine } from '../game-engine';
type State={values:number[][];owners:number[][];turnSlot:number};
type Action={type:'pick';row:number;col:number};
function seeded(seed:number){ let x=seed>>>0; return ()=>{ x=(Math.imul(1664525,x)+1013904223)>>>0; return x/4294967296; }; }
export class NumberGrab10Engine implements GameEngine<State,Action>{
 readonly code='number_grab_10'; readonly version=1; readonly minPlayers=2; readonly maxPlayers=2; readonly maxActions=10;
 initialState(ctx:GameContext):State{ const r=seeded(ctx.seed); const values=Array.from({length:4},()=>Array.from({length:4},()=>1+Math.floor(r()*9))); return {values,owners:Array.from({length:4},()=>Array(4).fill(0)),turnSlot:1}; }
 validateAction(state:State,a:Action,ctx:ApplyContext){ if(!a||a.type!=='pick')throw Error('invalid_action_type'); if(!Number.isInteger(a.row)||!Number.isInteger(a.col)||a.row<0||a.row>3||a.col<0||a.col>3)throw Error('invalid_cell'); const p=ctx.players.find(x=>x.userId===ctx.actorUserId); if(!p)throw Error('not_a_player'); if(p.slot!==state.turnSlot)throw Error('not_your_turn'); if(state.owners[a.row][a.col]!==0)throw Error('cell_taken'); if(ctx.actionCount>=ctx.maxActions)throw Error('match_finished'); }
 applyAction(state:State,a:Action,ctx:ApplyContext){ this.validateAction(state,a,ctx); const p=ctx.players.find(x=>x.userId===ctx.actorUserId)!; const owners=state.owners.map(r=>[...r]); owners[a.row][a.col]=p.slot; return {...state,owners,turnSlot:p.slot===1?2:1}; }
 score(s:State,ctx:GameContext){ const out:Record<string,number>={}; for(const p of ctx.players){let n=0; for(let r=0;r<4;r++)for(let c=0;c<4;c++)if(s.owners[r][c]===p.slot)n+=s.values[r][c];out[p.userId]=n;} return out; }
 isFinished(s:State,ctx:ApplyContext){ return ctx.actionCount>=ctx.maxActions || !s.owners.some(r=>r.includes(0)); }
 publicState(s:State,_v:string,ctx:GameContext){return {...s,scores:this.score(s,ctx)};}
}
