import { BadRequestException, ForbiddenException, Injectable, NotFoundException } from '@nestjs/common';
import { randomBytes } from 'node:crypto';
import { DbService } from '../../db/db.service';
import { GamesService } from '../games/games.service';

@Injectable()
export class WarsService{
  constructor(private readonly db:DbService,private readonly games:GamesService){}
  private code(){ return randomBytes(8).toString('base64url').replace(/[-_]/g,'').slice(0,10).toUpperCase(); }
  private async role(c:any,colonyId:string,userId:string){ const q=await c.query(`SELECT role FROM colony_members WHERE colony_id=$1 AND user_id=$2`,[colonyId,userId]); return q.rows[0]?.role||null; }
  private async command(c:any,colonyId:string,userId:string){ const r=await this.role(c,colonyId,userId); if(!['owner','captain'].includes(r)) throw new ForbiddenException('war_command_permission_denied'); return r; }

  async create(userId:string,b:{challengerColonyId:string;defenderColonyId:string;mode?:string;roundCount?:number;startsAt?:string;rematchOf?:string}){
    if(!b.challengerColonyId||!b.defenderColonyId||b.challengerColonyId===b.defenderColonyId) throw new BadRequestException('invalid_colonies');
    const roundCount=Math.max(1,Math.min(9,Number(b.roundCount||5))); const mode=b.mode==='async'?'async':'live';
    return this.db.tx(async c=>{
      await this.command(c,b.challengerColonyId,userId);
      const d=await c.query(`SELECT id FROM colonies WHERE id=$1`,[b.defenderColonyId]); if(!d.rowCount) throw new NotFoundException('defender_colony_not_found');
      const publicCode=this.code();
      const sq=await c.query(`SELECT id FROM seasons WHERE status='active' AND starts_at<=now() AND ends_at>now() ORDER BY starts_at DESC LIMIT 1`);
      const seasonId=sq.rows[0]?.id||null;
      const q=await c.query(`INSERT INTO colony_wars(challenger_colony_id,defender_colony_id,created_by,public_code,mode,round_count,starts_at,rematch_of,season_id,expires_at)
        VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,now()+CASE WHEN $5='live' THEN interval '2 hours' ELSE interval '48 hours' END) RETURNING *`,
        [b.challengerColonyId,b.defenderColonyId,userId,publicCode,mode,roundCount,b.startsAt||null,b.rematchOf||null,seasonId]);
      return {...q.rows[0],deepLink:`colonyclash://war/${publicCode}`};
    });
  }

  async mine(userId:string){
    const q=await this.db.query(`SELECT DISTINCT w.*,cc.name challenger_name,dc.name defender_name
      FROM colony_wars w JOIN colonies cc ON cc.id=w.challenger_colony_id JOIN colonies dc ON dc.id=w.defender_colony_id
      JOIN colony_members cm ON cm.colony_id IN (w.challenger_colony_id,w.defender_colony_id)
      WHERE cm.user_id=$1 ORDER BY w.created_at DESC LIMIT 100`,[userId]); return q.rows;
  }

  private async assertVisible(userId:string,w:any){
    const q=await this.db.query(`SELECT 1 FROM colony_members WHERE user_id=$1 AND colony_id IN ($2,$3)`,[userId,w.challenger_colony_id,w.defender_colony_id]);
    if(q.rowCount) return true; if(['active','finished'].includes(w.status)) return true; throw new ForbiddenException('war_not_visible');
  }

  async byCode(userId:string,code:string){ const q=await this.db.query(`SELECT * FROM colony_wars WHERE public_code=$1`,[String(code).toUpperCase()]); if(!q.rowCount) throw new NotFoundException('war_not_found'); await this.assertVisible(userId,q.rows[0]); return this.get(userId,q.rows[0].id); }

