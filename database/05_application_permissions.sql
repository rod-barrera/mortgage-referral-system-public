-- Ejecutar como administrador de la base del proyecto, despues de crear
-- el rol LOGIN mortgage_referrals_app con una contraseña externa al repositorio.
BEGIN;
GRANT CONNECT ON DATABASE mortgage_referrals_eval2 TO mortgage_referrals_app;
GRANT USAGE ON SCHEMA public TO mortgage_referrals_app;
GRANT SELECT ON public.role, public.status TO mortgage_referrals_app;
GRANT SELECT, INSERT, UPDATE ON public.user_account, public.referral TO mortgage_referrals_app;
GRANT SELECT, INSERT ON public.lead, public.follow_up, public.status_history TO mortgage_referrals_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO mortgage_referrals_app;
REVOKE UPDATE, DELETE, TRUNCATE ON public.status_history FROM mortgage_referrals_app;
REVOKE UPDATE, DELETE, TRUNCATE ON public.follow_up FROM mortgage_referrals_app;
COMMIT;
