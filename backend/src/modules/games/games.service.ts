import { BadRequestException, ForbiddenException, Injectable, NotFoundException } from '@nestjs/common';
import { createHash, randomBytes, randomInt } from 'node:crypto';
import { DbService } from '../../db/db.service';
import { GameRegistryService } from './game-registry.service';
import type { PoolClient } from 'pg';

@Injectable()
export class GamesService{
  constructor(private readonly db:DbService,private readonly registry:GameRegistryService){}
  catalog(){ return this.registry.list(); }

  async create(userId:string,b:{gameCode:string;mode?:'live'|'async';opponentUserId?:string;challengeId?:string;rematchOf?:string;matchmakingTicketId?:string;regionCode?:string}){
    const engine=this.registry.get(b.gameCode);
    const mode=b.mode||'live';
    if(!['live','async'].includes(mode)) throw new BadRequestException('invalid_mode');
    if(b.opponentUserId===userId) throw new BadRequestException('self_match');
    return this.db.tx(async c=>{
      if(b.opponentUserId){ const uq=await c.query(`SELECT id FROM users WHERE id=$1 AND status='active'`,[b.opponentUserId]); if(!uq.rowCount) throw new BadRequestException('opponent_not_found'); }
      const seed=randomInt(1,2147483646);
      const players=[{userId,slot:1},...(b.opponentUserId?[{userId:b.opponentUserId,slot:2}]:[])];
      const state=players.length===2?engine.initialState({seed,players,maxActions:engine.maxActions}):{};
      const roomCode=randomBytes(6).toString('base64url').replace(/[-_]/g,'').slice(0,8).toUpperCase();
      const turnSeconds=mode==='live'?Number(process.env.LIVE_TURN_SECONDS||45):Number(process.env.ASYNC_TURN_SECONDS||86400);
      const matchSeconds=mode==='live'?Number(process.env.LIVE_MATCH_SECONDS||1200):Number(process.env.ASYNC_MATCH_SECONDS||604800);
      const q=await c.query(`INSERT INTO matches(game_code,mode,state,created_by,seed,game_version,max_actions,current_turn_user_id,game_state,challenge_id,rematch_of,matchmaking_ticket_id,room_code,region_code,last_activity_at,turn_deadline_at,expires_at)
        VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,now(),CASE WHEN $15 THEN now()+make_interval(secs=>$16) ELSE NULL END,now()+make_interval(secs=>$17)) RETURNING *`,[engine.code,mode,players.length===2?'active':'created',userId,seed,engine.version,engine.maxActions,players.length===2?userId:null,JSON.stringify(state),b.challengeId||null,b.rematchOf||null,b.matchmakingTicketId||null,roomCode,b.regionCode||'global',players.length===2,turnSeconds,matchSeconds]);
      const id=q.rows[0].id;
      const allUserIds=[userId,...(b.opponentUserId?[b.opponentUserId]:[])];
      await c.query(`INSERT INTO player_stats(user_id) SELECT unnest($1::uuid[]) ON CONFLICT DO NOTHING`,[allUserIds]);
      const ur=await c.query(`SELECT user_id,skill_rating FROM player_stats WHERE user_id=ANY($1::uuid[])`,[allUserIds]);
      const ratings=new Map(ur.rows.map((x:any)=>[x.user_id,Number(x.skill_rating||1000)]));
      await c.query(`INSERT INTO match_players(match_id,user_id,rating_before) VALUES($1,$2,$3)`,[id,userId,ratings.get(userId)||1000]);
      if(b.opponentUserId) await c.query(`INSERT INTO match_players(match_id,user_id,rating_before) VALUES($1,$2,$3)`,[id,b.opponentUserId,ratings.get(b.opponentUserId)||1000]);
      if(players.length===2) await c.query(`UPDATE matches SET started_at=now() WHERE id=$1`,[id]);
      return this.viewTx(c,id,userId);
    });
  }

