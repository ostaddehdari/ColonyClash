import { Module } from '@nestjs/common';
import { DbModule } from '../../db/db.module';
import { EconomyService } from './economy.service';
import { EconomyController } from './economy.controller';
@Module({imports:[DbModule],providers:[EconomyService],controllers:[EconomyController],exports:[EconomyService]})
export class EconomyModule{}
