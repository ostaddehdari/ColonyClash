import { Body, Controller, Post, UseGuards } from '@nestjs/common';
import { DbService } from '../../db/db.service';
import { CurrentUser, JwtGuard } from '../../common/current-user';
@Controller('safety') @UseGuards(JwtGuard)
export class ModerationController{
 constructor(private readonly db:DbService){}
 @Post('block') async block(@CurrentUser()u:any,@Body()b:{userId:string}){
   await this.db.query(`INSERT INTO user_blocks(blocker_user_id,blocked_user_id) VALUES($1,$2) ON CONFLICT DO NOTHING`,[u.sub,b.userId]); return {ok:true};
 }
 @Post('report') async report(@CurrentUser()u:any,@Body()b:{targetType:string;targetId:string;targetUserId?:string;reason:string}){
   const q=await this.db.query(`INSERT INTO reports(reporter_user_id,target_user_id,target_type,target_id,reason) VALUES($1,$2,$3,$4,$5) RETURNING id,status`,[u.sub,b.targetUserId||null,b.targetType,b.targetId,b.reason.slice(0,500)]); return q.rows[0];
 }
}
