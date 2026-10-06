import { Body, Controller, Delete, Get, Param, Patch, Post, UseGuards } from '@nestjs/common';
import { ColoniesService } from './colonies.service';
import { CurrentUser, JwtGuard } from '../../common/current-user';

@Controller('colonies')
export class ColoniesController {
  constructor(private readonly colonies: ColoniesService) {}

  @UseGuards(JwtGuard) @Post()
  create(@CurrentUser() u:any,@Body() b:any) { return this.colonies.create(u.sub,b); }

  @UseGuards(JwtGuard) @Get('mine')
  mine(@CurrentUser() u:any){ return this.colonies.mine(u.sub); }

  @Get(':id') get(@Param('id') id:string) { return this.colonies.get(id); }
  @Get(':id/members') members(@Param('id') id:string){ return this.colonies.members(id); }

  @UseGuards(JwtGuard) @Post(':id/join')
  join(@CurrentUser() u:any,@Param('id') id:string) { return this.colonies.join(u.sub,id); }

  @UseGuards(JwtGuard) @Post(':id/leave')
  leave(@CurrentUser()u:any,@Param('id')id:string){ return this.colonies.leave(u.sub,id); }

  @UseGuards(JwtGuard) @Patch(':id/members/:userId/role')
  role(@CurrentUser()u:any,@Param('id')id:string,@Param('userId')userId:string,@Body()b:{role:any}){
    return this.colonies.updateRole(u.sub,id,userId,b.role);
  }

  @UseGuards(JwtGuard) @Delete(':id/members/:userId')
  remove(@CurrentUser()u:any,@Param('id')id:string,@Param('userId')userId:string){
    return this.colonies.removeMember(u.sub,id,userId);
  }

  @UseGuards(JwtGuard) @Post(':id/invites')
  invite(@CurrentUser()u:any,@Param('id')id:string,@Body()b:{userId?:string}){
    return this.colonies.createInvite(u.sub,id,b.userId);
  }

  @UseGuards(JwtGuard) @Post('invites/:token/accept')
  acceptInvite(@CurrentUser()u:any,@Param('token')token:string){ return this.colonies.acceptInvite(u.sub,token); }
}
