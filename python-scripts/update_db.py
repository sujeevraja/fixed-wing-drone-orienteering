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


class Controller:
    """class that manages the functionality of the entire script."""

    def __init__(self, config):
        self.config = config
        self._connection = None  # will point to a connection to a SQL database
        self._cursor = None

    def run(self):
        self._connection = sqlite3.connect(self.config.db_path)
        self._cursor = self._connection.cursor()

        if not self._table_exists():
            self._create_table()

        for f in os.listdir(self.config.results_path):
            if f.endswith(".yaml"):
                fpath = os.path.join(self.config.results_path, f)
                self._add_results_to_table(fpath)
                log.info(f"added results for {f}")

        self._connection.commit()
        self._cursor.close()
        self._connection.close()
        log.info("result addition completed")

    def _table_exists(self):
        cmd = f"""
            SELECT count(name)
            FROM sqlite_master
            WHERE type='table'
            AND name='{self.config.table_name}'"""
        self._cursor.execute(cmd)
        return self._cursor.fetchone()[0] == 1

    def _create_table(self):
        for f in os.listdir(self.config.results_path):
            if not f.endswith(".yaml"):
                continue

            with open(os.path.join(self.config.results_path, f), 'r') as fin:
                result_dict = yaml.load(fin, Loader=yaml.FullLoader)
                column_names = sorted(list(result_dict.keys()))
                self._cursor.execute(
                    f"""
                    CREATE TABLE
                    {self.config.table_name}
                    ({",".join(column_names)})""")

            return

        raise ScriptException(f"no yaml file found in results folder")

    @staticmethod
    def _build_create_table_command(name, num_columns):
        cmd_list = [f'CREATE TABLE {name}', '(']
        for _ in range(num_columns - 1):
            cmd_list.append('?,')
        cmd_list.append('?)')
        return ''.join(cmd_list)

    def _add_results_to_table(self, fpath):
        with open(fpath, "r") as f_result:
            result_dict = yaml.load(f_result, Loader=yaml.FullLoader)
            keys_and_values = [(key, val)
                               for (key, val) in result_dict.items()]
            keys_and_values.sort(key=operator.itemgetter(0))
            _, values = zip(*keys_and_values)
            values = [f"'{v}'" for v in values]

            cmd = f"""
                INSERT INTO {self.config.table_name}
                VALUES ({",".join(values)})"""
            self._cursor.execute(cmd)
            # self._cursor.execute(
            #     f"""INSERT INTO results VALUES ({",".join(values)})""")


def handle_command_line():
    parser = argparse.ArgumentParser()

    parser.add_argument("-r", "--resultspath", type=str,
                        help="path to folder with results")
    parser.add_argument("-t", "--tablename", type=str,
                        help="name of table to add results to")

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
