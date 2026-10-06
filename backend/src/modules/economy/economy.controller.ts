import { Controller, Get, Query, UseGuards } from '@nestjs/common';
import { EconomyService } from './economy.service';
import { CurrentUser, JwtGuard } from '../../common/current-user';
@Controller('wallet')
export class EconomyController {
 constructor(private readonly economy:EconomyService){}
 @UseGuards(JwtGuard) @Get() balance(@CurrentUser() u:any){return this.economy.balance(u.sub)}
 @UseGuards(JwtGuard) @Get('ledger') ledger(@CurrentUser() u:any,@Query('limit') limit?:string){return this.economy.ledger(u.sub,Number(limit||50))}
}
