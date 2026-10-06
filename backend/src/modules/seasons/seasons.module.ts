import { Module } from '@nestjs/common';
import { DbModule } from '../../db/db.module';
import { WarsModule } from '../wars/wars.module';
import { SeasonsController, TerritoriesController } from './seasons.controller';
import { SeasonsService } from './seasons.service';
@Module({imports:[DbModule,WarsModule],controllers:[SeasonsController,TerritoriesController],providers:[SeasonsService],exports:[SeasonsService]})
export class SeasonsModule{}
