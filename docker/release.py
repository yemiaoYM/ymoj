#!/usr/bin/env python3
"""维护者构建四个项目镜像，并生成使用当前 Docker 镜像的本地部署目录。"""

import argparse
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import zipfile


DOCKER_DIRECTORY = pathlib.Path(__file__).resolve().parent
if str(DOCKER_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(DOCKER_DIRECTORY))

from project_version import project_version


ROOT = pathlib.Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / "target/phase3-verification"
IMAGE_ID = re.compile(r"sha256:[0-9a-f]{64}")
REPOSITORY_DIGEST = re.compile(r"[a-zA-Z0-9][a-zA-Z0-9./:_-]*@sha256:[0-9a-f]{64}")
SERVICES = ("web", "worker", "go-judge", "frontend", "mysql", "redis", "rabbitmq")
BASE_SERVICES = ("mysql", "redis", "rabbitmq", "nginx")
PROJECT_SERVICES = ("web", "worker", "go-judge", "frontend")
COMPOSE_SERVICES = ("preflight", *SERVICES)
BASE_DEPLOYMENT_TAGS = {
    "mysql": "mysql-8.4", "redis": "redis-7.4-alpine", "rabbitmq": "rabbitmq-4.1-alpine",
}


def execute(command, cwd=ROOT, capture=False):
    result = subprocess.run(
        command, cwd=cwd, check=True, text=True, encoding="utf-8",
        errors="replace", stdout=subprocess.PIPE if capture else None,
    )
    return result.stdout if capture else None


def executable(name):
    resolved = shutil.which(name)
    if resolved is None:
        raise ValueError(f"维护者构建需要 {name}，请先安装；部署用户无需该工具")
    return resolved


def inspect_image(reference):
    image = json.loads(execute(["docker", "image", "inspect", reference], capture=True))[0]
    if image.get("Os") != "linux" or image.get("Architecture") != "amd64":
        raise ValueError("当前部署仅支持 linux/amd64 镜像")
    if not IMAGE_ID.fullmatch(image.get("Id", "")):
        raise ValueError("Docker 返回的镜像 ID 无效")
    # 不保存完整 inspect，其中可能包含构建者本地环境信息。
    labels = image.get("Config", {}).get("Labels") or {}
    return {
        "imageId": image["Id"], "repoDigests": image.get("RepoDigests", []),
        "version": labels.get("org.opencontainers.image.version", ""),
    }


def load_base_images(path, overrides):
    values = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(values, dict):
        raise ValueError("基础镜像锁必须是 JSON 对象")
    references = {}
    for service in BASE_SERVICES:
        reference = overrides.get(service) or values.get(service)
        if not isinstance(reference, str) or not REPOSITORY_DIGEST.fullmatch(reference):
            raise ValueError(f"{service} 必须指定带 sha256 的仓库镜像引用")
        references[service] = reference
    return references


def local_digest_reference(reference):
    """Docker 本地按 repository@digest 查找；只去掉末段标签，保留仓库端口。"""
    repository, digest = reference.rsplit("@", 1)
    separator = repository.rfind(":")
    if separator > repository.rfind("/"):
        repository = repository[:separator]
    return repository + "@" + digest


def verify_base_image(reference):
    # inspect 不存在时才拉取；固定 digest，不查询或更新浮动 tag。
    local_reference = local_digest_reference(reference)
    try:
        record = inspect_image(local_reference)
    except subprocess.CalledProcessError:
        execute(["docker", "pull", "--platform", "linux/amd64", reference])
        record = inspect_image(local_reference)
    expected_digest = reference.rsplit("@", 1)[1]
    if not any(value.endswith("@" + expected_digest) for value in record["repoDigests"]):
        raise ValueError("本地基础镜像与指定的仓库摘要不一致")
    return {"imageId": record["imageId"], "source": reference, "digestKind": "docker-config"}


