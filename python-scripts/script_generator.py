#!/usr/bin/env python3

import argparse
import csv
import logging
import os
import shutil
import subprocess
import typing


log = logging.getLogger(__name__)


class ScriptException(Exception):
    """Custom exception class with message for this module."""

    def __init__(self, value):
        self.value = value
        super().__init__(value)

    def __repr__(self):
        return repr(self.value)


def guess_cplex_library_path() -> str:
    gp_path = os.path.join(os.path.expanduser("~"), ".gradle",
                           "gradle.properties")
    if not os.path.isfile(gp_path):
        raise ScriptException(
            "gradle.properties not available at {}".format(gp_path))

    with open(gp_path, 'r') as fin:
        for line in fin:
            line = line.strip()
            if line.startswith('cplexLibPath='):
                return line.split('=')[-1].strip()

    raise ScriptException("unable to read value of cplexLibPath ")


def get_base_path() -> str:
    return os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))


def get_data_path() -> str:
    return os.path.join(get_base_path(), "data")


def get_jar_path() -> str:
    return os.path.join(get_base_path(), "build", "libs", "uber.jar")


class Config(typing.NamedTuple):
    csv_path: str
    run_type: str
    uberjar: bool
    cplex_lib_path: str = guess_cplex_library_path()
    data_path: str = get_data_path()
    jar_path: str = get_jar_path()


