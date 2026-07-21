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

const io = new Server(server, {
  cors: {
    origin: '*',
    methods: ['GET', 'POST', 'PATCH']
  }
});

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: process.env.NODE_ENV === 'production'
    ? { rejectUnauthorized: false }
    : undefined
});

const secret = process.env.JWT_SECRET || 'dev-secret';

type AuthedRequest = express.Request & {
  user?: {
    id: string;
    role: string;
    fullName?: string;
  };
};

async function initializeDatabase(): Promise<void> {
  await pool.query(`
    CREATE EXTENSION IF NOT EXISTS pgcrypto;

    CREATE TABLE IF NOT EXISTS users (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      role VARCHAR(20) NOT NULL,
      full_name VARCHAR(120) NOT NULL,
      phone VARCHAR(30) UNIQUE NOT NULL,
      email VARCHAR(160),
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

    CREATE TABLE IF NOT EXISTS vehicles (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      customer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      plate VARCHAR(30) NOT NULL,
      brand VARCHAR(80) NOT NULL,
      model VARCHAR(80) NOT NULL,
      color VARCHAR(50) NOT NULL,
      year INTEGER,
      transmission VARCHAR(30),
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      UNIQUE(customer_id, plate)
    );

    CREATE TABLE IF NOT EXISTS pricing_settings (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      base_fee NUMERIC(10,2) NOT NULL DEFAULT 250,
      per_km_fee NUMERIC(10,2) NOT NULL DEFAULT 30,
      waiting_per_minute NUMERIC(10,2) NOT NULL DEFAULT 5,
      night_multiplier NUMERIC(5,2) NOT NULL DEFAULT 1.2,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

    CREATE TABLE IF NOT EXISTS valet_requests (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      customer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      driver_id UUID REFERENCES users(id) ON DELETE SET NULL,
      vehicle_id UUID NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
      pickup_address TEXT NOT NULL,
      pickup_lat DOUBLE PRECISION NOT NULL,
      pickup_lng DOUBLE PRECISION NOT NULL,
      destination_address TEXT NOT NULL,
      destination_lat DOUBLE PRECISION NOT NULL,
      destination_lng DOUBLE PRECISION NOT NULL,
      distance_km NUMERIC(10,2) NOT NULL,
      quoted_price NUMERIC(10,2) NOT NULL,
      status VARCHAR(40) NOT NULL DEFAULT 'SEARCHING',
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

    INSERT INTO pricing_settings (
      base_fee,
      per_km_fee,
      waiting_per_minute,
      night_multiplier
    )
    SELECT 250, 30, 5, 1.2
    WHERE NOT EXISTS (
      SELECT 1 FROM pricing_settings
    );

    CREATE INDEX IF NOT EXISTS idx_requests_customer
      ON valet_requests(customer_id);

    CREATE INDEX IF NOT EXISTS idx_requests_driver
      ON valet_requests(driver_id);

    CREATE INDEX IF NOT EXISTS idx_requests_status
      ON valet_requests(status);
  `);

  console.log('Veritabanı tabloları hazır.');
}

function auth(
  req: AuthedRequest,
  res: express.Response,
  next: express.NextFunction
) {
  const token = req.headers.authorization?.replace('Bearer ', '');

  if (!token) {
    return res.status(401).json({ message: 'Oturum gerekli' });
  }

  try {
    req.user = jwt.verify(token, secret) as AuthedRequest['user'];
    next();
  } catch {
    return res.status(401).json({ message: 'Geçersiz oturum' });
  }
}

function role(...roles: string[]) {
  return (
    req: AuthedRequest,
    res: express.Response,
    next: express.NextFunction
  ) => {
    if (roles.includes(req.user?.role || '')) {
      return next();
    }

    return res.status(403).json({ message: 'Yetkisiz' });
  };
}

app.get('/', (_, res) => {
  res.json({
    ok: true,
    name: 'ValeKapımda API',
    status: 'online'
  });
});

