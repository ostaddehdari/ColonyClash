import { BadRequestException, ForbiddenException, Injectable, NotFoundException } from '@nestjs/common';
import { DbService } from '../../db/db.service';
import { WarsService } from '../wars/wars.service';

@Injectable()
export class SeasonsService{
  constructor(private readonly db:DbService,private readonly wars:WarsService){}

  private async command(c:any,colonyId:string,userId:string){
    const q=await c.query(`SELECT role FROM colony_members WHERE colony_id=$1 AND user_id=$2`,[colonyId,userId]);
    if(!q.rowCount||!['owner','captain'].includes(q.rows[0].role)) throw new ForbiddenException('territory_command_permission_denied');
  }

  private async ensureActiveSeason(){
    return this.db.tx(async c=>{
      await c.query(`SELECT pg_advisory_xact_lock(hashtext('colony_clash_season_rollover'))`);
      const current=await c.query(`SELECT * FROM seasons WHERE status='active' ORDER BY starts_at DESC LIMIT 1 FOR UPDATE`);
      if(current.rowCount && new Date(current.rows[0].ends_at).getTime()>Date.now()){
        await c.query(`INSERT INTO season_territories(season_id,territory_id) SELECT $1,id FROM territory_nodes WHERE enabled=true ON CONFLICT DO NOTHING`,[current.rows[0].id]);
        return current.rows[0];
      }
      if(current.rowCount){
        const old=current.rows[0];
        await c.query(`UPDATE territory_battles SET status='cancelled',settlement_type='cancelled',settled_at=now() WHERE season_id=$1 AND status IN ('reserving','declared','active')`,[old.id]);
        await c.query(`INSERT INTO season_rank_snapshots(season_id,colony_id,rank,points,territories_held,territories_captured,territories_defended,war_wins,war_losses)
          SELECT $1,s.colony_id,row_number() OVER(ORDER BY s.points DESC,held DESC,s.territories_captured DESC,s.updated_at ASC)::int,s.points,held,s.territories_captured,s.territories_defended,s.war_wins,s.war_losses
          FROM season_colony_stats s
          CROSS JOIN LATERAL (SELECT count(*)::int held FROM season_territories st WHERE st.season_id=s.season_id AND st.owner_colony_id=s.colony_id) x
          WHERE s.season_id=$1 ON CONFLICT DO NOTHING`,[old.id]);
        await c.query(`UPDATE seasons SET status='archived',archived_at=now() WHERE id=$1`,[old.id]);
        await c.query(`INSERT INTO season_events(season_id,event_type,payload) VALUES($1,'season_archived','{}'::jsonb)`,[old.id]);
        await c.query(`UPDATE colonies SET season_points=0`);
      }
      const now=new Date(); const code=`S${now.toISOString().slice(0,10).replace(/-/g,'')}_${now.getTime().toString(36).toUpperCase()}`;
      const q=await c.query(`INSERT INTO seasons(code,title_i18n,status,starts_at,ends_at)
        VALUES($1,$2::jsonb,'active',now(),now()+interval '28 days') RETURNING *`,[code,JSON.stringify({en:'New Season',fa:'فصل جدید',fr:'Nouvelle saison',ar:'موسم جديد',zh:'新赛季'})]);
      const s=q.rows[0];
      await c.query(`INSERT INTO season_territories(season_id,territory_id) SELECT $1,id FROM territory_nodes WHERE enabled=true ON CONFLICT DO NOTHING`,[s.id]);
      await c.query(`INSERT INTO season_events(season_id,event_type,payload) VALUES($1,'season_started',$2::jsonb)`,[s.id,JSON.stringify({automatic:true})]);
      return s;
    });
  }

  private async reconcileExpiredBattles(seasonId:string){
    const q=await this.db.query(`SELECT b.id FROM territory_battles b LEFT JOIN colony_wars w ON w.id=b.war_id
      WHERE b.season_id=$1 AND b.status IN ('declared','active') AND b.defense_deadline<=now() AND (w.id IS NULL OR w.status='declared') ORDER BY b.defense_deadline LIMIT 50`,[seasonId]);
    for(const row of q.rows) await this.forfeit(row.id).catch(()=>{});
  }