  async join(userId:string,id:string){
    return this.db.tx(async c=>{
      const m=await c.query(`SELECT * FROM matches WHERE id=$1 FOR UPDATE`,[id]);
      if(!m.rowCount) throw new NotFoundException('match_not_found');
      const row=m.rows[0]; if(row.state!=='created') throw new BadRequestException('match_not_joinable');
      const pc=await c.query(`SELECT user_id FROM match_players WHERE match_id=$1 ORDER BY user_id`,[id]);
      if(pc.rows.some(x=>x.user_id===userId)) return this.viewTx(c,id,userId);
      if(pc.rowCount>=2) throw new BadRequestException('match_full');
      await c.query(`INSERT INTO player_stats(user_id) VALUES($1) ON CONFLICT DO NOTHING`,[userId]);
      const rr=await c.query(`SELECT skill_rating FROM player_stats WHERE user_id=$1`,[userId]);
      await c.query(`INSERT INTO match_players(match_id,user_id,rating_before) VALUES($1,$2,$3)`,[id,userId,Number(rr.rows[0]?.skill_rating||1000)]);
      const players=[{userId:row.created_by,slot:1},{userId,slot:2}];
      const engine=this.registry.get(row.game_code);
      const state=engine.initialState({seed:Number(row.seed),players,maxActions:Number(row.max_actions)});
      const turnSeconds=row.mode==='live'?Number(process.env.LIVE_TURN_SECONDS||45):Number(process.env.ASYNC_TURN_SECONDS||86400);
      await c.query(`UPDATE matches SET state='active',started_at=now(),last_activity_at=now(),current_turn_user_id=$2,game_state=$3,turn_deadline_at=now()+make_interval(secs=>$4) WHERE id=$1`,[id,row.created_by,JSON.stringify(state),turnSeconds]);
      return this.viewTx(c,id,userId);
    });
  }

  async view(userId:string,id:string){ return this.db.tx(c=>this.viewTx(c,id,userId)); }

  private async viewTx(c:PoolClient,id:string,userId:string){
    const m=await c.query(`SELECT * FROM matches WHERE id=$1`,[id]); if(!m.rowCount) throw new NotFoundException('match_not_found');
    const playersQ=await c.query(`SELECT mp.user_id,mp.score,mp.result,mp.rating_before,mp.rating_after,mp.rating_delta,u.username,u.display_name FROM match_players mp JOIN users u ON u.id=mp.user_id WHERE mp.match_id=$1 ORDER BY mp.user_id`,[id]);
    if(!playersQ.rows.some(x=>x.user_id===userId) && m.rows[0].state!=='finished') throw new ForbiddenException('not_a_player');
    const players=playersQ.rows.map((x:any,i:number)=>({userId:x.user_id,slot:x.user_id===m.rows[0].created_by?1:2,username:x.username,displayName:x.display_name,score:Number(x.score),result:x.result,ratingBefore:Number(x.rating_before||1000),ratingAfter:x.rating_after==null?null:Number(x.rating_after),ratingDelta:x.rating_delta==null?null:Number(x.rating_delta)})).sort((a:any,b:any)=>a.slot-b.slot);
    const engine=this.registry.get(m.rows[0].game_code);
    const ctx={seed:Number(m.rows[0].seed),players:players.map((p:any)=>({userId:p.userId,slot:p.slot})),maxActions:Number(m.rows[0].max_actions)};
    const game=m.rows[0].state==='created'?null:engine.publicState(m.rows[0].game_state,userId,ctx);
    return {id:m.rows[0].id,gameCode:m.rows[0].game_code,gameVersion:m.rows[0].game_version,mode:m.rows[0].mode,state:m.rows[0].state,actionCount:m.rows[0].action_count,maxActions:m.rows[0].max_actions,currentTurnUserId:m.rows[0].current_turn_user_id,players,game,result:m.rows[0].result_payload,rematchOf:m.rows[0].rematch_of,roomCode:m.rows[0].room_code,regionCode:m.rows[0].region_code,turnDeadlineAt:m.rows[0].turn_deadline_at,expiresAt:m.rows[0].expires_at,lastActivityAt:m.rows[0].last_activity_at};
  }

