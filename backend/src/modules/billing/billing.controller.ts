import { Body, Controller, Get, Post, Query, UseGuards } from '@nestjs/common';
import { CurrentUser, JwtGuard } from '../../common/current-user';
import { BillingService } from './billing.service';
@Controller('billing')
export class BillingController{
 constructor(private readonly billing:BillingService){}
 @Get('products') products(@Query('market') market?:string){return this.billing.products(market||'play_global')}
 @UseGuards(JwtGuard) @Post('receipts') receipt(@CurrentUser() u:any,@Body() b:any){return this.billing.receive(u.sub,b)}
}
