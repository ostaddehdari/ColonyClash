import 'reflect-metadata';
import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';
async function bootstrap(){
 const app=await NestFactory.create(AppModule);
 app.enableCors({origin:process.env.CORS_ORIGIN==='*' ? true : process.env.CORS_ORIGIN?.split(',')});
 app.setGlobalPrefix('api/v1');
 await app.listen(Number(process.env.PORT||8080),'0.0.0.0');
 console.log(`COLONY_API_READY port=${process.env.PORT||8080}`);
}
bootstrap();
