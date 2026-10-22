#!/usr/bin/env python3
"""
Static sanity checks for the Java sources of the backend.

This is deliberately *not* a compiler: Maven and `javac` are the source of truth (see
docs/testing-strategy.md). What this script does is catch the classes of mistake that are cheap to
detect without a build and expensive to discover later — a typo in an import, a class declared in the
wrong package, a reference to a member that does not exist, a file that does not parse at all.

It is used in two ways:

    python3 tools/verify/java_static_check.py                 # check backend/src/main/java
    python3 tools/verify/java_static_check.py --strict        # warnings are failures
    python3 tools/verify/java_static_check.py --json report.json

Checks performed
  1. syntax            — every file parses with tree-sitter-java (no ERROR/MISSING nodes)
  2. package_path      — the package declaration matches the directory layout
  3. type_index        — every declared type (including nested) is discoverable by fully-qualified name
  4. imports           — every com.realtimechat.* import resolves to a declared type
  5. members           — X.member references where X is a project type and `member` is not declared on
                         X or on its declared supertypes (heuristic; reported as warnings)
  6. duplicates        — two files declaring the same fully-qualified type

Exit code is 0 when there are no errors (and no warnings under --strict).
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

try:
    import tree_sitter_java
    from tree_sitter import Language, Parser
except ImportError as exc:  # pragma: no cover - tooling guard
    print(f"tree-sitter is required for this check: {exc}", file=sys.stderr)
    print("hint: pip install tree-sitter tree-sitter-java", file=sys.stderr)
    sys.exit(2)

REPO_ROOT = Path(__file__).resolve().parents[2]

# Members that Hibernate/Spring/JPA generate or that come from library supertypes. Referencing one of
# these through a project type is legitimate and must not be reported as an unknown member.
MEMBER_ALLOWLIST = {
    # Hibernate/JPA generated accessors and metamodel helpers
    "get", "set", "values", "valueOf", "of", "builder",
    # Java record/enum/object methods that are always present
    "toString", "hashCode", "equals", "name", "ordinal", "getClass", "clone", "wait", "notify",
}

# Supertypes that live outside the project. When a type extends one of these, member checking stops:
# the member may be inherited from a framework base class we cannot see.
EXTERNAL_SUPERTYPE_MARKERS = (
    "JpaRepository", "CrudRepository", "PagingAndSortingRepository", "Repository",
    "OncePerRequestFilter", "Filter", "ChannelInterceptor", "HandshakeInterceptor",
    "WebSocketMessageBrokerConfigurer", "ChannelInterceptorAdapter", "ConstraintValidator",
    "HandlerMethodArgumentResolver", "RuntimeException", "Exception", "Error",
    "AbstractClassJavaType", "Servlet", "Principal", "Serializable",
)

DECLARATION_TYPES = {
    "class_declaration",
    "interface_declaration",
    "enum_declaration",
    "record_declaration",
    "annotation_type_declaration",
}


@dataclass
class TypeInfo:
    simple_name: str
    qualified_name: str
    file: Path
    kind: str
    package: str
    supertypes: list[str] = field(default_factory=list)
    members: set[str] = field(default_factory=set)
    methods: set[str] = field(default_factory=set)
    extends_external: bool = False
    nested: dict[str, "TypeInfo"] = field(default_factory=dict)


@dataclass
class Problems:
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    def error(self, where: str, message: str) -> None:
        self.errors.append(f"{where}: {message}")

    def warn(self, where: str, message: str) -> None:
        self.warnings.append(f"{where}: {message}")


def make_parser() -> Parser:
    language = Language(tree_sitter_java.language())
    return Parser(language)


def walk(node):
    """Depth-first iteration over every node in a tree."""
    stack = [node]
    while stack:
        current = stack.pop()
        yield current
        stack.extend(reversed(current.children))


def text(node, source: bytes) -> str:
    return source[node.start_byte:node.end_byte].decode("utf-8", errors="replace")


def find_child(node, kind: str):
    for child in node.children:
        if child.type == kind:
            return child
    return None


def find_children(node, kind: str):
    return [child for child in node.children if child.type == kind]


def parse_file(path: Path, parser: Parser, problems: Problems, index: dict[str, TypeInfo],
               simple_index: dict[str, list[TypeInfo]], class_count: list[int]) -> None:
    source = path.read_bytes()
    try:
        tree = parser.parse(source)
    except Exception as exc:  # pragma: no cover - parser failure
        problems.error(path.as_posix(), f"could not parse: {exc}")
        return

    if tree.root_node.has_error:
        # Report the first error node precisely: a line number is far more useful than "has_error".
        for node in walk(tree.root_node):
            if node.type == "ERROR" or node.is_missing:
                line = node.start_point[0] + 1
                snippet = text(node, source).splitlines()[0][:80] if text(node, source) else node.type
                problems.error(path.as_posix(), f"syntax error at line {line}: {snippet.strip()}")
                break
        return

    package_name = ""
    for node in tree.root_node.children:
        if node.type == "package_declaration":
            package_name = text(node, source).replace("package", "").strip().rstrip(";").strip()
            break

    # package/directory agreement
    if package_name:
        expected_suffix = Path(*package_name.split("."))
        if path.parent.as_posix().replace("\\", "/").endswith(str(expected_suffix).replace("\\", "/")) is False:
            problems.error(path.as_posix(),
                           f"package {package_name} does not match the directory layout")

    def handle_declaration(node, parent_qualified: str | None) -> None:
        name_node = find_child(node, "identifier")
        if name_node is None:
            return
        simple_name = text(name_node, source)
        qualified = f"{parent_qualified}.{simple_name}" if parent_qualified else (
            f"{package_name}.{simple_name}" if package_name else simple_name)
        info = TypeInfo(
            simple_name=simple_name,
            qualified_name=qualified,
            file=path,
            kind=node.type,
            package=package_name,
        )

        # supertypes (extends/implements)
        for clause in ("superclass", "super_interfaces", "extends_interfaces"):
            holder = find_child(node, clause)
            if holder is None:
                continue
            for type_node in walk(holder):
                if type_node.type == "type_identifier":
                    supertypes = info.supertypes
                    name = text(type_node, source)
                    if name not in supertypes:
                        supertypes.append(name)
        for name in info.supertypes:
            if any(marker in name for marker in EXTERNAL_SUPERTYPE_MARKERS):
                info.extends_external = True

        # members: methods, fields, constructors, nested types
        body = find_child(node, "class_body") or find_child(node, "interface_body") or find_child(
            node, "enum_body") or find_child(node, "annotation_type_body")
        if body is not None:
            for member in body.children:
                if member.type == "method_declaration":
                    name = find_child(member, "identifier")
                    if name is not None:
                        info.methods.add(text(name, source))
                        info.members.add(text(name, source))
                elif member.type == "constructor_declaration":
                    name = find_child(member, "identifier")
                    if name is not None:
                        info.members.add(text(name, source))
                elif member.type == "field_declaration":
                    for declarator in find_children(member, "variable_declarator"):
                        for ident in find_children(declarator, "identifier"):
                            info.members.add(text(ident, source))
                elif member.type == "enum_constant":
                    name = find_child(member, "identifier")
                    if name is not None:
                        info.members.add(text(name, source))
                elif member.type in DECLARATION_TYPES:
                    nested_name_node = find_child(member, "identifier")
                    if nested_name_node is not None:
                        info.members.add(text(nested_name_node, source))
                    handle_declaration(member, qualified)

        class_count[0] += 1
        if qualified in index:
            problems.error(path.as_posix(),
                           f"duplicate type {qualified} (also declared in "
                           f"{index[qualified].file.as_posix()})")
        else:
            index[qualified] = info
            simple_index.setdefault(simple_name, []).append(info)

    for node in tree.root_node.children:
        if node.type in DECLARATION_TYPES:
            handle_declaration(node, None)


def check_imports(path: Path, source: bytes, tree, index: dict[str, TypeInfo], problems: Problems) -> None:
    for node in tree.root_node.children:
        if node.type != "import_declaration":
            continue
        raw = text(node, source)
        match = re.match(r"import\s+(static\s+)?([\w.]+(?:\.\*)?)\s*;", raw)
        if not match:
            continue
        static, target = bool(match.group(1)), match.group(2)
        if target.endswith("*"):
            continue
        if static:
            # import static Foo.Bar.baz — the type part must exist
            parts = target.split(".")
            candidate = ".".join(parts[:-1])
            if candidate.startswith("com.realtimechat") and candidate not in index:
                problems.error(path.as_posix(), f"unresolved static import {target}")
            continue
        if not target.startswith("com.realtimechat"):
            continue
        if target not in index:
            problems.error(path.as_posix(), f"unresolved import {target}")


def check_members(path: Path, source: bytes, tree, index: dict[str, TypeInfo],
                  simple_index: dict[str, list[TypeInfo]], problems: Problems) -> None:
    """
    Report `SomeProjectType.unknownMember` references.

    Method/field/type members are guessed from declarations in the same file plus declared project
    supertypes. When a type extends something outside the project the check is skipped for that type,
    because inherited members cannot be seen. False positives are therefore possible only for types
    whose supertype chain is entirely inside the project and still hides a member — which would be a
    genuine surprise worth looking at.
    """
    local_types = {info.simple_name: info for info in simple_index.get("", [])}
    # Collect simple names declared in this file (they shadow same-named project types elsewhere).
    declared_here = set()
    for node in walk(tree.root_node):
        if node.type in DECLARATION_TYPES:
            name_node = find_child(node, "identifier")
            if name_node is not None:
                declared_here.add(text(name_node, source))

    seen: set[str] = set()
    for node in walk(tree.root_node):
        if node.type != "field_access" and node.type != "method_invocation":
            continue
        target_node = node.child_by_field_name("object") if node.type == "field_access" else node.child_by_field_name("object")
        field_node = node.child_by_field_name("field") if node.type == "field_access" else node.child_by_field_name("name")
        if target_node is None or field_node is None:
            continue
        if target_node.type != "identifier":
            continue
        type_name = text(target_node, source)
        member_name = text(field_node, source)
        if type_name in declared_here and type_name not in local_types:
            continue
        if member_name in MEMBER_ALLOWLIST:
            continue
        candidates = [c for c in simple_index.get(type_name, []) if c.package == current_package(tree, source)]
        if not candidates:
            candidates = simple_index.get(type_name, [])
        if not candidates:
            continue
        info = candidates[0]
        if info.kind == "enum_declaration":
            continue
        if info.extends_external:
            continue
        members = set(info.members)
        # Walk project supertypes (one level deep is enough for this codebase's shallow hierarchies).
        for supertype in info.supertypes:
            for parent in simple_index.get(supertype, []):
                members |= parent.members
                for grand in parent.supertypes:
                    for gp in simple_index.get(grand, []):
                        members |= gp.members
        if member_name in members:
            continue
        key = f"{info.qualified_name}.{member_name}"
        if key in seen:
            continue
        seen.add(key)
        line = node.start_point[0] + 1
        problems.warn(path.as_posix(),
                      f"line {line}: {info.simple_name}.{member_name} is not declared on "
                      f"{info.qualified_name} (or its project supertypes)")


_CURRENT_PACKAGE: dict[int, str] = {}


def current_package(tree, source: bytes) -> str:
    key = id(tree)
    if key in _CURRENT_PACKAGE:
        return _CURRENT_PACKAGE[key]
    package = ""
    for node in tree.root_node.children:
        if node.type == "package_declaration":
            package = text(node, source).replace("package", "").strip().rstrip(";").strip()
            break
    _CURRENT_PACKAGE[key] = package
    return package


def entity_schema_check(problems: Problems, index: dict[str, TypeInfo]) -> None:
    """Cross-check @Entity classes against the migration files (column names only)."""
    migration_dir = REPO_ROOT / "backend/src/main/resources/db/migration"
    if not migration_dir.exists():
        return
    schema: dict[str, set[str]] = {}
    for migration in sorted(migration_dir.glob("V*.sql")):
        sql = migration.read_text()
        for match in re.finditer(r"CREATE TABLE (?:IF NOT EXISTS )?(\w+)\s*\((.*?)\n\);", sql, re.S):
            table, body = match.group(1), match.group(2)
            columns = set()
            for line in body.splitlines():
                line = line.strip()
                if not line or line.startswith("--"):
                    continue
                if line.upper().startswith(("CONSTRAINT", "PRIMARY KEY", "FOREIGN KEY", "UNIQUE")):
                    continue
                column = re.match(r"([a-z_][a-z0-9_]*)\s+", line)
                if column:
                    columns.add(column.group(1))
            schema[table] = columns
        for match in re.finditer(r"ALTER TABLE (\w+)\s+ADD COLUMN (?:IF NOT EXISTS )?([a-z_][a-z0-9_]*)", sql):
            schema.setdefault(match.group(1), set()).add(match.group(2))

    for info in index.values():
        if info.kind != "class_declaration":
            continue
        entity_file = info.file.read_text()
        if "@Entity" not in entity_file:
            continue
        table_match = re.search(r'@Table\s*\(\s*name\s*=\s*"(\w+)"', entity_file)
        if not table_match:
            problems.error(info.file.as_posix(), "@Entity without @Table(name=...)")
            continue
        table = table_match.group(1)
        if table not in schema:
            problems.error(info.file.as_posix(), f"@Table(name=\"{table}\") does not exist in the migrations")
            continue
        # Only @Column/@JoinColumn name attributes are column names. @MapsId takes an *attribute* name
        # of the embedded id, so it is deliberately excluded.
        declared = set(re.findall(r'@(?:Column|JoinColumn)\s*\(\s*name\s*=\s*"([a-z_][a-z0-9_]*)"', entity_file))
        unknown = {c for c in declared if c not in schema[table]}
        if unknown:
            problems.error(info.file.as_posix(),
                           f"columns {sorted(unknown)} are not present in table {table}")


def main() -> int:
    parser_args = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser_args.add_argument("--root", default=str(REPO_ROOT / "backend/src/main/java"),
                             help="source directory to check (default: backend/src/main/java)")
    parser_args.add_argument("--strict", action="store_true", help="treat warnings as failures")
    parser_args.add_argument("--json", help="write a machine-readable report to this path")
    parser_args.add_argument("--skip-members", action="store_true",
                             help="skip the heuristic member check (fast mode)")
    args = parser_args.parse_args()

    root = Path(args.root)
    if not root.exists():
        print(f"source root {root} does not exist", file=sys.stderr)
        return 2

    parser = make_parser()
    problems = Problems()
    index: dict[str, TypeInfo] = {}
    simple_index: dict[str, list[TypeInfo]] = {}
    class_count = [0]

    files = sorted(root.rglob("*.java"))
    if not files:
        print(f"no .java files found under {root}", file=sys.stderr)
        return 2

    trees: dict[Path, tuple[bytes, object]] = {}
    for path in files:
        source = path.read_bytes()
        parse_file(path, parser, problems, index, simple_index, class_count)
        trees[path] = (source, parser.parse(source))

    for path, (source, tree) in trees.items():
        if tree.root_node.has_error:
            continue
        check_imports(path, source, tree, index, problems)
        if not args.skip_members:
            check_members(path, source, tree, index, simple_index, problems)

    entity_schema_check(problems, index)

    for message in problems.errors:
        print(f"ERROR   {message}")
    for message in problems.warnings:
        print(f"WARNING {message}")

    print()
    print(f"checked {len(files)} files, {class_count[0]} declared types")
    print(f"errors: {len(problems.errors)}, warnings: {len(problems.warnings)}")

    if args.json:
        Path(args.json).write_text(json.dumps({
            "files": len(files),
            "types": class_count[0],
            "errors": problems.errors,
            "warnings": problems.warnings,
        }, indent=2))

    if problems.errors:
        return 1
    if args.strict and problems.warnings:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
