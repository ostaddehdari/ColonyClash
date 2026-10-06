import { Injectable } from '@nestjs/common';
import { DbService } from '../../db/db.service';
import { createHash, randomBytes } from 'node:crypto';
@Injectable()
export class InvitesService {
 constructor(private readonly db:DbService){}
 async create(userId:string,colonyId:string|null,label?:string,phone?:string){
   const token=randomBytes(24).toString('base64url');
   const tokenHash=createHash('sha256').update(token).digest('hex');
   const phoneHash=phone ? createHash('sha256').update(phone.replace(/\D/g,'')).digest('hex') : null;
   const q=await this.db.query(`INSERT INTO invites(inviter_user_id,colony_id,token_hash,selected_contact_label,selected_phone_hash,expires_at)
      VALUES($1,$2,$3,$4,$5,now()+interval '7 days') RETURNING id,expires_at`,[userId,colonyId,tokenHash,label?.slice(0,80)||null,phoneHash]);
   return {...q.rows[0], token, invitePath:`/i/${token}`};
 }
}
