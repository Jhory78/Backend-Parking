-- Translates the users table to English, keeping every existing row.

-- Check constraint Hibernate added on the old enum column (absent on fresh databases)
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_rol_check;

ALTER TABLE users RENAME COLUMN nombre TO name;
ALTER TABLE users RENAME COLUMN correo TO email;
ALTER TABLE users RENAME COLUMN rol TO role;

UPDATE users SET role = 'OPERATOR' WHERE role = 'OPERARIO';

-- Boolean "activo" becomes an explicit status
ALTER TABLE users ADD COLUMN status VARCHAR(20);
UPDATE users SET status = CASE WHEN activo THEN 'ACTIVE' ELSE 'INACTIVE' END;
ALTER TABLE users ALTER COLUMN status SET NOT NULL;
ALTER TABLE users DROP COLUMN activo;

ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'OPERATOR'));
ALTER TABLE users ADD CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'INACTIVE'));