  private async forfeit(battleId:string){
    return this.db.tx(async c=>{
      const q=await c.query(`SELECT b.*,st.owner_colony_id,st.fortification FROM territory_battles b JOIN season_territories st ON st.season_id=b.season_id AND st.territory_id=b.territory_id WHERE b.id=$1 FOR UPDATE OF b,st`,[battleId]);
      if(!q.rowCount) return false; const b=q.rows[0];
      if(!['declared','active'].includes(b.status)||new Date(b.defense_deadline).getTime()>Date.now()) return false;
      const w=b.war_id?await c.query(`SELECT status FROM colony_wars WHERE id=$1 FOR UPDATE`,[b.war_id]):null;
      if(w?.rowCount && w.rows[0].status!=='declared') return false;
      if(w?.rowCount) await c.query(`UPDATE colony_wars SET status='expired',finished_at=now() WHERE id=$1 AND status='declared'`,[b.war_id]);
      await c.query(`UPDATE territory_battles SET status='forfeit',winner_colony_id=attacker_colony_id,settlement_type='forfeit',settled_at=now() WHERE id=$1`,[battleId]);
      await c.query(`UPDATE season_territories SET owner_colony_id=$3,state='owned',fortification=0,defense_streak=0,shield_until=now()+interval '45 minutes',active_battle_id=NULL,captured_at=now(),updated_at=now() WHERE season_id=$1 AND territory_id=$2`,[b.season_id,b.territory_id,b.attacker_colony_id]);
      await c.query(`INSERT INTO season_colony_stats(season_id,colony_id,points,territories_captured) VALUES($1,$2,250,1)
        ON CONFLICT(season_id,colony_id) DO UPDATE SET points=season_colony_stats.points+250,territories_captured=season_colony_stats.territories_captured+1,updated_at=now()`,[b.season_id,b.attacker_colony_id]);
      await c.query(`INSERT INTO season_events(season_id,territory_id,colony_id,event_type,payload) VALUES($1,$2,$3,'forfeit_capture',$4::jsonb)`,[b.season_id,b.territory_id,b.attacker_colony_id,JSON.stringify({battleId:b.id,warId:b.war_id})]);
      return true;
    });
  }

  async active(userId:string){
    const s=await this.ensureActiveSeason(); await this.reconcileExpiredBattles(s.id);
    const rank=await this.db.query(`SELECT ranked.* FROM (SELECT sc.colony_id,c.name,sc.points,row_number() OVER(ORDER BY sc.points DESC,held DESC,sc.territories_captured DESC)::int rank,
      held,sc.territories_captured,sc.territories_defended FROM season_colony_stats sc JOIN colonies c ON c.id=sc.colony_id
      CROSS JOIN LATERAL (SELECT count(*)::int held FROM season_territories st WHERE st.season_id=sc.season_id AND st.owner_colony_id=sc.colony_id) x WHERE sc.season_id=$1) ranked
      JOIN colony_members cm ON cm.colony_id=ranked.colony_id WHERE cm.user_id=$2 ORDER BY rank LIMIT 1`,[s.id,userId]);
    return {...s,daysRemaining:Math.max(0,Math.ceil((new Date(s.ends_at).getTime()-Date.now())/86400000)),myRank:rank.rows[0]||null};
  }

  async history(){
    const q=await this.db.query(`SELECT s.*, (SELECT count(*)::int FROM season_rank_snapshots r WHERE r.season_id=s.id) ranked_colonies FROM seasons s ORDER BY starts_at DESC LIMIT 20`); return q.rows;
  }

  async leaderboard(seasonId:string){
    const s=await this.db.query(`SELECT status FROM seasons WHERE id=$1`,[seasonId]); if(!s.rowCount) throw new NotFoundException('season_not_found');
    if(s.rows[0].status==='archived'){
      const q=await this.db.query(`SELECT r.*,c.name,c.slug,c.emblem_key FROM season_rank_snapshots r JOIN colonies c ON c.id=r.colony_id WHERE r.season_id=$1 ORDER BY rank LIMIT 100`,[seasonId]); return q.rows;
    }
    const q=await this.db.query(`SELECT sc.colony_id,c.name,c.slug,c.emblem_key,sc.points,sc.war_wins,sc.war_losses,sc.territories_captured,sc.territories_defended,
      (SELECT count(*)::int FROM season_territories st WHERE st.season_id=sc.season_id AND st.owner_colony_id=sc.colony_id) territories_held,
      row_number() OVER(ORDER BY sc.points DESC,(SELECT count(*) FROM season_territories st WHERE st.season_id=sc.season_id AND st.owner_colony_id=sc.colony_id) DESC,sc.territories_captured DESC)::int rank
      FROM season_colony_stats sc JOIN colonies c ON c.id=sc.colony_id WHERE sc.season_id=$1 ORDER BY rank LIMIT 100`,[seasonId]); return q.rows;
  }

