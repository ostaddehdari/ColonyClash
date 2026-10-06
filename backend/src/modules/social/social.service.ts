import { BadRequestException, ForbiddenException, Injectable, NotFoundException } from '@nestjs/common';
import { createHash, randomBytes } from 'node:crypto';
import { DbService } from '../../db/db.service';

@Injectable()
export class SocialService {
  constructor(private readonly db: DbService) {}

  async searchUsers(requesterId:string, query:string){
    const q = query.trim().replace(/^@/,'');
    if (q.length < 2) return [];
    const r = await this.db.query(`SELECT id,username,display_name,avatar_key FROM users
      WHERE id<>$1 AND status='active' AND (username ILIKE $2 OR display_name ILIKE $2)
      AND NOT EXISTS(SELECT 1 FROM user_blocks b WHERE (b.blocker_user_id=$1 AND b.blocked_user_id=users.id) OR (b.blocker_user_id=users.id AND b.blocked_user_id=$1))
      ORDER BY CASE WHEN lower(username)=lower($3) THEN 0 ELSE 1 END, username LIMIT 20`, [requesterId, `%${q}%`, q]);
    return r.rows;
  }

  async requestFriend(userId:string, friendId:string){
    if(userId===friendId) throw new BadRequestException('cannot_friend_self');
    return this.db.tx(async c=>{
      const blocked = await c.query(`SELECT 1 FROM user_blocks WHERE (blocker_user_id=$1 AND blocked_user_id=$2) OR (blocker_user_id=$2 AND blocked_user_id=$1)`,[userId,friendId]);
      if(blocked.rowCount) throw new BadRequestException('friend_blocked');
      await c.query(`INSERT INTO friendships(user_id,friend_user_id,status) VALUES($1,$2,'pending') ON CONFLICT(user_id,friend_user_id) DO UPDATE SET status='pending'`,[userId,friendId]);
      return {ok:true};
    });
  }

  async acceptFriend(userId:string, fromId:string){
    return this.db.tx(async c=>{
      const q=await c.query(`UPDATE friendships SET status='accepted' WHERE user_id=$1 AND friend_user_id=$2 AND status='pending' RETURNING user_id`,[fromId,userId]);
      if(!q.rowCount) throw new NotFoundException('friend_request_not_found');
      await c.query(`INSERT INTO friendships(user_id,friend_user_id,status) VALUES($1,$2,'accepted') ON CONFLICT(user_id,friend_user_id) DO UPDATE SET status='accepted'`,[userId,fromId]);
      return {ok:true};
    });
  }

  async friends(userId:string){
    const q=await this.db.query(`SELECT u.id,u.username,u.display_name,u.avatar_key,COALESCE(ps.level,1) level,COALESCE(ps.skill_rating,1000) skill_rating
      FROM friendships f JOIN users u ON u.id=f.friend_user_id LEFT JOIN player_stats ps ON ps.user_id=u.id
      WHERE f.user_id=$1 AND f.status='accepted' ORDER BY u.display_name`,[userId]);
    return q.rows;
  }

  async createSquad(userId:string,name:string,slug:string){
    const safeSlug=slug.trim().toLowerCase();
    if(!/^[a-z0-9-]{3,40}$/.test(safeSlug)) throw new BadRequestException('invalid_slug');
    return this.db.tx(async c=>{
      const q=await c.query(`INSERT INTO squads(owner_user_id,name,slug) VALUES($1,$2,$3) RETURNING *`,[userId,name.trim().slice(0,60),safeSlug]);
      const s=q.rows[0];
      await c.query(`INSERT INTO squad_members(squad_id,user_id,role) VALUES($1,$2,'owner')`,[s.id,userId]);
      return s;
    });
  }

  async mySquads(userId:string){
    const q=await this.db.query(`SELECT s.*,sm.role,(SELECT count(*)::int FROM squad_members x WHERE x.squad_id=s.id) members
      FROM squad_members sm JOIN squads s ON s.id=sm.squad_id WHERE sm.user_id=$1 ORDER BY sm.joined_at DESC`,[userId]);
    return q.rows;
  }

  async squadMembers(squadId:string){
    const q=await this.db.query(`SELECT u.id,u.username,u.display_name,u.avatar_key,sm.role FROM squad_members sm JOIN users u ON u.id=sm.user_id WHERE sm.squad_id=$1 ORDER BY sm.joined_at`,[squadId]);
    return q.rows;
  }

