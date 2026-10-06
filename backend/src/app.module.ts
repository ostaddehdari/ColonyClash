import { Module, Controller, Get } from '@nestjs/common';
import { DbModule } from './db/db.module';
import { AuthModule } from './modules/auth/auth.module';
import { ColoniesModule } from './modules/colonies/colonies.module';
import { EconomyModule } from './modules/economy/economy.module';
import { GiftsModule } from './modules/gifts/gifts.module';
import { InvitesModule } from './modules/invites/invites.module';
import { ModerationModule } from './modules/moderation/moderation.module';
import { RealtimeModule } from './modules/realtime/realtime.module';
import { ProfileModule } from './modules/profile/profile.module';
import { SocialModule } from './modules/social/social.module';
import { CatalogModule } from './modules/catalog/catalog.module';
import { BillingModule } from './modules/billing/billing.module';
import { ShopModule } from './modules/shop/shop.module';
import { InventoryModule } from './modules/inventory/inventory.module';
import { GamesModule } from './modules/games/games.module';
import { RedisModule } from './infra/redis/redis.module';
import { MatchmakingModule } from './modules/matchmaking/matchmaking.module';
import { WarsModule } from './modules/wars/wars.module';
import { SeasonsModule } from './modules/seasons/seasons.module';
@Controller() class HealthController { @Get('health') health(){ return {app:'colony-clash-api',version:'0.8.0',ok:true}; } }
@Module({imports:[DbModule,RedisModule,AuthModule,ProfileModule,SocialModule,ColoniesModule,EconomyModule,GiftsModule,InvitesModule,ModerationModule,RealtimeModule,CatalogModule,InventoryModule,ShopModule,BillingModule,GamesModule,MatchmakingModule,WarsModule,SeasonsModule],controllers:[HealthController]})
export class AppModule{}