  async get(userId:string,id:string){
    await this.syncInternal(id);
    const q=await this.db.query(`SELECT w.*,cc.name challenger_name,cc.slug challenger_slug,dc.name defender_name,dc.slug defender_slug,
      (SELECT count(*)::int FROM colony_war_spectators s WHERE s.war_id=w.id AND s.last_seen_at>now()-interval '2 minutes') spectator_count
      FROM colony_wars w JOIN colonies cc ON cc.id=w.challenger_colony_id JOIN colonies dc ON dc.id=w.defender_colony_id WHERE w.id=$1`,[id]);
    if(!q.rowCount) throw new NotFoundException('war_not_found'); const w=q.rows[0]; await this.assertVisible(userId,w);
    const roster=await this.db.query(`SELECT r.colony_id,r.user_id,r.slot,u.username,u.display_name,u.avatar_key,COALESCE(ps.skill_rating,1000) skill_rating
      FROM colony_war_roster r JOIN users u ON u.id=r.user_id LEFT JOIN player_stats ps ON ps.user_id=r.user_id WHERE r.war_id=$1 ORDER BY r.colony_id,r.slot`,[id]);
    const rounds=await this.db.query(`SELECT wr.*,cg.title game_title FROM colony_war_rounds wr JOIN game_catalog cg ON cg.game_code=wr.game_code WHERE wr.war_id=$1 ORDER BY wr.round_no`,[id]);
    const mvp=await this.db.query(`SELECT s.*,u.username,u.display_name FROM colony_war_player_stats s JOIN users u ON u.id=s.user_id WHERE s.war_id=$1 ORDER BY s.mvp_score DESC,s.points DESC,s.rounds_won DESC LIMIT 5`,[id]);
    const emotes=await this.db.query(`SELECT e.code,e.created_at,u.username FROM colony_war_emotes e JOIN users u ON u.id=e.user_id WHERE e.war_id=$1 ORDER BY e.id DESC LIMIT 30`,[id]);
    return {...w,roster:roster.rows,rounds:rounds.rows,mvp:mvp.rows,recentEmotes:emotes.rows};
  }

  async accept(userId:string,id:string){ return this.db.tx(async c=>{ const q=await c.query(`SELECT * FROM colony_wars WHERE id=$1 FOR UPDATE`,[id]); if(!q.rowCount) throw new NotFoundException('war_not_found'); const w=q.rows[0]; await this.command(c,w.defender_colony_id,userId); if(w.status!=='declared') throw new BadRequestException('war_not_declarable'); if(new Date(w.expires_at).getTime()<=Date.now()) throw new BadRequestException('war_expired'); await c.query(`UPDATE colony_wars SET status='accepted',accepted_by=$2,accepted_at=now() WHERE id=$1`,[id,userId]); await c.query(`UPDATE territory_battles SET status='active' WHERE war_id=$1 AND status='declared'`,[id]).catch(()=>{}); return {ok:true,status:'accepted'}; }); }

  async setRoster(userId:string,id:string,b:{colonyId:string;userIds:string[]}){
    const ids=[...new Set((b.userIds||[]).map(String))].slice(0,9); if(!ids.length) throw new BadRequestException('roster_required');
    return this.db.tx(async c=>{ const q=await c.query(`SELECT * FROM colony_wars WHERE id=$1 FOR UPDATE`,[id]); if(!q.rowCount) throw new NotFoundException('war_not_found'); const w=q.rows[0]; if(![w.challenger_colony_id,w.defender_colony_id].includes(b.colonyId)) throw new BadRequestException('colony_not_in_war'); await this.command(c,b.colonyId,userId); if(!['declared','accepted'].includes(w.status)) throw new BadRequestException('roster_locked');
      const members=await c.query(`SELECT user_id FROM colony_members WHERE colony_id=$1 AND user_id=ANY($2::uuid[])`,[b.colonyId,ids]); if(members.rowCount!==ids.length) throw new BadRequestException('roster_contains_non_member');
      await c.query(`DELETE FROM colony_war_roster WHERE war_id=$1 AND colony_id=$2`,[id,b.colonyId]);
      for(let i=0;i<ids.length;i++) await c.query(`INSERT INTO colony_war_roster(war_id,colony_id,user_id,slot,added_by) VALUES($1,$2,$3,$4,$5)`,[id,b.colonyId,ids[i],i+1,userId]);
      return {ok:true,count:ids.length}; });
  }

