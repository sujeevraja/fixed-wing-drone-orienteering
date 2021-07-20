#!/usr/bin/env python3

import argparse
import csv
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


class Config:
    """Class that holds global parameters."""

    def __init__(self):
        self.script_folder_path = os.path.dirname(os.path.realpath(__file__))
        self.base_path = os.path.abspath(
            os.path.join(self.script_folder_path, '..'))
        self.cplex_lib_path = guess_cplex_library_path()
        self.data_path = os.path.join(self.base_path, 'data')
        self.jar_path = os.path.join(
            self.base_path, 'build', 'libs', 'uber.jar')

        # Path to csv file with instance path, instance name and number of
        # discretizations. Instances from this file will be used to generate
        # run commands.
        self.instance_file_path = os.path.join(self.base_path,
                                               "final-results",
                                               "instances.csv")

        self.dominance_runs = False
        self.exhaustive_runs = False
        self.simple_search_runs = False
        self.single_thread_runs = False
        self.bang_for_buck_runs = False


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
        self._base_cmd = [
            "java", "-Xms32m", "-Xmx32g",
            "-Djava.library.path={}".format(self.config.cplex_lib_path),
            "-jar", "./uber.jar",
        ]

        self._prepare_uberjar()

        if self.config.bang_for_buck_runs:
            self._setup_bang_for_buck_runs()

        if self.config.dominance_runs:
            self._generate_non_exhaustive_setup(
                "dominance", cmd_args=[
                    "-s", "1",
                    "-i", "1",
                    "-rd", "0", ])

        if self.config.simple_search_runs:
            self._generate_non_exhaustive_setup(
                "simple", cmd_args=[
                    "-s", "1",
                    "-i", "0", ])

        if self.config.single_thread_runs:
            self._generate_non_exhaustive_setup(
                "onethread", cmd_args=[
                    "-s", "1",
                    "-i", "1", ])

        if self.config.exhaustive_runs:
            self._generate_exhaustive_setup(
                cases=self._collect_exhaustive_cases())

    def _setup_bang_for_buck_runs(self):
        cases = self._collect_exhaustive_cases(discretizations=["2", "4"])
        args = [
            ["-b", "1"],
            ["-b", "0"]
        ]
        self._generate_exhaustive_setup(cases, args, "bangforbuck")

    def _generate_non_exhaustive_setup(self, test_name, cmd_args):
        cases = self._collect_cases_from_file()
        runs_file_path = os.path.join(
            self.config.script_folder_path, '{}_runs.txt'.format(test_name))

        with open(runs_file_path, 'w') as f_out:
            counter = 0
            for folder_name, instance_name, num_disc in cases:
                folder_path = './data/{}/'.format(folder_name)
                results_file_name = "results_{}.yaml".format(counter)

                results_path = os.path.join("results", results_file_name)
                results_path = "./{}".format(results_path)

                cmd = [c for c in self._base_cmd]
                cmd.extend([
                    "-n", instance_name,
                    "-p", folder_path,
                    "-o", results_path,
                    "-d", str(num_disc),
                ])
                cmd.extend(cmd_args)
                f_out.write(' '.join(cmd))
                f_out.write('\n')
                counter += 1

        log.info("wrote cases to {}".format(runs_file_path))
        self._prepare_test_folder(
            test_name, self._get_folder_and_file_names(cases))

    def _generate_exhaustive_setup(self, cases, additional_cmds=[],
                                   test_name="exhaustive"):
        runs_file_path = os.path.join(
            self.config.script_folder_path, test_name + '_runs.txt')

        with open(runs_file_path, 'w') as f_out:
            counter = 0
            for folder, file_name, num_disc in cases:
                prefix = "./results/results"
                cmd = [c for c in self._base_cmd]
                cmd.extend([
                    "-n", file_name,
                    "-p", "./data/{}/".format(folder),
                    "-d", str(num_disc),
                    "-i", "1", ])

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

    def _collect_exhaustive_cases(self, discretizations=["2", "4", "6"]):
        cases = []
        for folder in os.listdir(self.config.data_path):
            if '_100_' in folder or '_102_' in folder:
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
        with open(self.config.instance_file_path, 'r', newline='') as csvfile:
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
        os.chdir(self.config.base_path)
        subprocess.check_call(['gradle', 'clean', 'cleanlogs', 'uberjar'])
        if not os.path.isfile(self.config.jar_path):
            raise ScriptException("uberjar build failed")
        log.info("prepared uberjar")

    def _prepare_test_folder(self, test_name, cases):
        rt_path = os.path.join(self.config.base_path, test_name)
        os.makedirs(rt_path, exist_ok=True)
        shutil.copy(self.config.jar_path, os.path.join(rt_path, 'uber.jar'))
        runs_file_name = '{}_runs.txt'.format(test_name)
        for f in [runs_file_name, 'submit-batch.sh', 'slurm-batch-job.sh']:
            src_path = os.path.join(self.config.script_folder_path, f)
            dst_path = os.path.join(rt_path, f)
            shutil.copy(src_path, dst_path)

        os.remove(os.path.join(self.config.script_folder_path, runs_file_name))
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


def handle_command_line():
    parser = argparse.ArgumentParser()

    parser.add_argument("-b", "--bangforbuck", action="store_true",
                        help="generate runs to test efficacy of bang-for-buck")
    parser.add_argument("-d", "--dominance", action="store_true",
                        help="generate runs file for dominance comparison")
    parser.add_argument("-e", "--exhaustive", action="store_true",
                        help="generate runs file for testing all instances")
    parser.add_argument("-i", "--instancefilepath", type=str,
                        help="path to csv file with instances to run")
    parser.add_argument("-s", "--simple", action="store_true",
                        help="generate runs file for simple search")
    parser.add_argument("-t", "--threading", action="store_true",
                        help="generate runs file for threading comparison")

    args = parser.parse_args()
    config = Config()

    config.bang_for_buck_runs = args.bangforbuck
    config.dominance_runs = args.dominance
    config.exhaustive_runs = args.exhaustive
    config.simple_search_runs = args.simple
    config.single_thread_runs = args.threading
    if args.instancefilepath:
        config.instance_file_path = args.instancefilepath

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
