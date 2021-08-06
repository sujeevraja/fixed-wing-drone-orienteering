#!/usr/bin/env python3

import argparse
import logging
import operator
import os
import sqlite3
import typing
import yaml

log = logging.getLogger(__name__)


class ScriptException(Exception):
    """Custom exception class with message for this module."""

    def __init__(self, value):
        self.value = value
        super().__init__(value)

    def __repr__(self):
        return repr(self.value)


class Config(typing.NamedTuple):
    results_path: str
    db_path: str
    table_name: str


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


def get_table_fields(results_path: str) -> typing.List[str]:
    for f in os.listdir(results_path):
        if not f.endswith(".yaml"):
            continue

        with open(os.path.join(results_path, f), 'r') as fin:
            result_dict = yaml.load(fin, Loader=yaml.FullLoader)
            return list(result_dict.keys())

    raise ScriptException(f"no results found at {results_path}")


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

        # if self.config.table_name == "bangforbuck":
        #     self._write_args_table()

        self._connection.commit()
        self._cursor.close()
        self._connection.close()
        log.info("result addition completed")

    def _write_results_table(self):
        fields = get_table_fields(self.config.results_path)
        create_table(self._cursor, self.config.table_name,
                     sorted(fields + [self._result_id_col]))

        for f in os.listdir(self.config.results_path):
            if f.endswith(".yaml"):
                fpath = os.path.join(self.config.results_path, f)
                self._add_results_to_table(fpath)
                log.info(f"added results for {f}")

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


def handle_command_line() -> Config:
    parser = argparse.ArgumentParser(
        formatter_class=argparse.ArgumentDefaultsHelpFormatter)

    base_path = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
    db_path = os.path.join(base_path, "final-results-v2", "results.db")
    parser.add_argument("-d", "--db_path", type=str,
                        help="path to SQLite DB file", default=db_path)

    results_path = os.path.join(base_path, "final-results-v2", "results")
    parser.add_argument("-r", "--results_path", type=str,
                        help="path to folder with results",
                        default=results_path)

    parser.add_argument("-t", "--table_name", type=str,
                        help="name of table to add results to",
                        default="exhaustive")

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