  async start(userId:string,id:string){
    const prep=await this.db.tx(async c=>{ const q=await c.query(`SELECT * FROM colony_wars WHERE id=$1 FOR UPDATE`,[id]); if(!q.rowCount) throw new NotFoundException('war_not_found'); const w=q.rows[0]; if(w.status!=='accepted') throw new BadRequestException('war_not_accepted'); const role1=await this.role(c,w.challenger_colony_id,userId),role2=await this.role(c,w.defender_colony_id,userId); if(!['owner','captain'].includes(role1)&&!['owner','captain'].includes(role2)) throw new ForbiddenException('war_command_permission_denied');
      const cr=await c.query(`SELECT user_id,slot FROM colony_war_roster WHERE war_id=$1 AND colony_id=$2 ORDER BY slot`,[id,w.challenger_colony_id]); const dr=await c.query(`SELECT user_id,slot FROM colony_war_roster WHERE war_id=$1 AND colony_id=$2 ORDER BY slot`,[id,w.defender_colony_id]); if(!cr.rowCount||!dr.rowCount) throw new BadRequestException('both_rosters_required');
      await c.query(`UPDATE colony_wars SET status='active',started_at=now(),current_round=1 WHERE id=$1`,[id]); return {w,cr:cr.rows,dr:dr.rows}; });
    const games=['color_war_10','number_grab_10','treasure_flip_10'];
    for(let r=1;r<=Number(prep.w.round_count);r++){
      const cu=prep.cr[(r-1)%prep.cr.length].user_id,du=prep.dr[(r-1)%prep.dr.length].user_id,gameCode=games[(r-1)%games.length];
      await this.db.query(`INSERT INTO colony_war_rounds(war_id,round_no,game_code,challenger_user_id,defender_user_id) VALUES($1,$2,$3,$4,$5) ON CONFLICT(war_id,round_no) DO NOTHING`,[id,r,gameCode,cu,du]);
    }
    await this.launchNextRound(id); return this.get(userId,id);
  }

  private async launchNextRound(id:string){
    const reserved=await this.db.tx(async c=>{
      const q=await c.query(`SELECT wr.*,w.mode,w.challenger_colony_id,w.defender_colony_id FROM colony_war_rounds wr JOIN colony_wars w ON w.id=wr.war_id WHERE wr.war_id=$1 AND wr.state='pending' ORDER BY round_no FOR UPDATE SKIP LOCKED LIMIT 1`,[id]);
      if(!q.rowCount) return null; const r=q.rows[0];
      await c.query(`UPDATE colony_war_rounds SET state='active',started_at=now() WHERE id=$1 AND state='pending'`,[r.id]);
      await c.query(`UPDATE colony_wars SET current_round=$2 WHERE id=$1`,[id,r.round_no]); return r;
    });
    if(!reserved) return null;
    try{
      const m=await this.games.create(reserved.challenger_user_id,{gameCode:reserved.game_code,mode:reserved.mode,opponentUserId:reserved.defender_user_id,regionCode:'global'});
      await this.db.query(`UPDATE colony_war_rounds SET match_id=$2 WHERE id=$1 AND state='active' AND match_id IS NULL`,[reserved.id,m.id]); return m;
    }catch(e){ await this.db.query(`UPDATE colony_war_rounds SET state='pending',started_at=NULL WHERE id=$1 AND state='active' AND match_id IS NULL`,[reserved.id]).catch(()=>{}); throw e; }
  }