app.get('/health', async (_, res) => {
  try {
    await pool.query('SELECT 1');
    res.json({
      ok: true,
      name: 'ValeKapımda API',
      database: 'connected'
    });
  } catch (error: any) {
    res.status(500).json({
      ok: false,
      name: 'ValeKapımda API',
      database: 'disconnected',
      message: error.message
    });
  }
});

app.post('/auth/demo-login', async (req, res) => {
  const schema = z.object({
    role: z.enum(['CUSTOMER', 'DRIVER', 'ADMIN']),
    phone: z.string().min(5),
    fullName: z.string().min(2)
  });

  const parsed = schema.safeParse(req.body);

  if (!parsed.success) {
    return res.status(400).json(parsed.error.flatten());
  }

  try {
    const data = parsed.data;

    const result = await pool.query(
      `INSERT INTO users(role, full_name, phone)
       VALUES($1, $2, $3)
       ON CONFLICT(phone)
       DO UPDATE SET
         full_name = EXCLUDED.full_name,
         role = EXCLUDED.role,
         updated_at = NOW()
       RETURNING id, role, full_name, phone`,
      [data.role, data.fullName, data.phone]
    );

    const user = result.rows[0];

    const token = jwt.sign(
      {
        id: user.id,
        role: user.role,
        fullName: user.full_name
      },
      secret,
      { expiresIn: '7d' }
    );

    res.json({ token, user });
  } catch (error: any) {
    res.status(500).json({ message: error.message });
  }
});

app.get(
  '/vehicles',
  auth,
  role('CUSTOMER'),
  async (req: AuthedRequest, res) => {
    try {
      const result = await pool.query(
        `SELECT *
         FROM vehicles
         WHERE customer_id = $1
         ORDER BY created_at DESC`,
        [req.user!.id]
      );

      res.json(result.rows);
    } catch (error: any) {
      res.status(500).json({ message: error.message });
    }
  }
);

app.post(
  '/vehicles',
  auth,
  role('CUSTOMER'),
  async (req: AuthedRequest, res) => {
    const schema = z.object({
      plate: z.string().min(4),
      brand: z.string().min(1),
      model: z.string().min(1),
      color: z.string().min(1),
      year: z.number().int().nullable().optional(),
      transmission: z.string().nullable().optional()
    });

    const parsed = schema.safeParse(req.body);

    if (!parsed.success) {
      return res.status(400).json(parsed.error.flatten());
    }

    try {
      const data = parsed.data;

      const existing = await pool.query(
        `SELECT *
         FROM vehicles
         WHERE customer_id = $1 AND plate = $2
         LIMIT 1`,
        [req.user!.id, data.plate]
      );

      if (existing.rows[0]) {
        return res.json(existing.rows[0]);
      }

      const result = await pool.query(
        `INSERT INTO vehicles(
          customer_id,
          plate,
          brand,
          model,
          color,
          year,
          transmission
        )
        VALUES($1, $2, $3, $4, $5, $6, $7)
        RETURNING *`,
        [
          req.user!.id,
          data.plate,
          data.brand,
          data.model,
          data.color,
          data.year ?? null,
          data.transmission ?? null
        ]
      );

      res.status(201).json(result.rows[0]);
    } catch (error: any) {
      res.status(500).json({ message: error.message });
    }
  }
);

app.get('/pricing', async (_, res) => {
  try {
    const result = await pool.query(
      `SELECT *
       FROM pricing_settings
       ORDER BY created_at DESC
       LIMIT 1`
    );

    res.json(
      result.rows[0] || {
        base_fee: 250,
        per_km_fee: 30,
        waiting_per_minute: 5,
        night_multiplier: 1.2
      }
    );
  } catch {
    res.json({
      base_fee: 250,
      per_km_fee: 30,
      waiting_per_minute: 5,
      night_multiplier: 1.2
    });
  }
});

