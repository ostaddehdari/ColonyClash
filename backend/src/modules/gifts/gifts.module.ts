import { Module } from '@nestjs/common';
import { GiftsService } from './gifts.service';
import { GiftsController } from './gifts.controller';
import { EconomyModule } from '../economy/economy.module';
@Module({imports:[EconomyModule],providers:[GiftsService],controllers:[GiftsController]})
export class GiftsModule {}