  async sync(userId:string,id:string){ await this.syncInternal(id); return this.get(userId,id); }
  private async syncInternal(id:string){
    const warq=await this.db.query(`SELECT * FROM colony_wars WHERE id=$1`,[id]); if(!warq.rowCount||warq.rows[0].status!=='active') return; const war=warq.rows[0];
    const active=await this.db.query(`SELECT wr.*,m.state match_state,m.result_payload FROM colony_war_rounds wr LEFT JOIN matches m ON m.id=wr.match_id WHERE wr.war_id=$1 AND wr.state='active' ORDER BY wr.round_no`,[id]);
    for(const r of active.rows){ if(r.match_state!=='finished') continue; await this.db.tx(async c=>{ const lock=await c.query(`SELECT * FROM colony_war_rounds WHERE id=$1 FOR UPDATE`,[r.id]); if(!lock.rowCount||lock.rows[0].state!=='active') return; const mp=await c.query(`SELECT user_id,result,score FROM match_players WHERE match_id=$1`,[r.match_id]); const cm=new Map([[r.challenger_user_id,war.challenger_colony_id],[r.defender_user_id,war.defender_colony_id]]); let cp=0,dp=0,winner:string|null=null; for(const p of mp.rows){ if(p.result==='win'){ winner=cm.get(p.user_id)||null; if(winner===war.challenger_colony_id) cp=1; else if(winner===war.defender_colony_id) dp=1; } } if(mp.rows.every((p:any)=>p.result==='draw')){ cp=1;dp=1; }
        await c.query(`UPDATE colony_war_rounds SET state='finished',challenger_points=$2,defender_points=$3,winner_colony_id=$4,finished_at=now() WHERE id=$1`,[r.id,cp,dp,winner]);
        for(const p of mp.rows){ const colonyId=cm.get(p.user_id)!; const won=p.result==='win'?1:0; const pts=Math.max(0,Number(p.score||0)); await c.query(`INSERT INTO colony_war_player_stats(war_id,user_id,colony_id,rounds_played,rounds_won,points,mvp_score) VALUES($1,$2,$3,1,$4,$5,$6) ON CONFLICT(war_id,user_id) DO UPDATE SET rounds_played=colony_war_player_stats.rounds_played+1,rounds_won=colony_war_player_stats.rounds_won+EXCLUDED.rounds_won,points=colony_war_player_stats.points+EXCLUDED.points,mvp_score=colony_war_player_stats.mvp_score+EXCLUDED.mvp_score`,[id,p.user_id,colonyId,won,pts,won*100+pts]); }
      }); }
    const totals=await this.db.query(`SELECT COALESCE(sum(challenger_points),0)::int c,COALESCE(sum(defender_points),0)::int d,count(*) FILTER(WHERE state='finished')::int finished,count(*)::int total FROM colony_war_rounds WHERE war_id=$1`,[id]); const t=totals.rows[0]; await this.db.query(`UPDATE colony_wars SET challenger_score=$2,defender_score=$3 WHERE id=$1`,[id,t.c,t.d]);
    if(Number(t.finished)>=Number(t.total)&&Number(t.total)>0){
      const win=t.c===t.d?null:(t.c>t.d?war.challenger_colony_id:war.defender_colony_id);
      await this.db.tx(async c=>{
        const lock=await c.query(`SELECT * FROM colony_wars WHERE id=$1 FOR UPDATE`,[id]); if(lock.rows[0]?.status!=='active') return;
        const lockedWar=lock.rows[0];
        await c.query(`UPDATE colony_wars SET status='finished',winner_colony_id=$2,finished_at=now() WHERE id=$1`,[id,win]);
        if(win) await c.query(`UPDATE colonies SET war_points=war_points+100,season_points=season_points+100 WHERE id=$1`,[win]);
        await c.query(`UPDATE colonies SET war_points=war_points+25,season_points=season_points+25 WHERE id=ANY($1::uuid[])`,[[war.challenger_colony_id,war.defender_colony_id]]);
        await this.settleSeasonWar(c,lockedWar,win);
      }); return;
    }
    const currently=await this.db.query(`SELECT 1 FROM colony_war_rounds WHERE war_id=$1 AND state='active'`,[id]); if(!currently.rowCount) await this.launchNextRound(id);
  }

  private async settleSeasonWar(c:any,war:any,winner:string|null){
    if(!war.season_id) return;
    const season=await c.query(`SELECT status,ends_at FROM seasons WHERE id=$1`,[war.season_id]);
    if(!season.rowCount||season.rows[0].status!=='active'||new Date(season.rows[0].ends_at).getTime()<=Date.now()) return;
    const inserted=await c.query(`INSERT INTO season_war_settlements(war_id,season_id,challenger_colony_id,defender_colony_id,winner_colony_id)
      VALUES($1,$2,$3,$4,$5) ON CONFLICT(war_id) DO NOTHING RETURNING war_id`,[war.id,war.season_id,war.challenger_colony_id,war.defender_colony_id,winner]);
    if(!inserted.rowCount) return;
    await c.query(`INSERT INTO season_colony_stats(season_id,colony_id,points) VALUES($1,$2,25),($1,$3,25)
      ON CONFLICT(season_id,colony_id) DO UPDATE SET points=season_colony_stats.points+25,updated_at=now()`,[war.season_id,war.challenger_colony_id,war.defender_colony_id]);
    if(winner){
      const loser=winner===war.challenger_colony_id?war.defender_colony_id:war.challenger_colony_id;
      await c.query(`UPDATE season_colony_stats SET points=points+100,war_wins=war_wins+1,updated_at=now() WHERE season_id=$1 AND colony_id=$2`,[war.season_id,winner]);
      await c.query(`UPDATE season_colony_stats SET war_losses=war_losses+1,updated_at=now() WHERE season_id=$1 AND colony_id=$2`,[war.season_id,loser]);
    }
    const bq=await c.query(`SELECT b.*,st.fortification,st.defense_streak FROM territory_battles b JOIN season_territories st ON st.season_id=b.season_id AND st.territory_id=b.territory_id WHERE b.war_id=$1 AND b.status IN ('declared','active') FOR UPDATE OF b,st`,[war.id]);
    if(!bq.rowCount) return; const b=bq.rows[0];
    if(winner===b.attacker_colony_id){
      await c.query(`UPDATE territory_battles SET status='settled',winner_colony_id=$2,settlement_type='capture',settled_at=now() WHERE id=$1`,[b.id,b.attacker_colony_id]);
      await c.query(`UPDATE season_territories SET owner_colony_id=$3,state='owned',fortification=0,defense_streak=0,shield_until=now()+interval '45 minutes',active_battle_id=NULL,captured_at=now(),updated_at=now() WHERE season_id=$1 AND territory_id=$2`,[b.season_id,b.territory_id,b.attacker_colony_id]);
      await c.query(`UPDATE season_colony_stats SET points=points+250,territories_captured=territories_captured+1,updated_at=now() WHERE season_id=$1 AND colony_id=$2`,[b.season_id,b.attacker_colony_id]);
      await c.query(`INSERT INTO season_events(season_id,territory_id,colony_id,event_type,payload) VALUES($1,$2,$3,'capture',$4::jsonb)`,[b.season_id,b.territory_id,b.attacker_colony_id,JSON.stringify({battleId:b.id,warId:war.id})]);
    }else{
      const fort=Math.min(3,Number(b.fortification||0)+1); const shieldMinutes=30+fort*15;
      await c.query(`UPDATE territory_battles SET status='settled',winner_colony_id=$2,settlement_type='defense',settled_at=now() WHERE id=$1`,[b.id,b.defender_colony_id]);
      await c.query(`UPDATE season_territories SET state='owned',fortification=$3,defense_streak=defense_streak+1,shield_until=now()+($4::int * interval '1 minute'),active_battle_id=NULL,updated_at=now() WHERE season_id=$1 AND territory_id=$2`,[b.season_id,b.territory_id,fort,shieldMinutes]);
      await c.query(`UPDATE season_colony_stats SET points=points+150,territories_defended=territories_defended+1,updated_at=now() WHERE season_id=$1 AND colony_id=$2`,[b.season_id,b.defender_colony_id]);
      await c.query(`INSERT INTO season_events(season_id,territory_id,colony_id,event_type,payload) VALUES($1,$2,$3,'defense',$4::jsonb)`,[b.season_id,b.territory_id,b.defender_colony_id,JSON.stringify({battleId:b.id,warId:war.id,tie:!winner,fortification:fort})]);
    }
  }

