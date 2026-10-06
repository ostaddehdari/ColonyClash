import { Injectable, UnauthorizedException } from '@nestjs/common';
import jwt from 'jsonwebtoken';
import { DbService } from '../../db/db.service';

@Injectable()
export class AuthService {
  constructor(private readonly db: DbService) {}

  async devLogin(username: string, displayName: string) {
    if (!/^[a-zA-Z0-9_]{3,24}$/.test(username)) throw new UnauthorizedException('invalid_username');
    const q = await this.db.query<{id:string;username:string;display_name:string}>(
      `INSERT INTO users(username,display_name) VALUES($1,$2)
       ON CONFLICT(username) DO UPDATE SET display_name=EXCLUDED.display_name
       RETURNING id,username,display_name`, [username, displayName || username]);
    const user = q.rows[0];
    const secret = process.env.JWT_SECRET || 'dev-secret-change-me';
    const token = jwt.sign({ sub: user.id, username: user.username }, secret, { expiresIn: '30d' });
    return { user, token };
  }

  verify(token: string): { sub: string; username: string } {
    return jwt.verify(token, process.env.JWT_SECRET || 'dev-secret-change-me') as any;
  }
}
