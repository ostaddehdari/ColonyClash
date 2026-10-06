import { Body, Controller, Post } from '@nestjs/common';
import { AuthService } from './auth.service';
@Controller('auth')
export class AuthController {
  constructor(private readonly auth: AuthService) {}
  @Post('dev-login')
  login(@Body() body: { username: string; displayName?: string }) {
    return this.auth.devLogin(body.username, body.displayName || body.username);
  }
}