  async spectate(userId:string,id:string){ const q=await this.db.query(`SELECT status FROM colony_wars WHERE id=$1`,[id]); if(!q.rowCount) throw new NotFoundException('war_not_found'); if(!['active','finished'].includes(q.rows[0].status)) throw new ForbiddenException('spectating_not_open'); await this.db.query(`INSERT INTO colony_war_spectators(war_id,user_id) VALUES($1,$2) ON CONFLICT(war_id,user_id) DO UPDATE SET last_seen_at=now()`,[id,userId]); return {ok:true,room:`war:${id}`}; }

  async rematch(userId:string,id:string){ const q=await this.db.query(`SELECT * FROM colony_wars WHERE id=$1`,[id]); if(!q.rowCount) throw new NotFoundException('war_not_found'); const w=q.rows[0]; if(w.status!=='finished') throw new BadRequestException('war_not_finished'); const actorColony=(await this.db.query(`SELECT colony_id FROM colony_members WHERE user_id=$1 AND colony_id IN ($2,$3) AND role IN ('owner','captain') LIMIT 1`,[userId,w.challenger_colony_id,w.defender_colony_id])).rows[0]?.colony_id; if(!actorColony) throw new ForbiddenException('war_command_permission_denied'); return this.create(userId,{challengerColonyId:actorColony,defenderColonyId:actorColony===w.challenger_colony_id?w.defender_colony_id:w.challenger_colony_id,mode:w.mode,roundCount:w.round_count,rematchOf:id}); }

  async cancel(userId:string,id:string){ return this.db.tx(async c=>{ const q=await c.query(`SELECT * FROM colony_wars WHERE id=$1 FOR UPDATE`,[id]); if(!q.rowCount) throw new NotFoundException('war_not_found'); const w=q.rows[0]; if(!['declared','accepted'].includes(w.status)) throw new BadRequestException('war_cannot_cancel'); await this.command(c,w.challenger_colony_id,userId); await c.query(`UPDATE colony_wars SET status='cancelled',finished_at=now() WHERE id=$1`,[id]); const b=await c.query(`UPDATE territory_battles SET status='cancelled',settlement_type='cancelled',settled_at=now() WHERE war_id=$1 AND status IN ('declared','active') RETURNING season_id,territory_id`,[id]).catch(()=>({rows:[]} as any)); for(const row of b.rows) await c.query(`UPDATE season_territories SET state=CASE WHEN owner_colony_id IS NULL THEN 'neutral' ELSE 'owned' END,active_battle_id=NULL,updated_at=now() WHERE season_id=$1 AND territory_id=$2`,[row.season_id,row.territory_id]); return {ok:true}; }); }
}
