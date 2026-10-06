import { Body, Controller, Delete, Get, Param, Post, Query, UseGuards } from '@nestjs/common';
import { CurrentUser, JwtGuard } from '../../common/current-user';
import { MatchmakingService } from './matchmaking.service';

@UseGuards(JwtGuard)
@Controller('matchmaking')
export class MatchmakingController{
  constructor(private readonly mm:MatchmakingService){}
  @Post('quick') quick(@CurrentUser()u:any,@Body()b:any){ return this.mm.quick(u.sub,b); }
  @Get('tickets/:id') ticket(@CurrentUser()u:any,@Param('id')id:string){ return this.mm.ticket(u.sub,id); }
  @Delete('tickets/:id') cancel(@CurrentUser()u:any,@Param('id')id:string){ return this.mm.cancel(u.sub,id); }
  @Get('reconnect') reconnect(@CurrentUser()u:any){ return this.mm.reconnect(u.sub); }
  @Get('presence/friends') friendsPresence(@CurrentUser()u:any){ return this.mm.friendPresence(u.sub); }
  @Post('presence/heartbeat') heartbeat(@CurrentUser()u:any){ return this.mm.heartbeat(u.sub); }
  @Post('invites') invite(@CurrentUser()u:any,@Body()b:any){ return this.mm.createInvite(u.sub,b); }
  @Get('invites/:code') inviteInfo(@CurrentUser()u:any,@Param('code')code:string){ return this.mm.inviteInfo(u.sub,code); }
  @Post('invites/:code/accept') acceptInvite(@CurrentUser()u:any,@Param('code')code:string){ return this.mm.acceptInvite(u.sub,code); }
  @Post('challenges/:code/accept-play') acceptChallenge(@CurrentUser()u:any,@Param('code')code:string){ return this.mm.acceptChallengeAndPlay(u.sub,code); }
  @Get('rating') rating(@CurrentUser()u:any,@Query('game')game?:string){ return this.mm.rating(u.sub,game); }
}
