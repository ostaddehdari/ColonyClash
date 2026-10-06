import { BadRequestException, ForbiddenException, Injectable, NotFoundException } from '@nestjs/common';
import { createHash, randomBytes } from 'node:crypto';
import { DbService } from '../../db/db.service';

const MEMBER_ROLES = ['owner','captain','recruiter','defender','raider','member'] as const;
type MemberRole = typeof MEMBER_ROLES[number];

@Injectable()
export class ColoniesService {
  constructor(private readonly db: DbService) {}

  private async assertRole(client:any, colonyId:string, userId:string, allowed:MemberRole[]) {
    const q = await client.query(`SELECT role FROM colony_members WHERE colony_id=$1 AND user_id=$2`, [colonyId,userId]);
    if (!q.rowCount || !allowed.includes(q.rows[0].role)) throw new ForbiddenException('colony_permission_denied');
    return q.rows[0].role as MemberRole;
  }

  async create(ownerId: string, body:{name:string;slug:string;motto?:string;description?:string;visibility?:string;regionCode?:string}) {
    const slug = body.slug.trim().toLowerCase();
    if (!/^[a-z0-9-]{3,40}$/.test(slug)) throw new BadRequestException('invalid_slug');
    if (!body.name?.trim()) throw new BadRequestException('name_required');
    const visibility = ['public','invite_only','private'].includes(body.visibility||'') ? body.visibility : 'public';
    return this.db.tx(async c => {
      const q = await c.query(`INSERT INTO colonies(owner_user_id,name,slug,motto,description,visibility,region_code)
        VALUES($1,$2,$3,$4,$5,$6,$7) RETURNING *`,[
        ownerId,body.name.trim().slice(0,60),slug,(body.motto||'').slice(0,120),(body.description||'').slice(0,500),visibility,body.regionCode?.slice(0,8).toUpperCase()||null
      ]);
      const colony = q.rows[0];
      await c.query(`INSERT INTO colony_members(colony_id,user_id,role) VALUES($1,$2,'owner')`,[colony.id,ownerId]);
      return colony;
    });
  }

  async get(id: string) {
    const q = await this.db.query(`SELECT c.*, count(cm.user_id)::int AS members
      FROM colonies c LEFT JOIN colony_members cm ON cm.colony_id=c.id
      WHERE c.id=$1 GROUP BY c.id`,[id]);
    if (!q.rowCount) throw new NotFoundException('colony_not_found');
    return q.rows[0];
  }

  async mine(userId:string) {
    const q = await this.db.query(`SELECT c.*,cm.role,
      (SELECT count(*)::int FROM colony_members x WHERE x.colony_id=c.id) AS members
      FROM colony_members cm JOIN colonies c ON c.id=cm.colony_id
      WHERE cm.user_id=$1 ORDER BY cm.joined_at DESC`,[userId]);
    return q.rows;
  }

  async members(colonyId:string) {
    const q = await this.db.query(`SELECT u.id,u.username,u.display_name,u.avatar_key,cm.role,cm.joined_at,
      COALESCE(ps.level,1) AS level,COALESCE(ps.skill_rating,1000) AS skill_rating
      FROM colony_members cm JOIN users u ON u.id=cm.user_id
      LEFT JOIN player_stats ps ON ps.user_id=u.id
      WHERE cm.colony_id=$1 ORDER BY CASE cm.role WHEN 'owner' THEN 0 WHEN 'captain' THEN 1 WHEN 'recruiter' THEN 2 ELSE 3 END,u.display_name`,[colonyId]);
    return q.rows;
  }

  async join(userId: string, colonyId: string) {
    return this.db.tx(async c => {
      const lock = await c.query(`SELECT member_limit,visibility FROM colonies WHERE id=$1 FOR UPDATE`,[colonyId]);
      if (!lock.rowCount) throw new NotFoundException('colony_not_found');
      if (lock.rows[0].visibility !== 'public') throw new ForbiddenException('invite_required');
      const count = await c.query(`SELECT count(*)::int n FROM colony_members WHERE colony_id=$1`,[colonyId]);
      if (count.rows[0].n >= lock.rows[0].member_limit) throw new BadRequestException('colony_full');
      await c.query(`INSERT INTO colony_members(colony_id,user_id) VALUES($1,$2) ON CONFLICT DO NOTHING`,[colonyId,userId]);
      return { ok:true };
    });
  }

  async leave(userId:string,colonyId:string) {
    return this.db.tx(async c=>{
      const member = await c.query(`SELECT role FROM colony_members WHERE colony_id=$1 AND user_id=$2 FOR UPDATE`,[colonyId,userId]);
      if(!member.rowCount) throw new NotFoundException('not_a_member');
      if(member.rows[0].role==='owner') throw new BadRequestException('owner_must_transfer_or_delete');
      await c.query(`DELETE FROM colony_members WHERE colony_id=$1 AND user_id=$2`,[colonyId,userId]);
      return {ok:true};
    });
  }

