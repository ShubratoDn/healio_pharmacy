-- PostgreSQL SQL Script to Truncate Sale, Stock, and Inventory Data
-- This script will delete all data from sales, stock-in, and inventory tables
-- while preserving product and dosage form information

-- Disable foreign key checks temporarily (PostgreSQL doesn't have this, but we'll use CASCADE)
-- Start a transaction for safety
BEGIN;

-- 1. Truncate Sale-related tables (child tables first, then parent)
TRUNCATE TABLE sale_item CASCADE;
TRUNCATE TABLE sale CASCADE;

-- 2. Truncate Stock In-related tables (child tables first, then parent)
TRUNCATE TABLE stock_in_item CASCADE;
TRUNCATE TABLE stock_in CASCADE;

-- 3. Truncate Inventory table
TRUNCATE TABLE inventory CASCADE;

-- Commit the transaction
COMMIT;

-- Alternative approach if you want to reset sequences as well:
-- BEGIN;
-- TRUNCATE TABLE sale_item RESTART IDENTITY CASCADE;
-- TRUNCATE TABLE sale RESTART IDENTITY CASCADE;
-- TRUNCATE TABLE stock_in_item RESTART IDENTITY CASCADE;
-- TRUNCATE TABLE stock_in RESTART IDENTITY CASCADE;
-- TRUNCATE TABLE inventory RESTART IDENTITY CASCADE;
-- COMMIT;



