import { Body, Controller, Get, Param, Post, UseGuards } from '@nestjs/common';
import { CurrentUser, JwtGuard } from '../../common/current-user';
import { GamesService } from './games.service';

@Controller('games')
export class GamesController{
  constructor(private readonly games:GamesService){}
  @Get() catalog(){ return this.games.catalog(); }
  @UseGuards(JwtGuard) @Post('matches') create(@CurrentUser()u:any,@Body()b:any){ return this.games.create(u.sub,b); }
  @UseGuards(JwtGuard) @Post('matches/:id/join') join(@CurrentUser()u:any,@Param('id')id:string){ return this.games.join(u.sub,id); }
  @UseGuards(JwtGuard) @Get('matches/:id') view(@CurrentUser()u:any,@Param('id')id:string){ return this.games.view(u.sub,id); }
  @UseGuards(JwtGuard) @Post('matches/:id/actions') action(@CurrentUser()u:any,@Param('id')id:string,@Body()b:any){ return this.games.action(u.sub,id,b); }
  @UseGuards(JwtGuard) @Get('matches/:id/actions') actions(@CurrentUser()u:any,@Param('id')id:string){ return this.games.actions(u.sub,id); }
  @UseGuards(JwtGuard) @Post('matches/:id/rematch') rematch(@CurrentUser()u:any,@Param('id')id:string){ return this.games.rematch(u.sub,id); }
}
