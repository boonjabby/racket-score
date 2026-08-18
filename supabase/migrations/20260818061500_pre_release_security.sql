do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conname = 'live_matches_snapshot_size'
          and conrelid = 'public.live_matches'::regclass
    ) then
        alter table public.live_matches
            add constraint live_matches_snapshot_size
            check (octet_length(snapshot::text) <= 16384);
    end if;
end;
$$;

create or replace function public.get_live_match(code text)
returns table (
    public_code text,
    sequence bigint,
    snapshot jsonb,
    status text,
    updated_at timestamptz,
    expires_at timestamptz
)
language sql
stable
security definer
set search_path = ''
set statement_timeout = '1s'
rows 1
as $$
    select
        match.public_code,
        match.sequence,
        match.snapshot,
        match.status,
        match.updated_at,
        match.expires_at
    from public.live_matches as match
    where code ~ '^[A-HJ-NP-Z2-9]{8}$'
      and match.public_code = code
      and match.status in ('live', 'complete')
      and match.expires_at > now()
    limit 1;
$$;

revoke all on function public.get_live_match(text) from public, authenticated;
grant execute on function public.get_live_match(text) to anon;

create extension if not exists pg_cron with schema pg_catalog;
revoke all on schema cron from public, anon, authenticated;
grant usage on schema cron to postgres;
grant all privileges on all tables in schema cron to postgres;

do $schedule$
declare
    existing_job record;
begin
    for existing_job in select jobid from cron.job where jobname = 'racket-score-delete-expired-matches' loop
        perform cron.unschedule(existing_job.jobid);
    end loop;
    perform cron.schedule(
        'racket-score-delete-expired-matches',
        '17 * * * *',
        'delete from public.live_matches where expires_at <= now();'
    );
end;
$schedule$;

delete from public.live_matches where expires_at <= now();
