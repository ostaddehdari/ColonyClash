import pg from 'pg';
import fs from 'node:fs';
import path from 'node:path';
const { Client } = pg;
const c = new Client({ connectionString: process.env.DATABASE_URL || 'postgres://colony:colony_dev_only@localhost:5432/colony' });
await c.connect();
const dir = path.resolve('sql');
for (const file of fs.readdirSync(dir).filter(x=>x.endsWith('.sql')).sort()) {
  const sql = fs.readFileSync(path.join(dir,file),'utf8');
  await c.query(sql);
  console.log(`applied ${file}`);
}
await c.end();