  async map(userId:string){
    const s=await this.ensureActiveSeason(); await this.reconcileExpiredBattles(s.id);
    const mine=await this.db.query(`SELECT colony_id FROM colony_members WHERE user_id=$1 ORDER BY joined_at LIMIT 1`,[userId]);
    const q=await this.db.query(`SELECT tn.id,tn.code,tn.name_i18n,tn.tier,tn.map_x,tn.map_y,tn.score_value,st.owner_colony_id,st.state,st.fortification,st.defense_streak,st.shield_until,st.active_battle_id,st.captured_at,
      c.name owner_name,c.slug owner_slug,c.emblem_key owner_emblem,
      COALESCE((SELECT jsonb_agg(t2.code ORDER BY t2.code) FROM territory_edges e JOIN territory_nodes t2 ON t2.id=e.to_territory_id WHERE e.from_territory_id=tn.id),'[]'::jsonb) adjacency
      FROM territory_nodes tn JOIN season_territories st ON st.territory_id=tn.id AND st.season_id=$1 LEFT JOIN colonies c ON c.id=st.owner_colony_id
      WHERE tn.enabled=true ORDER BY tn.tier,tn.map_y,tn.map_x`,[s.id]);
    return {season:s,myColonyId:mine.rows[0]?.colony_id||null,territories:q.rows.map((t:any)=>({...t,deepLink:`colonyclash://territory/${t.code}`}))};
  }

  async claimNeutral(userId:string,code:string,b:{colonyId:string}){
    const s=await this.ensureActiveSeason(); if(!b.colonyId) throw new BadRequestException('colony_required');
    return this.db.tx(async c=>{
      await this.command(c,b.colonyId,userId);
      const q=await c.query(`SELECT st.*,tn.tier,tn.code FROM season_territories st JOIN territory_nodes tn ON tn.id=st.territory_id WHERE st.season_id=$1 AND tn.code=$2 FOR UPDATE OF st`,[s.id,String(code).toUpperCase()]);
      if(!q.rowCount) throw new NotFoundException('territory_not_found'); const t=q.rows[0];
      if(t.state!=='neutral'||t.owner_colony_id) throw new BadRequestException('territory_not_neutral'); if(Number(t.tier)!==1) throw new BadRequestException('only_frontier_can_be_claimed');
      const owned=await c.query(`SELECT count(*)::int n FROM season_territories WHERE season_id=$1 AND owner_colony_id=$2`,[s.id,b.colonyId]); if(Number(owned.rows[0].n)>0) throw new BadRequestException('colony_already_has_territory');
      const stats=await c.query(`SELECT neutral_claims FROM season_colony_stats WHERE season_id=$1 AND colony_id=$2 FOR UPDATE`,[s.id,b.colonyId]); if(stats.rowCount&&Number(stats.rows[0].neutral_claims)>0) throw new BadRequestException('neutral_claim_already_used');
      await c.query(`UPDATE season_territories SET owner_colony_id=$3,state='owned',shield_until=now()+interval '2 hours',captured_at=now(),updated_at=now() WHERE season_id=$1 AND territory_id=$2`,[s.id,t.territory_id,b.colonyId]);
      await c.query(`INSERT INTO season_colony_stats(season_id,colony_id,points,neutral_claims) VALUES($1,$2,75,1) ON CONFLICT(season_id,colony_id) DO UPDATE SET points=season_colony_stats.points+75,neutral_claims=season_colony_stats.neutral_claims+1,updated_at=now()`,[s.id,b.colonyId]);
      await c.query(`INSERT INTO season_events(season_id,territory_id,colony_id,actor_user_id,event_type,payload) VALUES($1,$2,$3,$4,'neutral_claim',$5::jsonb)`,[s.id,t.territory_id,b.colonyId,userId,JSON.stringify({code:t.code})]);
      return {ok:true,seasonId:s.id,territoryCode:t.code,colonyId:b.colonyId,points:75,shieldMinutes:120};
    });
  }