  async action(userId:string,id:string,b:{clientActionId:string;type:string;row?:number;col?:number}){
    if(!b.clientActionId||b.clientActionId.length>80) throw new BadRequestException('client_action_id_required');
    return this.db.tx(async c=>{
      const m=await c.query(`SELECT * FROM matches WHERE id=$1 FOR UPDATE`,[id]); if(!m.rowCount) throw new NotFoundException('match_not_found');
      const row=m.rows[0]; if(row.state!=='active') throw new BadRequestException('match_not_active');
      if(row.expires_at && new Date(row.expires_at).getTime()<=Date.now()) throw new BadRequestException('match_expired');
      if(row.turn_deadline_at && new Date(row.turn_deadline_at).getTime()<=Date.now()) throw new BadRequestException('turn_timeout');
      if(row.current_turn_user_id && row.current_turn_user_id!==userId) throw new ForbiddenException('not_your_turn');
      const dup=await c.query(`SELECT id FROM match_actions WHERE match_id=$1 AND user_id=$2 AND client_action_id=$3`,[id,userId,b.clientActionId]);
      if(dup.rowCount) return this.viewTx(c,id,userId);
      const pq=await c.query(`SELECT user_id FROM match_players WHERE match_id=$1 ORDER BY CASE WHEN user_id=$2 THEN 0 ELSE 1 END`,[id,row.created_by]);
      if(pq.rowCount!==2) throw new BadRequestException('need_two_players');
      const players=[{userId:row.created_by,slot:1},{userId:pq.rows.find((x:any)=>x.user_id!==row.created_by).user_id,slot:2}];
      const engine=this.registry.get(row.game_code);
      const nextCount=Number(row.action_count)+1;
      const ctx={seed:Number(row.seed),players,maxActions:Number(row.max_actions),actorUserId:userId,actionCount:Number(row.action_count)};
      const action={type:b.type,row:b.row,col:b.col};
      let next:any;
      try { next=engine.applyAction(row.game_state,action as any,ctx); }
      catch(e:any){ throw new BadRequestException(String(e?.message||'invalid_game_action')); }
      const stateHash=createHash('sha256').update(JSON.stringify(next)).digest('hex');
      await c.query(`INSERT INTO match_actions(match_id,seq,user_id,client_action_id,action_type,action_payload,state_hash) VALUES($1,$2,$3,$4,$5,$6,$7)`,[id,nextCount,userId,b.clientActionId,b.type,JSON.stringify(action),stateHash]);
      const done=engine.isFinished(next,{...ctx,actionCount:nextCount});
      const nextPlayer=players.find(p=>p.slot===(next as any).turnSlot)?.userId||null;
      const turnSeconds=row.mode==='live'?Number(process.env.LIVE_TURN_SECONDS||45):Number(process.env.ASYNC_TURN_SECONDS||86400);
      await c.query(`UPDATE matches SET action_count=$2,game_state=$3,current_turn_user_id=$4,last_activity_at=now(),turn_deadline_at=CASE WHEN $4::uuid IS NULL THEN NULL ELSE now()+make_interval(secs=>$5) END WHERE id=$1`,[id,nextCount,JSON.stringify(next),done?null:nextPlayer,turnSeconds]);
      if(done) await this.finish(c,row,players,next);
      return this.viewTx(c,id,userId);
    });
  }

  private elo(rating:number,opponent:number,actual:number){ const expected=1/(1+Math.pow(10,(opponent-rating)/400)); return Math.max(0,Math.round(rating+32*(actual-expected))); }

  private async settleRatingsAndStats(c:PoolClient,row:any,players:{userId:string;slot:number}[],results:Map<string,'win'|'loss'|'draw'>){
    const rs=await c.query(`SELECT mp.user_id,mp.rating_before FROM match_players mp WHERE mp.match_id=$1`,[row.id]);
    const before=new Map(rs.rows.map((x:any)=>[x.user_id,Number(x.rating_before||1000)]));
    for(const p of players){
      const other=players.find(x=>x.userId!==p.userId)!; const result=results.get(p.userId)!; const actual=result==='win'?1:result==='draw'?0.5:0;
      const after=this.elo(before.get(p.userId)||1000,before.get(other.userId)||1000,actual);
      await c.query(`UPDATE match_players SET rating_after=$3,rating_delta=$3-rating_before WHERE match_id=$1 AND user_id=$2`,[row.id,p.userId,after]);
      await c.query(`INSERT INTO player_stats(user_id) VALUES($1) ON CONFLICT DO NOTHING`,[p.userId]);
      const won=result==='win', draw=result==='draw'; const xp=won?30:draw?20:10;
      await c.query(`UPDATE player_stats SET wins=wins+$2,losses=losses+$3,draws=draws+$4,win_streak=CASE WHEN $2=1 THEN win_streak+1 ELSE 0 END,best_win_streak=GREATEST(best_win_streak,CASE WHEN $2=1 THEN win_streak+1 ELSE best_win_streak END),xp=xp+$5,skill_rating=$6,updated_at=now() WHERE user_id=$1`,[p.userId,won?1:0,!won&&!draw?1:0,draw?1:0,xp,after]);
      const coin=won?40:draw?20:10; await this.grantTx(c,p.userId,'coin',coin,'match_reward','match',row.id,`match:${row.id}:coin:${p.userId}`);
    }
  }

  private async finish(c:PoolClient,row:any,players:{userId:string;slot:number}[],state:any){
    const engine=this.registry.get(row.game_code);
    const scores=engine.score(state,{seed:Number(row.seed),players,maxActions:Number(row.max_actions)});
    const vals=players.map(p=>({userId:p.userId,score:scores[p.userId]||0}));
    const best=Math.max(...vals.map(x=>x.score)); const winners=vals.filter(x=>x.score===best);
    const results=new Map<string,'win'|'loss'|'draw'>();
    for(const v of vals){ const result:'win'|'loss'|'draw'=winners.length>1?'draw':v.score===best?'win':'loss'; results.set(v.userId,result); await c.query(`UPDATE match_players SET score=$3,result=$4 WHERE match_id=$1 AND user_id=$2`,[row.id,v.userId,v.score,result]); }
    const resultPayload={scores,winners:winners.map(x=>x.userId),reason:'completed'};
    await c.query(`UPDATE matches SET state='finished',finished_at=now(),last_activity_at=now(),current_turn_user_id=NULL,turn_deadline_at=NULL,result_payload=$2 WHERE id=$1`,[row.id,JSON.stringify(resultPayload)]);
    await this.settleRatingsAndStats(c,row,players,results);
    await c.query(`UPDATE matches SET reward_settled_at=now() WHERE id=$1 AND reward_settled_at IS NULL`,[row.id]);
  }