def validate_jar(path, version=None):
    version = version if version is not None else project_version()
    if not path.is_file():
        raise ValueError(f"缺少构建产物：{path.relative_to(ROOT)}")
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        for name in names:
            leaf = pathlib.PurePosixPath(name).name.lower()
            if re.fullmatch(r"application-(?:dev|prod)\.(?:yml|yaml|properties)", leaf):
                raise ValueError(f"{path.name} 含私有环境配置，必须 clean 后重新构建")
        if "BOOT-INF/classes/application.yml" not in names:
            raise ValueError(f"{path.name} 不是完整的 Spring Boot 可执行包")
        artifact = path.stem
        metadata = [name for name in names if re.fullmatch(
            r"META-INF/maven/[^/]+/" + re.escape(artifact) + r"/pom\.properties", name,
        )]
        if len(metadata) != 1:
            raise ValueError(f"{path.name} 缺少唯一的项目版本元数据，请重新构建")
        properties = {}
        for line in archive.read(metadata[0]).decode("utf-8").splitlines():
            stripped = line.strip()
            if not stripped or stripped.startswith(("#", "!")):
                continue
            key, separator, value = stripped.partition("=")
            if not separator or key in properties:
                raise ValueError(f"{path.name} 的 Maven 版本元数据格式无效")
            properties[key] = value
        if properties.get("artifactId") != artifact or properties.get("version") != version:
            raise ValueError(f"{path.name} 版本与项目版本 {version} 不一致，不能为旧制品重新贴标签")


def validate_frontend(path, version=None):
    version = version if version is not None else project_version()
    if not (path / "index.html").is_file():
        raise ValueError("缺少前端 dist/index.html，请先完成前端构建")
    for item in path.rglob("*"):
        if item.is_symlink():
            raise ValueError("前端 dist 中不能包含符号链接")
        if item.is_file() and (item.name.startswith(".env") or item.suffix == ".map"):
            raise ValueError("前端 dist 不能包含私有环境文件或源码映射")
    marker = path / "version.json"
    if not marker.is_file():
        raise ValueError("前端 dist 缺少 version.json，请重新构建")
    metadata = json.loads(marker.read_text(encoding="utf-8"))
    if not isinstance(metadata, dict) or metadata.get("version") != version:
        raise ValueError(f"前端制品版本与项目版本 {version} 不一致，请重新构建")


def build_sources():
    # 仅清理子模块，保留父项目 target 下的下载缓存、历史验收及本地部署目录。
    # clean 防止此前开发构建残留的私有配置进入正式 jar。
    execute([
        executable("mvn"), "-pl", "ymcloud-common,ymcloud-pojo,ymcloud-core,ymcloud-judge,ymcloud-web",
        "clean", "package", "-DskipTests",
    ])
    frontend = ROOT / "ymcloud-vue"
    if any(path.is_file() and path.name != ".env.example" for path in frontend.glob(".env*")):
        raise ValueError("发布构建不读取前端本地 .env 文件，请使用干净源码工作区")
    if any(key.startswith("VITE_") for key in os.environ):
        raise ValueError("发布构建不接收本机 VITE_* 环境变量，请在干净环境中重试")
    npm = executable("npm")
    execute([npm, "ci"], cwd=frontend)
    execute([npm, "run", "build"], cwd=frontend)


