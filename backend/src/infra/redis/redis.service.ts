import { Injectable, OnModuleDestroy } from '@nestjs/common';
import Redis from 'ioredis';

@Injectable()
export class RedisService implements OnModuleDestroy {
  readonly client: Redis;
  constructor(){ this.client=new Redis(process.env.REDIS_URL||'redis://redis:6379',{lazyConnect:true,maxRetriesPerRequest:2}); }
  async ready(){ if(this.client.status==='wait') await this.client.connect(); }
  async setOnline(userId:string,connectionId:string,ttlSeconds=75){ await this.ready(); await this.client.set(`presence:user:${userId}`,JSON.stringify({connectionId,ts:Date.now()}),'EX',ttlSeconds); }
  async heartbeat(userId:string,ttlSeconds=75){ await this.ready(); const key=`presence:user:${userId}`; const raw=await this.client.get(key); const value=raw?JSON.parse(raw):{}; value.ts=Date.now(); await this.client.set(key,JSON.stringify(value),'EX',ttlSeconds); }
  async clearOnlineIfConnection(userId:string,connectionId:string){ await this.ready(); const key=`presence:user:${userId}`; const raw=await this.client.get(key); if(!raw) return false; try{ const value=JSON.parse(raw); if(value.connectionId!==connectionId) return false; }catch{return false;} await this.client.del(key); return true; }
  async isOnline(userId:string){ await this.ready(); return (await this.client.exists(`presence:user:${userId}`))===1; }
  async mOnline(userIds:string[]){ await this.ready(); if(!userIds.length) return {}; const p=this.client.pipeline(); userIds.forEach(id=>p.exists(`presence:user:${id}`)); const rows=await p.exec(); return Object.fromEntries(userIds.map((id,i)=>[id,Number(rows?.[i]?.[1]||0)===1])); }
  async markMatchConnection(matchId:string,userId:string,ttlSeconds=90){ await this.ready(); await this.client.set(`match:${matchId}:user:${userId}`,'1','EX',ttlSeconds); }
  async onModuleDestroy(){ try{ await this.client.quit(); }catch{} }
}
