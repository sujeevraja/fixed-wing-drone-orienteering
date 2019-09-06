#!/usr/bin/env python3

import argparse
import logging
import os
import shutil
import subprocess


log = logging.getLogger(__name__)


class ScriptException(Exception):
    """Custom exception class with message for this module."""

    def __init__(self, value):
        self.value = value
        super().__init__(value)

    def __repr__(self):
        return repr(self.value)


class Config(object):
    """Class that holds global parameters."""

    def __init__(self):
        self.script_folder_path = os.path.dirname(os.path.realpath(__file__))
        self.base_path = os.path.abspath(
            os.path.join(self.script_folder_path, '..'))
        self.cplex_lib_path = guess_cplex_library_path()
        self.data_path = os.path.join(self.base_path, 'data')
        self.jar_path = os.path.join(
            self.base_path, 'build', 'libs', 'uber.jar')

        self.simple_search_runs = False
        self.interleaved_search_runs = False


def guess_cplex_library_path():
    gp_path = os.path.join(os.path.expanduser(
        "~"), ".gradle", "gradle.properties")
    if not os.path.isfile(gp_path):
        raise ScriptException(
            "gradle.properties not available at {}".format(gp_path))

    with open(gp_path, 'r') as fin:
        for line in fin:
            line = line.strip()
            if line.startswith('cplexLibPath='):
                return line.split('=')[-1].strip()

    raise ScriptException("unable to read value of cplexLibPath ")


class Controller:
    """class that manages the functionality of the entire script."""

    def __init__(self, config):
        self.config = config
        self._base_cmd = None

    def run(self):
        cases = self._collect_cases_to_run()
        if not cases:
            raise ScriptException(
                "no cases found in {}".format(self.config.data_path))
        else:
            log.info("number of cases: {}".format(len(cases)))

        self._base_cmd = [
            "java", "-Xms32m", "-Xmx32g",
            "-Djava.library.path={}".format(self.config.cplex_lib_path),
            "-jar", "./uber.jar",
        ]

        if self.config.simple_search_runs:
            self._generate_search_run_files(cases, True)
        elif self.config.interleaved_search_runs:
            self._generate_search_run_files(cases, False)

        self._prepare_uberjar()
        self._prepare_test_folder()

    def _collect_cases_to_run(self):
        cases = []
        for top, _, files in os.walk(self.config.data_path):
            folder_name = os.path.basename(top)
            if '_100_' in folder_name or '_102_' in folder_name:
                continue

            for f in files:
                if f.endswith(".txt"):
                    cases.append((folder_name, f))

        return cases

    def _generate_search_run_files(self, cases, simple_search):
        runs_file_path = os.path.join(
            self.config.script_folder_path, 'exhaustive_runs.txt')

        search_arg = "0" if simple_search else "1"

        with open(runs_file_path, 'w') as f_out:
            for folder_name, instance_name in cases:
                folder_path = './data/{}/'.format(folder_name)
                cleaned_name = instance_name[:-4].replace('.', '_')

                for num_disc in [2, 4, 6]:
                    results_file_name = "results_{}_{}_d_{}_i_{}.yaml".format(
                        folder_name, cleaned_name, num_disc, search_arg)
                    results_path = os.path.join("results", results_file_name)
                    results_path = "./{}".format(results_path)

                    cmd = [c for c in self._base_cmd]
                    cmd.extend([
                        "-n", instance_name,
                        "-p", folder_path,
                        "-o", results_path,
                        "-d", str(num_disc),
                        "-i", "0" if simple_search else "1",
                    ])
                    f_out.write(' '.join(cmd))
                    f_out.write('\n')
        log.info("wrote cases to {}".format(runs_file_path))

    def _prepare_uberjar(self):
        os.chdir(self.config.base_path)
        subprocess.check_call(['gradle', 'clean', 'cleanlogs', 'uberjar'])
        if not os.path.isfile(self.config.jar_path):
            raise ScriptException("uberjar build failed")
        log.info("prepared uberjar")

    def _prepare_test_folder(self):
        rt_path = os.path.join(self.config.base_path, 'regression_testing')
        os.makedirs(rt_path, exist_ok=True)
        shutil.copy(self.config.jar_path, os.path.join(rt_path, 'uber.jar'))
        runs_file_name = 'exhaustive_runs.txt'
        for f in [runs_file_name, 'submit-batch.sh', 'slurm-batch-job.sh']:
            src_path = os.path.join(self.config.script_folder_path, f)
            dst_path = os.path.join(rt_path, f)
            shutil.copy(src_path, dst_path)

        os.remove(os.path.join(self.config.script_folder_path, runs_file_name))
        log.info('copied runs file and shell scripts to {}'.format(rt_path))

        test_data_path = os.path.join(rt_path, 'data')
        os.makedirs(test_data_path, exist_ok=True)
        for folder_name in os.listdir(self.config.data_path):
            if '_100_' not in folder_name and '_102_' not in folder_name:
                shutil.copytree(
                    os.path.join(self.config.data_path, folder_name),
                    os.path.join(test_data_path, folder_name))
        log.info('copied data files to {}'.format(rt_path))

        folder_names = ['output', 'results']
        for folder_name in folder_names:
            folder_path = os.path.join(rt_path, folder_name)
            os.makedirs(folder_path, exist_ok=True)
        log.info('created folders {}'.format(folder_names))


def handle_command_line():
    parser = argparse.ArgumentParser()

    parser.add_argument("-i", "--interleaved", action="store_true",
                        help="generate runs file for interleaved search")
    parser.add_argument("-s", "--simple", action="store_true",
                        help="generate runs file for simple search")

    args = parser.parse_args()
    config = Config()
    config.interleaved_search_runs = args.interleaved
    config.simple_search_runs = args.simple
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
