import { CanActivate, ExecutionContext, Injectable, UnauthorizedException, createParamDecorator } from '@nestjs/common';
import jwt from 'jsonwebtoken';

@Injectable()
export class JwtGuard implements CanActivate {
  canActivate(ctx: ExecutionContext): boolean {
    const req = ctx.switchToHttp().getRequest();
    const auth = String(req.headers.authorization || '');
    if (!auth.startsWith('Bearer ')) throw new UnauthorizedException();
    try {
      req.user = jwt.verify(auth.slice(7), process.env.JWT_SECRET || 'dev-secret-change-me');
      return true;
    } catch { throw new UnauthorizedException(); }
  }
}
export const CurrentUser = createParamDecorator((_d, ctx) => ctx.switchToHttp().getRequest().user);
