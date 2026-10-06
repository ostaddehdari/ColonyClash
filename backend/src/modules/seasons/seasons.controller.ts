import { Body, Controller, Get, Param, Post, UseGuards } from '@nestjs/common';
import { CurrentUser, JwtGuard } from '../../common/current-user';
import { SeasonsService } from './seasons.service';

@UseGuards(JwtGuard)
@Controller('seasons')
export class SeasonsController{
  constructor(private readonly seasons:SeasonsService){}
  @Get('active') active(@CurrentUser()u:any){ return this.seasons.active(u.sub); }
  @Get('history') history(){ return this.seasons.history(); }
  @Get(':id/leaderboard') leaderboard(@Param('id')id:string){ return this.seasons.leaderboard(id); }
}

@UseGuards(JwtGuard)
@Controller('territories')
export class TerritoriesController{
  constructor(private readonly seasons:SeasonsService){}
  @Get('map') map(@CurrentUser()u:any){ return this.seasons.map(u.sub); }
  @Get('battles/mine') battles(@CurrentUser()u:any){ return this.seasons.myBattles(u.sub); }
  @Get(':code/history') territoryHistory(@Param('code')code:string){ return this.seasons.territoryHistory(code); }
  @Post('sync') sync(@CurrentUser()u:any){ return this.seasons.sync(u.sub); }
  @Post(':code/claim') claim(@CurrentUser()u:any,@Param('code')code:string,@Body()b:any){ return this.seasons.claimNeutral(u.sub,code,b); }
  @Post(':code/challenge') challenge(@CurrentUser()u:any,@Param('code')code:string,@Body()b:any){ return this.seasons.challenge(u.sub,code,b); }
}
