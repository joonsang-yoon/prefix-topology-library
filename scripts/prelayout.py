#!/usr/bin/env python3

import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

FLOW = {
    "target": "freepdk45_demo",
    "name": "synflow",
    "pdk": "freepdk45",
    "standardCellLibrary": "nangate45",
    "synthesisStrategy": "AREA3",
}
DEFAULT_CONSTRAINTS = {
    "clockName": "virtual_clk",
    "clockPeriodNs": 1.0,
    "inputDelayNs": 0.0,
    "outputDelayNs": 0.0,
}


def main() -> None:
    if sys.argv[1] == "--prepare":
        lambdapdk_root()
        return

    output_file, request = Path(sys.argv[1]), json.loads(sys.argv[2])
    module = request["module"]
    rtl_files = [Path(path) for path in request["rtlFiles"]]
    constraints = DEFAULT_CONSTRAINTS | request.get("constraints", {})
    analysis = analyze(rtl_files, module, constraints)
    metrics = analysis["metrics"]
    report = {
        "metrics": {
            "areaUm2": round(metrics["areaUm2"], 3),
            "clockPeriodNs": metrics["clockPeriodNs"],
        },
        "module": module,
        "flow": FLOW,
        "constraints": constraints,
        "toolchain": analysis["toolchain"],
    } | request["summary"]

    output_file.parent.mkdir(parents=True, exist_ok=True)
    output_file.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")


def analyze(rtl_files: list[Path], module: str, constraints: dict[str, object]) -> dict[str, object]:
    if fake_payload := os.environ.get("PREFIX_TOPOLOGY_LIBRARY_FAKE_RUNNER_JSON"):
        wait_for_fake_peer()
        return json.loads(Path(fake_payload).read_text(encoding="utf-8"))

    import lambdapdk
    import siliconcompiler
    import siliconcompiler.utils.multiprocessing as sc_multiprocessing
    from siliconcompiler import ASIC, Design
    from siliconcompiler.targets import freepdk45_demo
    from siliconcompiler.tools.yosys import syn_asic

    force_tcp_mp_manager(sc_multiprocessing)
    root = lambdapdk_root()

    with tempfile.TemporaryDirectory(prefix="prefix-topology-library-prelayout-") as tempdir:
        workspace = Path(tempdir)
        sdc_file = workspace / f"{module}.sdc"
        sdc_file.write_text(
            """create_clock -name {clockName} -period {clockPeriodNs}
	set_input_delay {inputDelayNs} -clock {clockName} [all_inputs]
	set_output_delay {outputDelayNs} -clock {clockName} [all_outputs]
	""".format(**constraints),
            encoding="utf-8",
        )

        design = Design(module)
        with design.active_fileset("rtl"):
            design.set_topmodule(module)
            for rtl_file in rtl_files:
                design.add_file(str(rtl_file))
        with design.active_fileset("sdc"):
            design.add_file(str(sdc_file))

        project = ASIC(design)
        project.add_fileset(["rtl", "sdc"])
        freepdk45_demo(project)

        for name in (FLOW["pdk"], FLOW["standardCellLibrary"]):
            project.get_library(name).set_dataroot("lambdapdk", str(root), clobber=True)

        project.set_flow(FLOW["name"])
        project.option.set_jobname("prelayout")
        project.option.set_builddir(str(workspace / "prelayout-work"))
        project.set("option", "quiet", True)
        project.set("option", "nodisplay", True)
        syn_asic.ASICSynthesis.find_task(project).set_yosys_strategy(FLOW["synthesisStrategy"])
        project.run()

        history = project.history("prelayout")
        return {
            "metrics": {
                "areaUm2": history.get("metric", "cellarea", step="timing", index="0"),
                "clockPeriodNs": history.get("metric", "tmin", step="timing", index="0"),
            },
            "toolchain": {
                "siliconcompiler": str(siliconcompiler.__version__),
                "lambdapdk": str(lambdapdk.__version__),
                "yosys": tool_version(["yosys", "--version"]),
                "opensta": tool_version(["sta", "-version"]),
            },
        }


def wait_for_fake_peer() -> None:
    if not (barrier_dir := os.environ.get("PREFIX_TOPOLOGY_LIBRARY_FAKE_RUNNER_BARRIER_DIR")):
        return

    root = Path(barrier_dir)
    root.mkdir(parents=True, exist_ok=True)
    (root / str(os.getppid())).touch()

    for _ in range(600):
        if len(list(root.iterdir())) >= 2:
            return
        time.sleep(0.05)

    raise TimeoutError("fake prelayout worker processes did not overlap")


def force_tcp_mp_manager(sc_multiprocessing) -> None:
    class TcpSyncManager(sc_multiprocessing.SyncManager):
        def __init__(self, *args, **kwargs):
            if kwargs.get("address") is None:
                kwargs["address"] = ("127.0.0.1", 0)
            super().__init__(*args, **kwargs)

    sc_multiprocessing.SyncManager = TcpSyncManager


def lambdapdk_root() -> Path:
    import lambdapdk

    version = str(lambdapdk.__version__)
    cache_home = Path(os.environ.get("XDG_CACHE_HOME", Path.home() / ".cache"))
    cache_dir = cache_home / "prefix-topology-library" / "lambdapdk" / f"v{version}"
    root = cache_dir / f"lambdapdk-{version}"
    archive_path = cache_dir / f"lambdapdk-v{version}.tar.gz"

    if root.is_dir():
        return root

    cache_dir.mkdir(parents=True, exist_ok=True)
    if not archive_path.exists():
        urllib.request.urlretrieve(
            f"https://github.com/siliconcompiler/lambdapdk/archive/refs/tags/v{version}.tar.gz",
            archive_path,
        )
    shutil.unpack_archive(archive_path, cache_dir)
    return root


def tool_version(command: list[str]) -> str:
    return subprocess.check_output(command, text=True, stderr=subprocess.STDOUT).splitlines()[0]


if __name__ == "__main__":
    main()
