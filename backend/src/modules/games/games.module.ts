import { Module } from '@nestjs/common';
import { DbModule } from '../../db/db.module';
import { GameRegistryService } from './game-registry.service';
import { GamesController } from './games.controller';
import { GamesService } from './games.service';

@Module({imports:[DbModule],controllers:[GamesController],providers:[GameRegistryService,GamesService],exports:[GamesService,GameRegistryService]})
export class GamesModule{}
