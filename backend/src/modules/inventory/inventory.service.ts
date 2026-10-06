import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common';
import { DbService } from '../../db/db.service';
import type { PoolClient } from 'pg';

@Injectable()
export class InventoryService {
  constructor(private readonly db:DbService){}

  async list(userId:string){
    const q=await this.db.query(`SELECT i.sku,i.quantity,s.type,s.title,s.description,s.icon_key,s.payload
      FROM inventory_items i JOIN store_items s ON s.sku=i.sku
      WHERE i.owner_type='user' AND i.owner_id=$1 AND i.quantity>0 ORDER BY s.sort_order,s.sku`,[userId]);
    return q.rows;
  }

  async add(c:PoolClient,userId:string,sku:string,qty:number,reason:string,referenceType:string,referenceId:string,idempotencyKey:string){
    if(!Number.isSafeInteger(qty)||qty<=0) throw new BadRequestException('invalid_quantity');
    const item=await c.query(`SELECT max_stack FROM store_items WHERE sku=$1 AND active=true`,[sku]);
    if(!item.rowCount) throw new NotFoundException('sku_not_found');
    const exists=await c.query(`SELECT id FROM inventory_ledger WHERE idempotency_key=$1`,[idempotencyKey]);
    if(exists.rowCount) return {ok:true,duplicate:true};
    const current=await c.query(`SELECT quantity FROM inventory_items WHERE owner_type='user' AND owner_id=$1 AND sku=$2 FOR UPDATE`,[userId,sku]);
    const currentQty=current.rowCount?Number(current.rows[0].quantity):0;
    const maxStack=Number(item.rows[0].max_stack);
    if(currentQty+qty>maxStack) throw new BadRequestException('inventory_stack_limit');
    await c.query(`INSERT INTO inventory_items(owner_type,owner_id,sku,quantity) VALUES('user',$1,$2,$3)
      ON CONFLICT(owner_type,owner_id,sku) DO UPDATE SET quantity=inventory_items.quantity+EXCLUDED.quantity,updated_at=now()`,[userId,sku,qty]);
    await c.query(`INSERT INTO inventory_ledger(owner_type,owner_id,sku,delta,reason,reference_type,reference_id,idempotency_key)
      VALUES('user',$1,$2,$3,$4,$5,$6,$7)`,[userId,sku,qty,reason,referenceType,referenceId,idempotencyKey]);
    return {ok:true,duplicate:false};
  }

  async consume(userId:string,sku:string,qty=1,contextId='manual'){
    if(!Number.isSafeInteger(qty)||qty<1||qty>100) throw new BadRequestException('invalid_quantity');
    return this.db.tx(async c=>{
      const idempotencyKey=`consume:${userId}:${contextId}:${sku}`;
      const prior=await c.query(`SELECT id FROM inventory_ledger WHERE idempotency_key=$1`,[idempotencyKey]);
      if(prior.rowCount) return {ok:true,duplicate:true};
      const q=await c.query(`SELECT quantity FROM inventory_items WHERE owner_type='user' AND owner_id=$1 AND sku=$2 FOR UPDATE`,[userId,sku]);
      if(!q.rowCount||Number(q.rows[0].quantity)<qty) throw new BadRequestException('insufficient_inventory');
      await c.query(`UPDATE inventory_items SET quantity=quantity-$3,updated_at=now() WHERE owner_type='user' AND owner_id=$1 AND sku=$2`,[userId,sku,qty]);
      await c.query(`INSERT INTO inventory_ledger(owner_type,owner_id,sku,delta,reason,reference_type,reference_id,idempotency_key)
        VALUES('user',$1,$2,$3,'consume','game_context',$4,$5)`,[userId,sku,-qty,contextId,idempotencyKey]);
      return {ok:true};
    });
  }
}