  async sweepTimeouts(){
    const q=await this.db.query(`SELECT id FROM matches WHERE state='active' AND ((turn_deadline_at IS NOT NULL AND turn_deadline_at<=now()) OR (expires_at IS NOT NULL AND expires_at<=now())) ORDER BY COALESCE(turn_deadline_at,expires_at) LIMIT 50`);
    let finished=0; for(const x of q.rows){ if(await this.forfeitTimeout(x.id)) finished++; } return {finished};
  }

  private async forfeitTimeout(id:string){
    return this.db.tx(async c=>{
      const mq=await c.query(`SELECT * FROM matches WHERE id=$1 FOR UPDATE`,[id]); if(!mq.rowCount||mq.rows[0].state!=='active') return false; const row=mq.rows[0];
      const timedOut=(row.turn_deadline_at&&new Date(row.turn_deadline_at).getTime()<=Date.now())||(row.expires_at&&new Date(row.expires_at).getTime()<=Date.now()); if(!timedOut) return false;
      const pq=await c.query(`SELECT user_id FROM match_players WHERE match_id=$1`,[id]); if(pq.rowCount!==2){ await c.query(`UPDATE matches SET state='cancelled',finished_at=now(),timeout_reason='incomplete_match' WHERE id=$1`,[id]); return true; }
      const players=pq.rows.map((x:any,i:number)=>({userId:x.user_id,slot:i+1})); const loser=row.current_turn_user_id||players[0].userId; const winner=players.find(p=>p.userId!==loser)?.userId||players[1].userId;
      const results=new Map<string,'win'|'loss'|'draw'>([[winner,'win'],[loser,'loss']]);
      await c.query(`UPDATE match_players SET result=CASE WHEN user_id=$2 THEN 'win' ELSE 'loss' END WHERE match_id=$1`,[id,winner]);
      await c.query(`UPDATE matches SET state='finished',finished_at=now(),last_activity_at=now(),current_turn_user_id=NULL,turn_deadline_at=NULL,timeout_reason='turn_timeout',result_payload=$2 WHERE id=$1`,[id,JSON.stringify({winners:[winner],loser,reason:'turn_timeout'})]);
      await this.settleRatingsAndStats(c,row,players,results); await c.query(`UPDATE matches SET reward_settled_at=now() WHERE id=$1 AND reward_settled_at IS NULL`,[id]); return true;
    });
  }

  private async grantTx(c:PoolClient,userId:string,currency:'coin'|'gem'|'energy'|'love'|'power',amount:number,reason:string,referenceType:string,referenceId:string,key:string){
    const aq=await c.query(`INSERT INTO wallet_accounts(owner_type,owner_id,currency) VALUES('user',$1,$2) ON CONFLICT(owner_type,owner_id,currency) DO UPDATE SET currency=EXCLUDED.currency RETURNING id`,[userId,currency]);
    await c.query(`INSERT INTO wallet_ledger(account_id,amount,reason,reference_type,reference_id,idempotency_key) VALUES($1,$2,$3,$4,$5,$6) ON CONFLICT(idempotency_key) DO NOTHING`,[aq.rows[0].id,amount,reason,referenceType,referenceId,key]);
  }

  async actions(userId:string,id:string){ await this.view(userId,id); const q=await this.db.query(`SELECT seq,user_id,action_type,action_payload,state_hash,created_at FROM match_actions WHERE match_id=$1 ORDER BY seq`,[id]); return q.rows; }
  async rematch(userId:string,id:string){ const q=await this.db.query(`SELECT m.*,mp.user_id FROM matches m JOIN match_players mp ON mp.match_id=m.id WHERE m.id=$1`,[id]); if(!q.rowCount) throw new NotFoundException('match_not_found'); const row=q.rows[0]; const users=[...new Set(q.rows.map((x:any)=>x.user_id))]; if(!users.includes(userId)) throw new ForbiddenException('not_a_player'); const other=users.find((x:any)=>x!==userId); if(!other) throw new BadRequestException('no_opponent'); return this.create(userId,{gameCode:row.game_code,mode:row.mode,opponentUserId:other as string,rematchOf:id,regionCode:row.region_code||'global'}); }
}
