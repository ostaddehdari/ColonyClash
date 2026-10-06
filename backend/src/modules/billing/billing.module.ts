import { Module } from '@nestjs/common';
import { DbModule } from '../../db/db.module';
import { BillingController } from './billing.controller';
import { BillingService } from './billing.service';
@Module({imports:[DbModule],providers:[BillingService],controllers:[BillingController]})
export class BillingModule{}
