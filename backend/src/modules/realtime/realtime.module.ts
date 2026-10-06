import { Module } from '@nestjs/common';
import { DbModule } from '../../db/db.module';
import { RealtimeGateway } from './realtime.gateway';
@Module({imports:[DbModule],providers:[RealtimeGateway],exports:[RealtimeGateway]})
export class RealtimeModule{}
