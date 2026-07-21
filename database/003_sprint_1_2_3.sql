-- ValeKapımda Sprint 1+2+3 birleşik migration
CREATE TABLE IF NOT EXISTS coupons (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(), code VARCHAR(40) UNIQUE NOT NULL,
  discount_type VARCHAR(10) NOT NULL CHECK(discount_type IN ('PERCENT','FIXED')),
  discount_value NUMERIC(10,2) NOT NULL CHECK(discount_value>0), min_amount NUMERIC(10,2) NOT NULL DEFAULT 0,
  max_discount NUMERIC(10,2), active BOOLEAN NOT NULL DEFAULT TRUE,
  starts_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), ends_at TIMESTAMPTZ NOT NULL DEFAULT (NOW()+INTERVAL '1 year'),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS coupon_redemptions (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(), coupon_id UUID NOT NULL REFERENCES coupons(id),
  customer_id UUID NOT NULL REFERENCES users(id), request_id UUID REFERENCES valet_requests(id),
  redeemed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), UNIQUE(coupon_id,customer_id)
);
CREATE TABLE IF NOT EXISTS payments (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(), request_id UUID NOT NULL REFERENCES valet_requests(id),
  customer_id UUID NOT NULL REFERENCES users(id), provider VARCHAR(20) NOT NULL,
  provider_reference VARCHAR(200), amount NUMERIC(10,2) NOT NULL, discount_amount NUMERIC(10,2) NOT NULL DEFAULT 0,
  payable_amount NUMERIC(10,2) NOT NULL, status VARCHAR(30) NOT NULL DEFAULT 'PENDING', coupon_id UUID REFERENCES coupons(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_payments_customer ON payments(customer_id,created_at DESC);
CREATE TABLE IF NOT EXISTS notification_tokens (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(), user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token TEXT UNIQUE NOT NULL, platform VARCHAR(20) NOT NULL DEFAULT 'ANDROID', created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS notifications (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(), user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title VARCHAR(160) NOT NULL, body TEXT NOT NULL, data JSONB NOT NULL DEFAULT '{}'::jsonb,
  read_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id,created_at DESC);
INSERT INTO coupons(code,discount_type,discount_value,min_amount,max_discount)
VALUES('HOSGELDIN','PERCENT',15,300,150) ON CONFLICT(code) DO NOTHING;
