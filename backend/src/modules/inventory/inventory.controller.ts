import { Body, Controller, Get, Post, UseGuards } from '@nestjs/common';
import { CurrentUser, JwtGuard } from '../../common/current-user';
import { InventoryService } from './inventory.service';
@Controller('inventory') @UseGuards(JwtGuard)
export class InventoryController{
 constructor(private readonly inventory:InventoryService){}
 @Get() list(@CurrentUser() u:any){return this.inventory.list(u.sub)}
 @Post('use') use(@CurrentUser() u:any,@Body() b:any){return this.inventory.consume(u.sub,String(b.sku||''),Number(b.quantity||1),String(b.contextId||'manual'))}
}