def build_image(service, target, directory, nginx_image=None, version=None):
    version = version if version is not None else project_version()
    if service not in PROJECT_SERVICES:
        raise ValueError("只能为项目自产镜像设置项目版本")
    iid = directory / f"{service}.iid"
    metadata_path = directory / f"{service}.metadata.json"
    version_tag = f"ymcloud-{service}:{version}"
    command = [
        "docker", "buildx", "build", "--platform", "linux/amd64", "--load",
        "--tag", version_tag, "--iidfile", str(iid),
        "--build-arg", f"YMCLOUD_VERSION={version}",
        "--metadata-file", str(metadata_path),
    ]
    if service == "frontend":
        command.extend([
            "--file", "docker/frontend/Dockerfile", "--build-arg", f"NGINX_IMAGE={nginx_image}",
        ])
    else:
        cache = ARTIFACTS / "packages"
        cache.mkdir(parents=True, exist_ok=True)
        command.extend([
            "--file", "docker/go-judge/Dockerfile", "--target", target,
            "--build-context", f"deb-cache={cache}",
        ])
    execute([*command, "."])
    image_id = iid.read_text(encoding="utf-8").strip()
    record = inspect_image(image_id)
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    exported = metadata.get("containerimage.digest", "")
    if (record["imageId"] != image_id
            or metadata.get("containerimage.config.digest") != image_id
            or not IMAGE_ID.fullmatch(exported)):
        raise ValueError("构建摘要与实际镜像不一致，拒绝生成本地部署目录")
    if record.get("version") != version:
        raise ValueError("自产镜像的 OCI 版本标签与项目版本不一致")
    if inspect_image(version_tag)["imageId"] != image_id:
        raise ValueError("项目版本标签与实际构建镜像内容不一致")
    if service in ("go-judge", "worker", "web"):
        arguments = [] if service == "go-judge" else ["--toolchain-only"]
        execute([
            "docker", "run", "--rm", "--network", "none", "--entrypoint", "python3",
            image_id, "/opt/ymcloud/verify_versions.py", *arguments,
        ])
    elif service == "frontend":
        # 解析完整实际配置；用本机地址解析 upstream，无需启动 Web 或访问网络。
        execute([
            "docker", "run", "--rm", "--network", "none", "--add-host", "web:127.0.0.1",
            "--entrypoint", "nginx", image_id, "-t",
        ])
    return {
        "imageId": image_id, "exporterDigest": exported,
        "version": version, "versionTag": version_tag,
        "digestKind": "docker-config" if exported == image_id else "image-manifest",
    }


def render_compose(source, records, version=None):
    """校验公开版本标签，再用已验证的本地 config ID 生成部署配置。"""
    version = version if version is not None else project_version()
    sections = list(re.finditer(r"(?m)^services:[ \t]*\r?$", source))
    if len(sections) != 1:
        raise ValueError("Compose 必须包含唯一的 services 配置")
    start = sections[0].end()
    following = re.search(r"(?m)^[^\s#][^\r\n]*", source[start:])
    end = start + following.start() if following else len(source)
    body = source[start:end]
    entries = list(re.finditer(r"(?m)^  ([a-z][a-z0-9-]*):[ \t]*\r?$", body))
    names = [entry.group(1) for entry in entries]
    if len(names) != len(COMPOSE_SERVICES) or set(names) != set(COMPOSE_SERVICES):
        raise ValueError("Compose 服务必须完整且唯一，不能缺少或增加发布服务")
    if set(records) != set(SERVICES):
        raise ValueError("本地镜像记录必须完整对应七个发布镜像")
    rendered = body[:entries[0].start()]
    source_images = {}
    for index, entry in enumerate(entries):
        name = entry.group(1)
        service = "web" if name == "preflight" else name
        image_id = records[service].get("imageId", "")
        if not isinstance(image_id, str) or not IMAGE_ID.fullmatch(image_id):
            raise ValueError("本地部署引用必须是已验证的完整镜像 ID")
        stop = entries[index + 1].start() if index + 1 < len(entries) else len(body)
        block = body[entry.start():stop]
        images = list(re.finditer(r"(?m)^    image: ([^\r\n]+)\r?$", block))
        tag = f"{service}-{version}" if service in PROJECT_SERVICES else BASE_DEPLOYMENT_TAGS[service]
        expected = r"\$\{IMAGE_REPOSITORY:-([a-z0-9][a-z0-9./:_-]*)\}:" + re.escape(tag)
        reference = re.fullmatch(expected, images[0].group(1)) if len(images) == 1 else None
        if reference is None:
            raise ValueError(f"Compose 的 {name} 必须使用版本标签 {tag}，并与本次构建版本一致")
        repository = reference.group(1)
        if repository.rfind(":") > repository.rfind("/") or "//" in repository or repository.endswith("/"):
            raise ValueError("IMAGE_REPOSITORY 默认值必须是仓库地址，不能包含 tag 或空路径")
        source_images[name] = images[0].group(1)
        policies = list(re.finditer(r"(?m)^    pull_policy: ([^\r\n]+)\r?$", block))
        if len(policies) != 1 or policies[0].group(1) != "missing":
            raise ValueError(f"Compose 的 {name} 必须声明 pull_policy: missing")
        block = block[:images[0].start(1)] + image_id + block[images[0].end(1):]
        block = re.sub(r"(?m)^(    pull_policy: )missing(?=\r?$)", r"\g<1>never", block)
        if name == "mysql":
            block = render_seed_mount(block)
        rendered += block
    if source_images["preflight"] != source_images["web"]:
        raise ValueError("Compose 的 preflight 必须与 Web 使用相同仓库镜像标签")
    if len({reference.rsplit("}:", 1)[0] for reference in source_images.values()}) != 1:
        raise ValueError("Compose 的全部服务必须使用相同的 IMAGE_REPOSITORY 默认值")
    return source[:start] + rendered + source[end:]


