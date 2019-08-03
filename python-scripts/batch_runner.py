#!/usr/bin/env python3

import argparse
import logging
import operator
import os
import sqlite3
import subprocess
import yaml

log = logging.getLogger(__name__)


class Config(object):
    """Class that holds global parameters."""

    def __init__(self):
        folder_path = os.path.dirname(os.path.realpath(__file__))
        self.base_path = os.path.abspath(os.path.join(folder_path, '..'))
        self.cplex_lib_path = None
        self.data_path = os.path.join(self.base_path, 'data')
        self.db_path = os.path.join(self.base_path, 'logs', 'results.db')
        self.jar_path = os.path.join(self.base_path, 'build', 'libs', 'uber.jar')
        self.results_yaml_path = os.path.join(self.base_path, "logs", "results.yaml")


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
            "-t", "5"
        ]

        self._prepare_db()
        self._run_case(self._cases[0])
        cursor = self._connection.cursor()
        with open(self.config.results_yaml_path, "r") as f_result:
            result_dict = yaml.load(f_result, Loader=yaml.FullLoader)
            column_names = sorted(list(result_dict.keys()))
            cursor.execute(f"""CREATE TABLE results ({",".join(column_names)})""")
        self._add_results_to_table()
        log.info(f"completed 1 out of {len(self._cases)}")

        completed_count = 1
        for case in self._cases[1:]:
            self._run_case(case)
            self._add_results_to_table()
            self._connection.commit()
            completed_count += 1
            log.info(f"completed {completed_count} out of {len(self._cases)}")

        self._connection.close()

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
        if os.path.isfile(self.config.db_path):
            os.unlink(self.config.db_path)
        self._connection = sqlite3.connect(self.config.db_path)

    def _run_case(self, case):
        case_name = os.path.basename(case)
        case_path = os.path.dirname(case)
        cmd = [c for c in self._base_cmd]
        cmd.extend([
            "-n", case_name,
            "-p", case_path + "/",
                  ])
        subprocess.check_call(cmd)

    @staticmethod
    def _build_create_table_command(num_columns):
        cmd_list = ['CREATE TABLE results', '(']
        for _ in range(num_columns - 1):
            cmd_list.append('?,')
        cmd_list.append('?)')
        return ''.join(cmd_list)

    def _add_results_to_table(self):
        cursor = self._connection.cursor()
        with open(self.config.results_yaml_path, "r") as f_result:
            result_dict = yaml.load(f_result, Loader=yaml.FullLoader)
            keys_and_values = [(key, val) for (key, val) in result_dict.items()]
            keys_and_values.sort(key=operator.itemgetter(0))
            _, values = zip(*keys_and_values)
            values = [f"'{v}'" for v in values]

            cursor.execute(f"""INSERT INTO results VALUES ({",".join(values)})""")


def handle_command_line():
    parser = argparse.ArgumentParser()

    parser.add_argument("-j", "--jarpath", type=str,
                        help="path to stochastic solver jar")

    parser.add_argument("-a", "--all", help="run all sets",
                        action="store_true")

    args = parser.parse_args()
    config = Config()

    if args.jarpath:
        config.jar_path = args.jarpath

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
