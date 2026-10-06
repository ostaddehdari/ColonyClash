import { BadRequestException, ForbiddenException, Injectable, NotFoundException } from '@nestjs/common';
import { DbService } from '../../db/db.service';
import { EconomyService } from '../economy/economy.service';
import { randomUUID } from 'node:crypto';

@Injectable()
export class GiftsService {
  constructor(private readonly db:DbService, private readonly economy:EconomyService){}

  async send(sender:string,receiver:string,sku:string,message=''){
    if(sender===receiver) throw new BadRequestException('cannot_gift_self');
    return this.db.tx(async c=>{
      const blocked=await c.query(`SELECT 1 FROM user_blocks WHERE (blocker_user_id=$1 AND blocked_user_id=$2) OR (blocker_user_id=$2 AND blocked_user_id=$1) LIMIT 1`,[sender,receiver]);
      if(blocked.rowCount) throw new ForbiddenException('gift_blocked');
      const item=await this.economy.storeItem(c,sku);
      if(item.type!=='gift') throw new BadRequestException('sku_not_gift');
      const id=randomUUID();
      await this.economy.spend(c,sender,item.currency,Number(item.price),'gift_send','gift',id,`gift:${id}:spend`);
      const q=await c.query(`INSERT INTO gifts(id,sender_user_id,receiver_user_id,sku,message) VALUES($1,$2,$3,$4,$5) RETURNING *`,[id,sender,receiver,sku,message.slice(0,120)]);
      return q.rows[0];
    });
  }

  async inbox(userId:string){
    const q=await this.db.query(`SELECT g.*,s.title,s.description,s.icon_key,s.payload,u.display_name sender_name,(gc.gift_id IS NOT NULL) claimed
      FROM gifts g JOIN store_items s ON s.sku=g.sku JOIN users u ON u.id=g.sender_user_id LEFT JOIN gift_claims gc ON gc.gift_id=g.id
      WHERE receiver_user_id=$1 ORDER BY g.created_at DESC LIMIT 100`,[userId]);
    return q.rows;
  }

  async open(userId:string,giftId:string){
    return this.db.tx(async c=>{
      const q=await c.query(`SELECT g.*,s.payload,s.title FROM gifts g JOIN store_items s ON s.sku=g.sku WHERE g.id=$1 FOR UPDATE`,[giftId]);
      if(!q.rowCount) throw new NotFoundException('gift_not_found');
      const gift=q.rows[0];
      if(gift.receiver_user_id!==userId) throw new ForbiddenException('not_your_gift');
      const prior=await c.query(`SELECT rewards,claimed_at FROM gift_claims WHERE gift_id=$1`,[giftId]);
      if(prior.rowCount) return {ok:true,duplicate:true,rewards:prior.rows[0].rewards,claimedAt:prior.rows[0].claimed_at};
      const allowed=['coin','energy','love','power'];
      const rewards:any={};
      for(const currency of allowed){
        const amount=Number(gift.payload?.[currency]||0);
        if(Number.isSafeInteger(amount)&&amount>0){
          const accountQ=await c.query(`INSERT INTO wallet_accounts(owner_type,owner_id,currency) VALUES('user',$1,$2)
            ON CONFLICT(owner_type,owner_id,currency) DO UPDATE SET currency=EXCLUDED.currency RETURNING id`,[userId,currency]);
          await c.query(`INSERT INTO wallet_ledger(account_id,amount,reason,reference_type,reference_id,idempotency_key)
            VALUES($1,$2,'gift_open','gift',$3,$4) ON CONFLICT(idempotency_key) DO NOTHING`,[accountQ.rows[0].id,amount,giftId,`gift:${giftId}:${currency}`]);
          rewards[currency]=amount;
        }
      }
      await c.query(`INSERT INTO gift_claims(gift_id,receiver_user_id,rewards) VALUES($1,$2,$3)`,[giftId,userId,JSON.stringify(rewards)]);
      await c.query(`UPDATE gifts SET opened_at=COALESCE(opened_at,now()) WHERE id=$1`,[giftId]);
      return {ok:true,duplicate:false,title:gift.title,rewards};
    });
  }
}
