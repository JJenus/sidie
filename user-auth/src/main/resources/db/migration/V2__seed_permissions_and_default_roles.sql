-- Seed permissions
INSERT INTO permissions (permission_key, description) VALUES
  ('users.read', 'Read user information'),
  ('users.write', 'Create and update users'),
  ('users.delete', 'Delete users'),
  ('users.assign_roles', 'Assign roles to users'),
  ('organizations.read', 'Read organizations'),
  ('organizations.write', 'Create and update organizations'),
  ('roles.read', 'Read roles'),
  ('roles.write', 'Create and update roles'),
  ('vehicles.read', 'Read vehicle data'),
  ('vehicles.write', 'Create and update vehicles'),
  ('vehicles.assign_device', 'Assign devices to vehicles'),
  ('alerts.read', 'Read alerts'),
  ('alerts.write', 'Create and update alert rules'),
  ('trips.read', 'Read trip data'),
  ('trips.export', 'Export trip data'),
  ('devices.command', 'Send commands to devices'),
  ('devices.read', 'Read device data'),
  ('notifications.read', 'Read notifications'),
  ('notifications.write', 'Create and update notifications');

-- SUPER_ADMIN (system-wide)
INSERT INTO roles (name, org_id, description) VALUES ('SUPER_ADMIN', NULL, 'System administrator with all permissions');
INSERT INTO role_permissions (role_id, permission_id)
  SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'SUPER_ADMIN';

-- Default permission key sets for the three org-scoped roles
CREATE TABLE IF NOT EXISTS tmp_role_perm_keys (role_name VARCHAR(100) NOT NULL, perm_key VARCHAR(100) NOT NULL);
INSERT INTO tmp_role_perm_keys (role_name, perm_key) VALUES
  ('TENANT_ADMIN', 'users.read'),
  ('TENANT_ADMIN', 'users.write'),
  ('TENANT_ADMIN', 'users.delete'),
  ('TENANT_ADMIN', 'users.assign_roles'),
  ('TENANT_ADMIN', 'organizations.read'),
  ('TENANT_ADMIN', 'roles.read'),
  ('TENANT_ADMIN', 'roles.write'),
  ('TENANT_ADMIN', 'vehicles.read'),
  ('TENANT_ADMIN', 'vehicles.write'),
  ('TENANT_ADMIN', 'vehicles.assign_device'),
  ('TENANT_ADMIN', 'alerts.read'),
  ('TENANT_ADMIN', 'alerts.write'),
  ('TENANT_ADMIN', 'devices.read'),
  ('TENANT_ADMIN', 'devices.command'),
  ('OPERATOR', 'users.read'),
  ('OPERATOR', 'vehicles.read'),
  ('OPERATOR', 'alerts.read'),
  ('OPERATOR', 'alerts.write'),
  ('OPERATOR', 'trips.read'),
  ('OPERATOR', 'notifications.read'),
  ('OPERATOR', 'notifications.write'),
  ('VIEWER', 'vehicles.read'),
  ('VIEWER', 'alerts.read'),
  ('VIEWER', 'trips.read');

-- Note: org-scoped default role rows are created on-the-fly per organization at registration time
DROP TABLE IF EXISTS tmp_role_perm_keys;
