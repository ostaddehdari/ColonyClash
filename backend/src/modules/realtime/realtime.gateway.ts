import { ConnectedSocket, MessageBody, OnGatewayConnection, OnGatewayDisconnect, SubscribeMessage, WebSocketGateway, WebSocketServer } from '@nestjs/websockets';
import { Server, Socket } from 'socket.io';
import jwt from 'jsonwebtoken';
import { DbService } from '../../db/db.service';
import { RedisService } from '../../infra/redis/redis.service';

@WebSocketGateway({ namespace:'/rt', cors:{origin:'*'}, transports:['websocket','polling'] })
export class RealtimeGateway implements OnGatewayConnection,OnGatewayDisconnect {
  @WebSocketServer() server!:Server;
  constructor(private readonly db:DbService,private readonly redis:RedisService){}

  async handleConnection(client:Socket){
    try {
      const raw=String(client.handshake.auth?.token||'');
      const user=jwt.verify(raw,process.env.JWT_SECRET||'dev-secret-change-me') as any;
      client.data.user=user; client.join(`user:${user.sub}`);
      await this.redis.setOnline(user.sub,client.id);
      await this.db.query(`UPDATE users SET last_seen_at=now() WHERE id=$1`,[user.sub]).catch(()=>{});
      await this.notifyFriendsPresence(user.sub,true);
    } catch { client.disconnect(true); }
  }
  async handleDisconnect(client:Socket){
    const id=client.data.user?.sub; if(!id) return;
    await this.db.query(`UPDATE users SET last_seen_at=now() WHERE id=$1`,[id]).catch(()=>{});
    const matchIds:Array<string>=Array.from(client.data.matchIds||[]);
    for(const matchId of matchIds) await this.db.query(`UPDATE match_players SET disconnected_at=now() WHERE match_id=$1 AND user_id=$2`,[matchId,id]).catch(()=>{});
    const offline=await this.redis.clearOnlineIfConnection(id,client.id).catch(()=>false);
    if(offline) await this.notifyFriendsPresence(id,false);
  }

  private async notifyFriendsPresence(userId:string,online:boolean){
    const q=await this.db.query(`SELECT friend_user_id FROM friendships WHERE user_id=$1 AND status='accepted'`,[userId]).catch(()=>({rows:[]} as any));
    for(const row of q.rows) this.server.to(`user:${row.friend_user_id}`).emit('presence.changed',{userId,online,lastSeenAt:new Date().toISOString()});
  }

