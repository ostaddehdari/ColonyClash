import { Module } from '@nestjs/common';
import { DbModule } from '../../db/db.module';
import { GamesModule } from '../games/games.module';
import { MatchmakingController } from './matchmaking.controller';
import { MatchmakingService } from './matchmaking.service';
@Module({imports:[DbModule,GamesModule],controllers:[MatchmakingController],providers:[MatchmakingService],exports:[MatchmakingService]})
export class MatchmakingModule{}
