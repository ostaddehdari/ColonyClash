import { Injectable, NotFoundException } from '@nestjs/common';
import { GameEngine } from './game-engine';
import { ColorWar10Engine } from './engines/color-war-10.engine';
import { NumberGrab10Engine } from './engines/number-grab-10.engine';
import { TreasureFlip10Engine } from './engines/treasure-flip-10.engine';

@Injectable()
export class GameRegistryService{
  private readonly engines=new Map<string,GameEngine>();
  constructor(){ [new ColorWar10Engine(),new NumberGrab10Engine(),new TreasureFlip10Engine()].forEach(e=>this.engines.set(e.code,e)); }
  get(code:string){ const e=this.engines.get(code); if(!e) throw new NotFoundException('game_not_found'); return e; }
  list(){ return [...this.engines.values()].map(e=>({gameCode:e.code,version:e.version,minPlayers:e.minPlayers,maxPlayers:e.maxPlayers,maxActions:e.maxActions,supports:['live','async']})); }
}
