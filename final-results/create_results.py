from db_connector import DataBase
from queries import *
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import math
import os
 

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

query = generate_idssr_query()
df = db.get_dataframe(query)
df['instance_name'] = df['instance_name'].str.replace(r'.txt$', '')
for column in df.columns:
    if (column == 'instance_name'): 
        continue
    if ('opt' in column):
        del df[column]
        continue
    df[column] = df[column].apply(pd.to_numeric, downcast='float')
df['improvement_factor'] = (df['simple_time']-df['one_thread_time'])/df['simple_time']*100.00
index_list = df.query('simple_time > 3600.00').index
df.loc[index_list, 'simple_time'] = 3600.00
df.loc[index_list, 'improvement_factor'] = np.nan
print('mean improvement factor for I-DSSR = {}'.format(df['improvement_factor'].mean()))
df.to_csv('csv/idssr.csv', float_format='%.2f', na_rep='--', index=False)

query = generate_concurrency_query()
df = db.get_dataframe(query)
df['instance_name'] = df['instance_name'].str.replace(r'.txt$', '')
for column in df.columns:
    if (column == 'instance_name'): 
        continue
    if ('opt' in column):
        del df[column]
        continue
    if (column == 'num_targets'):
        df[column] = df[column].apply(pd.to_numeric, downcast='integer')
        continue
    df[column] = df[column].apply(pd.to_numeric, downcast='float')
df['improvement_factor'] = (df['one_thread_time']-df['concurrent_time'])/df['one_thread_time']*100.00
hist_values = list(df['improvement_factor'].values)
avg_improvement = df['improvement_factor'].mean()
num_bins = math.ceil((max(hist_values)-min(hist_values))/1.5)
bins = np.linspace(min(hist_values), max(hist_values), num_bins)
# plot setup
plt.rc('font',**{'family':'serif','serif':['Palatino']})
plt.rc('text', usetex=True)
fig, ax = plt.subplots(figsize=(3.5,5))
ax.set_xlabel(r'Relative run time improvement (\%)', fontsize=10)
ax.hist(hist_values, bins=23, density=False, alpha=0.5, edgecolor='black', color='#00AFBB', label=r'$|T|=70$')
plt.figtext(0.5, 0.8, 'Average = {:.2f}\%'.format(avg_improvement), fontsize=10)
plt.savefig('concurrency_histogram.pdf', format='pdf')
crop_cmd = 'pdfcrop concurrency_histogram.pdf; mv -f concurrency_histogram-crop.pdf concurrency_histogram.pdf'
os.system(crop_cmd)
os.system('mv -f concurrency_histogram.pdf plots/concurrency_histogram.pdf')