  async createSquadInvite(userId:string,squadId:string,invitedUserId?:string){
    return this.db.tx(async c=>{
      const member=await c.query(`SELECT role FROM squad_members WHERE squad_id=$1 AND user_id=$2`,[squadId,userId]);
      if(!member.rowCount || !['owner','captain'].includes(member.rows[0].role)) throw new ForbiddenException('squad_permission_denied');
      const token=randomBytes(24).toString('base64url');
      const tokenHash=createHash('sha256').update(token).digest('hex');
      const q=await c.query(`INSERT INTO squad_invites(squad_id,created_by,invited_user_id,token_hash,expires_at)
        VALUES($1,$2,$3,$4,now()+interval '7 days') RETURNING id,expires_at`,[squadId,userId,invitedUserId||null,tokenHash]);
      const base=(process.env.PUBLIC_APP_URL||'https://play.example.com').replace(/\/$/,'');
      return {...q.rows[0],token,inviteUrl:`${base}/join/squad/${token}`,qrPayload:`colonyclash://join/squad/${token}`};
    });
  }

  async acceptSquadInvite(userId:string,token:string){
    const hash=createHash('sha256').update(token).digest('hex');
    return this.db.tx(async c=>{
      const iq=await c.query(`SELECT * FROM squad_invites WHERE token_hash=$1 AND status='open' FOR UPDATE`,[hash]);
      if(!iq.rowCount) throw new NotFoundException('invite_not_found');
      const i=iq.rows[0];
      if(new Date(i.expires_at).getTime()<Date.now()) throw new BadRequestException('invite_expired');
      if(i.invited_user_id && i.invited_user_id!==userId) throw new ForbiddenException('invite_for_different_user');
      const s=await c.query(`SELECT member_limit FROM squads WHERE id=$1 FOR UPDATE`,[i.squad_id]);
      const count=await c.query(`SELECT count(*)::int n FROM squad_members WHERE squad_id=$1`,[i.squad_id]);
      if(count.rows[0].n>=s.rows[0].member_limit) throw new BadRequestException('squad_full');
      const joined=await c.query(`INSERT INTO squad_members(squad_id,user_id) VALUES($1,$2) ON CONFLICT DO NOTHING RETURNING user_id`,[i.squad_id,userId]);
      await c.query(`UPDATE squad_invites SET status='accepted',accepted_at=now() WHERE id=$1`,[i.id]);
      if(joined.rowCount){
        await c.query(`INSERT INTO player_stats(user_id,recruiter_points) VALUES($1,10) ON CONFLICT(user_id) DO UPDATE SET recruiter_points=player_stats.recruiter_points+10,updated_at=now()`,[i.created_by]);
      }
      return {ok:true,squadId:i.squad_id,recruiterReward:joined.rowCount?10:0};
    });
  }

  async createRivalry(userId:string,b:{opponentType:string;opponentId:string;challengerType?:string;challengerId?:string}){
    const challengerType=b.challengerType||'user';
    const challengerId=b.challengerId||userId;
    if(!['user','squad','colony'].includes(challengerType) || !['user','squad','colony'].includes(b.opponentType)) throw new BadRequestException('invalid_rival_type');
    if(challengerType==='user' && challengerId!==userId) throw new ForbiddenException('invalid_challenger');
    if(challengerType==='squad'){
      const m=await this.db.query(`SELECT 1 FROM squad_members WHERE squad_id=$1 AND user_id=$2 AND role IN ('owner','captain')`,[challengerId,userId]);
      if(!m.rowCount) throw new ForbiddenException('squad_permission_denied');
    }
    if(challengerType==='colony'){
      const m=await this.db.query(`SELECT 1 FROM colony_members WHERE colony_id=$1 AND user_id=$2 AND role IN ('owner','captain')`,[challengerId,userId]);
      if(!m.rowCount) throw new ForbiddenException('colony_permission_denied');
    }
    const q=await this.db.query(`INSERT INTO rivalries(challenger_type,challenger_id,opponent_type,opponent_id)
      VALUES($1,$2,$3,$4) RETURNING *`,[challengerType,challengerId,b.opponentType,b.opponentId]);
    return q.rows[0];
  }

  async myRivalries(userId:string){
    const q=await this.db.query(`SELECT * FROM rivalries WHERE (challenger_type='user' AND challenger_id=$1) OR (opponent_type='user' AND opponent_id=$1) ORDER BY created_at DESC LIMIT 100`,[userId]);
    return q.rows;
  }

  private makeChallengeCode(){ return randomBytes(7).toString('base64url').replace(/[-_]/g,'').slice(0,10).toUpperCase(); }

