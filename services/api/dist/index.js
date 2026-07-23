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
async function sendNetgsmSms(phone, message) {
    const usercode = process.env.NETGSM_USERCODE;
    const password = process.env.NETGSM_PASSWORD;
    const header = process.env.NETGSM_MSGHEADER;
    if (!usercode || !password || !header) {
        throw new Error('NetGSM ayarları eksik');
    }
    const params = new URLSearchParams({
        usercode,
        password,
        gsmno: phone.replace(/\D/g, ''),
        message,
        msgheader: header
    });
    const response = await fetch(`https://api.netgsm.com.tr/sms/send/get?${params.toString()}`);
    const text = await response.text();
    if (!text.startsWith('00')) {
        throw new Error(`NetGSM SMS hatası: ${text}`);
    }
    return text;
}
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
app.post('/test-sms', async (req, res) => {
    try {
        const { phone } = req.body;
        await sendNetgsmSms(phone, 'ValeKapimda test mesajidir.');
        res.json({ ok: true });
    }
    catch (e) {
        res.status(500).json({
            ok: false,
            message: e.message
        });
    }
});
app.post('/auth/login', async (req, res) => {
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
        let sql = `SELECT  vr.*, vr.distance_km AS "distanceKm", vr.quoted_price AS "quotedPrice", c.phone AS "customerPhone", d.full_name AS driver_name, d.phone AS driver_phone, COALESCE(AVG(rt.score),0)::float AS driver_rating
      FROM valet_requests vr
      LEFT JOIN users d ON d.id=vr.driver_id
      LEFT JOIN users c ON c.id=vr.customer_id
      LEFT JOIN ratings rt ON rt.driver_id=vr.driver_id`;
        const params = [];
        if (req.user?.role === 'CUSTOMER') {
            sql += ' WHERE vr.customer_id=$1';
            params.push(req.user.id);
        }
        if (req.user?.role === 'DRIVER') {
            sql += " WHERE vr.driver_id=$1 OR vr.status='SEARCHING'";
            params.push(req.user.id);
        }
        sql += ` GROUP BY vr.id,d.full_name,d.phone,c.phone ORDER BY vr.created_at DESC`;
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
        const customer = await pool.query(`SELECT phone FROM users WHERE id=$1`, [req.user.id]);
        io.emit('request:new', r.rows[0]);
        res.status(201).json(r.rows[0]);
        if (customer.rows[0]?.phone) {
            console.log("SMS gönderiliyor:", customer.rows[0].phone);
            sendNetgsmSms(customer.rows[0].phone, "Vale talebiniz alinmistir. En kisa surede size vale yonlendirilecektir.")
                .then((result) => {
                console.log("NetGSM cevabı:", result);
            })
                .catch((error) => {
                console.error("Talep SMS'i gönderilemedi:", error);
            });
        }
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
        const r = await pool.query(`UPDATE valet_requests
       SET status=$1::text,
           driver_id=COALESCE(driver_id,$2::uuid),
           completed_at=CASE WHEN $1::text='COMPLETED' THEN NOW() ELSE completed_at END,
           updated_at=NOW()
       WHERE id=$3::uuid
         AND ($1::text <> 'ASSIGNED' OR status='SEARCHING' OR driver_id=$2::uuid)
       RETURNING *`, [p.data.status, req.user.role === 'DRIVER' ? req.user.id : null, req.params.id]);
        if (!r.rows[0])
            return res.status(409).json({ message: 'Talep başka bir vale tarafından alınmış veya bulunamadı' });
        io.emit('request:updated', r.rows[0]);
        res.json(r.rows[0]);
    }
    catch (e) {
        res.status(500).json({ message: e.message });
    }
});
app.patch('/requests/:id/cancel', auth, role('CUSTOMER'), async (req, res) => {
    try {
        const r = await pool.query(`UPDATE valet_requests SET status='CANCELLED', updated_at=NOW()
       WHERE id=$1::uuid AND customer_id=$2::uuid AND status IN ('SEARCHING','ASSIGNED','DRIVER_EN_ROUTE') RETURNING *`, [req.params.id, req.user.id]);
        if (!r.rows[0])
            return res.status(409).json({ message: 'Bu talep artık iptal edilemez' });
        io.emit('request:updated', r.rows[0]);
        io.to(`request:${req.params.id}`).emit('request:updated', r.rows[0]);
        res.json(r.rows[0]);
    }
    catch (e) {
        res.status(500).json({ message: e.message });
    }
});
app.get('/places/search', async (req, res) => {
    try {
        const q = String(req.query.q || '').trim();
        if (q.length < 3) {
            return res.json([]);
        }
        const apiKey = process.env.GOOGLE_PLACES_API_KEY;
        if (!apiKey) {
            return res.status(500).json({
                message: 'Google Places API anahtarı tanımlı değil.'
            });
        }
        const requestBody = {
            textQuery: q,
            languageCode: 'tr',
            regionCode: 'TR',
            maxResultCount: 7
        };
        const lat = Number(req.query.lat);
        const lng = Number(req.query.lng);
        if (Number.isFinite(lat) && Number.isFinite(lng)) {
            requestBody.locationBias = {
                circle: {
                    center: {
                        latitude: lat,
                        longitude: lng
                    },
                    radius: 50000
                }
            };
        }
        const response = await fetch('https://places.googleapis.com/v1/places:searchText', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Goog-Api-Key': apiKey,
                'X-Goog-FieldMask': 'places.displayName,places.formattedAddress,places.location'
            },
            body: JSON.stringify(requestBody)
        });
        if (!response.ok) {
            const errorText = await response.text();
            console.error('Google Places HTTP hatası:', response.status, errorText);
            throw new Error(`Google Places HTTP hatası: ${response.status}`);
        }
        const data = await response.json();
        const results = (data.places || [])
            .filter((place) => place.location &&
            Number.isFinite(place.location.latitude) &&
            Number.isFinite(place.location.longitude))
            .map((place) => ({
            displayName: place.formattedAddress ||
                place.displayName?.text ||
                'Adres',
            lat: place.location.latitude,
            lng: place.location.longitude
        }));
        return res.json(results);
    }
    catch (e) {
        console.error('Google Places arama hatası:', e);
        return res.status(502).json({
            message: e.message
        });
    }
});
app.get('/places/reverse', async (req, res) => {
    try {
        const lat = Number(req.query.lat);
        const lng = Number(req.query.lng);
        if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
            return res.status(400).json({
                message: 'Geçerli lat ve lng değerleri gerekli.'
            });
        }
        const apiKey = process.env.GOOGLE_GEOCODING_API_KEY;
        if (!apiKey) {
            return res.status(500).json({
                message: 'Google Geocoding API anahtarı tanımlı değil.'
            });
        }
        const url = `https://maps.googleapis.com/maps/api/geocode/json` +
            `?latlng=${lat},${lng}` +
            `&language=tr` +
            `&region=tr` +
            `&key=${apiKey}`;
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error(`Google Geocoding HTTP hatası: ${response.status}`);
        }
        const data = await response.json();
        if (data.status !== 'OK' || !data.results?.length) {
            console.error('Google Geocoding hatası:', data.status, data.error_message);
            return res.status(404).json({
                message: 'Konum için adres bulunamadı.'
            });
        }
        const displayName = data.results[0].formatted_address;
        return res.json({
            displayName,
            lat,
            lng
        });
    }
    catch (e) {
        console.error('Reverse geocoding hatası:', e);
        return res.status(502).json({
            message: e.message
        });
    }
});
app.get('/route', async (req, res) => {
    try {
        const fromLat = Number(req.query.fromLat);
        const fromLng = Number(req.query.fromLng);
        const toLat = Number(req.query.toLat);
        const toLng = Number(req.query.toLng);
        if (![fromLat, fromLng, toLat, toLng].every(Number.isFinite)) {
            return res.status(400).json({ message: 'Koordinatlar geçersiz' });
        }
        const response = await fetch(`https://router.project-osrm.org/route/v1/driving/${fromLng},${fromLat};${toLng},${toLat}?overview=full&geometries=geojson&steps=true`, {
            headers: {
                "User-Agent": "ValeKapimda-App/1.0"
            }
        });
        if (!response.ok)
            throw new Error('Rota servisi yanıt vermedi');
        const data = await response.json();
        const route = data.routes?.[0];
        if (!route) {
            return res.status(404).json({ message: 'Rota bulunamadı' });
        }
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
// --- Professional product endpoints (Sprint 1 + 2 + 3) ---
app.get('/me', auth, async (req, res) => {
    try {
        const r = await pool.query(`SELECT id, role, full_name, phone, email, profile_photo_url, phone_verified_at, created_at
                                FROM users WHERE id=$1 AND is_active=TRUE`, [req.user.id]);
        if (!r.rows[0])
            return res.status(404).json({ message: 'Kullanıcı bulunamadı' });
        res.json(r.rows[0]);
    }
    catch (e) {
        res.status(500).json({ message: e.message });
    }
});
app.patch('/me', auth, async (req, res) => {
    const p = zod_1.z.object({ fullName: zod_1.z.string().min(2).max(120).optional(), email: zod_1.z.string().email().nullable().optional(), profilePhotoUrl: zod_1.z.string().url().nullable().optional() }).safeParse(req.body);
    if (!p.success)
        return res.status(400).json(p.error.flatten());
    try {
        const r = await pool.query(`UPDATE users SET full_name=COALESCE($1,full_name), email=COALESCE($2,email),
      profile_photo_url=COALESCE($3,profile_photo_url), updated_at=NOW() WHERE id=$4 RETURNING id,role,full_name,phone,email,profile_photo_url`, [p.data.fullName ?? null, p.data.email ?? null, p.data.profilePhotoUrl ?? null, req.user.id]);
        res.json(r.rows[0]);
    }
    catch (e) {
        res.status(500).json({ message: e.message });
    }
});
app.get('/favorites', auth, role('CUSTOMER'), async (req, res) => {
    try {
        const r = await pool.query('SELECT * FROM favorite_addresses WHERE customer_id=$1 ORDER BY created_at DESC', [req.user.id]);
        res.json(r.rows);
    }
    catch (e) {
        res.status(500).json({ message: e.message });
    }
});
app.post('/favorites', auth, role('CUSTOMER'), async (req, res) => {
    const p = zod_1.z.object({ label: zod_1.z.string().min(1).max(40), address: zod_1.z.string().min(3), lat: zod_1.z.number(), lng: zod_1.z.number() }).safeParse(req.body);
    if (!p.success)
        return res.status(400).json(p.error.flatten());
    try {
        const r = await pool.query('INSERT INTO favorite_addresses(customer_id,label,address,lat,lng) VALUES($1,$2,$3,$4,$5) RETURNING *', [req.user.id, p.data.label, p.data.address, p.data.lat, p.data.lng]);
        res.status(201).json(r.rows[0]);
    }
    catch (e) {
        res.status(500).json({ message: e.message });
    }
});
app.delete('/favorites/:id', auth, role('CUSTOMER'), async (req, res) => {
    try {
        await pool.query('DELETE FROM favorite_addresses WHERE id=$1 AND customer_id=$2', [req.params.id, req.user.id]);
        res.status(204).send();
    }
    catch (e) {
        res.status(500).json({ message: e.message });
    }
});
app.get('/requests/:id', auth, async (req, res) => {
    try {
        const params = [req.params.id];
        let guard = '';
        if (req.user.role === 'CUSTOMER') {
            guard = ' AND vr.customer_id=$2';
            params.push(req.user.id);
        }
        if (req.user.role === 'DRIVER') {
            guard = ' AND (vr.driver_id=$2 OR vr.status=\'SEARCHING\')';
            params.push(req.user.id);
        }
        const r = await pool.query(`SELECT vr.*, v.plate,v.brand,v.model,v.color, c.full_name customer_name,c.phone customer_phone,
      d.full_name driver_name,d.phone driver_phone, rt.score rating_score,rt.comment rating_comment,
      (SELECT COALESCE(AVG(score),0)::float FROM ratings WHERE driver_id=vr.driver_id) driver_rating
      FROM valet_requests vr JOIN vehicles v ON v.id=vr.vehicle_id JOIN users c ON c.id=vr.customer_id
      LEFT JOIN users d ON d.id=vr.driver_id LEFT JOIN ratings rt ON rt.request_id=vr.id WHERE vr.id=$1${guard}`, params);
        if (!r.rows[0])
            return res.status(404).json({ message: 'Talep bulunamadı' });
        res.json(r.rows[0]);
    }
    catch (e) {
        res.status(500).json({ message: e.message });
    }
});
app.post('/requests/:id/rating', auth, role('CUSTOMER'), async (req, res) => {
    const p = zod_1.z.object({ score: zod_1.z.number().int().min(1).max(5), comment: zod_1.z.string().max(1000).optional() }).safeParse(req.body);
    if (!p.success)
        return res.status(400).json(p.error.flatten());
    try {
        const check = await pool.query("SELECT id,driver_id,status FROM valet_requests WHERE id=$1 AND customer_id=$2", [req.params.id, req.user.id]);
        if (!check.rows[0])
            return res.status(404).json({ message: 'Talep bulunamadı' });
        if (check.rows[0].status !== 'COMPLETED')
            return res.status(409).json({ message: 'Yalnızca tamamlanan işlem puanlanabilir' });
        const r = await pool.query(`INSERT INTO ratings(request_id,customer_id,driver_id,score,comment) VALUES($1,$2,$3,$4,$5)
      ON CONFLICT(request_id) DO UPDATE SET score=EXCLUDED.score,comment=EXCLUDED.comment,updated_at=NOW() RETURNING *`, [req.params.id, req.user.id, check.rows[0].driver_id, p.data.score, p.data.comment || null]);
        res.status(201).json(r.rows[0]);
    }
    catch (e) {
        res.status(500).json({ message: e.message });
    }
});
app.post('/coupons/validate', auth, role('CUSTOMER'), async (req, res) => {
    const p = zod_1.z.object({ code: zod_1.z.string().min(2), amount: zod_1.z.number().positive() }).safeParse(req.body);
    if (!p.success)
        return res.status(400).json(p.error.flatten());
    try {
        const r = await pool.query(`SELECT * FROM coupons WHERE UPPER(code)=UPPER($1) AND active=TRUE AND starts_at<=NOW() AND ends_at>=NOW()`, [p.data.code]);
        const c = r.rows[0];
        if (!c)
            return res.status(404).json({ message: 'Kupon geçersiz veya süresi dolmuş' });
        if (Number(p.data.amount) < Number(c.min_amount))
            return res.status(409).json({ message: `Minimum sepet tutarı ₺${c.min_amount}` });
        const used = await pool.query('SELECT 1 FROM coupon_redemptions WHERE coupon_id=$1 AND customer_id=$2', [c.id, req.user.id]);
        if (used.rows[0])
            return res.status(409).json({ message: 'Bu kuponu daha önce kullandınız' });
        let discount = c.discount_type === 'PERCENT' ? p.data.amount * Number(c.discount_value) / 100 : Number(c.discount_value);
        if (c.max_discount != null)
            discount = Math.min(discount, Number(c.max_discount));
        discount = Math.min(discount, p.data.amount);
        res.json({ couponId: c.id, code: c.code, discount: Math.round(discount * 100) / 100, finalAmount: Math.round((p.data.amount - discount) * 100) / 100 });
    }
    catch (e) {
        res.status(500).json({ message: e.message });
    }
});
app.post('/payments/intents', auth, role('CUSTOMER'), async (req, res) => {
    const p = zod_1.z.object({ requestId: zod_1.z.string().uuid(), couponCode: zod_1.z.string().optional(), provider: zod_1.z.enum(['IYZICO', 'PAYTR', 'CASH']).default('CASH') }).safeParse(req.body);
    if (!p.success)
        return res.status(400).json(p.error.flatten());
    try {
        const q = await pool.query('SELECT id,quoted_price,status FROM valet_requests WHERE id=$1 AND customer_id=$2', [p.data.requestId, req.user.id]);
        if (!q.rows[0])
            return res.status(404).json({ message: 'Talep bulunamadı' });
        let amount = Number(q.rows[0].quoted_price), discount = 0;
        let couponId = null;
        if (p.data.couponCode) {
            const cr = await pool.query(`SELECT * FROM coupons WHERE UPPER(code)=UPPER($1) AND active=TRUE AND starts_at<=NOW() AND ends_at>=NOW()`, [p.data.couponCode]);
            const c = cr.rows[0];
            if (!c)
                return res.status(404).json({ message: 'Kupon geçersiz' });
            couponId = c.id;
            discount = c.discount_type === 'PERCENT' ? amount * Number(c.discount_value) / 100 : Number(c.discount_value);
            if (c.max_discount != null)
                discount = Math.min(discount, Number(c.max_discount));
        }
        const payable = Math.max(0, Math.round((amount - discount) * 100) / 100);
        const status = p.data.provider === 'CASH' ? 'AUTHORIZED' : 'PENDING';
        const r = await pool.query(`INSERT INTO payments(request_id,customer_id,provider,amount,discount_amount,payable_amount,status,coupon_id)
      VALUES($1,$2,$3,$4,$5,$6,$7,$8) RETURNING *`, [p.data.requestId, req.user.id, p.data.provider, amount, discount, payable, status, couponId]);
        res.status(201).json({ ...r.rows[0], providerSetupRequired: p.data.provider !== 'CASH' });
    }
    catch (e) {
        res.status(500).json({ message: e.message });
    }
});
app.get('/payments', auth, role('CUSTOMER'), async (req, res) => { try {
    const r = await pool.query('SELECT * FROM payments WHERE customer_id=$1 ORDER BY created_at DESC', [req.user.id]);
    res.json(r.rows);
}
catch (e) {
    res.status(500).json({ message: e.message });
} });
app.post('/notification-tokens', auth, async (req, res) => {
    const p = zod_1.z.object({ token: zod_1.z.string().min(10), platform: zod_1.z.enum(['ANDROID', 'IOS', 'WEB']).default('ANDROID') }).safeParse(req.body);
    if (!p.success)
        return res.status(400).json(p.error.flatten());
    try {
        const r = await pool.query(`INSERT INTO notification_tokens(user_id,token,platform) VALUES($1,$2,$3)
    ON CONFLICT(token) DO UPDATE SET user_id=EXCLUDED.user_id,platform=EXCLUDED.platform,updated_at=NOW() RETURNING *`, [req.user.id, p.data.token, p.data.platform]);
        res.status(201).json(r.rows[0]);
    }
    catch (e) {
        res.status(500).json({ message: e.message });
    }
});
app.get('/notifications', auth, async (req, res) => { try {
    const r = await pool.query('SELECT * FROM notifications WHERE user_id=$1 ORDER BY created_at DESC LIMIT 100', [req.user.id]);
    res.json(r.rows);
}
catch (e) {
    res.status(500).json({ message: e.message });
} });
app.patch('/notifications/:id/read', auth, async (req, res) => { try {
    const r = await pool.query('UPDATE notifications SET read_at=NOW() WHERE id=$1 AND user_id=$2 RETURNING *', [req.params.id, req.user.id]);
    res.json(r.rows[0] || null);
}
catch (e) {
    res.status(500).json({ message: e.message });
} });
app.post('/drivers/location', auth, role('DRIVER'), async (req, res) => {
    const p = zod_1.z.object({ lat: zod_1.z.number(), lng: zod_1.z.number(), heading: zod_1.z.number().optional(), accuracy: zod_1.z.number().optional(), requestId: zod_1.z.string().uuid().optional() }).safeParse(req.body);
    if (!p.success)
        return res.status(400).json(p.error.flatten());
    try {
        await pool.query(`INSERT INTO live_locations(user_id,lat,lng,heading,accuracy,updated_at) VALUES($1,$2,$3,$4,$5,NOW())
    ON CONFLICT(user_id) DO UPDATE SET lat=EXCLUDED.lat,lng=EXCLUDED.lng,heading=EXCLUDED.heading,accuracy=EXCLUDED.accuracy,updated_at=NOW()`, [req.user.id, p.data.lat, p.data.lng, p.data.heading || null, p.data.accuracy || null]);
        const payload = { driverId: req.user.id, requestId: p.data.requestId || null, lat: p.data.lat, lng: p.data.lng, heading: p.data.heading || null, updatedAt: new Date().toISOString() };
        io.emit('location:updated', payload);
        res.json(payload);
    }
    catch (e) {
        res.status(500).json({ message: e.message });
    }
});
app.get('/drivers/:id/location', auth, async (req, res) => { try {
    const r = await pool.query('SELECT * FROM live_locations WHERE user_id=$1', [req.params.id]);
    if (!r.rows[0])
        return res.status(404).json({ message: 'Konum bulunamadı' });
    res.json(r.rows[0]);
}
catch (e) {
    res.status(500).json({ message: e.message });
} });
app.get('/driver/earnings', auth, role('DRIVER'), async (req, res) => {
    try {
        const r = await pool.query(`SELECT COUNT(*)::int completed_count,COALESCE(SUM(quoted_price),0)::numeric total_earnings,
    COALESCE(SUM(CASE WHEN completed_at>=CURRENT_DATE THEN quoted_price ELSE 0 END),0)::numeric today_earnings
    FROM valet_requests WHERE driver_id=$1 AND status='COMPLETED'`, [req.user.id]);
        res.json(r.rows[0]);
    }
    catch (e) {
        res.status(500).json({ message: e.message });
    }
});
io.on('connection', socket => {
    socket.on('request:join', (requestId) => {
        if (typeof requestId === 'string' && requestId.length > 10)
            socket.join(`request:${requestId}`);
    });
    socket.on('request:leave', (requestId) => socket.leave(`request:${requestId}`));
    socket.on('location:update', (data) => {
        if (!data || typeof data.requestId !== 'string')
            return;
        const payload = { ...data, updatedAt: new Date().toISOString() };
        io.to(`request:${data.requestId}`).emit('location:updated', payload);
        socket.broadcast.emit('location:updated', payload);
    });
});
server.listen(Number(process.env.PORT || 4000), () => console.log('ValeKapımda API http://localhost:' + (process.env.PORT || 4000)));
