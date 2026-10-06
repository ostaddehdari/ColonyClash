import { BadRequestException, ForbiddenException, Injectable, NotFoundException, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { randomBytes } from 'node:crypto';
import { DbService } from '../../db/db.service';
import { RedisService } from '../../infra/redis/redis.service';
import { GamesService } from '../games/games.service';

@Injectable()
export class MatchmakingService implements OnModuleInit,OnModuleDestroy{
  private sweepTimer?:NodeJS.Timeout;
  constructor(private readonly db:DbService,private readonly redis:RedisService,private readonly games:GamesService){}
  onModuleInit(){ this.sweepTimer=setInterval(()=>this.sweep().catch(()=>{}),15000); this.sweepTimer.unref?.(); }
  onModuleDestroy(){ if(this.sweepTimer) clearInterval(this.sweepTimer); }

  private region(v:any){ const r=String(v||'global').toLowerCase(); return /^[a-z0-9-]{2,24}$/.test(r)?r:'global'; }
  private code(){ return randomBytes(8).toString('base64url').replace(/[-_]/g,'').slice(0,10).toUpperCase(); }

  async quick(userId:string,b:{gameCode:string;mode?:'live'|'async';regionCode?:string}){
    const gameCode=String(b.gameCode||'').trim(); if(!gameCode) throw new BadRequestException('game_required');
    const mode=b.mode==='async'?'async':'live'; const region=this.region(b.regionCode);
    const game=await this.db.query(`SELECT game_code,supports_live,supports_async FROM game_catalog WHERE game_code=$1 AND enabled=true`,[gameCode]);
    if(!game.rowCount) throw new BadRequestException('game_not_available');
    if(mode==='live'&&!game.rows[0].supports_live) throw new BadRequestException('live_not_supported');
    if(mode==='async'&&!game.rows[0].supports_async) throw new BadRequestException('async_not_supported');
    const rq=await this.db.query(`INSERT INTO player_stats(user_id) VALUES($1) ON CONFLICT DO NOTHING`,[userId]); void rq;
    const sr=await this.db.query(`SELECT skill_rating FROM player_stats WHERE user_id=$1`,[userId]); const rating=Number(sr.rows[0]?.skill_rating||1000);
    const reserve=await this.db.tx(async c=>{
      await c.query(`UPDATE matchmaking_tickets SET status='expired' WHERE status='queued' AND expires_at<=now()`);
      const own=await c.query(`SELECT * FROM matchmaking_tickets WHERE user_id=$1 AND game_code=$2 AND mode=$3 AND status IN ('queued','matching') ORDER BY created_at DESC LIMIT 1 FOR UPDATE`,[userId,gameCode,mode]);
      if(own.rowCount) return {kind:'existing',ticket:own.rows[0]} as const;
      const candidate=await c.query(`SELECT t.* FROM matchmaking_tickets t
        WHERE t.status='queued' AND t.expires_at>now() AND t.user_id<>$1 AND t.game_code=$2 AND t.mode=$3
          AND (t.region_code=$4 OR t.region_code='global' OR $4='global')
          AND ABS(t.skill_rating-$5) <= LEAST(600,100+FLOOR(EXTRACT(EPOCH FROM (now()-t.created_at))/10)*50)
          AND NOT EXISTS(SELECT 1 FROM user_blocks b WHERE (b.blocker_user_id=$1 AND b.blocked_user_id=t.user_id) OR (b.blocker_user_id=t.user_id AND b.blocked_user_id=$1))
        ORDER BY CASE WHEN t.region_code=$4 THEN 0 ELSE 1 END,ABS(t.skill_rating-$5),t.created_at
        FOR UPDATE SKIP LOCKED LIMIT 1`,[userId,gameCode,mode,region,rating]);
      if(candidate.rowCount){
        const mine=await c.query(`INSERT INTO matchmaking_tickets(user_id,game_code,mode,region_code,skill_rating,status,expires_at) VALUES($1,$2,$3,$4,$5,'matching',now()+interval '2 minutes') RETURNING *`,[userId,gameCode,mode,region,rating]);
        await c.query(`UPDATE matchmaking_tickets SET status='matching' WHERE id=$1`,[candidate.rows[0].id]);
        return {kind:'pair',mine:mine.rows[0],other:candidate.rows[0]} as const;
      }
      const ticket=await c.query(`INSERT INTO matchmaking_tickets(user_id,game_code,mode,region_code,skill_rating,expires_at) VALUES($1,$2,$3,$4,$5,now()+$6::interval) RETURNING *`,[userId,gameCode,mode,region,rating,mode==='live'?'2 minutes':'24 hours']);
      return {kind:'queued',ticket:ticket.rows[0]} as const;
    });
    if(reserve.kind==='existing') return this.ticket(userId,reserve.ticket.id);
    if(reserve.kind==='queued') return {status:'queued',ticket:reserve.ticket};
    try{
      const match=await this.games.create(userId,{gameCode,mode,opponentUserId:reserve.other.user_id,matchmakingTicketId:reserve.mine.id,regionCode:region});
      await this.db.query(`UPDATE matchmaking_tickets SET status='matched',match_id=$3,matched_at=now() WHERE id IN ($1,$2)`,[reserve.mine.id,reserve.other.id,match.id]);
      return {status:'matched',ticketId:reserve.mine.id,match};
    }catch(e){
      await this.db.query(`UPDATE matchmaking_tickets SET status='queued' WHERE id=$1 AND status='matching'`,[reserve.other.id]).catch(()=>{});
      await this.db.query(`UPDATE matchmaking_tickets SET status='cancelled' WHERE id=$1 AND status='matching'`,[reserve.mine.id]).catch(()=>{});
      throw e;
    }
  }

  async ticket(userId:string,id:string){ const q=await this.db.query(`SELECT * FROM matchmaking_tickets WHERE id=$1 AND user_id=$2`,[id,userId]); if(!q.rowCount) throw new NotFoundException('ticket_not_found'); const t=q.rows[0]; return {status:t.status,ticket:t,match:t.match_id?await this.games.view(userId,t.match_id):null}; }
  async cancel(userId:string,id:string){ const q=await this.db.query(`UPDATE matchmaking_tickets SET status='cancelled' WHERE id=$1 AND user_id=$2 AND status='queued' RETURNING id`,[id,userId]); if(!q.rowCount) throw new NotFoundException('queued_ticket_not_found'); return {ok:true}; }

  async reconnect(userId:string){
    const q=await this.db.query(`SELECT m.id FROM matches m JOIN match_players mp ON mp.match_id=m.id WHERE mp.user_id=$1 AND m.state='active' AND (m.expires_at IS NULL OR m.expires_at>now()) ORDER BY m.last_activity_at DESC LIMIT 5`,[userId]);
    const matches=[] as any[]; for(const x of q.rows) matches.push(await this.games.view(userId,x.id)); return matches;
  }
  async heartbeat(userId:string){ await this.redis.heartbeat(userId); await this.db.query(`UPDATE users SET last_seen_at=now() WHERE id=$1`,[userId]); return {ok:true,online:true,serverTime:new Date().toISOString()}; }
  async friendPresence(userId:string){ const q=await this.db.query(`SELECT u.id,u.username,u.display_name,u.avatar_key,u.last_seen_at FROM friendships f JOIN users u ON u.id=f.friend_user_id WHERE f.user_id=$1 AND f.status='accepted' ORDER BY u.display_name LIMIT 200`,[userId]); const online=await this.redis.mOnline(q.rows.map(x=>x.id)); return q.rows.map(x=>({...x,online:!!online[x.id]})); }

  async createInvite(userId:string,b:{invitedUserId?:string;gameCode:string;mode?:string;message?:string}){
    if(b.invitedUserId===userId) throw new BadRequestException('cannot_invite_self'); const mode=b.mode==='async'?'async':'live';
    const g=await this.db.query(`SELECT 1 FROM game_catalog WHERE game_code=$1 AND enabled=true`,[b.gameCode]); if(!g.rowCount) throw new BadRequestException('game_not_available');
    for(let i=0;i<5;i++){ const code=this.code(); try{ const q=await this.db.query(`INSERT INTO match_invites(created_by,invited_user_id,game_code,mode,public_code,message,expires_at) VALUES($1,$2,$3,$4,$5,$6,now()+$7::interval) RETURNING *`,[userId,b.invitedUserId||null,b.gameCode,mode,code,String(b.message||'').slice(0,160),mode==='live'?'15 minutes':'24 hours']); const base=(process.env.PUBLIC_APP_URL||'https://play.example.com').replace(/\/$/,''); return {...q.rows[0],url:`${base}/m/${code}`,deepLink:`colonyclash://match/${code}`}; }catch(e:any){ if(e?.code!=='23505'||i===4) throw e; } }
    throw new BadRequestException('invite_code_failed');
  }
  async inviteInfo(userId:string,code:string){ void userId; const q=await this.db.query(`SELECT i.*,u.username creator_username,u.display_name creator_name,u.avatar_key creator_avatar FROM match_invites i JOIN users u ON u.id=i.created_by WHERE i.public_code=$1`,[code.toUpperCase()]); if(!q.rowCount) throw new NotFoundException('invite_not_found'); return q.rows[0]; }
  async acceptInvite(userId:string,code:string){
    const claim=await this.db.tx(async c=>{ const q=await c.query(`SELECT * FROM match_invites WHERE public_code=$1 FOR UPDATE`,[code.toUpperCase()]); if(!q.rowCount) throw new NotFoundException('invite_not_found'); const i=q.rows[0]; if(i.match_id) return {existing:i.match_id,invite:i}; if(i.status!=='open'||new Date(i.expires_at).getTime()<=Date.now()) throw new BadRequestException('invite_not_open'); if(i.created_by===userId) throw new BadRequestException('cannot_accept_own_invite'); if(i.invited_user_id&&i.invited_user_id!==userId) throw new ForbiddenException('invite_not_for_you'); await c.query(`UPDATE match_invites SET status='accepted',accepted_at=now() WHERE id=$1`,[i.id]); return {invite:i}; });
    if((claim as any).existing) return this.games.view(userId,(claim as any).existing);
    try{ const m=await this.games.create((claim as any).invite.created_by,{gameCode:(claim as any).invite.game_code,mode:(claim as any).invite.mode,opponentUserId:userId,regionCode:'global'}); await this.db.query(`UPDATE match_invites SET match_id=$2 WHERE id=$1`,[(claim as any).invite.id,m.id]); return this.games.view(userId,m.id); }
    catch(e){ await this.db.query(`UPDATE match_invites SET status='open',accepted_at=NULL WHERE id=$1 AND match_id IS NULL`,[(claim as any).invite.id]).catch(()=>{}); throw e; }
  }

  async acceptChallengeAndPlay(userId:string,code:string){
    const claim=await this.db.tx(async c=>{ const q=await c.query(`SELECT * FROM challenges WHERE public_code=$1 FOR UPDATE`,[code.toUpperCase()]); if(!q.rowCount) throw new NotFoundException('challenge_not_found'); const ch=q.rows[0]; if(ch.match_id) return {existing:ch.match_id,ch}; if(ch.status!=='open'||new Date(ch.expires_at).getTime()<=Date.now()) throw new BadRequestException('challenge_not_open'); if(ch.created_by===userId) throw new BadRequestException('cannot_accept_own_challenge'); if(ch.target_type==='user'&&ch.target_id!==userId) throw new ForbiddenException('challenge_not_for_you'); if(ch.target_type==='squad'){ const m=await c.query(`SELECT 1 FROM squad_members WHERE squad_id=$1 AND user_id=$2`,[ch.target_id,userId]); if(!m.rowCount) throw new ForbiddenException('challenge_not_for_you'); } if(ch.target_type==='colony'){ const m=await c.query(`SELECT 1 FROM colony_members WHERE colony_id=$1 AND user_id=$2`,[ch.target_id,userId]); if(!m.rowCount) throw new ForbiddenException('challenge_not_for_you'); } await c.query(`UPDATE challenges SET status='accepted',accepted_by=$2,accepted_at=now() WHERE id=$1`,[ch.id,userId]); return {ch}; });
    if((claim as any).existing) return this.games.view(userId,(claim as any).existing);
    try{ const ch=(claim as any).ch; const m=await this.games.create(ch.created_by,{gameCode:ch.game_code,mode:ch.mode,opponentUserId:userId,challengeId:ch.id,regionCode:'global'}); await this.db.query(`UPDATE challenges SET match_id=$2,status='started' WHERE id=$1`,[ch.id,m.id]); await this.db.query(`INSERT INTO challenge_events(challenge_id,actor_user_id,event_type,payload) VALUES($1,$2,'started',$3)`,[ch.id,userId,JSON.stringify({matchId:m.id})]); return this.games.view(userId,m.id); }
    catch(e){ const ch=(claim as any).ch; await this.db.query(`UPDATE challenges SET status='open',accepted_by=NULL,accepted_at=NULL WHERE id=$1 AND match_id IS NULL`,[ch.id]).catch(()=>{}); throw e; }
  }

  async rating(userId:string,_game?:string){ const q=await this.db.query(`SELECT skill_rating,wins,losses,draws,win_streak,best_win_streak,xp,level FROM player_stats WHERE user_id=$1`,[userId]); return q.rows[0]||{skill_rating:1000,wins:0,losses:0,draws:0}; }
  async sweep(){ await this.db.query(`UPDATE matchmaking_tickets SET status='expired' WHERE status IN ('queued','matching') AND expires_at<=now()`); await this.db.query(`UPDATE match_invites SET status='expired' WHERE status='open' AND expires_at<=now()`); await this.games.sweepTimeouts(); }
}
