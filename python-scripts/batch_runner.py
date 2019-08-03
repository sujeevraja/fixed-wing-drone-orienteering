#!/usr/bin/env python3

import argparse
import logging
import os
import sqlite3
import subprocess


log = logging.getLogger(__name__)


class Config(object):
    """Class that holds global parameters."""

    def __init__(self):
        folder_path = os.path.dirname(os.path.realpath(__file__))
        self.base_path = os.path.abspath(os.path.join(folder_path, '..'))
        self.data_path = os.path.join(self.base_path, 'data')
        self.jar_path = os.path.join(self.base_path, 'build', 'libs', 'uber.jar')
        self.cplex_lib_path = None

        self.run_budget_set = False
        self.run_mean_set = False
        self.run_parallel_set = False
        self.run_quality_set = False
        self.run_time_comparison_set = False


class ScriptException(Exception):
    """Custom exception class with message for this module."""

    def __init__(self, value):
        self.value = value
        super().__init__(value)

    def __repr__(self):
        return repr(self.value)


def guess_cplex_library_path():
    gp_path = os.path.join(os.path.expanduser("~"), ".gradle", "gradle.properties")
    if not os.path.isfile(gp_path):
        log.warn("gradle.properties not available at {}".format(gp_path))
        return None

    with open(gp_path, 'r') as fin:
        for line in fin:
            line = line.strip()
            if line.startswith('cplexLibPath='):
                return line.split('=')[-1].strip()

    return None


class Controller:
    """class that manages the functionality of the entire script."""

    def __init__(self, config):
        self.config = config
        self._base_cmd = None
        self._models = ["naive", "dep", "benders"]
        self._cases = []  # paths to files with problem data
        self._connection = None  # will point to a connection to a SQL database

    def run(self):
        self._prepare_db()
        return

        if self.config.cplex_lib_path is None:
            self.config.cplex_lib_path = guess_cplex_library_path()

        self._prepare_uberjar()
        self._validate_setup()
        self._collect_cases_to_run()
        if not self._cases:
            raise ScriptException(f"no cases found in {self.config.data_path}")
        else:
            log.info(f"number of cases: {len(self._cases)}")

        self._base_cmd = [
            "java",
            "-Xms32m",
            "-Xmx32g",
            "-Djava.library.path={}".format(
                self.config.cplex_lib_path),
            "-jar",
            self.config.jar_path,
        ]

        for case in self._cases:
            case_name = os.path.basename(case)
            case_path = os.path.dirname(case)
            cmd = [c for c in self._base_cmd]
            cmd.extend([
                "-n", case_name,
                "-p", case_path + "/",
            ])
            subprocess.check_call(cmd)
            break

    def _validate_setup(self):
        logs_path = os.path.join(self.config.base_path, "logs")
        os.makedirs(logs_path, exist_ok=True)

        if not os.path.isdir(self.config.data_path):
            raise ScriptException(f"data folder not found at {self.config.data_path}")
        else:
            log.info("data folder found.")

        if not self.config.cplex_lib_path:
            raise ScriptException("unable to find cplex library path")
        elif not os.path.isdir(self.config.cplex_lib_path):
            raise ScriptException(
                "invalid folder at cplex library path: {}".format(
                    self.config.cplex_lib_path))
        else:
            log.info("located cplex library path.")

    def _prepare_uberjar(self):
        os.chdir(self.config.base_path)
        subprocess.check_call(['gradle', 'clean', 'cleanlogs', 'uberjar'])
        if not os.path.isfile(self.config.jar_path):
            raise ScriptException("uberjar build failed")

        log.info("prepared uberjar")

    def _collect_cases_to_run(self):
        for top, _, files in os.walk(self.config.data_path):
            for f in files:
                if f.endswith(".txt"):
                    self._cases.append(os.path.join(top, f))

    def _prepare_db(self):
        db_path = os.path.join(self.config.base_path, 'python-scripts', 'results.db')
        self._conn = sqlite3.connect(db_path)


def handle_command_line():
    parser = argparse.ArgumentParser()

    parser.add_argument("-j", "--jarpath", type=str,
                        help="path to stochastic solver jar")

    parser.add_argument("-a", "--all", help="run all sets",
                        action="store_true")
    parser.add_argument("-b", "--budget", help="run budget set",
                        action="store_true")
    parser.add_argument("-m", "--mean", help="run mean set",
                        action="store_true")
    parser.add_argument("-p", "--parallel", help="run parallel run set",
                        action="store_true")
    parser.add_argument("-q", "--quality", help="run quality set",
                        action="store_true")
    parser.add_argument("-t", "--time", help="run time comparison set",
                        action="store_true")

    args = parser.parse_args()
    config = Config()

    if args.all:
        config.run_budget_set = True
        config.run_mean_set = True
        config.run_parallel_set = True
        config.run_quality_set = True
        config.run_time_comparison_set = True
    else:
        config.run_budget_set = args.budget
        config.run_mean_set = args.mean
        config.run_parallel_set = args.parallel
        config.run_quality_set = args.quality
        config.run_time_comparison_set = args.time

    if args.jarpath:
        config.jar_path = args.jarpath

    log.info("do budget runs: {}".format(config.run_budget_set))
    log.info("do mean runs: {}".format(config.run_mean_set))
    log.info("do quality runs: {}".format(config.run_quality_set))
    log.info("do time comparison runs: {}".format(
        config.run_time_comparison_set))

    return config


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
