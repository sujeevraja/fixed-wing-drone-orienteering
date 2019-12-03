from db_connector import DataBase
from queries import *
import pandas as pd
import numpy as np
 

db = DataBase('results.db')
num_targets = [21, 32, 33, 64]
num_discretizations = [2, 4, 6]
result_category = ['optimal', 'infeasible', 'timed-out']

row_list = []
for t in num_targets:
    row = [t]
    for d in num_discretizations:
        for c in result_category:
            query = generate_exhaustive_table_count_query(category=c, num_targets=t, num_discretizations=d)
            results = db.execute_query(query)
            row.append(results[0][0])
    row_list.append(row)

exhaustive_df = pd.DataFrame(row_list, columns=['num_targets', 'opt_2', 'infeas_2', 'to_2', 'opt_4', 'infeas_4', 'to_4', 'opt_6', 'infeas_6', 'to_6'])

exhaustive_df.to_csv('csv/exhaustive_counts.csv', index=False)

for t in num_targets:
    query = generate_exhaustive_opt_table_query(t)
    df = db.get_dataframe(query)
    columns = df.columns
    for column in columns:
        if ('nodes' in column):
            df[column] = df[column].apply(pd.to_numeric, downcast='integer').fillna(0)
        if (column == 'instance_name'):
            continue 
        else:
            df[column] = df[column].apply(pd.to_numeric, downcast='float').fillna(0)
    for d in num_discretizations:
        time_col_name = 'time_{}'.format(d)
        rub_col_name = 'rub_{}'.format(d)
        rlb_col_name = 'rlb_{}'.format(d)
        opt_col_name = 'opt_{}'.format(d)
        nodes_col_name = 'nodes_{}'.format(d)
        index_list = df.query('time_{} > 3600.00'.format(d)).index
        df.loc[index_list, time_col_name] = 3600.00
        df.loc[(df[rub_col_name] == 0.0), rub_col_name] = np.nan
        index_list = df.query('rlb_{} == 0.00'.format(d)).index
        df.loc[index_list, rub_col_name] = np.nan
        df.loc[index_list, rlb_col_name] = np.nan
        df.loc[index_list, opt_col_name] = np.nan
    for column in columns:
        if ('rlb' in column):
            del df[column]
            continue 
        if (column == 'instance_name' or 'nodes' in column):
            continue 
        else:
            df[column] = df[column].apply(pd.to_numeric, downcast='float')
    df['instance_name'] = df['instance_name'].str.replace(r'.txt$', '')
    df.to_csv('csv/full_{}.csv'.format(t), float_format='%.2f', na_rep='--', index=False)
