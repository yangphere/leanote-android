"""Task metadata, dependency, and acceptance gates."""

from __future__ import annotations

import json
import re
from datetime import date
from pathlib import Path

from .paths import DIR_ARCHIVE, FILE_TASK_JSON, get_tasks_dir


VALID_STATUSES = {"planning", "in_progress", "review", "completed"}
_UNCHECKED_ITEM = re.compile(r"^\s*[-*+]\s+\[\s\]\s+", re.IGNORECASE)
_HEADING = re.compile(r"^(#{1,6})\s+(.+?)\s*$")


def _task_directories(repo_root: Path) -> list[Path]:
    tasks_dir = get_tasks_dir(repo_root)
    if not tasks_dir.is_dir():
        return []
    result = [p for p in tasks_dir.iterdir() if p.is_dir() and p.name != DIR_ARCHIVE]
    archive = tasks_dir / DIR_ARCHIVE
    if archive.is_dir():
        for month in archive.iterdir():
            if month.is_dir():
                result.extend(p for p in month.iterdir() if p.is_dir())
    return result


def _load_index(repo_root: Path) -> tuple[dict[str, tuple[Path, dict]], list[str]]:
    index: dict[str, tuple[Path, dict]] = {}
    errors: list[str] = []
    for task_dir in _task_directories(repo_root):
        task_json = task_dir / FILE_TASK_JSON
        if not task_json.is_file():
            errors.append(f"{task_dir.name}: task.json is missing")
            continue
        try:
            data = json.loads(task_json.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            errors.append(f"{task_dir.name}: task.json is unreadable: {exc}")
            continue
        if not isinstance(data, dict):
            errors.append(f"{task_dir.name}: task.json must contain an object")
            continue
        if task_dir.name in index:
            errors.append(f"{task_dir.name}: duplicate active/archive task directory name")
            continue
        index[task_dir.name] = (task_dir, data)
    return index, errors


def _parse_iso_date(value: object, field: str, task_name: str, errors: list[str]) -> date | None:
    if not isinstance(value, str):
        errors.append(f"{task_name}: {field} must be an ISO date (YYYY-MM-DD)")
        return None
    try:
        return date.fromisoformat(value)
    except ValueError:
        errors.append(f"{task_name}: {field} must be an ISO date (YYYY-MM-DD)")
        return None


def _validate_basic(task_name: str, data: dict, errors: list[str]) -> None:
    status = data.get("status")
    if status not in VALID_STATUSES:
        errors.append(f"{task_name}: invalid status {status!r}")

    created = _parse_iso_date(data.get("createdAt"), "createdAt", task_name, errors)
    completed_value = data.get("completedAt")
    if status == "completed":
        if completed_value is None:
            errors.append(f"{task_name}: completed status requires completedAt")
        else:
            completed = _parse_iso_date(completed_value, "completedAt", task_name, errors)
            if created and completed and completed < created:
                errors.append(f"{task_name}: completedAt cannot precede createdAt")
    elif completed_value is not None:
        errors.append(f"{task_name}: non-completed status requires completedAt to be null")

    for field in ("children", "subtasks"):
        value = data.get(field, [])
        if not isinstance(value, list) or any(not isinstance(item, str) or not item for item in value):
            errors.append(f"{task_name}: {field} must be a list of task names")
    parent = data.get("parent")
    if parent is not None and (not isinstance(parent, str) or not parent):
        errors.append(f"{task_name}: parent must be a task name or null")
    meta = data.get("meta", {})
    if not isinstance(meta, dict):
        errors.append(f"{task_name}: meta must be an object")
    else:
        depends = meta.get("depends_on", [])
        if not isinstance(depends, list) or any(
            not isinstance(item, str) or not item for item in depends
        ):
            errors.append(f"{task_name}: meta.depends_on must be a list of task names")


def validate_task_graph(task_dir: Path, repo_root: Path) -> list[str]:
    """Validate one task and every relationship reachable from it."""
    index, index_errors = _load_index(repo_root)
    task_name = task_dir.name
    errors = list(index_errors)
    if task_name not in index:
        errors.append(f"{task_name}: task.json is missing or unreadable")
        return errors

    _, target = index[task_name]
    _validate_basic(task_name, target, errors)

    parent = target.get("parent")
    if isinstance(parent, str) and parent:
        parent_entry = index.get(parent)
        if parent_entry is None:
            errors.append(f"{task_name}: parent task does not exist: {parent}")
        elif task_name not in parent_entry[1].get("children", []):
            errors.append(f"{task_name}: parent {parent} does not list it in children")

    children = target.get("children", [])
    for child in children if isinstance(children, list) else []:
        child_entry = index.get(child)
        if child_entry is None:
            errors.append(f"{task_name}: child task does not exist: {child}")
        elif child_entry[1].get("parent") != task_name:
            errors.append(f"{task_name}: child {child} does not point back to its parent")

    visiting: list[str] = []
    visited: set[str] = set()

    def visit(name: str) -> None:
        if name in visiting:
            start = visiting.index(name)
            errors.append("dependency cycle: " + " -> ".join([*visiting[start:], name]))
            return
        if name in visited:
            return
        entry = index.get(name)
        if entry is None:
            errors.append(f"{task_name}: dependency task does not exist: {name}")
            return
        visiting.append(name)
        _validate_basic(name, entry[1], errors)
        meta = entry[1].get("meta", {})
        dependencies = meta.get("depends_on", []) if isinstance(meta, dict) else []
        if isinstance(dependencies, list):
            for dependency in dependencies:
                if isinstance(dependency, str) and dependency:
                    visit(dependency)
        visiting.pop()
        visited.add(name)

    visit(task_name)
    return list(dict.fromkeys(errors))


def incomplete_dependencies(task_dir: Path, repo_root: Path) -> list[str]:
    """Return reachable dependencies that are absent or not completed."""
    index, _ = _load_index(repo_root)
    target = index.get(task_dir.name)
    if target is None:
        return [f"{task_dir.name} (missing task.json)"]
    result: list[str] = []
    visited: set[str] = set()

    def visit(name: str) -> None:
        if name in visited:
            return
        visited.add(name)
        entry = index.get(name)
        if entry is None:
            result.append(f"{name} (missing)")
            return
        if entry[1].get("status") != "completed":
            result.append(f"{name} ({entry[1].get('status', 'missing status')})")
        meta = entry[1].get("meta", {})
        dependencies = meta.get("depends_on", []) if isinstance(meta, dict) else []
        if isinstance(dependencies, list):
            for dependency in dependencies:
                if isinstance(dependency, str):
                    visit(dependency)

    meta = target[1].get("meta", {})
    dependencies = meta.get("depends_on", []) if isinstance(meta, dict) else []
    if isinstance(dependencies, list):
        for dependency in dependencies:
            if isinstance(dependency, str):
                visit(dependency)
    return result


def unchecked_acceptance_items(prd_path: Path) -> list[tuple[int, str]]:
    """Return unchecked list items inside Acceptance Criteria sections."""
    try:
        lines = prd_path.read_text(encoding="utf-8").splitlines()
    except OSError:
        return []
    section_level: int | None = None
    result: list[tuple[int, str]] = []
    for line_number, line in enumerate(lines, 1):
        heading = _HEADING.match(line)
        if heading:
            level = len(heading.group(1))
            title = heading.group(2).strip().lower()
            if section_level is not None and level <= section_level:
                section_level = None
            if "acceptance criteria" in title or "验收" in title:
                section_level = level
            continue
        if section_level is not None and _UNCHECKED_ITEM.match(line):
            result.append((line_number, line.strip()))
    return result
