import { BadRequestException, ForbiddenException, Injectable } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { DbService } from '../../db/db.service';
import { EconomyService } from '../economy/economy.service';
import { InventoryService } from '../inventory/inventory.service';

@Injectable()
export class ShopService{
 constructor(private readonly db:DbService,private readonly economy:EconomyService,private readonly inventory:InventoryService){}

 async catalog(market='play_global'){
   const q=await this.db.query(`SELECT s.sku,s.type,s.title,s.description,s.icon_key,s.currency AS virtual_currency,s.price AS virtual_price,s.payload,s.sort_order,
      p.currency AS cash_currency,p.amount_minor AS cash_amount_minor,p.provider
      FROM store_items s LEFT JOIN price_catalog p ON p.sku=s.sku AND p.market=$1 AND p.active=true
      WHERE s.active=true ORDER BY s.sort_order,s.sku`,[market]);
   return q.rows;
 }

 async purchase(userId:string,sku:string,quantity=1,targetId?:string){
   if(!Number.isSafeInteger(quantity)||quantity<1||quantity>20) throw new BadRequestException('invalid_quantity');
   return this.db.tx(async c=>{
     const item=await this.economy.storeItem(c,sku);
     if(item.type==='gift') throw new BadRequestException('use_gift_send');
     if(item.type==='colony_upgrade' && !targetId) throw new BadRequestException('colony_target_required');
     const orderId=randomUUID();
     const total=Number(item.price)*quantity;
     await this.economy.spend(c,userId,item.currency,total,'store_purchase','store_order',orderId,`store:${orderId}`);
     await c.query(`INSERT INTO store_orders(id,user_id,sku,quantity,unit_price,virtual_currency,target_type,target_id,status)
       VALUES($1,$2,$3,$4,$5,$6,$7,$8,'completed')`,[orderId,userId,sku,quantity,Number(item.price),item.currency,item.type==='colony_upgrade'?'colony':'user',item.type==='colony_upgrade'?targetId:userId]);

     if(item.type==='booster' || item.type==='cosmetic'){
       await this.inventory.add(c,userId,sku,quantity,'store_purchase','store_order',orderId,`inventory:order:${orderId}`);
     } else if(item.type==='pass'){
       const entitlement=String(item.payload?.entitlement||sku);
       await c.query(`INSERT INTO user_entitlements(user_id,entitlement_key,source_sku) VALUES($1,$2,$3)
         ON CONFLICT(user_id,entitlement_key) DO UPDATE SET source_sku=EXCLUDED.source_sku`,[userId,entitlement,sku]);
     } else if(item.type==='colony_upgrade'){
       const colony=await c.query(`SELECT owner_user_id,member_limit FROM colonies WHERE id=$1 FOR UPDATE`,[targetId]);
       if(!colony.rowCount) throw new BadRequestException('colony_not_found');
       if(colony.rows[0].owner_user_id!==userId) throw new ForbiddenException('owner_required_for_upgrade');
       const slots=Number(item.payload?.member_slots||0);
       if(slots>0){
         await c.query(`UPDATE colonies SET member_limit=LEAST(10000,member_limit+$2) WHERE id=$1`,[targetId,slots*quantity]);
       }
       const entitlement=item.payload?.entitlement;
       if(entitlement){
         await c.query(`UPDATE colonies SET banner_key=COALESCE(banner_key,$2) WHERE id=$1`,[targetId,String(entitlement)]);
       }
       await c.query(`INSERT INTO colony_upgrades(colony_id,purchased_by,sku,effect) VALUES($1,$2,$3,$4)`,[targetId,userId,sku,JSON.stringify(item.payload||{})]);
     }
     return {ok:true,orderId,sku,quantity,total,virtualCurrency:item.currency};
   });
 }
}