app.get('/requests', auth, async (req: AuthedRequest, res) => {
  try {
    let sql = 'SELECT * FROM valet_requests';
    const params: any[] = [];

    if (req.user?.role === 'CUSTOMER') {
      sql += ' WHERE customer_id = $1';
      params.push(req.user.id);
    }

    if (req.user?.role === 'DRIVER') {
      sql += " WHERE driver_id = $1 OR status = 'SEARCHING'";
      params.push(req.user.id);
    }

    sql += ' ORDER BY created_at DESC';

    const result = await pool.query(sql, params);
    res.json(result.rows);
  } catch (error: any) {
    res.status(500).json({ message: error.message });
  }
});

app.post(
  '/requests',
  auth,
  role('CUSTOMER'),
  async (req: AuthedRequest, res) => {
    const schema = z.object({
      vehicleId: z.string().uuid(),
      pickupAddress: z.string().min(1),
      pickupLat: z.number(),
      pickupLng: z.number(),
      destinationAddress: z.string().min(1),
      destinationLat: z.number(),
      destinationLng: z.number(),
      distanceKm: z.number().positive(),
      quotedPrice: z.number().positive()
    });

    const parsed = schema.safeParse(req.body);

    if (!parsed.success) {
      return res.status(400).json(parsed.error.flatten());
    }

    try {
      const data = parsed.data;

      const result = await pool.query(
        `INSERT INTO valet_requests(
          customer_id,
          vehicle_id,
          pickup_address,
          pickup_lat,
          pickup_lng,
          destination_address,
          destination_lat,
          destination_lng,
          distance_km,
          quoted_price
        )
        VALUES($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
        RETURNING *`,
        [
          req.user!.id,
          data.vehicleId,
          data.pickupAddress,
          data.pickupLat,
          data.pickupLng,
          data.destinationAddress,
          data.destinationLat,
          data.destinationLng,
          data.distanceKm,
          data.quotedPrice
        ]
      );

      const request = result.rows[0];

      io.emit('request:new', request);
      res.status(201).json(request);
    } catch (error: any) {
      res.status(500).json({ message: error.message });
    }
  }
);

app.patch(
  '/requests/:id/status',
  auth,
  role('DRIVER', 'ADMIN'),
  async (req: AuthedRequest, res) => {
    const schema = z.object({
      status: z.enum([
        'ASSIGNED',
        'DRIVER_EN_ROUTE',
        'ARRIVED',
        'VEHICLE_RECEIVED',
        'IN_TRANSIT',
        'DELIVERED',
        'COMPLETED',
        'CANCELLED'
      ])
    });

    const parsed = schema.safeParse(req.body);

    if (!parsed.success) {
      return res.status(400).json(parsed.error.flatten());
    }

    try {
      const result = await pool.query(
        `UPDATE valet_requests
         SET
           status = $1,
           driver_id = COALESCE(driver_id, $2),
           updated_at = NOW()
         WHERE id = $3
         RETURNING *`,
        [
          parsed.data.status,
          req.user!.role === 'DRIVER' ? req.user!.id : null,
          req.params.id
        ]
      );

      if (!result.rows[0]) {
        return res.status(404).json({ message: 'Talep bulunamadı' });
      }

      const updatedRequest = result.rows[0];

      io.emit('request:updated', updatedRequest);
      res.json(updatedRequest);
    } catch (error: any) {
      res.status(500).json({ message: error.message });
    }
  }
);

io.on('connection', socket => {
  console.log('Socket bağlandı:', socket.id);

  socket.on('location:update', data => {
    socket.broadcast.emit('location:updated', data);
  });

  socket.on('disconnect', reason => {
    console.log('Socket ayrıldı:', socket.id, reason);
  });
});

async function startServer(): Promise<void> {
  try {
    await initializeDatabase();

    const port = Number(process.env.PORT || 4000);

    server.listen(port, '0.0.0.0', () => {
      console.log(`ValeKapımda API ${port} portunda çalışıyor.`);
    });
  } catch (error) {
    console.error('Sunucu başlatılamadı:', error);
    process.exit(1);
  }
}

startServer();
