import { Body, Controller, Get, Param, Post, UseGuards } from '@nestjs/common';
import { GiftsService } from './gifts.service';
import { CurrentUser, JwtGuard } from '../../common/current-user';
@Controller('gifts') @UseGuards(JwtGuard)
export class GiftsController{
 constructor(private readonly gifts:GiftsService){}
 @Post('send') send(@CurrentUser() u:any,@Body() b:any){return this.gifts.send(u.sub,String(b.receiverUserId||''),String(b.sku||''),String(b.message||''))}
 @Get('inbox') inbox(@CurrentUser() u:any){return this.gifts.inbox(u.sub)}
 @Post(':id/open') open(@CurrentUser() u:any,@Param('id') id:string){return this.gifts.open(u.sub,id)}
}
