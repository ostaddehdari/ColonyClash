import { Module } from '@nestjs/common';
import { ColoniesService } from './colonies.service';
import { ColoniesController } from './colonies.controller';
@Module({providers:[ColoniesService],controllers:[ColoniesController]})
export class ColoniesModule {}