  async createChallenge(userId:string,b:{targetType:string;targetId?:string;gameCode:string;mode?:string;message?:string;stakePoints?:number}){
    if(!['user','squad','colony','open'].includes(b.targetType)) throw new BadRequestException('invalid_target_type');
    if(!b.gameCode?.trim()) throw new BadRequestException('game_required');
    for(let i=0;i<5;i++){
      const code=this.makeChallengeCode();
      try{
        const q=await this.db.query(`INSERT INTO challenges(created_by,target_type,target_id,game_code,mode,message,stake_points,expires_at,public_code)
          VALUES($1,$2,$3,$4,$5,$6,$7,now()+interval '24 hours',$8) RETURNING *`,[
            userId,b.targetType,b.targetId||null,b.gameCode.slice(0,40),b.mode==='async'?'async':'live',(b.message||'').slice(0,160),Math.max(0,Math.min(1000000,b.stakePoints||0)),code]);
        await this.db.query(`INSERT INTO challenge_events(challenge_id,actor_user_id,event_type) VALUES($1,$2,'created')`,[q.rows[0].id,userId]);
        const base=(process.env.PUBLIC_APP_URL||'https://play.example.com').replace(/\/$/,'');
        return {...q.rows[0],challengeUrl:`${base}/c/${code}`,deepLink:`colonyclash://challenge/${code}`};
      }catch(e:any){ if(e?.code!=='23505' || i===4) throw e; }
    }
    throw new BadRequestException('challenge_code_generation_failed');
  }

  async challengeByCode(code:string){
    const q=await this.db.query(`SELECT c.*,u.username creator_username,u.display_name creator_name,u.avatar_key creator_avatar
      FROM challenges c JOIN users u ON u.id=c.created_by WHERE c.public_code=$1`,[code.toUpperCase()]);
    if(!q.rowCount) throw new NotFoundException('challenge_not_found');
    return q.rows[0];
  }

  async acceptChallenge(userId:string,code:string){
    return this.db.tx(async c=>{
      const q=await c.query(`SELECT * FROM challenges WHERE public_code=$1 FOR UPDATE`,[code.toUpperCase()]);
      if(!q.rowCount) throw new NotFoundException('challenge_not_found');
      const ch=q.rows[0];
      if(ch.status!=='open') throw new BadRequestException('challenge_not_open');
      if(ch.created_by===userId) throw new BadRequestException('cannot_accept_own_challenge');
      if(new Date(ch.expires_at).getTime()<Date.now()) throw new BadRequestException('challenge_expired');
      if(ch.target_type==='user' && ch.target_id!==userId) throw new ForbiddenException('challenge_not_for_you');
      if(ch.target_type==='squad'){
        const m=await c.query(`SELECT 1 FROM squad_members WHERE squad_id=$1 AND user_id=$2`,[ch.target_id,userId]);
        if(!m.rowCount) throw new ForbiddenException('challenge_not_for_you');
      }
      if(ch.target_type==='colony'){
        const m=await c.query(`SELECT 1 FROM colony_members WHERE colony_id=$1 AND user_id=$2`,[ch.target_id,userId]);
        if(!m.rowCount) throw new ForbiddenException('challenge_not_for_you');
      }
      await c.query(`UPDATE challenges SET status='accepted',accepted_by=$2,accepted_at=now() WHERE id=$1`,[ch.id,userId]);
      await c.query(`INSERT INTO challenge_events(challenge_id,actor_user_id,event_type) VALUES($1,$2,'accepted')`,[ch.id,userId]);
      return {ok:true,challengeId:ch.id,gameCode:ch.game_code,mode:ch.mode};
    });
  }

  async cancelChallenge(userId:string,id:string){
    const q=await this.db.query(`UPDATE challenges SET status='cancelled' WHERE id=$1 AND created_by=$2 AND status='open' RETURNING id`,[id,userId]);
    if(!q.rowCount) throw new NotFoundException('challenge_not_cancellable');
    await this.db.query(`INSERT INTO challenge_events(challenge_id,actor_user_id,event_type) VALUES($1,$2,'cancelled')`,[id,userId]);
    return {ok:true};
  }

  async incomingChallenges(userId:string){
    const q=await this.db.query(`SELECT c.*,u.username creator_username,u.display_name creator_name FROM challenges c JOIN users u ON u.id=c.created_by
      WHERE c.status='open' AND c.expires_at>now() AND (
        c.target_type='open' OR (c.target_type='user' AND c.target_id=$1)
        OR (c.target_type='squad' AND EXISTS(SELECT 1 FROM squad_members sm WHERE sm.squad_id=c.target_id AND sm.user_id=$1))
        OR (c.target_type='colony' AND EXISTS(SELECT 1 FROM colony_members cm WHERE cm.colony_id=c.target_id AND cm.user_id=$1))
      ) ORDER BY c.created_at DESC LIMIT 50`,[userId]);
    return q.rows;
  }

  async markShared(userId:string,id:string){
    const q=await this.db.query(`UPDATE challenges SET share_count=share_count+1 WHERE id=$1 AND created_by=$2 RETURNING share_count`,[id,userId]);
    if(!q.rowCount) throw new NotFoundException('challenge_not_found');
    await this.db.query(`INSERT INTO challenge_events(challenge_id,actor_user_id,event_type) VALUES($1,$2,'shared')`,[id,userId]);
    return q.rows[0];
  }
}