  async challenge(userId:string,code:string,b:{attackerColonyId:string;mode?:string;roundCount?:number}){
    const s=await this.ensureActiveSeason(); const mode=b.mode==='async'?'async':'live'; if(!b.attackerColonyId) throw new BadRequestException('attacker_colony_required');
    const reservation=await this.db.tx(async c=>{
      await this.command(c,b.attackerColonyId,userId);
      const q=await c.query(`SELECT st.*,tn.code,tn.tier FROM season_territories st JOIN territory_nodes tn ON tn.id=st.territory_id WHERE st.season_id=$1 AND tn.code=$2 FOR UPDATE OF st`,[s.id,String(code).toUpperCase()]);
      if(!q.rowCount) throw new NotFoundException('territory_not_found'); const t=q.rows[0];
      if(!t.owner_colony_id) throw new BadRequestException('claim_neutral_first'); if(t.owner_colony_id===b.attackerColonyId) throw new BadRequestException('already_owned'); if(t.state==='contested') throw new BadRequestException('territory_already_contested');
      if(t.shield_until&&new Date(t.shield_until).getTime()>Date.now()) throw new BadRequestException('territory_shielded');
      const owned=await c.query(`SELECT count(*)::int n FROM season_territories WHERE season_id=$1 AND owner_colony_id=$2`,[s.id,b.attackerColonyId]);
      if(Number(owned.rows[0].n)===0){ if(Number(t.tier)!==1) throw new BadRequestException('frontier_entry_required'); }
      else { const adjacent=await c.query(`SELECT 1 FROM territory_edges e JOIN season_territories mine ON mine.season_id=$1 AND mine.territory_id=e.from_territory_id AND mine.owner_colony_id=$2 WHERE e.to_territory_id=$3 LIMIT 1`,[s.id,b.attackerColonyId,t.territory_id]); if(!adjacent.rowCount) throw new BadRequestException('territory_not_adjacent'); }
      const deadline=mode==='live'?`now()+interval '30 minutes'`:`now()+interval '12 hours'`;
      const ins=await c.query(`INSERT INTO territory_battles(season_id,territory_id,attacker_colony_id,defender_colony_id,status,defense_deadline) VALUES($1,$2,$3,$4,'reserving',${deadline}) RETURNING *`,[s.id,t.territory_id,b.attackerColonyId,t.owner_colony_id]);
      return {...ins.rows[0],code:t.code};
    });
    try{
      const war=await this.wars.create(userId,{challengerColonyId:b.attackerColonyId,defenderColonyId:reservation.defender_colony_id,mode,roundCount:Math.max(1,Math.min(9,Number(b.roundCount||5)))});
      await this.db.tx(async c=>{
        await c.query(`UPDATE territory_battles SET war_id=$2,status='declared' WHERE id=$1`,[reservation.id,war.id]);
        await c.query(`UPDATE season_territories SET state='contested',active_battle_id=$3,updated_at=now() WHERE season_id=$1 AND territory_id=$2`,[s.id,reservation.territory_id,reservation.id]);
        await c.query(`INSERT INTO season_events(season_id,territory_id,colony_id,actor_user_id,event_type,payload) VALUES($1,$2,$3,$4,'battle_declared',$5::jsonb)`,[s.id,reservation.territory_id,b.attackerColonyId,userId,JSON.stringify({battleId:reservation.id,warId:war.id,mode})]);
      });
      return {battleId:reservation.id,territoryCode:reservation.code,defenseDeadline:reservation.defense_deadline,war};
    }catch(e){ await this.db.query(`DELETE FROM territory_battles WHERE id=$1 AND status='reserving'`,[reservation.id]).catch(()=>{}); throw e; }
  }

  async myBattles(userId:string){
    const s=await this.ensureActiveSeason(); await this.reconcileExpiredBattles(s.id);
    const q=await this.db.query(`SELECT b.*,tn.code,tn.name_i18n,a.name attacker_name,d.name defender_name,w.public_code war_code,w.status war_status
      FROM territory_battles b JOIN territory_nodes tn ON tn.id=b.territory_id JOIN colonies a ON a.id=b.attacker_colony_id JOIN colonies d ON d.id=b.defender_colony_id LEFT JOIN colony_wars w ON w.id=b.war_id
      WHERE b.season_id=$1 AND EXISTS(SELECT 1 FROM colony_members cm WHERE cm.user_id=$2 AND cm.colony_id IN (b.attacker_colony_id,b.defender_colony_id)) ORDER BY b.created_at DESC LIMIT 100`,[s.id,userId]); return q.rows;
  }

  async territoryHistory(code:string){
    const q=await this.db.query(`SELECT e.*,tn.code,tn.name_i18n,c.name colony_name FROM season_events e JOIN territory_nodes tn ON tn.id=e.territory_id LEFT JOIN colonies c ON c.id=e.colony_id WHERE tn.code=$1 ORDER BY e.created_at DESC LIMIT 100`,[String(code).toUpperCase()]); return q.rows;
  }

  async sync(userId:string){ const s=await this.ensureActiveSeason(); await this.reconcileExpiredBattles(s.id); return this.map(userId); }
}
