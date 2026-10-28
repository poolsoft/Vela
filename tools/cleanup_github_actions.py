#!/usr/bin/env python3
import argparse
import datetime
import json
import os
import subprocess
import sys

def run_gh_command(args):
    result = subprocess.run(
        ["gh"] + args,
        capture_output=True,
        text=True,
        encoding="utf-8"
    )
    if result.returncode != 0:
        print(f"Hata ({' '.join(args)}): {result.stderr.strip()}", file=sys.stderr)
        return None
    return result.stdout

def parse_iso_datetime(dt_str):
    if not dt_str:
        return datetime.datetime.min.replace(tzinfo=datetime.timezone.utc)
    clean_str = dt_str.replace("Z", "+00:00")
    return datetime.datetime.fromisoformat(clean_str)

def cleanup_artifacts(repo, min_keep, days_limit, dry_run=False):
    print(f"\n--- Artifact Temizligi: {repo} (Min Korunan: {min_keep}, Gun Limiti: {days_limit}) ---")
    output = run_gh_command(["api", "--paginate", f"repos/{repo}/actions/artifacts"])
    if not output:
        print("Artifact listesi alinamadi.")
        return

    try:
        data = json.loads(output)
        artifacts = data.get("artifacts", [])
    except Exception as e:
        print(f"JSON parse hatasi: {e}")
        return

    print(f"Toplam tespit edilen artifact sayisi: {len(artifacts)}")
    if len(artifacts) <= min_keep:
        print(f"Artifact sayisi ({len(artifacts)}) asgari koruma limitinde ({min_keep}). Silme yapilmadi.")
        return

    artifacts.sort(key=lambda x: parse_iso_datetime(x.get("created_at")), reverse=True)
    protected = artifacts[:min_keep]
    candidates = artifacts[min_keep:]

    now = datetime.datetime.now(datetime.timezone.utc)
    threshold = now - datetime.timedelta(days=days_limit)

    to_delete = []
    for art in candidates:
        created_at = parse_iso_datetime(art.get("created_at"))
        if created_at < threshold:
            to_delete.append(art)

    print(f"Korunan guncel artifact sayisi: {len(protected)}")
    print(f"7 gunden eski silinecek artifact sayisi: {len(to_delete)}")

    deleted_count = 0
    for art in to_delete:
        art_id = art["id"]
        art_name = art.get("name", "isimsiz")
        if dry_run:
            print(f"[DRY-RUN] Silinecek: Artifact {art_id} ({art_name}) - Olusturma: {art.get('created_at')}")
            deleted_count += 1
        else:
            del_res = run_gh_command(["api", "-X", "DELETE", f"repos/{repo}/actions/artifacts/{art_id}"])
            if del_res is not None:
                print(f"Silindi: Artifact {art_id} ({art_name})")
                deleted_count += 1

    print(f"Tamamlanan artifact temizligi: {deleted_count} silindi.")

def cleanup_runs(repo, min_keep, days_limit, dry_run=False):
    print(f"\n--- Workflow Run Temizligi: {repo} (Min Korunan: {min_keep}, Gun Limiti: {days_limit}) ---")
    output = run_gh_command(["api", "--paginate", f"repos/{repo}/actions/runs?status=completed"])
    if not output:
        print("Workflow run listesi alinamadi.")
        return

    try:
        data = json.loads(output)
        runs = data.get("workflow_runs", [])
    except Exception as e:
        print(f"JSON parse hatasi: {e}")
        return

    print(f"Toplam tamamlanmis run sayisi: {len(runs)}")
    if len(runs) <= min_keep:
        print(f"Run sayisi ({len(runs)}) asgari koruma limitinde ({min_keep}). Silme yapilmadi.")
        return

    runs.sort(key=lambda x: parse_iso_datetime(x.get("created_at")), reverse=True)
    protected = runs[:min_keep]
    candidates = runs[min_keep:]

    now = datetime.datetime.now(datetime.timezone.utc)
    threshold = now - datetime.timedelta(days=days_limit)

    to_delete = []
    for r in candidates:
        created_at = parse_iso_datetime(r.get("created_at"))
        if created_at < threshold:
            to_delete.append(r)

    print(f"Korunan guncel run sayisi: {len(protected)}")
    print(f"7 gunden eski silinecek run sayisi: {len(to_delete)}")

    deleted_count = 0
    for r in to_delete:
        run_id = r["id"]
        run_name = r.get("name", "isimsiz")
        if dry_run:
            print(f"[DRY-RUN] Silinecek: Run {run_id} ({run_name}) - Olusturma: {r.get('created_at')}")
            deleted_count += 1
        else:
            del_res = run_gh_command(["run", "delete", str(run_id), "--repo", repo])
            if del_res is not None:
                print(f"Silindi: Run {run_id} ({run_name})")
                deleted_count += 1

    print(f"Tamamlanan run temizligi: {deleted_count} silindi.")

def main():
    parser = argparse.ArgumentParser(description="GitHub Actions akilli artifact ve run temizleme scripti")
    parser.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY", "poolsoft/Vela"), help="GitHub repo (ornek: poolsoft/Vela)")
    parser.add_argument("--min-keep", type=int, default=3, help="Asgari korunacak son basarili kayit adedi")
    parser.add_argument("--days", type=int, default=7, help="Kac gunden eskilerin temizlenecegi")
    parser.add_argument("--dry-run", action="store_true", help="Gercekten silmeden sadece listele")
    args = parser.parse_args()

    cleanup_artifacts(args.repo, args.min_keep, args.days, args.dry_run)
    cleanup_runs(args.repo, args.min_keep, args.days, args.dry_run)

if __name__ == "__main__":
    main()
