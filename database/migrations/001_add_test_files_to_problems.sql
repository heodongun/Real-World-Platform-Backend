-- Add test_files and starter_code columns to problems table
-- Migration: 001_add_test_files_to_problems.sql

-- Add test_files column (stores JSON map of test file paths and contents)
ALTER TABLE problems ADD COLUMN IF NOT EXISTS test_files TEXT NOT NULL DEFAULT '{}';

-- Add starter_code column (optional initial code template for users)
ALTER TABLE problems ADD COLUMN IF NOT EXISTS starter_code TEXT;

-- Add comment for documentation
COMMENT ON COLUMN problems.test_files IS 'JSON map of test file paths to their contents (e.g., {"src/test/kotlin/Test.kt": "test code"})';
COMMENT ON COLUMN problems.starter_code IS 'Optional starter code template provided to users for the problem';
