import { Body, Controller, Get, Post, Query, UseGuards } from '@nestjs/common';
import { CurrentUser, JwtGuard } from '../../common/current-user';
import { ShopService } from './shop.service';
@Controller('shop')
export class ShopController{
 constructor(private readonly shop:ShopService){}
 @Get() catalog(@Query('market') market?:string){return this.shop.catalog(market||'play_global')}
 @UseGuards(JwtGuard) @Post('buy') buy(@CurrentUser() u:any,@Body() b:any){return this.shop.purchase(u.sub,String(b.sku||''),Number(b.quantity||1),b.targetId?String(b.targetId):undefined)}
}
