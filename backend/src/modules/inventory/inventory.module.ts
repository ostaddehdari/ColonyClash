import { Module } from '@nestjs/common';
import { DbModule } from '../../db/db.module';
import { InventoryService } from './inventory.service';
import { InventoryController } from './inventory.controller';
@Module({imports:[DbModule],providers:[InventoryService],controllers:[InventoryController],exports:[InventoryService]})
export class InventoryModule{}
