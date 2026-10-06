import type { ApplyContext, GameContext, GameEngine } from '../game-engine';
type State={values:number[][];owners:number[][];turnSlot:number;last?:{row:number;col:number;value:number;slot:number}};
type Action={type:'flip';row:number;col:number};
function seeded(seed:number){ let x=(seed^0x9e3779b9)>>>0; return ()=>{ x=(Math.imul(1103515245,x)+12345)>>>0; return x/4294967296; }; }
export class TreasureFlip10Engine implements GameEngine<State,Action>{
 readonly code='treasure_flip_10'; readonly version=1; readonly minPlayers=2; readonly maxPlayers=2; readonly maxActions=10;
 initialState(ctx:GameContext):State{ const r=seeded(ctx.seed); const pool=[-2,-1,1,1,2,2,3,3,4,5]; const values=Array.from({length:4},()=>Array.from({length:4},()=>pool[Math.floor(r()*pool.length)])); return {values,owners:Array.from({length:4},()=>Array(4).fill(0)),turnSlot:1}; }
 validateAction(state:State,a:Action,ctx:ApplyContext){ if(!a||a.type!=='flip')throw Error('invalid_action_type'); if(!Number.isInteger(a.row)||!Number.isInteger(a.col)||a.row<0||a.row>3||a.col<0||a.col>3)throw Error('invalid_cell'); const p=ctx.players.find(x=>x.userId===ctx.actorUserId); if(!p)throw Error('not_a_player'); if(p.slot!==state.turnSlot)throw Error('not_your_turn'); if(state.owners[a.row][a.col]!==0)throw Error('cell_taken'); if(ctx.actionCount>=ctx.maxActions)throw Error('match_finished'); }
 applyAction(state:State,a:Action,ctx:ApplyContext){ this.validateAction(state,a,ctx); const p=ctx.players.find(x=>x.userId===ctx.actorUserId)!; const owners=state.owners.map(r=>[...r]); owners[a.row][a.col]=p.slot; return {...state,owners,turnSlot:p.slot===1?2:1,last:{row:a.row,col:a.col,value:state.values[a.row][a.col],slot:p.slot}}; }
 score(s:State,ctx:GameContext){ const out:Record<string,number>={}; for(const p of ctx.players){let n=0; for(let r=0;r<4;r++)for(let c=0;c<4;c++)if(s.owners[r][c]===p.slot)n+=s.values[r][c];out[p.userId]=n;} return out; }
 isFinished(s:State,ctx:ApplyContext){ return ctx.actionCount>=ctx.maxActions || !s.owners.some(r=>r.includes(0)); }
 publicState(s:State,_v:string,ctx:GameContext){ const cells=s.owners.map((row,r)=>row.map((owner,c)=>({owner,value:owner?s.values[r][c]:null}))); return {cells,turnSlot:s.turnSlot,last:s.last,scores:this.score(s,ctx)}; }
}
