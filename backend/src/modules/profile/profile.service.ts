import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common';
import { randomBytes } from 'node:crypto';
import { DbService } from '../../db/db.service';

@Injectable()
export class ProfileService {
  constructor(private readonly db: DbService) {}

  async me(userId: string) {
    const q = await this.db.query(`SELECT u.id,u.username,u.display_name,u.locale,u.country_code,u.referral_code,u.avatar_key,u.created_at,
      COALESCE(ps.level,1) level,COALESCE(ps.xp,0) xp,COALESCE(ps.wins,0) wins,COALESCE(ps.losses,0) losses,
      COALESCE(ps.draws,0) draws,COALESCE(ps.win_streak,0) win_streak,COALESCE(ps.best_win_streak,0) best_win_streak,
      COALESCE(ps.skill_rating,1000) skill_rating,COALESCE(ps.recruiter_points,0) recruiter_points
      FROM users u LEFT JOIN player_stats ps ON ps.user_id=u.id WHERE u.id=$1`, [userId]);
    if (!q.rowCount) throw new NotFoundException('user_not_found');
    return q.rows[0];
  }

  async publicByUsername(username:string) {
    const q=await this.db.query(`SELECT u.id,u.username,u.display_name,u.avatar_key,u.country_code,u.created_at,
      COALESCE(ps.level,1) level,COALESCE(ps.wins,0) wins,COALESCE(ps.losses,0) losses,COALESCE(ps.draws,0) draws,
      COALESCE(ps.best_win_streak,0) best_win_streak,COALESCE(ps.skill_rating,1000) skill_rating,
      COALESCE(ps.recruiter_points,0) recruiter_points
      FROM users u LEFT JOIN player_stats ps ON ps.user_id=u.id WHERE lower(u.username)=lower($1) AND u.status='active'`,[username.replace(/^@/,'')]);
    if(!q.rowCount) throw new NotFoundException('user_not_found');
    const user=q.rows[0];
    const colonies=await this.db.query(`SELECT c.id,c.name,c.slug,c.emblem_key,cm.role,c.level,c.war_points
      FROM colony_members cm JOIN colonies c ON c.id=cm.colony_id WHERE cm.user_id=$1 ORDER BY c.war_points DESC LIMIT 10`,[user.id]);
    const squads=await this.db.query(`SELECT s.id,s.name,s.slug,sm.role FROM squad_members sm JOIN squads s ON s.id=sm.squad_id WHERE sm.user_id=$1 ORDER BY sm.joined_at DESC LIMIT 10`,[user.id]);
    return {...user,colonies:colonies.rows,squads:squads.rows};
  }

  async update(userId: string, body: { displayName?: string; locale?: string; countryCode?: string; avatarKey?: string }) {
    const locale = body.locale?.toLowerCase();
    if (locale && !['en','fa','fr','ar','zh'].includes(locale)) throw new BadRequestException('unsupported_locale');
    const q = await this.db.query(`UPDATE users SET
      display_name=COALESCE($2,display_name), locale=COALESCE($3,locale), country_code=COALESCE($4,country_code), avatar_key=COALESCE($5,avatar_key)
      WHERE id=$1 RETURNING id,username,display_name,locale,country_code,referral_code,avatar_key`,
      [userId, body.displayName?.slice(0,80) || null, locale || null, body.countryCode?.slice(0,2).toUpperCase() || null, body.avatarKey?.slice(0,80) || null]);
    return q.rows[0];
  }

  async ensureReferralCode(userId: string) {
    const current = await this.db.query<{referral_code:string|null}>(`SELECT referral_code FROM users WHERE id=$1`, [userId]);
    if (!current.rowCount) throw new NotFoundException('user_not_found');
    if (current.rows[0].referral_code) return { referralCode: current.rows[0].referral_code };
    for (let i=0;i<5;i++) {
      const code = randomBytes(5).toString('base64url').replace(/[-_]/g,'').slice(0,8).toUpperCase();
      try {
        const q = await this.db.query(`UPDATE users SET referral_code=$2 WHERE id=$1 AND referral_code IS NULL RETURNING referral_code`, [userId, code]);
        if (q.rowCount) return { referralCode: q.rows[0].referral_code };
      } catch(e:any) { if(e?.code!=='23505' || i===4) throw e; }
    }
    throw new BadRequestException('referral_code_generation_failed');
  }
}
