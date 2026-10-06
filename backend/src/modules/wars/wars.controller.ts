import { Body, Controller, Get, Param, Post, UseGuards } from '@nestjs/common';
import { CurrentUser, JwtGuard } from '../../common/current-user';
import { WarsService } from './wars.service';
@UseGuards(JwtGuard)
@Controller('wars')
export class WarsController{
  constructor(private readonly wars:WarsService){}
  @Post() create(@CurrentUser()u:any,@Body()b:any){ return this.wars.create(u.sub,b); }
  @Get('mine') mine(@CurrentUser()u:any){ return this.wars.mine(u.sub); }
  @Get('code/:code') byCode(@CurrentUser()u:any,@Param('code')code:string){ return this.wars.byCode(u.sub,code); }
  @Get(':id') get(@CurrentUser()u:any,@Param('id')id:string){ return this.wars.get(u.sub,id); }
  @Post(':id/accept') accept(@CurrentUser()u:any,@Param('id')id:string){ return this.wars.accept(u.sub,id); }
  @Post(':id/roster') roster(@CurrentUser()u:any,@Param('id')id:string,@Body()b:any){ return this.wars.setRoster(u.sub,id,b); }
  @Post(':id/start') start(@CurrentUser()u:any,@Param('id')id:string){ return this.wars.start(u.sub,id); }
  @Post(':id/sync') sync(@CurrentUser()u:any,@Param('id')id:string){ return this.wars.sync(u.sub,id); }
  @Post(':id/spectate') spectate(@CurrentUser()u:any,@Param('id')id:string){ return this.wars.spectate(u.sub,id); }
  @Post(':id/rematch') rematch(@CurrentUser()u:any,@Param('id')id:string){ return this.wars.rematch(u.sub,id); }
  @Post(':id/cancel') cancel(@CurrentUser()u:any,@Param('id')id:string){ return this.wars.cancel(u.sub,id); }
}
