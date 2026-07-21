CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TYPE user_role AS ENUM ('CUSTOMER','DRIVER','ADMIN');
CREATE TYPE request_status AS ENUM ('SEARCHING','ASSIGNED','DRIVER_EN_ROUTE','ARRIVED','VEHICLE_RECEIVED','IN_TRANSIT','DELIVERED','COMPLETED','CANCELLED');

CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  role user_role NOT NULL,
  full_name VARCHAR(120) NOT NULL,
  phone VARCHAR(30) UNIQUE NOT NULL,
  email VARCHAR(160) UNIQUE,
  password_hash TEXT,
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE vehicles (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  customer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  plate VARCHAR(20) NOT NULL,
  brand VARCHAR(80), model VARCHAR(80), color VARCHAR(50), note TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE pricing_settings (
  id SERIAL PRIMARY KEY,
  base_fee NUMERIC(10,2) NOT NULL DEFAULT 250,
  per_km_fee NUMERIC(10,2) NOT NULL DEFAULT 30,
  waiting_per_minute NUMERIC(10,2) NOT NULL DEFAULT 5,
  night_multiplier NUMERIC(5,2) NOT NULL DEFAULT 1.20,
  updated_at TIMESTAMPTZ DEFAULT NOW()
);
INSERT INTO pricing_settings(base_fee,per_km_fee,waiting_per_minute,night_multiplier) VALUES (250,30,5,1.20);

CREATE TABLE valet_requests (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  customer_id UUID NOT NULL REFERENCES users(id),
  driver_id UUID REFERENCES users(id),
  vehicle_id UUID NOT NULL REFERENCES vehicles(id),
  pickup_address TEXT NOT NULL,
  pickup_lat NUMERIC(10,7) NOT NULL,
  pickup_lng NUMERIC(10,7) NOT NULL,
  destination_address TEXT NOT NULL,
  destination_lat NUMERIC(10,7) NOT NULL,
  destination_lng NUMERIC(10,7) NOT NULL,
  distance_km NUMERIC(10,2) NOT NULL,
  quoted_price NUMERIC(10,2) NOT NULL,
  status request_status NOT NULL DEFAULT 'SEARCHING',
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE live_locations (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  lat NUMERIC(10,7) NOT NULL,
  lng NUMERIC(10,7) NOT NULL,
  heading NUMERIC(6,2),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_requests_status ON valet_requests(status);
CREATE INDEX idx_requests_customer ON valet_requests(customer_id);
CREATE INDEX idx_requests_driver ON valet_requests(driver_id);