def render_seed_mount(block):
    """只改初始化 SQL 的来源，保留只读与禁止自动创建宿主路径的约束。"""
    mounts = list(re.finditer(r"(?m)^      - [^\r\n]*(?:\r?\n(?: {8,}\S[^\r\n]*|[ \t]*(?=\r?$)))*", block))
    binds = [mount for mount in mounts if re.search(r"(?m)^(?:      - |        )type: bind\r?$", mount.group())]
    if len(binds) != 1:
        raise ValueError("MySQL 必须包含唯一的长语法初始化 SQL 挂载")
    mount = binds[0]
    text = mount.group()
    required = {
        "source": "../sql/ymcloud.sql",
        "target": "/docker-entrypoint-initdb.d/01-ymcloud.sql",
        "read_only": "true",
    }
    for key, expected in required.items():
        values = re.findall(r"(?m)^(?:      - |        )" + key + r": ([^\r\n]+)\r?$", text)
        if values != [expected]:
            raise ValueError(f"MySQL 初始化 SQL 挂载的 {key} 配置不正确")
    if (len(re.findall(r"(?m)^        bind:\r?$", text)) != 1
            or re.findall(r"(?m)^          create_host_path: ([^\r\n]+)\r?$", text) != ["false"]):
        raise ValueError("MySQL 初始化 SQL 挂载必须禁止自动创建宿主路径")
    text = text.replace("source: ../sql/ymcloud.sql", "source: ./initdb/ymcloud.sql")
    return block[:mount.start()] + text + block[mount.end():]


def validate_deployment_environment(environment, compose):
    """部署目录只接收与 Compose 默认值一致的公开模板，不携带本机私有配置。"""
    defaults = {}
    for match in re.finditer(r"(?<!\$)\$\{([A-Z][A-Z0-9_]*):-([^{}]*)\}", compose):
        key, value = match.groups()
        if key in defaults and defaults[key] != value:
            raise ValueError(f"Compose 中 {key} 的默认值不一致")
        defaults[key] = value
    values = {}
    for line in environment.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        match = re.fullmatch(r"([A-Z][A-Z0-9_]*)=(.*)", stripped)
        if match is None:
            raise ValueError("deploy/.env 必须使用公开模板的 KEY=value 格式，不复制私有环境配置")
        key, value = match.groups()
        if key in values:
            raise ValueError(f"deploy/.env 重复定义 {key}")
        if key not in defaults or value != defaults[key]:
            raise ValueError(f"deploy/.env 的 {key} 已偏离 Compose 公开默认值，拒绝复制私有配置")
        values[key] = value
    if not defaults or values.keys() != defaults.keys():
        raise ValueError("deploy/.env 与 Compose 的公开默认配置项不完整对应")


