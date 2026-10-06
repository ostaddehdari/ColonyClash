import { Controller, Get, Query } from '@nestjs/common';
import { CatalogService } from './catalog.service';
@Controller('catalog')
export class CatalogController{ constructor(private readonly catalog:CatalogService){} @Get() list(@Query('market')market='play_global'){return this.catalog.list(market)} }
