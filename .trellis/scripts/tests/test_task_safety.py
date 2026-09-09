from __future__ import annotations

from contextlib import redirect_stderr, redirect_stdout
import io
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPTS_DIR = Path(__file__).resolve().parents[1]
TASK_SCRIPT = SCRIPTS_DIR / "task.py"
sys.path.insert(0, str(SCRIPTS_DIR))

from common.developer import init_developer


def run(command: list[str], cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        command,
        cwd=cwd,
        text=True,
        encoding="utf-8",
        errors="replace",
        capture_output=True,
        check=False,
    )


class TrellisRepoTestCase(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.repo = Path(self.temp_dir.name)
        (self.repo / ".trellis" / "tasks").mkdir(parents=True)
        run(["git", "init", "-b", "dev"], self.repo)
        run(["git", "config", "user.name", "Trellis Test"], self.repo)
        run(["git", "config", "user.email", "trellis@example.test"], self.repo)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def write_task(
        self,
        name: str,
        *,
        status: str = "planning",
        parent: str | None = None,
        children: list[str] | None = None,
        depends_on: list[str] | None = None,
        completed_at: str | None = None,
        delivery_mode: str | None = None,
        branch: str | None = "feature/test",
        base_branch: str | None = "dev",
        checked_acceptance: bool = True,
    ) -> Path:
        task_dir = self.repo / ".trellis" / "tasks" / name
        task_dir.mkdir(parents=True, exist_ok=True)
        meta: dict[str, object] = {"depends_on": depends_on or []}
        if delivery_mode is not None:
            meta["delivery_mode"] = delivery_mode
        data = {
            "id": name,
            "name": name,
            "title": name,
            "description": "test task",
            "status": status,
            "createdAt": "2026-09-09",
            "completedAt": completed_at,
            "branch": branch,
            "base_branch": base_branch,
            "children": children or [],
            "subtasks": [],
            "parent": parent,
            "meta": meta,
        }
        (task_dir / "task.json").write_text(
            json.dumps(data, indent=2) + "\n", encoding="utf-8"
        )
        mark = "x" if checked_acceptance else " "
        (task_dir / "prd.md").write_text(
            f"# Requirements\n\n## Acceptance Criteria\n\n- [{mark}] complete\n",
            encoding="utf-8",
        )
        return task_dir

    def task(self, *args: str) -> subprocess.CompletedProcess[str]:
        return run([sys.executable, str(TASK_SCRIPT), *args], self.repo)


class ArchiveSafetyTests(TrellisRepoTestCase):
    def test_archive_commit_does_not_include_another_dirty_archived_task(self) -> None:
        source = self.write_task("09-09-current", status="in_progress")
        archive_root = self.repo / ".trellis" / "tasks" / "archive" / "2026-08"
        dirty_archives = [archive_root / "08-01-other", archive_root / "08-02-second"]
        for archived_task in dirty_archives:
            archived_task.mkdir(parents=True)
            (archived_task / "task.json").write_text(
                '{"status":"completed"}\n', encoding="utf-8"
            )
        run(["git", "add", ".trellis/tasks"], self.repo)
        run(["git", "commit", "-m", "seed tasks"], self.repo)
        for archived_task in dirty_archives:
            (archived_task / "task.json").write_text(
                '{"status":"completed","dirty":true}\n', encoding="utf-8"
            )

        result = self.task("archive", source.name, "--skip-branch-validation")

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        committed = run(["git", "show", "--format=", "--name-only", "HEAD"], self.repo).stdout
        self.assertNotIn("08-01-other", committed)
        self.assertNotIn("08-02-second", committed)
        status = run(["git", "status", "--short"], self.repo).stdout
        self.assertIn("08-01-other/task.json", status)
        self.assertIn("08-02-second/task.json", status)

    def test_archive_refuses_unchecked_acceptance_criteria(self) -> None:
        task_dir = self.write_task(
            "09-09-open-acceptance", status="in_progress", checked_acceptance=False
        )

        result = self.task(
            "archive", task_dir.name, "--no-commit", "--skip-branch-validation"
        )

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("acceptance", (result.stdout + result.stderr).lower())
        self.assertTrue(task_dir.is_dir())

    def test_direct_delivery_accepts_same_branch_and_base_branch(self) -> None:
        task_dir = self.write_task(
            "09-09-direct",
            status="in_progress",
            delivery_mode="direct",
            branch="dev",
            base_branch="dev",
        )

        result = self.task("archive", task_dir.name, "--no-commit")

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_same_branch_without_direct_delivery_is_rejected(self) -> None:
        task_dir = self.write_task(
            "09-09-pr", status="in_progress", branch="dev", base_branch="dev"
        )

        result = self.task("archive", task_dir.name, "--no-commit")

        self.assertNotEqual(result.returncode, 0)
        self.assertTrue(task_dir.is_dir())


class DeveloperSafetyTests(unittest.TestCase):
    def test_init_rejects_unsafe_names_before_writing_identity(self) -> None:
        for unsafe_name in ("../escape", "team/member", r"team\member", ".", "..", None):
            with self.subTest(name=unsafe_name), tempfile.TemporaryDirectory() as temp:
                repo = Path(temp)
                (repo / ".trellis").mkdir()
                name = str(repo / "absolute-escape") if unsafe_name is None else unsafe_name

                with redirect_stderr(io.StringIO()), redirect_stdout(io.StringIO()):
                    initialized = init_developer(name, repo)
                self.assertFalse(initialized)
                self.assertFalse((repo / ".trellis" / ".developer").exists())

    def test_init_accepts_safe_slug_and_creates_identity_last(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            repo = Path(temp)
            (repo / ".trellis").mkdir()

            with redirect_stderr(io.StringIO()), redirect_stdout(io.StringIO()):
                initialized = init_developer("dev_team-1", repo)
            self.assertTrue(initialized)
            self.assertEqual(
                (repo / ".trellis" / ".developer").read_text(encoding="utf-8").splitlines()[0],
                "name=dev_team-1",
            )
            self.assertTrue((repo / ".trellis" / "workspace" / "dev_team-1" / "journal-1.md").is_file())


class TaskGraphValidationTests(TrellisRepoTestCase):
    def test_validate_rejects_missing_dependency(self) -> None:
        task_dir = self.write_task("09-09-task", depends_on=["09-09-missing"])

        result = self.task("validate", task_dir.name)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("09-09-missing", result.stdout + result.stderr)

    def test_validate_rejects_dependency_cycle(self) -> None:
        first = self.write_task("09-09-first", depends_on=["09-09-second"])
        self.write_task("09-09-second", depends_on=[first.name])

        result = self.task("validate", first.name)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("cycle", (result.stdout + result.stderr).lower())

    def test_validate_rejects_non_reciprocal_parent_child_link(self) -> None:
        parent = self.write_task("09-09-parent", children=[])
        child = self.write_task("09-09-child", parent=parent.name)

        result = self.task("validate", child.name)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("parent", (result.stdout + result.stderr).lower())

    def test_validate_rejects_inconsistent_status_dates(self) -> None:
        task_dir = self.write_task(
            "09-09-completed", status="completed", completed_at=None
        )

        result = self.task("validate", task_dir.name)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("completedat", (result.stdout + result.stderr).lower())

    def test_start_refuses_incomplete_dependency(self) -> None:
        dependency = self.write_task("09-09-dependency", status="in_progress")
        task_dir = self.write_task("09-09-dependent", depends_on=[dependency.name])

        result = self.task("start", task_dir.name)

        self.assertNotEqual(result.returncode, 0)
        data = json.loads((task_dir / "task.json").read_text(encoding="utf-8"))
        self.assertEqual(data["status"], "planning")

    def test_archive_refuses_incomplete_dependency(self) -> None:
        dependency = self.write_task("09-09-dependency", status="in_progress")
        task_dir = self.write_task(
            "09-09-dependent", status="in_progress", depends_on=[dependency.name]
        )

        result = self.task(
            "archive", task_dir.name, "--no-commit", "--skip-branch-validation"
        )

        self.assertNotEqual(result.returncode, 0)
        self.assertTrue(task_dir.is_dir())


if __name__ == "__main__":
    unittest.main()