def deployment_templates():
    source = ROOT / "deploy"
    if source.is_symlink() or not source.is_dir():
        raise ValueError("缺少真实的 deploy 发布模板目录")
    templates = {}
    for name in (".env", "compose.yaml", "README.md"):
        path = source / name
        if path.is_symlink() or not path.is_file():
            raise ValueError(f"缺少真实的 deploy/{name} 发布模板文件")
        templates[name] = path.read_text(encoding="utf-8")
    validate_deployment_environment(templates[".env"], templates["compose.yaml"])
    return templates


def copy_deployment(destination, records, version=None):
    if destination.exists() and any(destination.iterdir()):
        raise ValueError("部署输出目录非空，请使用新的目录；不会覆盖已有配置或部署目录")
    templates = deployment_templates()
    templates["compose.yaml"] = render_compose(templates["compose.yaml"], records, version)
    seed = ROOT / "sql/ymcloud.sql"
    if seed.is_symlink() or not seed.is_file():
        raise ValueError("缺少真实的 sql/ymcloud.sql 初始化文件")
    destination.mkdir(parents=True, exist_ok=True)
    # 显式白名单，Python 预检已封装进镜像，不复制额外脚本或用户本地配置。
    for name, text in templates.items():
        (destination / name).write_text(text, encoding="utf-8")
    initdb = destination / "initdb"
    initdb.mkdir(exist_ok=True)
    shutil.copyfile(seed, initdb / "ymcloud.sql")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=pathlib.Path, default=ARTIFACTS / "full-stack-deployment")
    parser.add_argument("--base-images", type=pathlib.Path, default=ROOT / "docker/base-images.lock.json")
    parser.add_argument("--skip-build", action="store_true", help="仅跳过 Maven/npm，镜像仍构建并验证")
    for service in BASE_SERVICES:
        parser.add_argument(f"--{service}-image", help="带 sha256 的固定镜像引用")
    args = parser.parse_args()
    version = project_version()
    destination = args.output.resolve()
    if destination == ROOT or ROOT in destination.parents and ARTIFACTS not in destination.parents:
        raise ValueError("源码仓库内的本地部署目录必须放在 target/phase3-verification 下")
    if destination.exists() and (not destination.is_dir() or any(destination.iterdir())):
        raise ValueError("输出目录必须不存在或为空，避免覆盖既有部署目录和私有 .env")
    deployment_templates()
    overrides = {service: getattr(args, service + "_image") for service in BASE_SERVICES}
    references = load_base_images(args.base_images, overrides)
    engine = json.loads(execute(["docker", "info", "--format", "{{json .}}"], capture=True))
    if engine.get("OSType") != "linux":
        raise ValueError("构建需要运行中的 Linux Docker Engine")
    if not args.skip_build:
        build_sources()
    for module in ("ymcloud-web", "ymcloud-judge"):
        validate_jar(ROOT / module / "target" / f"{module}.jar", version)
    validate_frontend(ROOT / "ymcloud-vue/dist", version)
    directory = ARTIFACTS / "full-stack-build"
    directory.mkdir(parents=True, exist_ok=True)
    bases = {service: verify_base_image(reference) for service, reference in references.items()}
    records = {service: bases[service] for service in ("mysql", "redis", "rabbitmq")}
    for service, target in (("go-judge", "runtime"), ("worker", "worker"), ("web", "web"), ("frontend", None)):
        records[service] = build_image(service, target, directory, references["nginx"], version)
    copy_deployment(destination, records, version)
    lock = {"version": version, "platform": "linux/amd64", "images": records, "buildBases": references}
    (destination / "images.lock.json").write_text(json.dumps(lock, indent=2) + "\n", encoding="utf-8")
    print(f"本地部署目录已生成：{destination}")
    print("镜像已加载到当前 Docker；进入上述目录，按需编辑 .env，执行 docker compose up -d。")
    print("公开默认密码和密钥仅用于快速测试，正式部署前必须更换。")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    try:
        main()
    except (ValueError, OSError, zipfile.BadZipFile, subprocess.CalledProcessError) as error:
        print(f"本地部署目录构建失败：{error}", file=sys.stderr)
        sys.exit(1)
