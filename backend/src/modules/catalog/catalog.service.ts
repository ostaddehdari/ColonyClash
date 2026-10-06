import { Injectable } from '@nestjs/common';
import { DbService } from '../../db/db.service';
@Injectable()
export class CatalogService{
 constructor(private readonly db:DbService){}
 async list(market:string){
  const m=['play_global','direct_iran','direct_china'].includes(market)?market:'play_global';
  const q=await this.db.query(`SELECT s.sku,s.type,s.title,s.payload,p.market,p.currency,p.amount_minor,p.provider
    FROM store_items s JOIN price_catalog p ON p.sku=s.sku
    WHERE s.active=true AND p.active=true AND p.market=$1 ORDER BY s.type,s.sku`,[m]);
  return q.rows;
 }
}
