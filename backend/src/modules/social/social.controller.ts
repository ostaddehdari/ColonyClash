import { Body, Controller, Get, Param, Post, Query, UseGuards } from '@nestjs/common';
import { CurrentUser, JwtGuard } from '../../common/current-user';
import { SocialService } from './social.service';

@Controller('social')
export class SocialController {
  constructor(private readonly social: SocialService) {}

  @UseGuards(JwtGuard) @Get('users/search') search(@CurrentUser()u:any,@Query('q')q=''){ return this.social.searchUsers(u.sub,q); }
  @UseGuards(JwtGuard) @Post('friends/:id/request') request(@CurrentUser()u:any,@Param('id')id:string){ return this.social.requestFriend(u.sub,id); }
  @UseGuards(JwtGuard) @Post('friends/:id/accept') accept(@CurrentUser()u:any,@Param('id')id:string){ return this.social.acceptFriend(u.sub,id); }
  @UseGuards(JwtGuard) @Get('friends') friends(@CurrentUser()u:any){ return this.social.friends(u.sub); }

  @UseGuards(JwtGuard) @Post('squads') squad(@CurrentUser()u:any,@Body()b:{name:string;slug:string}){ return this.social.createSquad(u.sub,b.name,b.slug); }
  @UseGuards(JwtGuard) @Get('squads/mine') squads(@CurrentUser()u:any){ return this.social.mySquads(u.sub); }
  @UseGuards(JwtGuard) @Get('squads/:id/members') squadMembers(@Param('id')id:string){ return this.social.squadMembers(id); }
  @UseGuards(JwtGuard) @Post('squads/:id/invites') squadInvite(@CurrentUser()u:any,@Param('id')id:string,@Body()b:{userId?:string}){ return this.social.createSquadInvite(u.sub,id,b.userId); }
  @UseGuards(JwtGuard) @Post('squads/invites/:token/accept') acceptSquadInvite(@CurrentUser()u:any,@Param('token')token:string){ return this.social.acceptSquadInvite(u.sub,token); }

  @UseGuards(JwtGuard) @Post('rivalries') rivalry(@CurrentUser()u:any,@Body()b:any){ return this.social.createRivalry(u.sub,b); }
  @UseGuards(JwtGuard) @Get('rivalries/mine') rivalries(@CurrentUser()u:any){ return this.social.myRivalries(u.sub); }

  @UseGuards(JwtGuard) @Post('challenges') challenge(@CurrentUser()u:any,@Body()b:any){ return this.social.createChallenge(u.sub,b); }
  @UseGuards(JwtGuard) @Get('challenges/incoming') incoming(@CurrentUser()u:any){ return this.social.incomingChallenges(u.sub); }
  @Get('challenges/code/:code') byCode(@Param('code')code:string){ return this.social.challengeByCode(code); }
  @UseGuards(JwtGuard) @Post('challenges/code/:code/accept') acceptChallenge(@CurrentUser()u:any,@Param('code')code:string){ return this.social.acceptChallenge(u.sub,code); }
  @UseGuards(JwtGuard) @Post('challenges/:id/cancel') cancel(@CurrentUser()u:any,@Param('id')id:string){ return this.social.cancelChallenge(u.sub,id); }
  @UseGuards(JwtGuard) @Post('challenges/:id/shared') shared(@CurrentUser()u:any,@Param('id')id:string){ return this.social.markShared(u.sub,id); }
}
