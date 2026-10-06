import { BadRequestException, Injectable } from '@nestjs/common';
import { createHash, randomUUID } from 'node:crypto';
import { DbService } from '../../db/db.service';
@Injectable()
export class BillingService{
 constructor(private readonly db:DbService){}
 async products(market:string){
   const q=await this.db.query(`SELECT product_key,market,provider,provider_product_id,product_type,grant_payload FROM billing_products WHERE market=$1 AND active=true ORDER BY product_key`,[market]);
   return q.rows;
 }
 async receive(userId:string,b:any){
   const market=String(b.market||''); const provider=String(b.provider||''); const productKey=String(b.productKey||'');
   if(!['play_global','direct_iran','direct_china'].includes(market)) throw new BadRequestException('invalid_market');
   const valid=await this.db.query(`SELECT 1 FROM billing_products WHERE product_key=$1 AND market=$2 AND provider=$3 AND active=true`,[productKey,market,provider]);
   if(!valid.rowCount) throw new BadRequestException('unknown_product');
   const token=String(b.purchaseToken||'');
   const tokenHash=token?createHash('sha256').update(token).digest('hex'):null;
   const id=randomUUID();
   await this.db.query(`INSERT INTO purchase_receipts(id,user_id,market,provider,product_key,provider_order_id,purchase_token_hash,raw_meta)
     VALUES($1,$2,$3,$4,$5,$6,$7,$8) ON CONFLICT DO NOTHING`,[id,userId,market,provider,productKey,b.providerOrderId?String(b.providerOrderId):null,tokenHash,JSON.stringify({clientState:String(b.clientState||''),receivedFrom:'app'})]);
   return {ok:true,state:'received',grant:false,message:'provider_verification_required'};
 }
}
