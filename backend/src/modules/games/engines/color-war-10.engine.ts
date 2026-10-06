import type { ApplyContext, GameContext, GameEngine } from '../game-engine';

type Cell=0|1|2;
type State={board:Cell[][];turnSlot:number;lastMove?:{slot:number;row:number;col:number;captured:number}};
type Action={type:'place';row:number;col:number};

export class ColorWar10Engine implements GameEngine<State,Action>{
  readonly code='color_war_10';
  readonly version=1;
  readonly minPlayers=2;
  readonly maxPlayers=2;
  readonly maxActions=10;

  initialState(_ctx:GameContext):State{
    const board:Array<Cell[]> = Array.from({length:5},()=>Array<Cell>(5).fill(0));
    board[0][0]=1; board[4][4]=2;
    return {board,turnSlot:1};
  }

  validateAction(state:State,action:Action,ctx:ApplyContext){
    if(!action || action.type!=='place') throw new Error('invalid_action_type');
    if(!Number.isInteger(action.row)||!Number.isInteger(action.col)||action.row<0||action.row>4||action.col<0||action.col>4) throw new Error('invalid_cell');
    const p=ctx.players.find(x=>x.userId===ctx.actorUserId);
    if(!p) throw new Error('not_a_player');
    if(p.slot!==state.turnSlot) throw new Error('not_your_turn');
    if(state.board[action.row][action.col]!==0) throw new Error('cell_not_empty');
    if(ctx.actionCount>=ctx.maxActions) throw new Error('match_finished');
  }

  applyAction(state:State,action:Action,ctx:ApplyContext):State{
    this.validateAction(state,action,ctx);
    const p=ctx.players.find(x=>x.userId===ctx.actorUserId)!;
    const mine=p.slot as 1|2;
    const enemy=(mine===1?2:1) as 1|2;
    const board=state.board.map(r=>[...r]) as Cell[][];
    board[action.row][action.col]=mine;
    let captured=0;
    const dirs=[[1,0],[-1,0],[0,1],[0,-1]];
    for(const [dr,dc] of dirs){
      const r=action.row+dr,c=action.col+dc;
      if(r<0||r>4||c<0||c>4||board[r][c]!==enemy) continue;
      let friendly=0;
      for(const [er,ec] of dirs){
        const rr=r+er,cc=c+ec;
        if(rr>=0&&rr<5&&cc>=0&&cc<5&&board[rr][cc]===mine) friendly++;
      }
      if(friendly>=2){ board[r][c]=mine; captured++; }
    }
    return {board,turnSlot:mine===1?2:1,lastMove:{slot:mine,row:action.row,col:action.col,captured}};
  }

  score(state:State,ctx:GameContext){
    const out:Record<string,number>={};
    for(const p of ctx.players) out[p.userId]=state.board.flat().filter(x=>x===p.slot).length;
    return out;
  }

  isFinished(state:State,ctx:ApplyContext){
    return ctx.actionCount>=ctx.maxActions || !state.board.some(r=>r.includes(0));
  }

  publicState(state:State,_viewer:string,ctx:GameContext){ return {...state,scores:this.score(state,ctx)}; }
}
