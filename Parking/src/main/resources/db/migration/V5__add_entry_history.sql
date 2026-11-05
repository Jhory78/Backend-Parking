-- Entry history (RF-10): who registered each entry, and the order it is read in.

-- NULL in the entries registered before this migration: their operator was not recorded.
ALTER TABLE vehicle_entries ADD COLUMN registered_by_id BIGINT REFERENCES users (id);

CREATE INDEX vehicle_entries_entry_time_idx ON vehicle_entries (entry_time DESC, id DESC);
