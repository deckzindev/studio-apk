-- ============================================================================
-- SCRIPT SQL COMPLETO PARA O SUPABASE (GitBuilder Cloud)
-- Execute este script no menu: Supabase Dashboard -> SQL Editor -> New Query
-- ============================================================================

-- 1. TABELA DE CONFIGURAÇÃO GLOBAL (Onde fica o Token do GitHub do Administrador)
CREATE TABLE IF NOT EXISTS public.app_config (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- Ativar segurança Row Level Security (RLS)
ALTER TABLE public.app_config ENABLE ROW LEVEL SECURITY;

-- Política 1: Permitir leitura das configurações pelo App (anon e authenticated)
DROP POLICY IF EXISTS "Permitir leitura publica de configs" ON public.app_config;
CREATE POLICY "Permitir leitura publica de configs"
    ON public.app_config
    FOR SELECT
    TO anon, authenticated
    USING (true);

-- Política 2: Apenas o painel do Supabase / service_role pode inserir ou alterar o token
DROP POLICY IF EXISTS "Apenas admin pode modificar config" ON public.app_config;
CREATE POLICY "Apenas admin pode modificar config"
    ON public.app_config
    FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

-- Inserção do Token GitHub do Administrador:
-- IMPORTANTE: Substitua 'ghp_SEU_TOKEN_AQUI' pelo seu token pessoal do GitHub!
INSERT INTO public.app_config (key, value, description)
VALUES (
    'github_token',
    'ghp_SEU_TOKEN_AQUI',
    'Token do Administrador utilizado automaticamente por todos os usuarios para compilar APKs'
)
ON CONFLICT (key) DO UPDATE
SET value = EXCLUDED.value,
    updated_at = timezone('utc'::text, now());


-- ============================================================================
-- 2. TABELA DE HISTÓRICO DE COMPILAÇÕES (build_records)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.build_records (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    user_email TEXT,
    project_name TEXT NOT NULL,
    repo_name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'SUCCESS',
    elapsed_seconds BIGINT DEFAULT 0,
    timestamp BIGINT NOT NULL,
    apk_url TEXT,
    apk_size BIGINT,
    apk_file_name TEXT,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- Índices para buscas rápidas por usuário e data
CREATE INDEX IF NOT EXISTS idx_build_records_user_id ON public.build_records(user_id);
CREATE INDEX IF NOT EXISTS idx_build_records_timestamp ON public.build_records(timestamp DESC);

-- Ativar RLS
ALTER TABLE public.build_records ENABLE ROW LEVEL SECURITY;

-- Política 1: Usuário só pode ver os seus próprios registros
DROP POLICY IF EXISTS "Usuarios podem ler seu proprio historico" ON public.build_records;
CREATE POLICY "Usuarios podem ler seu proprio historico"
    ON public.build_records
    FOR SELECT
    TO authenticated
    USING (auth.uid()::text = user_id);

-- Política 2: Usuário só pode inserir registros para o seu próprio user_id
DROP POLICY IF EXISTS "Usuarios podem inserir seu proprio historico" ON public.build_records;
CREATE POLICY "Usuarios podem inserir seu proprio historico"
    ON public.build_records
    FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid()::text = user_id);

-- Política 3: Usuário pode atualizar seus próprios registros
DROP POLICY IF EXISTS "Usuarios podem atualizar seu proprio historico" ON public.build_records;
CREATE POLICY "Usuarios podem atualizar seu proprio historico"
    ON public.build_records
    FOR UPDATE
    TO authenticated
    USING (auth.uid()::text = user_id)
    WITH CHECK (auth.uid()::text = user_id);

-- Política 4: Usuário pode deletar seus próprios registros
DROP POLICY IF EXISTS "Usuarios podem deletar seu proprio historico" ON public.build_records;
CREATE POLICY "Usuarios podem deletar seu proprio historico"
    ON public.build_records
    FOR DELETE
    TO authenticated
    USING (auth.uid()::text = user_id);


-- ============================================================================
-- 3. CONFIGURAÇÃO DO BUCKET DE ARMAZENAMENTO PARA APKS (Storage 'apks')
-- ============================================================================

-- Cria o bucket público 'apks' se ele não existir
INSERT INTO storage.buckets (id, name, public)
VALUES ('apks', 'apks', true)
ON CONFLICT (id) DO UPDATE
SET public = true;

-- Políticas de segurança para o Storage de APKs
DROP POLICY IF EXISTS "Download publico de APKs" ON storage.objects;
CREATE POLICY "Download publico de APKs"
    ON storage.objects
    FOR SELECT
    TO public
    USING (bucket_id = 'apks');

DROP POLICY IF EXISTS "Usuarios autenticados podem enviar APKs" ON storage.objects;
CREATE POLICY "Usuarios autenticados podem enviar APKs"
    ON storage.objects
    FOR INSERT
    TO authenticated
    WITH CHECK (bucket_id = 'apks');

DROP POLICY IF EXISTS "Usuarios autenticados podem atualizar APKs" ON storage.objects;
CREATE POLICY "Usuarios autenticados podem atualizar APKs"
    ON storage.objects
    FOR UPDATE
    TO authenticated
    USING (bucket_id = 'apks');

DROP POLICY IF EXISTS "Usuarios autenticados podem deletar APKs" ON storage.objects;
CREATE POLICY "Usuarios autenticados podem deletar APKs"
    ON storage.objects
    FOR DELETE
    TO authenticated
    USING (bucket_id = 'apks');
