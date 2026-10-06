export type GamePlayer = { userId:string; slot:number };
export type GameContext = { seed:number; players:GamePlayer[]; maxActions:number };
export type ApplyContext = GameContext & { actorUserId:string; actionCount:number };

export interface GameEngine<State=any,Action=any> {
  readonly code:string;
  readonly version:number;
  readonly minPlayers:number;
  readonly maxPlayers:number;
  readonly maxActions:number;
  initialState(ctx:GameContext):State;
  validateAction(state:State,action:Action,ctx:ApplyContext):void;
  applyAction(state:State,action:Action,ctx:ApplyContext):State;
  score(state:State,ctx:GameContext):Record<string,number>;
  isFinished(state:State,ctx:ApplyContext):boolean;
  publicState(state:State,viewerUserId:string,ctx:GameContext):unknown;
}
