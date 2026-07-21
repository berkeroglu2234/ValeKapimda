import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import http from 'http';
import { Server } from 'socket.io';
import { Pool } from 'pg';
import jwt from 'jsonwebtoken';
import { z } from 'zod';

const app = express();
app.use(cors());
app.use(express.json());
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: '*' } });
const pool = new Pool({ connectionString: process.env.DATABASE_URL });
const secret = process.env.JWT_SECRET || 'dev-secret';

type AuthedRequest = express.Request & { user?: { id: string; role: string; fullName?: string } };
function auth(req: AuthedRequest, res: express.Response, next: express.NextFunction) {
  const token = req.headers.authorization?.replace('Bearer ', '');
  if (!token) return res.status(401).json({ message: 'Oturum gerekli' });
  try { req.user = jwt.verify(token, secret) as any; next(); }
  catch { return res.status(401).json({ message: 'Geçersiz oturum' }); }
}
function role(...roles: string[]) {
  return (req: AuthedRequest, res: express.Response, next: express.NextFunction) =>
    roles.includes(req.user?.role || '') ? next() : res.status(403).json({ message: 'Yetkisiz' });
}

app.get('/', (_, res) => {
  res.json({
    ok: true,
    name: 'ValeKapımda API',
    status: 'online'
  });
});

app.get('/health', (_, res) => {
  res.json({
    ok: true,
    name: 'ValeKapımda API'
  });
});

app.post('/auth/demo-login', async (req, res) => {
  const schema = z.object({ role: z.enum(['CUSTOMER', 'DRIVER', 'ADMIN']), phone: z.string().min(5), fullName: z.string().min(2) });
  const parsed = schema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json(parsed.error.flatten());
  try {
    const p = parsed.data;
    const result = await pool.query(
      `INSERT INTO users(role, full_name, phone) VALUES($1,$2,$3)
       ON CONFLICT(phone) DO UPDATE SET full_name=EXCLUDED.full_name, role=EXCLUDED.role
       RETURNING id, role, full_name, phone`,
      [p.role, p.fullName, p.phone]
    );
    const user = result.rows[0];
    const token = jwt.sign({ id: user.id, role: user.role, fullName: user.full_name }, secret, { expiresIn: '7d' });
    res.json({ token, user });
  } catch (e: any) { res.status(500).json({ message: e.message }); }
});

app.get('/vehicles', auth, role('CUSTOMER'), async (req: AuthedRequest, res) => {
  try { const r = await pool.query('SELECT * FROM vehicles WHERE customer_id=$1 ORDER BY created_at DESC', [req.user!.id]); res.json(r.rows); }
  catch (e: any) { res.status(500).json({ message: e.message }); }
});

app.post('/vehicles', auth, role('CUSTOMER'), async (req: AuthedRequest, res) => {
  const schema = z.object({ plate: z.string().min(4), brand: z.string().min(1), model: z.string().min(1), color: z.string().min(1) });
  const p = schema.safeParse(req.body);
  if (!p.success) return res.status(400).json(p.error.flatten());
  try {
    const existing = await pool.query('SELECT * FROM vehicles WHERE customer_id=$1 AND plate=$2 LIMIT 1', [req.user!.id, p.data.plate]);
    if (existing.rows[0]) return res.json(existing.rows[0]);
    const r = await pool.query(
      'INSERT INTO vehicles(customer_id,plate,brand,model,color) VALUES($1,$2,$3,$4,$5) RETURNING *',
      [req.user!.id, p.data.plate, p.data.brand, p.data.model, p.data.color]
    );
    res.status(201).json(r.rows[0]);
  } catch (e: any) { res.status(500).json({ message: e.message }); }
});

app.get('/pricing', async (_, res) => {
  try { const r = await pool.query('SELECT * FROM pricing_settings ORDER BY id DESC LIMIT 1'); res.json(r.rows[0]); }
  catch { res.json({ base_fee: 250, per_km_fee: 30, waiting_per_minute: 5, night_multiplier: 1.2 }); }
});

app.get('/requests', auth, async (req: AuthedRequest, res) => {
  try {
    let sql = 'SELECT * FROM valet_requests';
    const params: any[] = [];
    if (req.user?.role === 'CUSTOMER') { sql += ' WHERE customer_id=$1'; params.push(req.user.id); }
    if (req.user?.role === 'DRIVER') { sql += " WHERE driver_id=$1 OR status='SEARCHING'"; params.push(req.user.id); }
    sql += ' ORDER BY created_at DESC';
    const r = await pool.query(sql, params); res.json(r.rows);
  } catch (e: any) { res.status(500).json({ message: e.message }); }
});

app.post('/requests', auth, role('CUSTOMER'), async (req: AuthedRequest, res) => {
  const schema = z.object({
    vehicleId: z.string().uuid(), pickupAddress: z.string(), pickupLat: z.number(), pickupLng: z.number(),
    destinationAddress: z.string(), destinationLat: z.number(), destinationLng: z.number(),
    distanceKm: z.number().positive(), quotedPrice: z.number().positive()
  });
  const p = schema.safeParse(req.body);
  if (!p.success) return res.status(400).json(p.error.flatten());
  try {
    const v = p.data;
    const r = await pool.query(
      `INSERT INTO valet_requests(customer_id,vehicle_id,pickup_address,pickup_lat,pickup_lng,destination_address,destination_lat,destination_lng,distance_km,quoted_price)
       VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10) RETURNING *`,
      [req.user!.id, v.vehicleId, v.pickupAddress, v.pickupLat, v.pickupLng, v.destinationAddress, v.destinationLat, v.destinationLng, v.distanceKm, v.quotedPrice]
    );
    io.emit('request:new', r.rows[0]); res.status(201).json(r.rows[0]);
  } catch (e: any) { res.status(500).json({ message: e.message }); }
});

app.patch('/requests/:id/status', auth, role('DRIVER', 'ADMIN'), async (req: AuthedRequest, res) => {
  const schema = z.object({ status: z.enum(['ASSIGNED','DRIVER_EN_ROUTE','ARRIVED','VEHICLE_RECEIVED','IN_TRANSIT','DELIVERED','COMPLETED','CANCELLED']) });
  const p = schema.safeParse(req.body);
  if (!p.success) return res.status(400).json(p.error.flatten());
  try {
    const r = await pool.query('UPDATE valet_requests SET status=$1, driver_id=COALESCE(driver_id,$2), updated_at=NOW() WHERE id=$3 RETURNING *', [p.data.status, req.user!.role === 'DRIVER' ? req.user!.id : null, req.params.id]);
    io.emit('request:updated', r.rows[0]); res.json(r.rows[0]);
  } catch (e: any) { res.status(500).json({ message: e.message }); }
});

io.on('connection', socket => { socket.on('location:update', data => socket.broadcast.emit('location:updated', data)); });
server.listen(Number(process.env.PORT || 4000), () => console.log('ValeKapımda API http://localhost:' + (process.env.PORT || 4000)));
