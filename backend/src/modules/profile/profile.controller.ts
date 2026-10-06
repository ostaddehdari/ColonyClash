import { Body, Controller, Get, Param, Patch, Post, UseGuards } from '@nestjs/common';
import { CurrentUser, JwtGuard } from '../../common/current-user';
import { ProfileService } from './profile.service';

@Controller('profile')
export class ProfileController {
  constructor(private readonly profile: ProfileService) {}
  @UseGuards(JwtGuard) @Get('me') me(@CurrentUser() u:any){ return this.profile.me(u.sub); }
  @UseGuards(JwtGuard) @Patch('me') update(@CurrentUser()u:any,@Body()b:any){ return this.profile.update(u.sub,b); }
  @UseGuards(JwtGuard) @Post('referral-code') referral(@CurrentUser()u:any){ return this.profile.ensureReferralCode(u.sub); }
  @Get('u/:username') publicProfile(@Param('username')username:string){ return this.profile.publicByUsername(username); }
}
