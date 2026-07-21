"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
require("dotenv/config");
const express_1 = __importDefault(require("express"));
const cors_1 = __importDefault(require("cors"));
const http_1 = __importDefault(require("http"));
const socket_io_1 = require("socket.io");
const pg_1 = require("pg");
const jsonwebtoken_1 = __importDefault(require("jsonwebtoken"));
const zod_1 = require("zod");
const app = (0, express_1.default)();
app.use((0, cors_1.default)());
app.use(express_1.default.json());
const server = http_1.default.createServer(app);
const io = new socket_io_1.Server(server, { cors: { origin: '*' } });
const pool = new pg_1.Pool({ connectionString: process.env.DATABASE_URL });
const secret = process.env.JWT_SECRET || 'dev-secret';
function auth(req, res, next) {
    const token = req.headers.authorization?.replace('Bearer ', '');
    if (!token)
        return res.status(401).json({ message: 'Oturum gerekli' });
    try {
        req.user = jsonwebtoken_1.default.verify(token, secret);
        next();
    }
    catch {
        return res.status(401).json({ message: 'Geçersiz oturum' });
    }
}
function role(...roles) {
    return (req, res, next) => roles.includes(req.user?.role || '') ? next() : res.status(403).json({ message: 'Yetkisiz' });
}
app.get('/health', (_, res) => res.json({ ok: true, name: 'ValeKapımda API' }));
app.post('/auth/demo-login', async (req, res) => {
    const schema = zod_1.z.object({ role: zod_1.z.enum(['CUSTOMER', 'DRIVER', 'ADMIN']), phone: zod_1.z.string().min(5), fullName: zod_1.z.string().min(2) });
    const parsed = schema.safeParse(req.body);
    if (!parsed.success)
        return res.status(400).json(parsed.error.flatten());
    try {
        const p = parsed.data;
        const result = await pool.query(`INSERT INTO users(role, full_name, phone) VALUES($1,$2,$3)
       ON CONFLICT(phone) DO UPDATE SET full_name=EXCLUDED.full_name, role=EXCLUDED.role
       RETURNING id, role, full_name, phone`, [p.role, p.fullName, p.phone]);
        const user = result.rows[0];
        const token = jsonwebtoken_1.default.sign({ id: user.id, role: user.role, fullName: user.full_name }, secret, { expiresIn: '7d' });
        res.json({ token, user });
    }
    catch (e) {
        res.status(500).json({ message: e.message });
    }
});
app.get('/vehicles', auth, role('CUSTOMER'), async (req, res) => {
    try {
        const r = await pool.query('SELECT * FROM vehicles WHERE customer_id=$1 ORDER BY created_at DESC', [req.user.id]);
        res.json(r.rows);
    }
    catch (e) {
        res.status(500).json({ message: e.message });
    }
});
app.post('/vehicles', auth, role('CUSTOMER'), async (req, res) => {
    const schema = zod_1.z.object({ plate: zod_1.z.string().min(4), brand: zod_1.z.string().min(1), model: zod_1.z.string().min(1), color: zod_1.z.string().min(1) });
    const p = schema.safeParse(req.body);
    if (!p.success)
        return res.status(400).json(p.error.flatten());
    try {
        const existing = await pool.query('SELECT * FROM vehicles WHERE customer_id=$1 AND plate=$2 LIMIT 1', [req.user.id, p.data.plate]);
        if (existing.rows[0])
            return res.json(existing.rows[0]);
        const r = await pool.query('INSERT INTO vehicles(customer_id,plate,brand,model,color) VALUES($1,$2,$3,$4,$5) RETURNING *', [req.user.id, p.data.plate, p.data.brand, p.data.model, p.data.color]);
        res.status(201).json(r.rows[0]);
    }
    catch (e) {
        res.status(500).json({ message: e.message });
    }
});
app.get('/pricing', async (_, res) => {
    try {
        const r = await pool.query('SELECT * FROM pricing_settings ORDER BY id DESC LIMIT 1');
        res.json(r.rows[0]);
    }
    catch {
        res.json({ base_fee: 250, per_km_fee: 30, waiting_per_minute: 5, night_multiplier: 1.2 });
    }
});
app.get('/requests', auth, async (req, res) => {
    try {
        let sql = 'SELECT * FROM valet_requests';
        const params = [];
        if (req.user?.role === 'CUSTOMER') {
            sql += ' WHERE customer_id=$1';
            params.push(req.user.id);
        }
        if (req.user?.role === 'DRIVER') {
            sql += " WHERE driver_id=$1 OR status='SEARCHING'";
            params.push(req.user.id);
        }
        sql += ' ORDER BY created_at DESC';
        const r = await pool.query(sql, params);
        res.json(r.rows);
    }
    catch (e) {
        res.status(500).json({ message: e.message });
    }
});
app.post('/requests', auth, role('CUSTOMER'), async (req, res) => {
    const schema = zod_1.z.object({
        vehicleId: zod_1.z.string().uuid(), pickupAddress: zod_1.z.string(), pickupLat: zod_1.z.number(), pickupLng: zod_1.z.number(),
        destinationAddress: zod_1.z.string(), destinationLat: zod_1.z.number(), destinationLng: zod_1.z.number(),
        distanceKm: zod_1.z.number().positive(), quotedPrice: zod_1.z.number().positive()
    });
    const p = schema.safeParse(req.body);
    if (!p.success)
        return res.status(400).json(p.error.flatten());
    try {
        const v = p.data;
        const r = await pool.query(`INSERT INTO valet_requests(customer_id,vehicle_id,pickup_address,pickup_lat,pickup_lng,destination_address,destination_lat,destination_lng,distance_km,quoted_price)
       VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10) RETURNING *`, [req.user.id, v.vehicleId, v.pickupAddress, v.pickupLat, v.pickupLng, v.destinationAddress, v.destinationLat, v.destinationLng, v.distanceKm, v.quotedPrice]);
        io.emit('request:new', r.rows[0]);
        res.status(201).json(r.rows[0]);
    }
    catch (e) {
        res.status(500).json({ message: e.message });
    }
});
app.patch('/requests/:id/status', auth, role('DRIVER', 'ADMIN'), async (req, res) => {
    const schema = zod_1.z.object({ status: zod_1.z.enum(['ASSIGNED', 'DRIVER_EN_ROUTE', 'ARRIVED', 'VEHICLE_RECEIVED', 'IN_TRANSIT', 'DELIVERED', 'COMPLETED', 'CANCELLED']) });
    const p = schema.safeParse(req.body);
    if (!p.success)
        return res.status(400).json(p.error.flatten());
    try {
        const r = await pool.query('UPDATE valet_requests SET status=$1, driver_id=COALESCE(driver_id,$2), updated_at=NOW() WHERE id=$3 RETURNING *', [p.data.status, req.user.role === 'DRIVER' ? req.user.id : null, req.params.id]);
        io.emit('request:updated', r.rows[0]);
        res.json(r.rows[0]);
    }
    catch (e) {
        res.status(500).json({ message: e.message });
    }
});
app.get('/places/search', async (req, res) => {
    try {
        const q = String(req.query.q || '').trim();
        if (q.length < 3)
            return res.json([]);
        const viewbox = req.query.lat && req.query.lng
            ? `&viewbox=${Number(req.query.lng) - 0.6},${Number(req.query.lat) + 0.4},${Number(req.query.lng) + 0.6},${Number(req.query.lat) - 0.4}&bounded=0`
            : '';
        const url = `https://nominatim.openstreetmap.org/search?format=jsonv2&limit=7&countrycodes=tr&accept-language=tr&q=${encodeURIComponent(q)}${viewbox}`;
        const response = await fetch(url, { headers: { 'User-Agent': 'ValeKapimda/1.0 (support@valekapimda.app)' } });
        if (!response.ok)
            throw new Error('Adres servisi yanıt vermedi');
        const data = await response.json();
        res.json(data.map(x => ({ displayName: x.display_name, lat: Number(x.lat), lng: Number(x.lon) })));
    }
    catch (e) {
        res.status(502).json({ message: e.message });
    }
});
app.get('/places/reverse', async (req, res) => {
    try {
        const lat = Number(req.query.lat), lng = Number(req.query.lng);
        const response = await fetch(`https://nominatim.openstreetmap.org/reverse?format=jsonv2&accept-language=tr&lat=${lat}&lon=${lng}`, { headers: { 'User-Agent': 'ValeKapimda/1.0 (support@valekapimda.app)' } });
        if (!response.ok)
            throw new Error('Adres servisi yanıt vermedi');
        const data = await response.json();
        res.json({ displayName: data.display_name || `${lat}, ${lng}` });
    }
    catch (e) {
        res.status(502).json({ message: e.message });
    }
});
app.get('/route', async (req, res) => {
    try {
        const fromLat = Number(req.query.fromLat), fromLng = Number(req.query.fromLng);
        const toLat = Number(req.query.toLat), toLng = Number(req.query.toLng);
        if (![fromLat, fromLng, toLat, toLng].every(Number.isFinite))
            return res.status(400).json({ message: 'Koordinatlar geçersiz' });
        const response = await fetch(`https://router.project-osrm.org/route/v1/driving/${fromLng},${fromLat};${toLng},${toLat}?overview=full&geometries=geojson`);
        if (!response.ok)
            throw new Error('Rota servisi yanıt vermedi');
        const data = await response.json();
        const route = data.routes?.[0];
        if (!route)
            return res.status(404).json({ message: 'Rota bulunamadı' });
        res.json({
            distanceKm: Math.round((route.distance / 1000) * 10) / 10,
            durationMinutes: Math.max(1, Math.round(route.duration / 60)),
            points: route.geometry.coordinates.map((c) => [c[1], c[0]])
        });
    }
    catch (e) {
        res.status(502).json({ message: e.message });
    }
});
io.on('connection', socket => { socket.on('location:update', data => socket.broadcast.emit('location:updated', data)); });
server.listen(Number(process.env.PORT || 4000), () => console.log('ValeKapımda API http://localhost:' + (process.env.PORT || 4000)));