class Controller:
    """class that manages the functionality of the entire script."""

    def __init__(self, config: Config):
        self.config = config
        self._base_cmd = [
            "java", "-Xms32m", "-Xmx32g",
            "-Djava.library.path={}".format(self.config.cplex_lib_path),
            "-jar", "./uber.jar",
        ]

    def run(self):
        if self.config.uberjar:
            self._prepare_uberjar()
        if self.config.run_type == "b":
            self._setup_bang_for_buck_runs()
            return

        if self.config.run_type == "e":
            self._generate_exhaustive_setup(
                cases=self._collect_exhaustive_cases())
            return

        if self.config.run_type == "u":
            self._generate_exhaustive_setup(
                cases=self._collect_exhaustive_cases(
                    discretizations=["1"], excludes=[]),
                test_name="euclidean"
            )
            return


        if self.config.run_type == "s":
            test_name = "search"
        elif self.config.run_type == "t":
            test_name = "threading"
        else:
            raise ScriptException(f"unknown run type {self.config.run_type}")

        arg_sets = [
            ["-s", "1", "-i", "0"],
        ]
        self._generate_non_exhaustive_setup(test_name, arg_sets)

    def _setup_bang_for_buck_runs(self):
        cases = self._collect_exhaustive_cases(discretizations=["2", "4"])
        args = [
            ["-b", "1"],
            ["-b", "0"]
        ]
        self._generate_exhaustive_setup(cases, args, "bangforbuck")

    def _generate_non_exhaustive_setup(
            self, test_name: str, arg_sets: typing.List[typing.List[str]]):
        cases = self._collect_cases_from_file()
        runs_file_path = os.path.join(
            os.path.dirname(__file__), f"{test_name}_runs.txt")

        with open(runs_file_path, 'w') as f_out:
            counter = 0
            for folder_name, instance_name, num_disc in cases:
                folder_path = './data/{}/'.format(folder_name)
                cmd = [c for c in self._base_cmd]
                cmd.extend([
                    "-n", instance_name,
                    "-p", folder_path,
                    "-d", str(num_disc),
                ])

                for cmd_args in arg_sets:
                    results_file_name = "results_{}.yaml".format(counter)
                    results_path = os.path.join("results", results_file_name)
                    results_path = "./{}".format(results_path)
                    cmd1 = cmd + cmd_args + ["-o", results_path]
                    f_out.write(' '.join(cmd1))
                    f_out.write('\n')
                    counter += 1

        log.info("wrote cases to {}".format(runs_file_path))
        self._prepare_test_folder(
            test_name, self._get_folder_and_file_names(cases))

    def _generate_exhaustive_setup(self, cases, additional_cmds=[],
                                   test_name="exhaustive"):
        runs_file_path = os.path.join(
            os.path.dirname(__file__), f"{test_name}_runs.txt")

        with open(runs_file_path, 'w') as f_out:
            counter = 0
            for folder, file_name, num_disc in cases:
                prefix = "./results/results"
                cmd = [c for c in self._base_cmd]
                cmd.extend([
                    "-n", file_name,
                    "-p", "./data/{}/".format(folder),
                    "-d", str(num_disc),
                    "-i", "0", ])

                if not additional_cmds:
                    cmd.extend(["-o", prefix + "_{}.yaml".format(counter)])
                    f_out.write(' '.join(cmd))
                    f_out.write('\n')
                    counter += 1
                    continue

                for addition in additional_cmds:
                    cmd1 = [c for c in cmd]
                    cmd1.extend(addition)
                    cmd1.extend(["-o", prefix + "_{}.yaml".format(counter)])
                    f_out.write(' '.join(cmd1))
                    f_out.write('\n')
                    counter += 1

        log.info("wrote cases to {}".format(runs_file_path))
        self._prepare_test_folder(
            test_name, self._get_folder_and_file_names(cases))

    def _collect_exhaustive_cases(self, discretizations=["2", "4", "6"], excludes = ["_100_", "_102_"]):
        cases = []
        for folder in os.listdir(self.config.data_path):
            if excludes and any([e in folder for e in excludes]):
                continue

            for f in os.listdir(os.path.join(self.config.data_path, folder)):
                if f.endswith(".txt"):
                    for num_disc in discretizations:
                        cases.append((folder, f, num_disc))

        if not cases:
            raise ScriptException("no cases found for exhaustive runs")

        return cases

    def _collect_cases_from_file(self):
        cases = []
        with open(self.config.csv_path, 'r', newline='') as csvfile:
            reader = csv.reader(csvfile)
            next(reader)  # skip header row
            for row in reader:
                folder_name = row[0].replace('./data/', '')
                folder_name = folder_name[:-1]
                cases.append((folder_name, row[1], int(row[2])))

        if not cases:
            raise ScriptException('no instances found in csv file')

        return cases

    def _get_folder_and_file_names(self, cases):
        return list(set([(c[0], c[1]) for c in cases]))

    def _prepare_uberjar(self):
        base_path = get_base_path()
        cwd = os.getcwd()
        os.chdir(base_path)
        subprocess.check_call(['gradle', 'clean', 'cleanlogs', 'uberjar'])
        if not os.path.isfile(self.config.jar_path):
            raise ScriptException("uberjar build failed")
        log.info("prepared uberjar")
        os.chdir(cwd)

    def _prepare_test_folder(self, test_name, cases):
        rt_path = os.path.join(get_base_path(), test_name)
        os.makedirs(rt_path, exist_ok=True)
        if self.config.uberjar:
            shutil.copy(self.config.jar_path,
                os.path.join(rt_path, 'uber.jar'))
        runs_file_name = '{}_runs.txt'.format(test_name)
        for f in [runs_file_name, 'submit-batch.sh', 'slurm-batch-job.sh']:
            src_path = os.path.join(os.path.dirname(__file__), f)
            dst_path = os.path.join(rt_path, f)
            shutil.copy(src_path, dst_path)

        os.remove(os.path.join(os.path.dirname(__file__), runs_file_name))
        log.info('copied runs file and shell scripts to {}'.format(rt_path))

        test_data_path = os.path.join(rt_path, 'data')
        os.makedirs(test_data_path, exist_ok=True)
        for folder_name, file_name, in cases:
            folder_path = os.path.join(test_data_path, folder_name)
            os.makedirs(folder_path, exist_ok=True)
            src = os.path.join(self.config.data_path, folder_name, file_name)
            dst = os.path.join(folder_path, file_name)
            log.info("src: {}".format(src))
            log.info("dst: {}".format(dst))
            shutil.copy(src, dst)
            log.info("copied {}, {}".format(folder_name, file_name))

        for name in ['output', 'results']:
            folder_path = os.path.join(rt_path, name)
            os.makedirs(folder_path, exist_ok=True)
        log.info('created output and result folders.')


def handle_command_line() -> Config:
    parser = argparse.ArgumentParser(
        formatter_class=argparse.ArgumentDefaultsHelpFormatter)

    # Path to csv file with instance path, instance name and number of
    # discretizations. Instances from this file will be used to generate
    # run commands.
    results_path = os.path.join(get_base_path(), "final-results-v2")
    fpath = os.path.join(results_path, "search_comparison_instances.csv")
    parser.add_argument("-c", "--csv_path", type=str,
                        help="path to csv file with instances to run",
                        default=fpath)

    parser.add_argument("-r", "--run_type", choices=["b", "e", "s", "t", "u"],
                        help="run type: bang-for-buck (b), exhaustive (e), "
                        "search (s), thread (t) or euclidean (u)",
                        default="s")

    parser.add_argument("-u", "--uberjar", action="store_true",
                        help="generate uberJar")

    args = parser.parse_args()
    return Config(**vars(args))


def main():
    logging.basicConfig(format='%(asctime)s %(levelname)s--: %(message)s',
                        level=logging.DEBUG)

    try:
        config = handle_command_line()
        controller = Controller(config)
        controller.run()
    except ScriptException as se:
        log.error(se)


if __name__ == '__main__':
    main()
