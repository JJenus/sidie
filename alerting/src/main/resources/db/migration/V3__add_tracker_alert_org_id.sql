ALTER TABLE tracker_alerts ADD COLUMN IF NOT EXISTS organization_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_tracker_alerts_org_id ON tracker_alerts(organization_id);
CREATE INDEX IF NOT EXISTS idx_tracker_alerts_org_vehicle ON tracker_alerts(organization_id, vehicle_id);
