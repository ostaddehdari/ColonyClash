import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common';
import { DbService } from '../../db/db.service';
import type { PoolClient } from 'pg';

type Currency='coin'|'gem'|'energy'|'love'|'power';
type OwnerType='user'|'colony'|'system';

@Injectable()
export class EconomyService {
  constructor(private readonly db: DbService) {}

  private async account(c:PoolClient, ownerType:OwnerType, ownerId:string|null, currency:Currency) {
    const q=await c.query(`INSERT INTO wallet_accounts(owner_type,owner_id,currency) VALUES($1,$2,$3)
      ON CONFLICT(owner_type,owner_id,currency) DO UPDATE SET currency=EXCLUDED.currency RETURNING id`,[ownerType,ownerId,currency]);
    return q.rows[0].id as string;
  }

  async balance(userId:string) {
    const q=await this.db.query(`SELECT wa.currency, COALESCE(sum(wl.amount),0)::bigint AS balance
      FROM wallet_accounts wa LEFT JOIN wallet_ledger wl ON wl.account_id=wa.id
      WHERE wa.owner_type='user' AND wa.owner_id=$1 GROUP BY wa.currency`,[userId]);
    const out:any={coin:0,gem:0,energy:0,love:0,power:0};
    for(const r of q.rows) out[r.currency]=Number(r.balance);
    return out;
  }

  async ledger(userId:string, limit=50) {
    const q=await this.db.query(`SELECT wl.id,wa.currency,wl.amount,wl.reason,wl.reference_type,wl.reference_id,wl.created_at
      FROM wallet_ledger wl JOIN wallet_accounts wa ON wa.id=wl.account_id
      WHERE wa.owner_type='user' AND wa.owner_id=$1 ORDER BY wl.created_at DESC LIMIT $2`,[userId,Math.min(Math.max(limit,1),200)]);
    return q.rows;
  }

  async grant(userId:string,currency:Currency,amount:number,reason:string,idempotencyKey:string,referenceType='grant',referenceId=idempotencyKey) {
    if (!Number.isSafeInteger(amount)||amount<=0) throw new BadRequestException('invalid_amount');
    return this.db.tx(async c=>{
      const accountId=await this.account(c,'user',userId,currency);
      await c.query(`INSERT INTO wallet_ledger(account_id,amount,reason,reference_type,reference_id,idempotency_key)
        VALUES($1,$2,$3,$4,$5,$6) ON CONFLICT(idempotency_key) DO NOTHING`,[accountId,amount,reason,referenceType,referenceId,idempotencyKey]);
      return {ok:true};
    });
  }

  async spend(c:PoolClient,userId:string,currency:Currency,amount:number,reason:string,referenceType:string,referenceId:string,idempotencyKey:string) {
    if(!Number.isSafeInteger(amount)||amount<=0) throw new BadRequestException('invalid_amount');
    const accountId=await this.account(c,'user',userId,currency);
    await c.query(`SELECT pg_advisory_xact_lock(hashtextextended($1,0))`,[`wallet:${accountId}`]);
    const prior=await c.query(`SELECT id FROM wallet_ledger WHERE idempotency_key=$1`,[idempotencyKey]);
    if(prior.rowCount) return {accountId,duplicate:true};
    const bal=await c.query(`SELECT COALESCE(sum(amount),0)::bigint AS n FROM wallet_ledger WHERE account_id=$1`,[accountId]);
    if(Number(bal.rows[0].n)<amount) throw new BadRequestException('insufficient_balance');
    await c.query(`INSERT INTO wallet_ledger(account_id,amount,reason,reference_type,reference_id,idempotency_key)
      VALUES($1,$2,$3,$4,$5,$6)`,[accountId,-amount,reason,referenceType,referenceId,idempotencyKey]);
    return {accountId,duplicate:false};
  }

  async storeItem(c:PoolClient,sku:string) {
    const q=await c.query(`SELECT * FROM store_items WHERE sku=$1 AND active=true FOR SHARE`,[sku]);
    if(!q.rowCount) throw new NotFoundException('sku_not_found');
    return q.rows[0];
  }

  async spendForSku(userId:string,sku:string,referenceId:string,quantity=1) {
    if(!Number.isSafeInteger(quantity)||quantity<1||quantity>100) throw new BadRequestException('invalid_quantity');
    return this.db.tx(async c=>{
      const item=await this.storeItem(c,sku);
      const price=Number(item.price)*quantity;
      await this.spend(c,userId,item.currency,price,'store_purchase','sku',referenceId,`purchase:${referenceId}`);
      return {...item,quantity,total_price:price};
    });
  }
}
