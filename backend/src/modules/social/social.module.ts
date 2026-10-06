import { Module } from '@nestjs/common';
import { SocialController } from './social.controller';
import { SocialService } from './social.service';
@Module({providers:[SocialService],controllers:[SocialController]})
export class SocialModule{}
