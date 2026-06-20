-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Create a default tenant schema for development
CREATE SCHEMA IF NOT EXISTS tenant_default;
