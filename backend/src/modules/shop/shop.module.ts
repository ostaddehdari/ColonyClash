import { Module } from '@nestjs/common';
import { DbModule } from '../../db/db.module';
import { EconomyModule } from '../economy/economy.module';
import { InventoryModule } from '../inventory/inventory.module';
import { ShopService } from './shop.service';
import { ShopController } from './shop.controller';
@Module({imports:[DbModule,EconomyModule,InventoryModule],providers:[ShopService],controllers:[ShopController]})
export class ShopModule{}
