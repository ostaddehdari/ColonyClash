import { Module } from '@nestjs/common';
import { DbModule } from '../../db/db.module';
import { GamesModule } from '../games/games.module';
import { WarsController } from './wars.controller';
import { WarsService } from './wars.service';
@Module({imports:[DbModule,GamesModule],controllers:[WarsController],providers:[WarsService],exports:[WarsService]})
export class WarsModule{}
