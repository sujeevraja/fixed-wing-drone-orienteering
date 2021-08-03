#!/usr/bin/env python3

import argparse
import logging
import operator
import os
import sqlite3
import yaml

log = logging.getLogger(__name__)


class Config(object):
    """Class that holds global parameters."""

    def __init__(self):
        folder_path = os.path.dirname(os.path.realpath(__file__))
        self.base_path = os.path.abspath(os.path.join(folder_path, '..'))
        self.results_path = os.path.join(self.base_path, 'results')
        self.db_path = os.path.join(self.base_path, 'final-results', 'results.db')
        self.table_name = 'search_comparison'


class ScriptException(Exception):
    """Custom exception class with message for this module."""

    def __init__(self, value):
        self.value = value
        super().__init__(value)

    def __repr__(self):
        return repr(self.value)


def table_exists(cursor, name):
    cmd = f"""
        SELECT count(name)
        FROM sqlite_master
        WHERE type='table'
        AND name='{name}'"""
    cursor.execute(cmd)
    return cursor.fetchone()[0] == 1


def create_table(cursor, name, fields):
    if not table_exists(cursor, name):
        field_str = ",".join(fields)
        cursor.execute(
            f"""CREATE TABLE {name} ({field_str})""")


class Controller:
    """class that manages the functionality of the entire script."""

    def __init__(self, config):
        self.config = config
        self._connection = None  # will point to a connection to a SQL database
        self._cursor = None
        self._result_id_col = "result_id"

    def run(self):
        self._connection = sqlite3.connect(self.config.db_path)
        self._cursor = self._connection.cursor()

        self._write_results_table()

        if self.config.table_name == "bangforbuck":
            self._write_args_table()

        self._connection.commit()
        self._cursor.close()
        self._connection.close()
        log.info("result addition completed")
    
    def _write_results_table(self):
        table_created = False
        for f in os.listdir(self.config.results_path):
            if not f.endswith(".yaml"):
                continue

            with open(os.path.join(self.config.results_path, f), 'r') as fin:
                result_dict = yaml.load(fin, Loader=yaml.FullLoader)
                col_names = list(result_dict.keys()) + [self._result_id_col]
                col_names.sort()
                create_table(self._cursor, self.config.table_name, col_names)
                table_created = True
            
            break

        if not table_created:
            raise ScriptException(f"no yaml file found in results folder")

        for f in os.listdir(self.config.results_path):
            if f.endswith(".yaml"):
                fpath = os.path.join(self.config.results_path, f)
                self._add_results_to_table(fpath)
                log.info(f"added results for {f}")


    def _write_args_table(self):
        table_name = self.config.table_name + "_args"
        fields = [self._result_id_col, "bfb"]
        create_table(self._cursor, table_name, fields)

        runs_path = os.path.join(
            os.path.dirname(__file__),
            "..",
            "bangforbuck",
            "bangforbuck_runs.txt"
        )

        with open(runs_path, "r") as runsfile:
            for line in runsfile:
                cmds = line.strip().split()
                result_name = cmds[-1].split(".")[1].split("/")[-1]
                result_id = int(result_name.split("_")[-1])

                bfb_used = None
                for i, cmd in enumerate(cmds):
                    if cmd == "-b":
                        bfb_used = int(cmds[i+1])
                        break

                row = [str(result_id), str(bfb_used)]
                insert_cmd = f"""
                    INSERT INTO {table_name}
                    VALUES ({",".join(row)})"""
                self._cursor.execute(insert_cmd)

    def _add_results_to_table(self, fpath):
        with open(fpath, "r") as f_result:
            name = os.path.basename(fpath.strip()).split(".")[0]
            id = int(name.split("_")[1])
            result_dict = yaml.load(f_result, Loader=yaml.FullLoader)
            keys_and_vals = [(k, v) for (k, v) in result_dict.items()] + \
                [(self._result_id_col, id)]
            keys_and_vals.sort(key=operator.itemgetter(0))
            _, values = zip(*keys_and_vals)
            values = [f"'{v}'" for v in values]

            cmd = f"""
                INSERT INTO {self.config.table_name}
                VALUES ({",".join(values)})"""
            self._cursor.execute(cmd)


def handle_command_line():
    parser = argparse.ArgumentParser()

    parser.add_argument("-r", "--resultspath", type=str,
                        help="path to folder with results",
                        default="../results")
    parser.add_argument("-t", "--tablename", type=str,
                        help="name of table to add results to",
                        default="bangforbuck")

    args = parser.parse_args()
    config = Config()

    if args.resultspath:
        config.results_path = args.resultspath

    if args.tablename:
        config.table_name = args.tablename

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
