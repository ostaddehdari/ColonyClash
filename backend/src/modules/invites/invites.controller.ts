import { Body, Controller, Post, UseGuards } from '@nestjs/common';
import { InvitesService } from './invites.service';
import { CurrentUser, JwtGuard } from '../../common/current-user';
@Controller('invites') @UseGuards(JwtGuard)
export class InvitesController{
 constructor(private readonly invites:InvitesService){}
 @Post() create(@CurrentUser()u:any,@Body()b:{colonyId?:string;contactLabel?:string;phone?:string}){return this.invites.create(u.sub,b.colonyId||null,b.contactLabel,b.phone)}
}
