-- Seed a default tenant for development
CREATE SCHEMA IF NOT EXISTS tenant_default;

INSERT INTO tenants (slug, name, schema_name)
VALUES ('default', 'Default Tenant', 'tenant_default')
ON CONFLICT (slug) DO NOTHING;
