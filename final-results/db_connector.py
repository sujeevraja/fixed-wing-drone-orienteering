import sqlite3
from sqlite3 import Error
import pandas as pd

class DataBase:
    """Class to hold a database and execute queries on it"""
    def __init__(self, db_file):
        self.connection = None
        try:
            self.connection = sqlite3.connect(db_file)
        except Error as e:
            print(e)
            
    def execute_query(self, query):
        current = self.connection.cursor()
        current.execute(query)
        return current.fetchall()
    
    def count_entries(self, query):
        rows = self.execute_query(query)
        return len(rows)

    def get_dataframe(self, query):
        return pd.read_sql_query(query, self.connection)