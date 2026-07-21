-- ValeKapımda professional foundation migration
-- Run once on the existing PostgreSQL database.

ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_photo_url TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_verified_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS year INTEGER;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS transmission VARCHAR(30);
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
CREATE UNIQUE INDEX IF NOT EXISTS uq_vehicle_customer_plate_active ON vehicles(customer_id, plate) WHERE deleted_at IS NULL;

ALTER TABLE valet_requests ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;

ALTER TABLE live_locations ADD COLUMN IF NOT EXISTS accuracy NUMERIC(8,2);

CREATE TABLE IF NOT EXISTS otp_codes (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  phone VARCHAR(30) NOT NULL,
  purpose VARCHAR(20) NOT NULL DEFAULT 'LOGIN',
  code_hash TEXT NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ,
  request_ip VARCHAR(80),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_otp_phone_created ON otp_codes(phone, created_at DESC);

CREATE TABLE IF NOT EXISTS refresh_tokens (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash TEXT UNIQUE NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  user_agent VARCHAR(300),
  ip_address VARCHAR(80),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_refresh_user ON refresh_tokens(user_id);

CREATE TABLE IF NOT EXISTS favorite_addresses (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  customer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  label VARCHAR(40) NOT NULL,
  address TEXT NOT NULL,
  lat NUMERIC(10,7) NOT NULL,
  lng NUMERIC(10,7) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_favorite_customer ON favorite_addresses(customer_id);

CREATE TABLE IF NOT EXISTS ratings (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  request_id UUID UNIQUE NOT NULL REFERENCES valet_requests(id) ON DELETE CASCADE,
  customer_id UUID NOT NULL REFERENCES users(id),
  driver_id UUID REFERENCES users(id),
  score INTEGER NOT NULL CHECK(score BETWEEN 1 AND 5),
  comment TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ratings_driver ON ratings(driver_id);