  async updateRole(actorId:string,colonyId:string,targetUserId:string,role:MemberRole) {
    if(!MEMBER_ROLES.includes(role) || role==='owner') throw new BadRequestException('invalid_role');
    return this.db.tx(async c=>{
      const actorRole = await this.assertRole(c,colonyId,actorId,['owner','captain']);
      const target = await c.query(`SELECT role FROM colony_members WHERE colony_id=$1 AND user_id=$2 FOR UPDATE`,[colonyId,targetUserId]);
      if(!target.rowCount) throw new NotFoundException('member_not_found');
      if(target.rows[0].role==='owner') throw new ForbiddenException('cannot_change_owner');
      if(actorRole==='captain' && ['captain','recruiter'].includes(role)) throw new ForbiddenException('owner_only_role');
      await c.query(`UPDATE colony_members SET role=$3 WHERE colony_id=$1 AND user_id=$2`,[colonyId,targetUserId,role]);
      return {ok:true,role};
    });
  }

  async removeMember(actorId:string,colonyId:string,targetUserId:string) {
    if(actorId===targetUserId) throw new BadRequestException('use_leave_endpoint');
    return this.db.tx(async c=>{
      await this.assertRole(c,colonyId,actorId,['owner','captain']);
      const target=await c.query(`SELECT role FROM colony_members WHERE colony_id=$1 AND user_id=$2 FOR UPDATE`,[colonyId,targetUserId]);
      if(!target.rowCount) throw new NotFoundException('member_not_found');
      if(target.rows[0].role==='owner') throw new ForbiddenException('cannot_remove_owner');
      await c.query(`DELETE FROM colony_members WHERE colony_id=$1 AND user_id=$2`,[colonyId,targetUserId]);
      return {ok:true};
    });
  }

  async createInvite(actorId:string,colonyId:string,invitedUserId?:string) {
    return this.db.tx(async c=>{
      await this.assertRole(c,colonyId,actorId,['owner','captain','recruiter']);
      const token=randomBytes(24).toString('base64url');
      const tokenHash=createHash('sha256').update(token).digest('hex');
      const q=await c.query(`INSERT INTO colony_invites(colony_id,created_by,invited_user_id,token_hash,expires_at)
        VALUES($1,$2,$3,$4,now()+interval '7 days') RETURNING id,expires_at`,[colonyId,actorId,invitedUserId||null,tokenHash]);
      const base=(process.env.PUBLIC_APP_URL||'https://play.example.com').replace(/\/$/,'');
      return {...q.rows[0],token,inviteUrl:`${base}/join/colony/${token}`,qrPayload:`colonyclash://join/colony/${token}`};
    });
  }

  async acceptInvite(userId:string,token:string) {
    const tokenHash=createHash('sha256').update(token).digest('hex');
    return this.db.tx(async c=>{
      const iq=await c.query(`SELECT * FROM colony_invites WHERE token_hash=$1 AND status='open' FOR UPDATE`,[tokenHash]);
      if(!iq.rowCount) throw new NotFoundException('invite_not_found');
      const invite=iq.rows[0];
      if(new Date(invite.expires_at).getTime()<Date.now()) {
        await c.query(`UPDATE colony_invites SET status='expired' WHERE id=$1`,[invite.id]);
        throw new BadRequestException('invite_expired');
      }
      if(invite.invited_user_id && invite.invited_user_id!==userId) throw new ForbiddenException('invite_for_different_user');
      const lock=await c.query(`SELECT member_limit FROM colonies WHERE id=$1 FOR UPDATE`,[invite.colony_id]);
      const count=await c.query(`SELECT count(*)::int n FROM colony_members WHERE colony_id=$1`,[invite.colony_id]);
      if(count.rows[0].n>=lock.rows[0].member_limit) throw new BadRequestException('colony_full');
      const joined=await c.query(`INSERT INTO colony_members(colony_id,user_id) VALUES($1,$2) ON CONFLICT DO NOTHING RETURNING user_id`,[invite.colony_id,userId]);
      await c.query(`UPDATE colony_invites SET status='accepted',accepted_at=now() WHERE id=$1`,[invite.id]);
      if(joined.rowCount){
        await c.query(`INSERT INTO player_stats(user_id,recruiter_points) VALUES($1,25) ON CONFLICT(user_id) DO UPDATE SET recruiter_points=player_stats.recruiter_points+25,updated_at=now()`,[invite.created_by]);
        await c.query(`UPDATE colonies SET xp=xp+100,level=GREATEST(level,1+floor((xp+100)/1000.0)::int) WHERE id=$1`,[invite.colony_id]);
      }
      return {ok:true,colonyId:invite.colony_id,recruiterReward:joined.rowCount?25:0,colonyXp:joined.rowCount?100:0};
    });
  }
}