  @SubscribeMessage('presence.heartbeat') async heartbeat(@ConnectedSocket()c:Socket){ await this.redis.heartbeat(c.data.user.sub); return {ok:true,ts:Date.now()}; }
  @SubscribeMessage('colony.join') joinColony(@ConnectedSocket()c:Socket,@MessageBody()b:{colonyId:string}){ c.join(`colony:${b.colonyId}`); return {ok:true}; }
  @SubscribeMessage('emote') emote(@ConnectedSocket()c:Socket,@MessageBody()b:{room:string;code:string}){
    if(!/^[A-Z0-9_]{1,24}$/.test(b.code)) return {ok:false};
    this.server.to(b.room).emit('emote',{from:c.data.user.sub,code:b.code,ts:Date.now()}); return {ok:true};
  }
  @SubscribeMessage('match.join') async joinMatch(@ConnectedSocket()c:Socket,@MessageBody()b:{matchId:string}){
    const q=await this.db.query(`SELECT 1 FROM match_players WHERE match_id=$1 AND user_id=$2`,[b.matchId,c.data.user.sub]);
    if(!q.rowCount) return {ok:false,error:'not_a_player'};
    c.join(`match:${b.matchId}`); if(!c.data.matchIds) c.data.matchIds=new Set<string>(); c.data.matchIds.add(b.matchId); await this.redis.markMatchConnection(b.matchId,c.data.user.sub); await this.db.query(`UPDATE match_players SET connected_at=now(),disconnected_at=NULL WHERE match_id=$1 AND user_id=$2`,[b.matchId,c.data.user.sub]);
    this.server.to(`match:${b.matchId}`).emit('match.presence',{userId:c.data.user.sub,online:true}); return {ok:true,room:`match:${b.matchId}`};
  }
  @SubscribeMessage('match.heartbeat') async matchHeartbeat(@ConnectedSocket()c:Socket,@MessageBody()b:{matchId:string}){ await this.redis.markMatchConnection(b.matchId,c.data.user.sub); return {ok:true,ts:Date.now()}; }
  @SubscribeMessage('match.event') async matchEvent(@ConnectedSocket()c:Socket,@MessageBody()b:{matchId:string;type:string;payload?:any}){
    if(!/^[a-z0-9_.-]{1,32}$/i.test(String(b.type||''))) return {ok:false,error:'invalid_type'};
    const q=await this.db.query(`SELECT 1 FROM match_players WHERE match_id=$1 AND user_id=$2`,[b.matchId,c.data.user.sub]); if(!q.rowCount) return {ok:false,error:'not_a_player'};
    this.server.to(`match:${b.matchId}`).emit('match.event',{from:c.data.user.sub,type:b.type,payload:b.payload||{},ts:Date.now()}); return {ok:true};
  }
  @SubscribeMessage('war.join') async joinWar(@ConnectedSocket()c:Socket,@MessageBody()b:{warId:string}){
    const q=await this.db.query(`SELECT status FROM colony_wars WHERE id=$1`,[b.warId]);
    if(!q.rowCount||!['active','finished'].includes(q.rows[0].status)) return {ok:false,error:'war_not_open'};
    c.join(`war:${b.warId}`);
    await this.db.query(`INSERT INTO colony_war_spectators(war_id,user_id) VALUES($1,$2) ON CONFLICT(war_id,user_id) DO UPDATE SET last_seen_at=now()`,[b.warId,c.data.user.sub]);
    const count=await this.db.query(`SELECT count(*)::int n FROM colony_war_spectators WHERE war_id=$1 AND last_seen_at>now()-interval '2 minutes'`,[b.warId]);
    this.server.to(`war:${b.warId}`).emit('war.spectators',{warId:b.warId,count:count.rows[0]?.n||0});
    return {ok:true,room:`war:${b.warId}`,spectators:count.rows[0]?.n||0};
  }

  @SubscribeMessage('war.heartbeat') async warHeartbeat(@ConnectedSocket()c:Socket,@MessageBody()b:{warId:string}){
    await this.db.query(`UPDATE colony_war_spectators SET last_seen_at=now() WHERE war_id=$1 AND user_id=$2`,[b.warId,c.data.user.sub]);
    return {ok:true,ts:Date.now()};
  }

  @SubscribeMessage('war.emote') async warEmote(@ConnectedSocket()c:Socket,@MessageBody()b:{warId:string;code:string}){
    const code=String(b.code||'').toUpperCase(); if(!/^[A-Z0-9_]{1,24}$/.test(code)) return {ok:false,error:'invalid_emote'};
    await this.redis.ready(); const key=`war:emote:${b.warId}:${c.data.user.sub}`; const n=await this.redis.client.incr(key); if(n===1) await this.redis.client.expire(key,5); if(n>6) return {ok:false,error:'rate_limited'};
    const q=await this.db.query(`SELECT status FROM colony_wars WHERE id=$1`,[b.warId]); if(!q.rowCount||q.rows[0].status!=='active') return {ok:false,error:'war_not_active'};
    await this.db.query(`INSERT INTO colony_war_emotes(war_id,user_id,code) VALUES($1,$2,$3)`,[b.warId,c.data.user.sub,code]);
    const payload={warId:b.warId,from:c.data.user.sub,code,ts:Date.now()}; this.server.to(`war:${b.warId}`).emit('war.emote',payload); return {ok:true};
  }

  @SubscribeMessage('war.score') async warScore(@ConnectedSocket()c:Socket,@MessageBody()b:{warId:string}){
    const q=await this.db.query(`SELECT challenger_score,defender_score,current_round,status,winner_colony_id FROM colony_wars WHERE id=$1`,[b.warId]); if(!q.rowCount) return {ok:false,error:'war_not_found'}; return {ok:true,...q.rows[0]};
  }

}
